#!/usr/bin/env python3
"""Saetze "Wusstest du?" einmalig mit KI erzeugen - nach dem Companion-Prinzip.

Ablauf in drei Schritten:
    tools/wusstest_ki.py vorbereiten --gebiet bund --jahr 1979   # Artikeltexte nach build/wusstest/<los>/
    (die Saetze schreibt Claude in build/wusstest/<los>/antworten.json:
     {"<schluessel>": {"satz": "...", "beleg": "woertliches Zitat"}})
    tools/wusstest_ki.py uebernehmen build/wusstest/<los>          # pruefen und nach daten/wusstest_ki.json

Gepruefte Saetze erscheinen in der App gekennzeichnet als KI-Entwurf. Ein Satz wird nur uebernommen,
wenn sein Beleg woertlich im Artikeltext steht (wie in der App, KiGemeinsam.pruefen). Handgepflegte
Saetze in daten/themen.hand.json haben immer Vorrang.
"""
import argparse
import json
import re
import sys
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from wikipedia_import import DATEN, USER_AGENT  # noqa: E402

HIER = Path(__file__).resolve().parent.parent
ZIEL = DATEN / "wusstest_ki.json"
HOECHSTENS_ZEICHEN = 12_000


def artikeltext(titel):
    q = {"action": "query", "prop": "extracts", "explaintext": "1", "redirects": "1", "titles": titel, "format": "json", "formatversion": "2"}
    req = urllib.request.Request("https://de.wikipedia.org/w/api.php?" + urllib.parse.urlencode(q), headers={"User-Agent": USER_AGENT})
    seiten = json.load(urllib.request.urlopen(req, timeout=60))["query"]["pages"]
    text = (seiten[0].get("extract") or "") if seiten else ""
    if len(text) > HOECHSTENS_ZEICHEN:                      # wie KiGemeinsam.kuerzen
        schnitt = text.rfind("\n", 0, HOECHSTENS_ZEICHEN)
        text = text[: schnitt if schnitt > HOECHSTENS_ZEICHEN // 2 else HOECHSTENS_ZEICHEN]
    return text


def normal(s):
    s = re.sub(r"\s+", " ", s).replace("„", '"').replace("“", '"').replace("”", '"')
    return s.strip().lower()


def hauptthemen(gebiet, jahre):
    """Hauptthemen der Marken (Schluessel, Artikel) - ohne solche, die schon einen Satz haben."""
    vorhanden = set(json.loads(ZIEL.read_text(encoding="utf-8"))) if ZIEL.exists() else set()
    hand = json.loads((DATEN / "themen.hand.json").read_text(encoding="utf-8")) if (DATEN / "themen.hand.json").exists() else {}
    vorhanden |= {k for k, v in hand.items() if isinstance(v, dict) and v.get("wusstest_du")}
    gefunden = {}
    # Hauptthemen aus Handkorrekturen ({"1002": {"hauptthema": "Europäisches Parlament"}}) gehen vor
    themen_json = json.loads((DATEN / "themen.json").read_text(encoding="utf-8")) if (DATEN / "themen.json").exists() else {}
    nach_artikel = {v.get("artikel"): k for k, v in themen_json.items()}
    for jahr in jahre:
        datei = DATEN / gebiet / f"{jahr}.json"
        if not datei.exists():
            continue
        hand_datei = datei.with_name(f"{jahr}.hand.json")
        hand_jahr = json.loads(hand_datei.read_text(encoding="utf-8")) if hand_datei.exists() else {}
        for m in json.loads(datei.read_text(encoding="utf-8"))["marken"]:
            h = hand_jahr.get(m["mi_nr"]) if isinstance(hand_jahr.get(m["mi_nr"]), dict) else None
            if h and h.get("hauptthema"):
                k = nach_artikel.get(h["hauptthema"]) or "titel:" + h["hauptthema"]
                if k not in vorhanden:
                    gefunden.setdefault(k, h["hauptthema"])
                continue
            for t in m["themen"]:
                if t.get("haupt"):
                    k = t.get("wikidata") or "titel:" + t["artikel"]
                    if k not in vorhanden:
                        gefunden.setdefault(k, t["artikel"])
    return gefunden


def vorbereiten(args):
    jahre = range(args.jahr[0], args.jahr[-1] + 1) if len(args.jahr) == 2 and args.bis else args.jahr
    themen = hauptthemen(args.gebiet, jahre)
    los = HIER / "build" / "wusstest" / (args.name or f"{args.gebiet}-{'-'.join(map(str, args.jahr))}")
    los.mkdir(parents=True, exist_ok=True)
    liste = []
    for k, artikel in sorted(themen.items(), key=lambda x: x[1]):
        text = artikeltext(artikel)
        if len(text) < 300:
            continue
        datei = re.sub(r"[^\w]+", "_", k) + ".txt"
        (los / datei).write_text(f"{artikel}\n\n{text}", encoding="utf-8")
        liste.append({"schluessel": k, "artikel": artikel, "datei": datei})
    (los / "liste.json").write_text(json.dumps(liste, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(liste)} Themen in {los.relative_to(HIER)}", file=sys.stderr)


def uebernehmen(args):
    los = Path(args.los)
    liste = {e["schluessel"]: e for e in json.loads((los / "liste.json").read_text(encoding="utf-8"))}
    antworten = json.loads((los / "antworten.json").read_text(encoding="utf-8"))
    bestand = json.loads(ZIEL.read_text(encoding="utf-8")) if ZIEL.exists() else {}
    gut, verworfen = 0, []
    for k, a in antworten.items():
        e = liste.get(k)
        satz, beleg = (a.get("satz") or "").strip(), (a.get("beleg") or "").strip()
        if not e or not satz:
            continue
        text = (los / e["datei"]).read_text(encoding="utf-8")
        if len(beleg) < 15 or normal(beleg).rstrip(".…") not in normal(text):
            verworfen.append(e["artikel"])
            continue
        bestand[k] = {"thema": e["artikel"], "satz": satz, "beleg": beleg, "modell": args.modell, "datum": date.today().isoformat()}
        gut += 1
    ZIEL.write_text(json.dumps(dict(sorted(bestand.items())), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{gut} übernommen, {len(verworfen)} ohne wörtlichen Beleg verworfen {verworfen[:8]}", file=sys.stderr)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    u = p.add_subparsers(dest="schritt", required=True)
    v = u.add_parser("vorbereiten")
    v.add_argument("--gebiet", default="bund")
    v.add_argument("--jahr", type=int, nargs="+", required=True)
    v.add_argument("--bis", action="store_true", help="--jahr VON BIS als Bereich")
    v.add_argument("--name")
    w = u.add_parser("uebernehmen")
    w.add_argument("los")
    w.add_argument("--modell", default="Claude (Claude Code)")
    args = p.parse_args()
    vorbereiten(args) if args.schritt == "vorbereiten" else uebernehmen(args)


if __name__ == "__main__":
    main()
