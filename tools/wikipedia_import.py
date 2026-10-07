#!/usr/bin/env python3
"""Import einer Briefmarken-Jahrgangsliste aus der deutschen Wikipedia.

Liest den Wikitext der Jahrgangsliste über die MediaWiki-API (nicht die
gerenderte Seite), wertet die Tabelle aus und schreibt ein JSON pro Gebiet
und Jahrgang nach daten/<gebiet>/<jahr>.json.

Aufruf:
    tools/wikipedia_import.py --gebiet bund --jahr 1979
    tools/wikipedia_import.py --gebiet bund --jahr 1979 --offline   (nur Cache)

Was das Skript macht:
- Sätze erkennen: Zeilen ohne fette Überschrift gehören zum vorigen Anlass.
- rowspan in Zellen auswerten (kommt in manchen Jahrgängen vor).
- Links in Anlass und Beschreibung werden Themen. Hauptthema ist der erste
  Link der Bildbeschreibung, sonst der erste Link des Anlasses.
- Dateinamen der Vorschaubilder -> commons_datei.
- Lückenprüfung: fehlende MiNr. werden gemeldet, nicht still übersprungen.
- Wikidata-IDs der Themen werden nachgeschlagen (Weiterleitungen aufgelöst).

Lizenz der Daten: Wikipedia-Inhalte stehen unter CC BY-SA 4.0. Das JSON
nennt Quelle, Revision und Lizenz.
"""
import argparse
import json
import re
import sys
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

USER_AGENT = "philaphil-import/0.1 (https://github.com/thobgg/philaphil)"
WIKI_API = "https://de.wikipedia.org/w/api.php"
HIER = Path(__file__).resolve().parent
CACHE = HIER / "cache"
DATEN = HIER.parent / "daten"

# Seitentitel der Jahrgangslisten je Gebiet
GEBIETE = {
    "bund": ("Bund", "Briefmarken-Jahrgang {jahr} der Deutschen Bundespost"),
    "berlin": ("Berlin", "Briefmarken-Jahrgang {jahr} der Deutschen Bundespost Berlin"),
    "ddr": ("DDR", "Briefmarken-Jahrgang {jahr} der Deutschen Post der DDR"),
}

# Links, die kein Thema sind (Philatelie-Begriffe, Einheiten)
KEINE_THEMEN = {
    "Zuschlagmarke", "Wohlfahrtsmarke", "Dauermarke", "Sondermarke",
    "Briefmarke", "Pfennig", "Michel-Katalog", "Deutsche Mark",
    "Kleinbogen", "Blockausgabe", "Zusammendruck",
}

MONATE = {
    "januar": 1, "februar": 2, "märz": 3, "april": 4, "mai": 5, "juni": 6,
    "juli": 7, "august": 8, "september": 9, "oktober": 10, "november": 11,
    "dezember": 12,
}


# ---------------------------------------------------------------- API-Zugriff
def api_abfrage(params, offline=False, cache_name=None):
    """Fragt die Wikipedia-API ab, mit einfachem Datei-Cache."""
    CACHE.mkdir(exist_ok=True)
    params = dict(params, format="json", formatversion="2")
    cache_datei = CACHE / (cache_name or ("api_" + urllib.parse.quote(json.dumps(params, sort_keys=True), safe="") + ".json"))
    if cache_datei.exists() and (offline or cache_name):
        return json.loads(cache_datei.read_text(encoding="utf-8"))
    if offline:
        sys.exit(f"Offline, aber kein Cache für {cache_datei.name}")
    url = WIKI_API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as antwort:
        daten = json.loads(antwort.read().decode("utf-8"))
    cache_datei.write_text(json.dumps(daten, ensure_ascii=False, indent=1), encoding="utf-8")
    return daten


