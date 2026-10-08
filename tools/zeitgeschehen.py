#!/usr/bin/env python3
"""Was in einem Jahr geschah: Auswahl aus dem Wikipedia-Jahresartikel ("1979") fuer die Zeitreise.

Aufruf:
    tools/zeitgeschehen.py --von 1872 --bis 2026      [--offline]

Aus den Abschnitten unter "Ereignisse" (Politik, Wirtschaft, Kultur, Wissenschaft …) werden die
Aufzaehlungspunkte gelesen und je Jahr hoechstens 12 ausgewaehlt, in dieser Reihenfolge:
  1. Ereignisse, die einen Artikel verlinken, der auch Thema einer Marke dieses Jahres ist
     (die Europawahl 1979 und die Marke zur ersten Direktwahl) - die Bruecke zwischen Marke und Zeit
  2. Ereignisse mit Deutschlandbezug (Bundesrepublik, DDR, Berlin, Bonn, Reich …)
Ergebnis: daten/zeitgeschehen/<jahr>.json mit Quelle und Lizenz (CC BY-SA 4.0).
"""
import argparse
import json
import re
import sys
import urllib.parse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from wikipedia_import import DATEN, MONATE, RE_LINK, RE_REF, api_abfrage, klartext  # noqa: E402

ZIEL = DATEN / "zeitgeschehen"
HOECHSTENS = 14
DEUTSCH = re.compile(r"(?i)deutschland|deutsche[nrs]?\b|bundesrepublik|\bddr\b|\bbrd\b|berlin|\bbonn\b|bundestag|"
                     r"reichstag|reichskanzler|kaiserreich|weimarer|\bsed\b|\bspd\b|\bcdu\b|\bcsu\b|\bfdp\b|"
                     r"münchen|hamburg|frankfurt|köln|leipzig|dresden|wiedervereinigung")
# Ohne Erzaehlwert: Vertraege und Abkommen zwischen Staaten, Amtswechsel im Ausland
LANGWEILIG = re.compile(r"(?i)doppelbesteuerung|abkommen|übereinkommen|vertrag zwischen|schließt ein .*abkommen|seeverkehrs|diplomatische beziehungen|"
                        r"\b(schweiz|österreich)\b(?!.*deutsch)")
# Themen, die zu oft vorkommen (Laender, Staedte), stiften keinen Bezug
HAEUFIG = 15
RUBRIK_UEBERSPRINGEN = re.compile(r"(?i)geboren|gestorben|galerie|weblinks|einzelnachweise|siehe auch|kalender|jahrestag|nobelpreis")


_HAEUFIGKEIT = None


def haeufigkeit():
    """Wie viele Marken (alle Gebiete, alle Jahre) ein Thema tragen."""
    global _HAEUFIGKEIT
    if _HAEUFIGKEIT is None:
        from collections import Counter
        z = Counter()
        for datei in DATEN.glob("*/*.json"):
            if datei.name.endswith((".hand.json", ".commons.json")) or datei.parent.name == "zeitgeschehen":
                continue
            for m in json.loads(datei.read_text(encoding="utf-8"))["marken"]:
                z.update({t["artikel"] for t in m["themen"]})
        _HAEUFIGKEIT = z
    return _HAEUFIGKEIT


def themen_des_jahres(jahr):
    """Hauptthemen der Marken dieses Jahres (alle Gebiete), ohne Allerweltsthemen wie Laender und Staedte."""
    titel = set()
    for datei in DATEN.glob(f"*/{jahr}.json"):
        if datei.parent.name == "zeitgeschehen":
            continue
        hand_datei = datei.with_name(f"{jahr}.hand.json")
        hand = json.loads(hand_datei.read_text(encoding="utf-8")) if hand_datei.exists() else {}
        for m in json.loads(datei.read_text(encoding="utf-8"))["marken"]:
            haupt = (hand.get(m["mi_nr"]) or {}).get("hauptthema") if isinstance(hand.get(m["mi_nr"]), dict) else None
            for t in m["themen"]:
                ist_haupt = (t["artikel"] == haupt or t["titel"] == haupt) if haupt else t.get("haupt")
                if ist_haupt and haeufigkeit()[t["artikel"]] <= HAEUFIG:
                    titel.add(t["artikel"])
            if haupt and haeufigkeit()[haupt] <= HAEUFIG:
                titel.add(haupt)
    return titel


def ereignisse(wikitext):
    """Liefert (rubrik, datum_text, text_wiki) fuer jeden Aufzaehlungspunkt unter == Ereignisse ==."""
    start = re.search(r"^==\s*Ereignisse\s*==\s*$", wikitext, re.M)
    if not start:
        return []
    ende = re.search(r"^==\s*[^=].*?==\s*$", wikitext[start.end():], re.M)
    block = wikitext[start.end(): start.end() + ende.start()] if ende else wikitext[start.end():]
    rubrik, ergebnis = "", []
    for zeile in block.split("\n"):
        k = re.match(r"^(={3,4})\s*(.*?)\s*\1\s*$", zeile)
        if k:
            if len(k.group(1)) == 3:
                rubrik = klartext(k.group(2))
            continue
        if not zeile.startswith("*") or zeile.startswith("**") or RUBRIK_UEBERSPRINGEN.search(rubrik):
            continue
        text = RE_REF.sub("", zeile.lstrip("* ").strip())
        text = re.sub(r"\{\{0+\}\}", "", text)                 # Fuellvorlage {{0}} vor einstelligen Tagen
        # "9. November:", "[[9. November]]:", Doppeldatum "9./10. November:"
        m = re.match(r"^(\[\[)?(\d{1,2})\.(?:\s*/\s*\d{1,2}\.)?\s*(\[\[)?([A-Za-zäöüÄÖÜ]+)(\]\])?\s*:\s*(.*)$", text)
        datum, inhalt = (f"{m.group(2)}. {m.group(4)}", m.group(6)) if m else ("", text)
        if len(klartext(inhalt)) >= 25:
            ergebnis.append((rubrik, datum, inhalt))
    return ergebnis


