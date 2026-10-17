#!/bin/bash
# Baut das Benutzerhandbuch aus docs/handbuch.md: PDF (LuaLaTeX, KOMA-Script) und Seite fuer die Webseite.
# Pandoc kommt aus build/werkzeuge (python3 -m venv build/werkzeuge/venv && build/werkzeuge/venv/bin/pip install pypandoc_binary).
set -e
cd "$(dirname "$0")/.."
PANDOC=${PANDOC:-build/werkzeuge/pandoc}
VERSION=$(grep philaphil.versionName gradle.properties | cut -d= -f2)
ZIEL=build/handbuch
mkdir -p "$ZIEL"

# PDF: Bilder relativ zu docs/
(cd docs && "../$PANDOC" handbuch.md -o "../$ZIEL/handbuch.tex" --standalone \
    -V documentclass=scrartcl -V classoption=titlepage -V classoption=parskip=half \
    -V papersize=a4 -V geometry=margin=2.5cm -V lang=de -V toc-title=Inhalt \
    -V subtitle="Benutzerhandbuch · Version $VERSION" \
    --toc --toc-depth=2 -H handbuch-satz/kopf.tex)
(cd docs && TEXINPUTS=.:: lualatex -interaction=nonstopmode -output-directory="../$ZIEL" "../$ZIEL/handbuch.tex" >/dev/null \
    && TEXINPUTS=.:: lualatex -interaction=nonstopmode -output-directory="../$ZIEL" "../$ZIEL/handbuch.tex" >/dev/null) \
    || { grep -n '^!' "$ZIEL/handbuch.log" | head; exit 1; }
mv "$ZIEL/handbuch.pdf" "$ZIEL/PhilaPhil-Handbuch-$VERSION.pdf"
grep -i 'Missing character' "$ZIEL/handbuch.log" | sort -u | head || true

# Webseite: nur der Inhalt, den Rahmen setzt tools/handbuch_seite.py
"$PANDOC" docs/handbuch.md -o "$ZIEL/inhalt.html" --toc --toc-depth=2 --section-divs -V toc-title=Inhalt \
    --template=docs/handbuch-satz/seite.html -V version="$VERSION"
echo "fertig: $ZIEL/PhilaPhil-Handbuch-$VERSION.pdf, $ZIEL/inhalt.html"