def wikitext_laden(titel, offline=False, frisch=False):
    """Liefert (wikitext, revision, seitentitel)."""
    cache_name = "seite_" + re.sub(r"[^\w]+", "_", titel) + ".json"
    if frisch and (CACHE / cache_name).exists():
        (CACHE / cache_name).unlink()
    daten = api_abfrage({
        "action": "query", "prop": "revisions", "rvprop": "content|ids|timestamp",
        "rvslots": "main", "titles": titel, "redirects": "1",
    }, offline=offline, cache_name=cache_name)
    seite = daten["query"]["pages"][0]
    if seite.get("missing"):
        sys.exit(f"Seite nicht gefunden: {titel}")
    rev = seite["revisions"][0]
    return rev["slots"]["main"]["content"], rev["revid"], seite["title"]


def wikidata_ids(titel_liste, offline=False):
    """Löst Weiterleitungen auf und holt Wikidata-IDs zu Artikeltiteln.

    Liefert dict: ursprünglicher Titel -> (endgültiger Titel, Wikidata-ID oder None).
    """
    ergebnis = {}
    titel_liste = sorted(set(titel_liste))
    for i in range(0, len(titel_liste), 50):
        block = titel_liste[i:i + 50]
        daten = api_abfrage({
            "action": "query", "prop": "pageprops", "ppprop": "wikibase_item",
            "titles": "|".join(block), "redirects": "1",
        }, offline=offline, cache_name="wikidata_" + stabiler_hash(block) + ".json")
        umleitung = {}
        for n in daten["query"].get("normalized", []):
            umleitung[n["from"]] = n["to"]
        weiter = {}
        for r in daten["query"].get("redirects", []):
            weiter[r["from"]] = r["to"]
        seiten = {s["title"]: s for s in daten["query"]["pages"]}
        for t in block:
            ziel = umleitung.get(t, t)
            ziel = weiter.get(ziel, ziel)
            seite = seiten.get(ziel, {})
            qid = seite.get("pageprops", {}).get("wikibase_item")
            if seite.get("missing"):
                print(f"  Hinweis: Artikel fehlt: {t}", file=sys.stderr)
            ergebnis[t] = (ziel, qid)
    return ergebnis


def stabiler_hash(liste):
    import hashlib
    return hashlib.sha1("|".join(liste).encode("utf-8")).hexdigest()[:12]


# ---------------------------------------------------------- Wikitext-Helfer
RE_LINK = re.compile(r"\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|([^\]]*))?\]\]")
RE_DATEI = re.compile(r"\[\[(?:Datei|File|Bild|Image):([^\]|]+)", re.IGNORECASE)
RE_REF = re.compile(r"<ref[^>/]*/>|<ref[^>]*>.*?</ref>", re.DOTALL | re.IGNORECASE)
RE_VORLAGE = re.compile(r"\{\{[^{}]*\}\}")
RE_HTML = re.compile(r"<[^>]+>")


def links(text):
    """Alle Artikel-Links (Titel, Beschriftung) in Reihenfolge, ohne Dateien."""
    gefunden = []
    for m in RE_LINK.finditer(text):
        titel = m.group(1).strip()
        if ":" in titel.split(" ")[0] and titel.lower().startswith(("datei:", "file:", "bild:", "kategorie:")):
            continue
        beschriftung = (m.group(2) or titel).strip()
        gefunden.append((titel, beschriftung))
    return gefunden