def einleitung(wikitext):
    """Erste ein, zwei Saetze vor dem ersten Abschnitt, wenn sie das Jahr einordnen ("Das Jahr 1989 stand …")."""
    kopf = wikitext.split("\n==", 1)[0]
    for absatz in kopf.split("\n"):
        a = absatz.strip()
        if a.startswith(("{", "|", "!", "[[Datei", "[[File", "<", "*", ":")) or len(a) < 80:
            continue
        text = klartext(RE_REF.sub("", a))
        saetze = re.split(r"(?<=[a-zäöüß\)])\.\s+(?=[A-ZÄÖÜ])", text)
        kurz = ". ".join(saetze[:2]).rstrip(".") + "."
        return kurz if len(kurz) > 60 else None
    return None


def iso(datum_text, jahr):
    m = re.match(r"(\d{1,2})\.\s*([A-Za-zäöüÄÖÜ]+)", datum_text)
    monat = MONATE.get(m.group(2).lower()) if m else None
    return f"{jahr:04d}-{monat:02d}-{int(m.group(1)):02d}" if monat else None


def auswahl(jahr, offline=False):
    try:
        daten = api_abfrage({"action": "query", "prop": "revisions", "rvprop": "content|ids", "rvslots": "main",
                             "titles": str(jahr), "redirects": "1"}, offline=offline, cache_name=f"jahr_{jahr}.json")
        seite = daten["query"]["pages"][0]
        rev = seite["revisions"][0]
    except (KeyError, IndexError, SystemExit):
        return None
    wikitext = rev["slots"]["main"]["content"]
    themen = themen_des_jahres(jahr)
    # Was die Einleitung des Jahresartikels verlinkt, praegt das Jahr (1989: Berliner Mauer)
    kopf = wikitext.split("\n==", 1)[0]
    praegend = {t.strip() for t, _ in RE_LINK.findall(kopf) if not t.lower().startswith(("datei:", "file:", "kategorie:"))}
    kandidaten = []
    for rubrik, datum_text, inhalt in ereignisse(wikitext):
        verlinkt = {t.strip() for t, _ in RE_LINK.findall(inhalt)}
        bezug = sorted(verlinkt & themen)
        text = klartext(inhalt)
        if LANGWEILIG.search(text):
            continue
        deutsch = bool(DEUTSCH.search(text))
        rang = 0 if bezug else 1 if deutsch else 2
        e_praegend = deutsch and bool(verlinkt & praegend)
        if rang < 2:
            kandidaten.append({"datum": iso(datum_text, jahr), "rubrik": rubrik, "text": text, "bezug": bezug, "_rang": rang, "_praegend": e_praegend})
    # Erst alle mit Bezug zu einer Marke (hoechstens die Haelfte), dann die deutschen reihum ueber die Monate,
    # damit nicht der Januar das Jahr fuellt; Politik zuerst, weil sie die Zeit am staerksten praegt.
    mit_bezug = [e for e in kandidaten if e["_rang"] == 0][: HOECHSTENS // 2]
    nach_monat = {}
    for e in sorted((e for e in kandidaten if e["_rang"] == 1), key=lambda e: (not e["_praegend"], e["rubrik"] != "Politik", e["datum"] or "9999")):
        nach_monat.setdefault((e["datum"] or "9999-99")[5:7], []).append(e)
    gewaehlt = list(mit_bezug)
    while len(gewaehlt) < HOECHSTENS and any(nach_monat.values()):
        for monat in sorted(nach_monat):
            if nach_monat[monat] and len(gewaehlt) < HOECHSTENS:
                gewaehlt.append(nach_monat[monat].pop(0))
    gewaehlt.sort(key=lambda e: e["datum"] or "9999")
    for e in gewaehlt:
        del e["_rang"], e["_praegend"]
    return {
        "jahr": jahr,
        "einleitung": einleitung(wikitext),
        "quelle": {"titel": seite["title"], "url": "https://de.wikipedia.org/wiki/" + urllib.parse.quote(seite["title"]),
                   "revision": rev["revid"], "lizenz": "CC BY-SA 4.0"},
        "ereignisse": gewaehlt,
    }


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--von", type=int, default=1872)
    p.add_argument("--bis", type=int, default=2026)
    p.add_argument("--offline", action="store_true")
    args = p.parse_args()
    ZIEL.mkdir(parents=True, exist_ok=True)
    summe = bezug = 0
    for jahr in range(args.von, args.bis + 1):
        erg = auswahl(jahr, args.offline)
        if not erg:
            print(f"{jahr}: kein Jahresartikel", file=sys.stderr)
            continue
        (ZIEL / f"{jahr}.json").write_text(json.dumps(erg, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        summe += len(erg["ereignisse"])
        bezug += sum(1 for e in erg["ereignisse"] if e["bezug"])
    print(f"{summe} Ereignisse ausgewählt, davon {bezug} mit Bezug zu einer Marke", file=sys.stderr)


if __name__ == "__main__":
    main()
