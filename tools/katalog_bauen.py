#!/usr/bin/env python3
"""Baut aus den JSON-Dateien in daten/ die katalog.db für die App.

Aufruf:
    tools/katalog_bauen.py [--ziel pfad/katalog.db]

- liest alle daten/<gebiet>/<jahr>.json
- legt daten/<gebiet>/<jahr>.hand.json darüber (Handkorrekturen)
- schreibt marke, thema, marke_thema, quelle und eine FTS5-Suchtabelle

Die DB wird bei jedem Lauf komplett neu geschrieben. Der eigene Bestand
liegt NICHT hier, sondern in einer zweiten Datenbank der App.
"""
import argparse
import json
import re
import sqlite3
import sys
from datetime import date
from pathlib import Path

HIER = Path(__file__).resolve().parent
DATEN = HIER.parent / "daten"

SCHEMA = """
CREATE TABLE info (
  schluessel TEXT PRIMARY KEY,
  wert TEXT
);

CREATE TABLE gebiet (
  schluessel TEXT PRIMARY KEY,   -- 'bund', 'reich' (Ordner in daten/)
  name TEXT NOT NULL UNIQUE,     -- 'Bund', 'Reich' (marke.gebiet)
  anzeige TEXT NOT NULL,         -- 'Deutsches Reich'
  dateiname TEXT NOT NULL,       -- Kuerzel fuer eigene Bilder: Reich-529.jpg
  von INTEGER, bis INTEGER,
  reihe INTEGER                  -- Reihenfolge in der Auswahl
);

CREATE TABLE quelle (
  gebiet TEXT NOT NULL,
  jahr INTEGER NOT NULL,
  titel TEXT NOT NULL,
  url TEXT NOT NULL,
  revision INTEGER,
  abgerufen TEXT,
  lizenz TEXT,
  lizenz_url TEXT,
  PRIMARY KEY (gebiet, jahr)
);

CREATE TABLE marke (
  id INTEGER PRIMARY KEY,
  gebiet TEXT NOT NULL,          -- 'Bund', 'Berlin', 'DDR' …
  mi_nr TEXT NOT NULL,           -- TEXT wegen '830A', 'Bl. 12'
  sortier_nr INTEGER,            -- Zahlanteil der MiNr für die Reihenfolge
  jahr INTEGER NOT NULL,
  art TEXT,                      -- 'Sondermarke', 'Dauermarke'
  ausgabetag TEXT,               -- ISO-Datum
  wert TEXT,                     -- '60', '40+20'
  waehrung TEXT,                 -- 'Pf'
  anlass TEXT,                   -- gilt für den ganzen Satz
  satz INTEGER DEFAULT 0,        -- 1 = Teil eines Satzes
  bild_beschreibung TEXT,
  entwerfer TEXT,
  auflage INTEGER,
  zaehnung TEXT,                 -- später von Hand ergänzt
  druckart TEXT,                 -- später von Hand ergänzt
  commons_datei TEXT,
  block TEXT,                    -- 'Block 16', wenn nur im Block erschienen
  gueltig_bis TEXT,              -- bis 1968 hatten Marken ein Ablaufdatum
  anmerkung TEXT,                -- erzaehlende Fussnoten der Wikipedia-Liste, durch Leerzeile getrennt
  UNIQUE (gebiet, mi_nr)
);
CREATE INDEX marke_jahr ON marke (gebiet, jahr, sortier_nr);

CREATE TABLE thema (
  id INTEGER PRIMARY KEY,
  wikidata TEXT UNIQUE,
  titel TEXT NOT NULL,           -- Artikeltitel in der Wikipedia
  artikel_url TEXT,
  kurztext TEXT,
  wusstest_du TEXT,
  quelle TEXT,                   -- 'wikipedia', 'eigen', 'ki-entwurf'
  geladen_am TEXT
);

CREATE TABLE marke_thema (
  marke_id INTEGER NOT NULL REFERENCES marke(id),
  thema_id INTEGER NOT NULL REFERENCES thema(id),
  haupt INTEGER DEFAULT 0,       -- 1 = Hauptthema
  reihe INTEGER DEFAULT 0,       -- Reihenfolge der Nennung
  PRIMARY KEY (marke_id, thema_id)
);

-- Volltextsuche über Anlass, Bildbeschreibung und Themen; rowid = marke.id
CREATE VIRTUAL TABLE marke_fts USING fts5 (
  mi_nr, anlass, bild_beschreibung, themen, entwerfer,
  tokenize = 'unicode61 remove_diacritics 2'
);
"""