def klartext(text):
    """Entfernt Wiki-Auszeichnung, behält die Beschriftung der Links."""
    text = RE_REF.sub("", text)
    text = RE_DATEI.sub("", text)
    text = RE_LINK.sub(lambda m: (m.group(2) or m.group(1)).strip(), text)
    text = RE_VORLAGE.sub("", text)
    text = RE_HTML.sub(" ", text)
    text = text.replace("'''", "").replace("''", "")
    text = text.replace("&nbsp;", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip(" ;,")


def zelle_trennen(erste_zeile):
    """Trennt Zellattribute (align=left | ...) vom Inhalt."""
    pos = erste_zeile.find("|")
    if pos >= 0:
        vorn = erste_zeile[:pos]
        if "[[" not in vorn and "{{" not in vorn and re.fullmatch(r"[\w\s=\"':#;%.-]*", vorn):
            return vorn, erste_zeile[pos + 1:]
    return "", erste_zeile


def attribut(attribute, name, standard=1):
    m = re.search(name + r"\s*=\s*\"?(\d+)", attribute)
    return int(m.group(1)) if m else standard


# ------------------------------------------------------------ Tabellenparser
def tabelle_parsen(wikitext):
    """Liefert Liste von Zeilen; jede Zeile ist eine Liste von Zellentexten.

    Zusätzlich gibt jede Zeile ihren Abschnitt (<h3>Sondermarken</h3> …) mit.
    rowspan wird aufgelöst: der Zellentext wird in die Folgezeilen übernommen.
    """
    start = wikitext.find("== Liste der Ausgaben")
    if start < 0:
        start = 0
    anfang = wikitext.find("{|", start)
    ende = wikitext.find("\n|}", anfang)
    if anfang < 0 or ende < 0:
        sys.exit("Keine Tabelle gefunden")
    block = wikitext[anfang:ende]

    rohe_zeilen = []       # Liste von (zellen: [(attribute, inhalt)])
    aktuelle = []
    for zeile in block.split("\n")[1:]:
        if zeile.startswith("|-"):
            if aktuelle:
                rohe_zeilen.append(aktuelle)
            aktuelle = []
        elif zeile.startswith("|") or zeile.startswith("!"):
            # Mehrere Zellen in einer Zeile: || bzw. !!
            teile = re.split(r"\s*(?:\|\||!!)\s*", zeile[1:])
            for teil in teile:
                attribute, inhalt = zelle_trennen(teil)
                aktuelle.append([attribute, inhalt])
        else:
            if aktuelle:
                aktuelle[-1][1] += "\n" + zeile
            # Zeilen vor der ersten Zelle (Tabellenattribute) ignorieren
    if aktuelle:
        rohe_zeilen.append(aktuelle)

    abschnitt = None
    ergebnis = []
    laufend = {}    # Spaltenindex -> [verbleibende Zeilen, Inhalt]
    for zellen in rohe_zeilen:
        if len(zellen) == 1 and "<h3>" in zellen[0][1]:
            abschnitt = klartext(zellen[0][1])
            laufend = {}
            continue
        if any("'''Bild'''" in z[1] for z in zellen):
            continue        # Kopfzeile
        # rowspan-Übernahmen einsetzen
        ausgabe = []
        spalte = 0
        rest = list(zellen)
        while rest or any(v[0] > 0 for v in laufend.values()):
            if spalte in laufend and laufend[spalte][0] > 0:
                ausgabe.append(laufend[spalte][1])
                laufend[spalte][0] -= 1
                spalte += 1
                continue
            if not rest:
                break
            attribute, inhalt = rest.pop(0)
            rs = attribut(attribute, "rowspan")
            if rs > 1:
                laufend[spalte] = [rs - 1, inhalt]
            ausgabe.append(inhalt)
            spalte += attribut(attribute, "colspan")
        ergebnis.append((abschnitt, ausgabe))
    return ergebnis


# ---------------------------------------------------------- Zelleninhalte
def beschreibung_parsen(text):
    """Zerlegt die Beschreibungszelle in Anlass (fett) und Bildbeschreibung.

    Liefert (anlass_wiki oder None, beschreibung_wiki).
    """
    text = RE_REF.sub("", text)
    zeilen = [z.rstrip() for z in text.split("\n")]
    anlass = None
    beschreibung = []
    for z in zeilen:
        s = z.strip()
        if not s:
            continue
        if s.startswith(":"):
            beschreibung.append(s.lstrip(":* ").strip())
        elif "'''" in s and anlass is None:
            anlass = s
        else:
            # Text ohne Doppelpunkt nach dem Anlass gehört zur Beschreibung
            beschreibung.append(s)
    return anlass, "; ".join(beschreibung)


def datum_parsen(text, jahr):
    t = klartext(text)
    m = re.match(r"(\d{1,2})\.\s*([A-Za-zäöüÄÖÜ]+)(?:\s+(\d{4}))?", t)
    if not m:
        return None, t
    tag = int(m.group(1))
    monat = MONATE.get(m.group(2).lower())
    j = int(m.group(3)) if m.group(3) else jahr
    if not monat:
        return None, t
    return f"{j:04d}-{monat:02d}-{tag:02d}", t


def zahl_parsen(text):
    t = klartext(text)
    ziffern = re.sub(r"[.\s]", "", t)
    return int(ziffern) if ziffern.isdigit() else None


def themen_ableiten(anlass_wiki, beschreibung_wiki, im_satz):
    """Liste von {titel, beschriftung, haupt} in Reihenfolge, erster = Hauptthema.

    Einzelmarke: der erste Link des Anlasses ist das Hauptthema (Agnes Miegel,
    Paul Klee), die Links der Bildbeschreibung sind Nebenthemen.
    Satzmarke: der Anlass gilt für den ganzen Satz (Nobelpreisträger), darum
    ist hier der erste Link der Bildbeschreibung das Hauptthema (Einstein).
    """
    gesehen = set()
    reihe = []
    anlass_links = links(anlass_wiki or "")
    beschreibung_links = links(beschreibung_wiki or "")
    reihenfolge = beschreibung_links + anlass_links if im_satz else anlass_links + beschreibung_links
    for titel, beschriftung in reihenfolge:
        if titel in KEINE_THEMEN or titel in gesehen:
            continue
        gesehen.add(titel)
        reihe.append({"titel": titel, "beschriftung": beschriftung})
    for i, t in enumerate(reihe):
        t["haupt"] = i == 0
    return reihe


# ----------------------------------------------------------------- Hauptlauf
def importieren(gebiet_schluessel, jahr, offline=False, frisch=False):
    gebiet, titel_muster = GEBIETE[gebiet_schluessel]
    seitentitel = titel_muster.format(jahr=jahr)
    wikitext, revision, seitentitel = wikitext_laden(seitentitel, offline=offline, frisch=frisch)

    m = re.search(r"umfasste (\d+) \[\[Sondermarke", wikitext)
    erwartet_sondermarken = int(m.group(1)) if m else None

    marken = []
    anlass_wiki = None          # läuft über Satzzeilen weiter
    anlass_abschnitt = None
    for abschnitt, zellen in tabelle_parsen(wikitext):
        if len(zellen) < 7:
            print(f"  Hinweis: Zeile mit {len(zellen)} Zellen übersprungen: {klartext(' '.join(zellen))[:60]}", file=sys.stderr)
            continue
        bild, beschreibung, wert, datum, auflage, entwurf, minr = zellen[:7]
        mi_nr = klartext(minr)
        if not mi_nr:
            continue
        neuer_anlass, beschreibung_wiki = beschreibung_parsen(beschreibung)
        if neuer_anlass or abschnitt != anlass_abschnitt:
            anlass_wiki = neuer_anlass
            anlass_abschnitt = abschnitt
        datei = RE_DATEI.search(bild)
        ausgabetag, datum_text = datum_parsen(datum, jahr)
        if not ausgabetag:
            print(f"  Hinweis: Datum nicht erkannt bei MiNr {mi_nr}: {datum_text}", file=sys.stderr)
        marke = {
            "mi_nr": mi_nr,
            "art": (abschnitt or "").rstrip("n") if abschnitt else None,   # Sondermarken -> Sondermarke
            "ausgabetag": ausgabetag,
            "wert": klartext(wert),
            "waehrung": "Pf",
            "anlass": klartext(anlass_wiki or ""),
            "bild_beschreibung": klartext(beschreibung_wiki),
            "entwerfer": klartext(entwurf) or None,
            "auflage": zahl_parsen(auflage),
            "commons_datei": datei.group(1).strip() if datei else None,
            "_anlass_wiki": anlass_wiki,
            "_beschreibung_wiki": beschreibung_wiki,
        }
        marken.append(marke)

    # Sätze erkennen: mehrere Marken mit demselben Anlass im selben Abschnitt
    anzahl_je_anlass = {}
    for mk in marken:
        schluessel = (mk["art"], mk["anlass"])
        anzahl_je_anlass[schluessel] = anzahl_je_anlass.get(schluessel, 0) + 1
    for mk in marken:
        mk["satz"] = anzahl_je_anlass[(mk["art"], mk["anlass"])] > 1
        mk["themen"] = themen_ableiten(mk.pop("_anlass_wiki"), mk.pop("_beschreibung_wiki"), mk["satz"])

    # Wikidata-IDs nachschlagen
    alle_titel = [t["titel"] for mk in marken for t in mk["themen"]]
    print(f"  {len(marken)} Marken, {len(set(alle_titel))} Themen, Wikidata nachschlagen …", file=sys.stderr)
    ids = wikidata_ids(alle_titel, offline=offline)
    for mk in marken:
        for t in mk["themen"]:
            ziel, qid = ids.get(t["titel"], (t["titel"], None))
            t["artikel"] = ziel
            t["wikidata"] = qid

    # Lückenprüfung
    nummern = sorted({int(m.group(1)) for mk in marken if (m := re.match(r"(\d+)", mk["mi_nr"]))})
    luecken = [n for n in range(nummern[0], nummern[-1] + 1) if n not in nummern] if nummern else []
    sonder = sum(1 for mk in marken if mk["art"] == "Sondermarke")
    pruefung = {
        "mi_nr_von": nummern[0] if nummern else None,
        "mi_nr_bis": nummern[-1] if nummern else None,
        "luecken": luecken,
        "sondermarken_gezaehlt": sonder,
        "sondermarken_laut_einleitung": erwartet_sondermarken,
    }
    if luecken:
        print(f"  LÜCKEN in der Tabelle: MiNr {luecken}", file=sys.stderr)
    if erwartet_sondermarken is not None and erwartet_sondermarken != sonder:
        print(f"  ABWEICHUNG: Einleitung nennt {erwartet_sondermarken} Sondermarken, Tabelle hat {sonder}", file=sys.stderr)
    ohne_bild = [mk["mi_nr"] for mk in marken if not mk["commons_datei"]]
    print(f"  Ohne Commons-Bild: {len(ohne_bild)} ({', '.join(ohne_bild)})", file=sys.stderr)

    ausgabe = {
        "gebiet": gebiet,
        "jahr": jahr,
        "quelle": {
            "titel": seitentitel,
            "url": "https://de.wikipedia.org/wiki/" + urllib.parse.quote(seitentitel.replace(" ", "_")),
            "revision": revision,
            "abgerufen": date.today().isoformat(),
            "lizenz": "CC BY-SA 4.0",
            "lizenz_url": "https://creativecommons.org/licenses/by-sa/4.0/",
        },
        "pruefung": pruefung,
        "marken": marken,
    }
    ziel = DATEN / gebiet_schluessel / f"{jahr}.json"
    ziel.parent.mkdir(parents=True, exist_ok=True)
    ziel.write_text(json.dumps(ausgabe, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"  geschrieben: {ziel.relative_to(HIER.parent)}", file=sys.stderr)
    return ausgabe


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--gebiet", choices=GEBIETE.keys(), default="bund")
    p.add_argument("--jahr", type=int, required=True, nargs="+", help="ein oder mehrere Jahrgänge")
    p.add_argument("--offline", action="store_true", help="nur aus dem Cache lesen")
    p.add_argument("--frisch", action="store_true", help="Cache der Seite verwerfen und neu laden")
    args = p.parse_args()
    for jahr in args.jahr:
        print(f"{args.gebiet} {jahr}:", file=sys.stderr)
        importieren(args.gebiet, jahr, offline=args.offline, frisch=args.frisch)


if __name__ == "__main__":
    main()
