#!/usr/bin/env python3
"""Holt zu allen Themen mit Wikidata-Kennung die Kalenderdaten fuer "Heute vor Jahren".

Aufruf:
    tools/wikidata_daten.py            # fehlende Themen nachladen
    tools/wikidata_daten.py --alle     # alles neu

Eigenschaften: P569 Geburt, P570 Tod, P571 Gruendung/Entstehung, P585 Zeitpunkt, P580 Beginn.
Nur tagesgenaue Angaben (Genauigkeit 11) und nur gregorianische Daten nach 1582.
Ergebnis: daten/themen_daten.json  {QID: [{"art": "geburt", "datum": "1879-03-14"}, …]}
Wikidata-Daten stehen unter CC0.
"""
import argparse
import json
import sys
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from wikipedia_import import DATEN, USER_AGENT  # noqa: E402

API = "https://www.wikidata.org/w/api.php"
ZIEL = DATEN / "themen_daten.json"
EIGENSCHAFTEN = {"P569": "geburt", "P570": "tod", "P571": "gruendung", "P585": "ereignis", "P580": "beginn"}


def qids_sammeln():
    gefunden = set()
    for datei in sorted(DATEN.glob("*/*.json")):
        if datei.name.endswith((".hand.json", ".commons.json")) or datei.parent.name == "zeitgeschehen":
            continue
        for marke in json.loads(datei.read_text(encoding="utf-8"))["marken"]:
            gefunden.update(t["wikidata"] for t in marke["themen"] if t.get("wikidata"))
    themen = DATEN / "themen.json"
    if themen.exists():
        gefunden.update(v["wikidata"] for v in json.loads(themen.read_text(encoding="utf-8")).values() if v.get("wikidata"))
    return sorted(gefunden)


def datum(wert):
    """Wikidata-Zeitwert -> "YYYY-MM-DD" oder None, wenn nicht tagesgenau."""
    if wert.get("precision") != 11 or wert.get("calendarmodel", "").endswith("Q1985786"):     # julianisch
        return None
    t = wert.get("time", "")
    if not t.startswith("+"):
        return None
    jahr, monat, tag = t[1:11].split("-")
    if int(jahr) < 1583 or monat == "00" or tag == "00":
        return None
    return f"{jahr}-{monat}-{tag}"


def laden(qids):
    ergebnis = {}
    for i in range(0, len(qids), 50):
        block = qids[i:i + 50]
        q = {"action": "wbgetentities", "ids": "|".join(block), "props": "claims", "format": "json"}
        req = urllib.request.Request(API + "?" + urllib.parse.urlencode(q), headers={"User-Agent": USER_AGENT})
        d = json.load(urllib.request.urlopen(req, timeout=60))
        for qid, e in d.get("entities", {}).items():
            daten = []
            for p, art in EIGENSCHAFTEN.items():
                for c in e.get("claims", {}).get(p, [])[:1]:          # erste Angabe genuegt
                    w = c.get("mainsnak", {}).get("datavalue", {}).get("value")
                    if isinstance(w, dict) and (dt := datum(w)):
                        daten.append({"art": art, "datum": dt})
            ergebnis[qid] = daten
        print(f"  {min(i + 50, len(qids))}/{len(qids)}", file=sys.stderr, end="\r")
    print(file=sys.stderr)
    return ergebnis


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--alle", action="store_true")
    args = p.parse_args()
    bestand = {} if args.alle or not ZIEL.exists() else json.loads(ZIEL.read_text(encoding="utf-8"))
    alle = qids_sammeln()
    offen = [q for q in alle if q not in bestand]
    print(f"{len(alle)} Themen mit Wikidata-Kennung, {len(offen)} zu laden", file=sys.stderr)
    bestand.update(laden(offen))
    ZIEL.write_text(json.dumps(dict(sorted(bestand.items())), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    mit = sum(1 for v in bestand.values() if v)
    print(f"{mit} Themen mit tagesgenauem Datum -> {ZIEL.relative_to(DATEN.parent)} (Stand {date.today()})", file=sys.stderr)


if __name__ == "__main__":
    main()
