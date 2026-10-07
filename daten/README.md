# Briefmarken-Daten

JSON pro Gebiet und Jahrgang, erzeugt aus den Jahrgangslisten der deutschen
Wikipedia (`tools/wikipedia_import.py`). Jede Datei nennt Quelle, Revision
und Abrufdatum.

```
bund/1979.json        Import aus Wikipedia (wird bei Neuimport überschrieben)
bund/1979.hand.json   Handkorrekturen, bleiben erhalten (Bilder, Hauptthema, Zähnung …)
```

Lizenz: CC BY-SA 4.0 (siehe LICENSE). Texte stammen aus der Wikipedia,
Autoren siehe Versionsgeschichte der jeweiligen Seite.
Bilder liegen nicht hier, sondern werden zur Laufzeit von Wikimedia Commons
geladen; die Lizenz steht dort in den Metadaten.