def hand_anwenden(marke, korrektur):
    """Legt eine Handkorrektur über eine Marke."""
    for feld, wert in korrektur.items():
        if feld == "hauptthema":
            themen = marke["themen"]
            treffer = [t for t in themen if wert in (t.get("artikel"), t.get("titel"), t.get("beschriftung"))]
            if not treffer:
                print(f"  Hinweis: Hauptthema '{wert}' bei MiNr {marke['mi_nr']} nicht unter den Themen, wird ergänzt", file=sys.stderr)
                treffer = [{"titel": wert, "artikel": wert, "beschriftung": wert, "wikidata": None}]
            rest = [t for t in themen if t is not treffer[0]]
            marke["themen"] = [treffer[0]] + rest
            for i, t in enumerate(marke["themen"]):
                t["haupt"] = i == 0
        elif feld.startswith("_"):
            continue
        else:
            marke[feld] = wert


def bauen(ziel):
    ziel.parent.mkdir(parents=True, exist_ok=True)
    if ziel.exists():
        ziel.unlink()
    db = sqlite3.connect(ziel)
    db.executescript(SCHEMA)

    themen_ids = {}     # Schlüssel (wikidata oder titel) -> thema.id
    # Kurztexte aus Wikipedia (tools/themen_laden.py) und eigene Ergänzungen
    texte = json.loads((DATEN / "themen.json").read_text(encoding="utf-8")) if (DATEN / "themen.json").exists() else {}
    hand_themen = json.loads((DATEN / "themen.hand.json").read_text(encoding="utf-8")) if (DATEN / "themen.hand.json").exists() else {}

    def thema_id(t):
        schluessel = t.get("wikidata") or "titel:" + t["artikel"]
        if schluessel in themen_ids:
            return themen_ids[schluessel]
        text = texte.get(schluessel, {})
        hand = hand_themen.get(schluessel, {})
        url = text.get("quelle_url") or "https://de.wikipedia.org/wiki/" + t["artikel"].replace(" ", "_")
        kurztext, quelle = text.get("kurztext"), "wikipedia" if text.get("kurztext") else None
        if hand.get("kurztext"):
            kurztext, quelle = hand["kurztext"], "eigen"
        cur = db.execute(
            "INSERT INTO thema (wikidata, titel, artikel_url, kurztext, wusstest_du, quelle, geladen_am) VALUES (?,?,?,?,?,?,?)",
            (t.get("wikidata") or text.get("wikidata"), text.get("titel") or t["artikel"], url, kurztext, hand.get("wusstest_du"), quelle, text.get("geladen_am")))
        themen_ids[schluessel] = cur.lastrowid
        return cur.lastrowid

    gebiete = {k: v for k, v in json.loads((DATEN / "gebiete.json").read_text(encoding="utf-8")).items() if not k.startswith("_")}
    for reihe, (k, g) in enumerate(gebiete.items()):
        db.execute("INSERT INTO gebiet VALUES (?,?,?,?,?,?,?)", (k, g["name"], g.get("anzeige", g["name"]), g.get("dateiname", g["name"]), g.get("von"), g.get("bis"), reihe))
    dateien = sorted(p for p in DATEN.glob("*/*.json") if not p.name.endswith((".hand.json", ".commons.json")))
    if not dateien:
        sys.exit("Keine JSON-Dateien in daten/ gefunden")
    anzahl = 0
    gesehen = {}        # (gebiet, mi_nr) -> jahr der ersten Nennung
    for datei in dateien:
        jahrgang = json.loads(datei.read_text(encoding="utf-8"))
        hand_datei = datei.with_name(datei.stem + ".hand.json")
        hand = json.loads(hand_datei.read_text(encoding="utf-8")) if hand_datei.exists() else {}
        # Zusaetzliche Commons-Bilder aus tools/commons_abgleich.py - nur fuer Marken ohne Bild in der Liste
        zusatz_datei = datei.with_name(datei.stem + ".commons.json")
        zusatz = json.loads(zusatz_datei.read_text(encoding="utf-8")) if zusatz_datei.exists() else {}
        q = jahrgang["quelle"]
        db.execute("INSERT INTO quelle VALUES (?,?,?,?,?,?,?,?)", (
            jahrgang["gebiet"], jahrgang["jahr"], q["titel"], q["url"], q.get("revision"),
            q.get("abgerufen"), q.get("lizenz"), q.get("lizenz_url")))
        doppelt = []
        for mk in jahrgang["marken"]:
            if not mk.get("commons_datei") and mk["mi_nr"] in zusatz:
                mk["commons_datei"] = zusatz[mk["mi_nr"]]["datei"]
            if mk["mi_nr"] in hand:
                hand_anwenden(mk, hand[mk["mi_nr"]])
            schluessel = (jahrgang["gebiet"], mk["mi_nr"])
            if schluessel in gesehen:
                if gesehen[schluessel] == jahrgang["jahr"]:
                    # Gleiche Nummer im selben Jahrgang (z. B. "3…" fuer noch unbekannte Nummern): durchzaehlen
                    n = 2
                    while (jahrgang["gebiet"], f"{mk['mi_nr']} ({n})") in gesehen:
                        n += 1
                    mk["mi_nr"] = f"{mk['mi_nr']} ({n})"
                else:
                    # Dauermarken stehen oft in zwei Jahrgangslisten: die erste Nennung gilt
                    doppelt.append(f"{mk['mi_nr']} (schon {gesehen[schluessel]})")
                    continue
            gesehen[(jahrgang["gebiet"], mk["mi_nr"])] = jahrgang["jahr"]
            if "ATM" in mk["mi_nr"] and mk.get("art") is None:
                mk["art"] = "Automatenmarke"
            # Sortierung: erste Nummer der Zelle ("2204, 2205" -> 2204); Automatenmarken ans Ende des Jahrgangs
            erste = re.search(r"\d+", mk["mi_nr"])
            ziffern = erste.group() if erste else ""
            if mk["mi_nr"].startswith("ATM"):
                ziffern = str(900000 + int(ziffern))
            cur = db.execute("""INSERT INTO marke (gebiet, mi_nr, sortier_nr, jahr, art, ausgabetag, wert, waehrung,
                anlass, satz, bild_beschreibung, entwerfer, auflage, zaehnung, druckart, commons_datei, block, gueltig_bis, anmerkung)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
                jahrgang["gebiet"], mk["mi_nr"], int(ziffern) if ziffern else None, jahrgang["jahr"],
                mk.get("art"), mk.get("ausgabetag"), mk.get("wert"), mk.get("waehrung"),
                mk.get("anlass"), 1 if mk.get("satz") else 0, mk.get("bild_beschreibung"),
                mk.get("entwerfer"), mk.get("auflage"), mk.get("zaehnung"), mk.get("druckart"),
                mk.get("commons_datei"), mk.get("block"), mk.get("gueltig_bis"),
                "\n\n".join(mk.get("anmerkungen") or []) or None))
            marke_id = cur.lastrowid
            for reihe, t in enumerate(mk.get("themen", [])):
                db.execute("INSERT OR IGNORE INTO marke_thema VALUES (?,?,?,?)",
                           (marke_id, thema_id(t), 1 if t.get("haupt") else 0, reihe))
            db.execute("INSERT INTO marke_fts (rowid, mi_nr, anlass, bild_beschreibung, themen, entwerfer) VALUES (?,?,?,?,?,?)", (
                marke_id, mk["mi_nr"], mk.get("anlass"), " ".join([mk.get("bild_beschreibung") or ""] + (mk.get("anmerkungen") or [])),
                " · ".join(t["artikel"] for t in mk.get("themen", [])), mk.get("entwerfer")))
            anzahl += 1
        unbekannt = [k for k in hand if not k.startswith("_") and k not in {m["mi_nr"] for m in jahrgang["marken"]}]
        if unbekannt:
            print(f"  Hinweis: Handkorrekturen ohne Marke in {hand_datei.name}: {unbekannt}", file=sys.stderr)
        if doppelt:
            print(f"  {datei.relative_to(DATEN)}: übersprungen, weil schon in anderem Jahrgang: {', '.join(doppelt)}", file=sys.stderr)

    db.execute("INSERT INTO info VALUES ('schema_version', '1')")
    db.execute("INSERT INTO info VALUES ('gebaut_am', ?)", (date.today().isoformat(),))
    db.execute("INSERT INTO info VALUES ('lizenz', 'CC BY-SA 4.0')")
    # Schema-Version fuer den Android-Treiber: 1 = "Schema steht schon", sonst wuerde er CREATE TABLE ausfuehren.
    db.execute("PRAGMA user_version = 1")
    db.commit()
    db.execute("VACUUM")
    db.close()
    mit_text = db_zaehlen(ziel, "SELECT COUNT(*) FROM thema WHERE kurztext IS NOT NULL")
    print(f"{anzahl} Marken, {len(themen_ids)} Themen ({mit_text} mit Kurztext) -> {ziel}", file=sys.stderr)


def db_zaehlen(ziel, sql):
    db = sqlite3.connect(ziel)
    try:
        return db.execute(sql).fetchone()[0]
    finally:
        db.close()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    # Standardziel: als Compose-Ressource in der App (wird beim ersten Start in den Datenordner kopiert)
    p.add_argument("--ziel", type=Path, default=HIER.parent / "shared" / "src" / "commonMain" / "composeResources" / "files" / "katalog.db")
    args = p.parse_args()
    bauen(args.ziel)


if __name__ == "__main__":
    main()
