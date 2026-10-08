# PhilaPhil – Freund der Philatelie

Briefmarken als Zeitgeschichte. Ein Katalog, der erzählt – kein Katalog, der bewertet.

Jede Marke steht für etwas: ein Ereignis, eine Person, eine Zeit. PhilaPhil nimmt dich an die Hand
und führt in die Zeit der Herausgabe und in das Thema der Marke. Für Bewertung und Spezialwissen
gibt es den MICHEL-Katalog; PhilaPhil erzählt, *warum* es eine Marke gibt und was damals los war.

- Daten bleiben bei dir: kein Konto, kein Abo, keine Werbung, offline nutzbar.
- Offene Formate: JSON pro Jahrgang, SQLite in der App.
- Android, Linux und Windows aus einer Codebasis (Kotlin, Compose Multiplatform).
- Fakten und Themen stammen aus der Wikipedia (CC BY-SA 4.0), Bilder zur Laufzeit von Wikimedia Commons
  mit Lizenzangabe unter jedem Bild. Keine Preise, keine Katalog-Systematik.

<p>
<img src="docs/bilder/handy-liste.png" width="230" alt="Markenliste eines Jahrgangs">&nbsp;
<img src="docs/bilder/handy-marke.png" width="230" alt="Eine Marke mit Fakten">&nbsp;
<img src="docs/bilder/handy-wusstest-du.png" width="230" alt="Themenkarte mit Kurztext und Wusstest du">
</p>

<img src="docs/bilder/desktop-tablet.png" width="720" alt="Tablet und Desktop: Liste links, Marke und Themenkarte rechts">

Stand: Bund komplett, 1949 bis 2026 (3854 Marken) mit Themenkarten (Kurztext aus der Wikipedia, „Wusstest du?“, Marken zum Thema), Suche und Blättern durch die Jahrgänge.

```
daten/     JSON pro Gebiet und Jahrgang (CC BY-SA 4.0), später eigenes Repo briefmarken-daten
tools/     Import aus Wikipedia, Bau der katalog.db
shared/    Kotlin-Kern: Datenbank, Oberfläche (Compose Multiplatform)
app/       Android-Hülle
desktop/   Linux/Windows-Hülle
```

## Daten erzeugen

```
tools/wikipedia_import.py --gebiet bund --jahr $(seq -s" " 1949 2026)   # -> daten/bund/<jahr>.json
tools/themen_laden.py                                  # Kurztexte der Themen -> daten/themen.json
tools/katalog_bauen.py                                 # -> shared/.../composeResources/files/katalog.db
```

Eigene Ergänzungen zu Themen („Wusstest du?“, eigener Kurztext) stehen in `daten/themen.hand.json`.

Handkorrekturen (Bilder, Hauptthema, Zähnung, Druckart) kommen nach
`daten/<gebiet>/<jahr>.hand.json` und überleben jeden Neuimport.

Lizenz: Code GPL-3.0 (LICENSE), Daten CC BY-SA 4.0 (daten/LICENSE).

## Pakete

Fertige Pakete liegen bei den [Releases](https://github.com/thobgg/philaphil/releases):

```
PhilaAnd-<version>.apk   Android (ab Android 8)
PhilaWin-<version>.exe   Windows-Installer
PhilaTux-<version>.deb   Debian/Ubuntu
```

Ein Release auf GitHub stößt den Workflow `.github/workflows/release.yml` an, der die drei Pakete baut
und anhängt.

## App bauen

Gradle 8.13 braucht JDK 21 (das System-Java 25 geht nicht):

```
export JAVA_HOME=~/.jdks/jdk-21.0.12+8
./gradlew :desktop:run              # Desktop starten
./gradlew :desktop:packageDeb       # Linux-Paket -> desktop/build/compose/binaries/main/deb/
./gradlew :app:assembleDebug        # Android -> app/build/outputs/apk/debug/app-debug.apk
```

Die Katalog-Datenbank liegt als Ressource in `shared/src/commonMain/composeResources/files/katalog.db`
und wird beim ersten Start in den Datenordner kopiert (Linux: `~/.local/share/philaphil`).
Bilder kommen zur Laufzeit von Wikimedia Commons und landen im Cache (`~/.cache/philaphil/bilder`).
