#!/usr/bin/env python3
"""Sucht in den Commons-Kategorien eines Jahrgangs nach Bildern, die die Wikipedia-Liste nicht verlinkt.

Aufruf:
    tools/commons_abgleich.py --gebiet bund --jahr 1949 … 2026

Quelle: Category:<jahr> Deutsche Bundespost stamps (bis 1994) bzw. Category:<jahr> stamps of Germany.
Zuordnung zu einer Marke ohne Bild:
  1. MiNr im Dateinamen ("DBP 1980 1045 Reichstag zu Gelnhausen.jpg")       -> sicher
  2. sonst Woerter des Anlasses im Dateinamen (mind. 2 bedeutsame Woerter,
     eindeutig nur eine Marke)                                              -> wahrscheinlich
Dateien von Berlin, DDR und Saar sowie Briefe/Umschlaege werden ausgelassen.

Ergebnis: daten/<gebiet>/<jahr>.commons.json  {MiNr: {datei, weg}} - wird beim Katalogbau
genommen, wenn die Marke kein Bild aus der Liste hat. Handkorrekturen gehen immer vor.
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from wikipedia_import import DATEN, USER_AGENT  # noqa: E402
import urllib.parse  # noqa: E402
import urllib.request  # noqa: E402

API = "https://commons.wikimedia.org/w/api.php"
AUSSCHLUSS = re.compile(r"(?i)\betb\b|\bpsc\b|ersttag|\bfdc\b|berlin|\bbln\b|ddr|\bgdr\b|german democratic|saar|umschlag|envelope|brief\b|cover|ganzsache|postcard|postkarte|stempel|cancel")
FUELLWOERTER = {"jahre", "jahr", "geburtstag", "todestag", "deutsche", "deutschen", "deutscher", "bundespost", "stamp", "stamps",
                "briefmarke", "germany", "deutschland", "serie", "für", "fuer", "und", "der", "die", "das", "des", "von", "zum",
                "zur", "den", "dem", "mit", "auf", "aus", "dbp", "dpag", "jpg", "png", "100", "the", "of", "and", "in", "im"}


def kategorie(jahr):
    return f"{jahr} Deutsche Bundespost stamps" if jahr <= 1994 else f"{jahr} stamps of Germany"


def dateien(jahr):
    gefunden, weiter = [], {}
    while True:
        q = {"action": "query", "list": "categorymembers", "cmtitle": "Category:" + kategorie(jahr), "cmtype": "file",
             "cmlimit": "500", "format": "json", "formatversion": "2", **weiter}
        req = urllib.request.Request(API + "?" + urllib.parse.urlencode(q), headers={"User-Agent": USER_AGENT})
        d = json.load(urllib.request.urlopen(req, timeout=60))
        gefunden += [m["title"][5:] for m in d.get("query", {}).get("categorymembers", [])]
        if "continue" not in d:
            return gefunden
        weiter = d["continue"]


def woerter(text):
    text = text.lower().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    return {w for w in re.findall(r"[a-z]{4,}", text) if w not in FUELLWOERTER}


def abgleich(gebiet, jahr):
    jahrgang = json.loads((DATEN / gebiet / f"{jahr}.json").read_text(encoding="utf-8"))
    hand_datei = DATEN / gebiet / f"{jahr}.hand.json"
    hand = json.loads(hand_datei.read_text(encoding="utf-8")) if hand_datei.exists() else {}
    marken = jahrgang["marken"]
    vergeben = {m["commons_datei"] for m in marken if m.get("commons_datei")} | {v.get("commons_datei") for v in hand.values() if isinstance(v, dict)}
    ohne = [m for m in marken if not m.get("commons_datei") and not (hand.get(m["mi_nr"]) or {}).get("commons_datei")]
    if not ohne:
        return {}
    frei = [d for d in dateien(jahr) if d not in vergeben and not AUSSCHLUSS.search(d)]
    ergebnis = {}

    # 1. MiNr im Dateinamen
    nummern = {m["mi_nr"]: m for m in ohne if m["mi_nr"].isdigit()}
    for d in list(frei):
        zahlen = [z for z in re.findall(r"(?<!\d)(\d{2,4})(?!\d)", d) if z != str(jahr)]
        treffer = [z for z in zahlen if z in nummern and z not in ergebnis]
        if len(treffer) == 1:
            ergebnis[treffer[0]] = {"datei": d, "weg": "minr"}
            frei.remove(d)

    # 2. Woerter des Anlasses - nur eindeutige Paare (eine Datei, eine Marke)
    rest = [m for m in ohne if m["mi_nr"] not in ergebnis]
    kandidaten = {}
    for d in frei:
        wd = woerter(d)
        passend = []
        for m in rest:
            wm = woerter((m.get("anlass") or "") + " " + (m.get("bild_beschreibung") or ""))
            gemeinsam = wd & wm
            if len(gemeinsam) >= 2 or (len(gemeinsam) == 1 and len(next(iter(gemeinsam))) >= 8 and len(wm) <= 3):
                passend.append(m["mi_nr"])
        if len(passend) == 1:
            kandidaten.setdefault(passend[0], []).append(d)
    for nr, ds in kandidaten.items():
        if len(ds) == 1:
            ergebnis[nr] = {"datei": ds[0], "weg": "woerter"}
    return ergebnis


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--gebiet", default="bund")
    p.add_argument("--jahr", type=int, nargs="+", required=True)
    args = p.parse_args()
    summe = {"minr": 0, "woerter": 0}
    for jahr in args.jahr:
        if not (DATEN / args.gebiet / f"{jahr}.json").exists():
            continue
        try:
            erg = abgleich(args.gebiet, jahr)
        except Exception as e:      # ein Jahr ohne Kategorie bricht nicht alles ab
            print(f"{jahr}: Fehler {e}", file=sys.stderr)
            continue
        ziel = DATEN / args.gebiet / f"{jahr}.commons.json"
        if erg:
            ziel.write_text(json.dumps(dict(sorted(erg.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        elif ziel.exists():
            ziel.unlink()
        for v in erg.values():
            summe[v["weg"]] += 1
        if erg:
            print(f"{jahr}: {len(erg)} neu ({', '.join(f'{k} {v['datei'][:40]}' for k, v in list(erg.items())[:3])}{' …' if len(erg) > 3 else ''})", file=sys.stderr)
    print(f"Neu zugeordnet: {summe['minr']} über MiNr, {summe['woerter']} über Wörter", file=sys.stderr)


if __name__ == "__main__":
    main()
