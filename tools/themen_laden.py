#!/usr/bin/env python3
"""Lädt Kurztexte zu den Themen aller Jahrgänge aus der deutschen Wikipedia.

Aufruf:
    tools/themen_laden.py            # fehlende Themen nachladen
    tools/themen_laden.py --alle     # alle Themen neu laden

Quelle ist die Einleitung des Artikels (TextExtracts-API, Klartext), gekürzt
auf drei Sätze. Ergebnis: daten/themen.json, ein Eintrag je Thema mit
Artikel, Wikidata-ID, Kurztext, Revision, Abrufdatum und Lizenz (CC BY-SA 4.0).

Eigene Ergänzungen ("Wusstest du?", eigener Kurztext) gehören nach
daten/themen.hand.json und werden hier nie überschrieben.
"""
import argparse
import json
import re
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from wikipedia_import import DATEN, api_abfrage  # noqa: E402

ZIEL = DATEN / "themen.json"
SAETZE = 3


def themen_sammeln():
    """Alle Themen aus den Jahrgangsdateien, Schlüssel = Wikidata-ID oder Artikel."""
    themen = {}
    for datei in sorted(DATEN.glob("*/*.json")):
        if datei.name.endswith((".hand.json", ".commons.json")) or datei.parent.name == "zeitgeschehen":
            continue
        for marke in json.loads(datei.read_text(encoding="utf-8"))["marken"]:
            for t in marke["themen"]:
                schluessel = t.get("wikidata") or "titel:" + t["artikel"]
                themen.setdefault(schluessel, {"wikidata": t.get("wikidata"), "artikel": t["artikel"]})
        # Hauptthemen aus den Handkorrekturen, die nicht in der Wikipedia-Liste verlinkt sind
        hand = datei.with_name(datei.stem + ".hand.json")
        if hand.exists():
            for k, v in json.loads(hand.read_text(encoding="utf-8")).items():
                if not k.startswith("_") and v.get("hauptthema"):
                    name = v["hauptthema"]
                    if not any(t["artikel"] == name for t in themen.values()):
                        themen.setdefault("titel:" + name, {"wikidata": None, "artikel": name})
    return themen


def kurztext_bereinigen(text):
    """Klartext der Einleitung: Lautschrift und Hörbeispiele raus, Leerraum glätten."""
    text = re.sub(r"\[[^\]]*ˈ[^\]]*\]", "", text)          # Lautschrift in eckigen Klammern
    text = re.sub(r"\s*\(\s*\)", "", text)                  # leere Klammern danach
    text = re.sub(r"\s*\?\s*/\s*i\s*", " ", text)           # Reste von Audio-Links
    text = re.sub(r"\s+([,;.])", r"\1", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = text.replace(" ,", ",").strip()
    return text


ABKUERZUNGEN = {"z", "B", "bzw", "u", "a", "ca", "Dr", "Prof", "St", "Nr", "geb", "gest", "Jh", "Hl", "bzgl", "vgl",
                "usw", "etc", "ev", "kath", "Mio", "Mrd", "Abs", "Art", "sog", "ggf", "inkl", "evtl", "Hrsg", "Bd"}


def saetze_kuerzen(text, anzahl):
    """Schneidet nach dem n-ten Satz ab. Ein Punkt nach Ziffer (14. März) oder Abkuerzung (z. B.) zaehlt nicht."""
    text = text.split("\n")[0].strip()                      # nur der erste Absatz
    gefunden = 0
    for m in re.finditer(r"[.!?](?=\s+[A-ZÄÖÜ„(0-9]|$)", text):
        ende = m.end()
        wort = re.search(r"(\S+)$", text[:ende - 1])
        wort = wort.group(1).strip("(„\"") if wort else ""
        if wort.isdigit() or wort in ABKUERZUNGEN or re.fullmatch(r"[A-Za-zÄÖÜäöü]", wort) or re.fullmatch(r"[IVX]+", wort):
            continue
        gefunden += 1
        if gefunden >= anzahl:
            return text[:ende].strip()
    return text


def laden(artikel_liste, offline=False, frisch=False):
    """Liefert dict Artikel -> {kurztext, revision, titel}. Bis 20 Artikel je Anfrage (exintro erlaubt mehrere)."""
    ergebnis = {}
    for i in range(0, len(artikel_liste), 20):
        block = artikel_liste[i:i + 20]
        name = "extract_" + re.sub(r"[^\w]+", "_", "_".join(block))[:80] + ".json"
        if frisch:
            (Path(__file__).resolve().parent / "cache" / name).unlink(missing_ok=True)
        daten = api_abfrage({
            "action": "query", "prop": "extracts|revisions|pageprops", "rvprop": "ids", "ppprop": "wikibase_item",
            # Ganze Einleitung holen und selbst kuerzen: die API zaehlt "14. März" als Satzende.
            "exintro": "1", "explaintext": "1", "exlimit": "20",
            "titles": "|".join(block), "redirects": "1",
        }, offline=offline, cache_name=name)
        weiter = {r["from"]: r["to"] for r in daten["query"].get("redirects", [])}
        normal = {n["from"]: n["to"] for n in daten["query"].get("normalized", [])}
        seiten = {s["title"]: s for s in daten["query"]["pages"]}
        for artikel in block:
            ziel = weiter.get(normal.get(artikel, artikel), normal.get(artikel, artikel))
            seite = seiten.get(ziel)
            if not seite or seite.get("missing") or not seite.get("extract"):
                print(f"  kein Text: {artikel}", file=sys.stderr)
                continue
            ergebnis[artikel] = {
                "titel": seite["title"],
                "kurztext": saetze_kuerzen(kurztext_bereinigen(seite["extract"]), SAETZE),
                "revision": (seite.get("revisions") or [{}])[0].get("revid"),
                "wikidata": seite.get("pageprops", {}).get("wikibase_item"),
            }
    return ergebnis


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--alle", action="store_true", help="auch schon geladene Themen neu holen")
    p.add_argument("--offline", action="store_true")
    args = p.parse_args()

    bestand = json.loads(ZIEL.read_text(encoding="utf-8")) if ZIEL.exists() else {}
    themen = themen_sammeln()
    offen = [s for s in themen if args.alle or s not in bestand or not bestand[s].get("kurztext")]
    print(f"{len(themen)} Themen, {len(offen)} zu laden", file=sys.stderr)
    texte = laden([themen[s]["artikel"] for s in offen], offline=args.offline, frisch=args.alle)
    heute = date.today().isoformat()
    for s in offen:
        t = themen[s]
        text = texte.get(t["artikel"])
        eintrag = bestand.get(s, {})
        eintrag.update({"wikidata": t["wikidata"] or (text or {}).get("wikidata"), "artikel": t["artikel"]})
        if text:
            eintrag.update({
                "titel": text["titel"],
                "kurztext": text["kurztext"],
                "quelle_url": "https://de.wikipedia.org/wiki/" + text["titel"].replace(" ", "_"),
                "revision": text["revision"],
                "lizenz": "CC BY-SA 4.0",
                "geladen_am": heute,
            })
        bestand[s] = eintrag
    ZIEL.write_text(json.dumps(dict(sorted(bestand.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    mit_text = sum(1 for e in bestand.values() if e.get("kurztext"))
    print(f"{mit_text} Themen mit Kurztext -> {ZIEL.relative_to(DATEN.parent)}", file=sys.stderr)


if __name__ == "__main__":
    main()
