package de.bgghome.philaphil.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

const val HANDBUCH_URL = "https://philaphil.bgg-home.de/handbuch/"

/** Ein Abschnitt der Hilfe: Ueberschrift und Absaetze. */
private class Abschnitt(val titel: String, vararg val absaetze: String)

/** Kurzhilfe in der App; das ausfuehrliche Handbuch liegt auf der Webseite (HANDBUCH_URL). */
private val HILFE = listOf(
    Abschnitt("Blättern und suchen",
        "Ein Tipp auf den Titel oben wählt Gebiet und Jahrgang: Bund, Berlin, DDR, Deutsches Reich oder die Besatzungszonen 1945 bis 1949. Die Zonen stehen als ein Eintrag; dahinter wählst du Gemeinschaftsausgaben, Bizone, die französischen Länder oder die sowjetischen Gebiete. Mit den Pfeilen blätterst du von Jahr zu Jahr.",
        "Die Suche findet Marken über alle Gebiete nach Anlass, Motiv, Thema oder MiNr. Darunter zeigen „Vorhanden“ und „Fehlend“ nur die Marken, die du hast oder die dir fehlen.",
        "„Was … geschah“ über der Liste erzählt das Jahr. Das Kalendersymbol öffnet „Heute vor Jahren“: Jahrestage zu den Menschen und Dingen auf den Marken."),
    Abschnitt("Die Marke",
        "Ein Tipp öffnet die Karte mit Ausgabetag, Wert, Auflage und Entwurf, darunter das Thema in wenigen Sätzen. Wische nach links oder rechts zur nächsten Marke des Jahrgangs.",
        "Ein Tipp aufs Bild zeigt es groß: mit zwei Fingern vergrößern, Doppeltipp zum Zoomen. Gehört die Marke zu einem Satz oder einer Dauerserie, zeigt „Ganzer Satz“ alle Werte nebeneinander.",
        "Jedes Thema führt zu allen anderen Marken dazu, über Jahre und Gebiete hinweg.",
        "Die Karte ist in allen Gebieten gleich aufgebaut. Fehlt eine Angabe in der Wikipedia-Liste, etwa die Auflage beim Deutschen Reich, lässt sie die Zeile weg. In den Besatzungszonen zählt jeder Druck als eigene Marke, die Bizone etwa hat für die M-Serie je eine Nummer für den amerikanischen, englischen und deutschen Druck. Die Zeitreise gehört zum Jahr: Berlin 1961 und DDR 1961 erzählen dasselbe Jahr."),
    Abschnitt("Dein Bestand",
        "Unter „Mein Bestand“ tippst du an, was du hast: ** postfrisch, * ungebraucht, ⊙ gestempelt, FDC Ersttagsbrief. Mit − und + änderst du die Anzahl, auf 0 ist der Eintrag wieder weg. Dazu passt eine Notiz.",
        "Die Liste zeigt deine Erhaltungen neben jeder Marke, oben steht etwa „26 von 38 im Bestand“."),
    Abschnitt("Eigene Bilder",
        "„Bild hinzufügen“ nimmt ein Foto auf oder wählt eines aus der Galerie. Es gilt für jede Marke, ob du sie besitzt oder nicht, und geht vor dem Bild von Wikimedia Commons.",
        "Die Bilder liegen im Sammlungsordner unter „Bilder“ und heißen nach Gebiet und MiNr., etwa Bund-1031.jpg. Du kannst sie dort auch selbst ablegen und dann „Neu einlesen“ tippen."),
    Abschnitt("Mehrere Geräte: Abgleich mit der NAS",
        "Handy, Tablet und PC haben denselben Bestand, wenn sie einen gemeinsamen WebDAV-Ordner nutzen, etwa auf einer Synology-NAS oder in Nextcloud. Jedes Gerät arbeitet weiter ohne Netz und gleicht ab: beim Start, nach jeder Änderung und wenn du die App wieder öffnest.",
        "Synology: Im Paket-Zentrum „WebDAV Server“ installieren und HTTPS einschalten (Port 5006). Einen Ordner anlegen, etwa „PhilaPhil“ in deinem Home. Adresse in der App: https://<NAS>:5006/home/PhilaPhil. Für unterwegs einen Reverse Proxy mit eigenem Namen einrichten, dann gilt die Adresse überall.",
        "Nextcloud: Adresse https://<Server>/remote.php/dav/files/<Benutzer>/PhilaPhil, mit einem App-Passwort.",
        "Im Heimnetz meldet sich die NAS oft mit einem Zertifikat für ihren Namen statt für die IP-Adresse. Die App fragt dann einmal, ob sie diesem Zertifikat vertrauen soll.",
        "Ändern zwei Geräte dieselbe Marke, bevor sie abgeglichen haben, gilt die spätere Änderung. Einmal am Tag legt die App den bisherigen Stand im Ordner „Sicherung“ ab, die letzten 30 Tage bleiben.",
        "Tipp: Lege auf der NAS einen eigenen Benutzer an, der nur den Ordner PhilaPhil sieht, und trage den in der App ein."),
    Abschnitt("Private Bilder",
        "Bilder, die nur für dich bestimmt sind, legst du auf der NAS in den Unterordner „Privat“, benannt wie die eigenen Bilder (Bund-1031.jpg). Eine Datei quelle.txt enthält die Zeile, die unter dem Bild steht.",
        "Jedes Gerät holt sie einmal im WLAN und behält sie. Sie kommen nie in die App selbst oder zu anderen. Nimm nur Bilder, die du privat nutzen darfst."),
    Abschnitt("Bilder für unterwegs",
        "„Vorschaubilder laden“ holt die kleinen Bilder aller Marken von Wikimedia Commons (etwa 40 MB). Danach zeigt die Liste auch ohne Netz Bilder."),
    Abschnitt("KI-Begleiter",
        "Freiwillig und ausgeschaltet, bis du ihn einschaltest. Mit deinem eigenen Schlüssel bei Claude, ChatGPT oder Gemini schlägt er für ein Thema einen Satz „Wusstest du?“ vor, aus dem Wikipedia-Artikel und mit Beleg. Du prüfst jeden Satz, bevor er erscheint."),
    Abschnitt("Deine Daten",
        "Kein Konto, kein Abo, keine Werbung, keine Auswertung. Bestand und Bilder liegen auf deinem Gerät und, wenn du willst, auf deiner eigenen NAS, als offene Dateien (bestand.json, Bilder). Ins Netz gehen nur Abrufe bei Wikipedia und Wikimedia Commons, der Abgleich mit deiner NAS und, wenn eingeschaltet, der KI-Begleiter."),
    Abschnitt("Quellen",
        "Markendaten und Texte aus der deutschsprachigen Wikipedia (CC BY-SA 4.0), Bilder von Wikimedia Commons mit der Lizenz unter jedem Bild. Für Bewertung und Spezialwissen ist der MICHEL-Katalog die richtige Adresse."),
)

@Composable
fun Hilfe(viewModel: AppViewModel, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hilfe", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Schließen") }
                }
                HorizontalDivider()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 720.dp)) {
                    Text("PhilaPhil erzählt, warum es eine Marke gibt und was damals los war. Ein Katalog, der erzählt – kein Katalog, der bewertet.",
                        style = MaterialTheme.typography.bodyLarge)
                    HILFE.forEach { a ->
                        Spacer(Modifier.height(20.dp))
                        Text(a.titel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        a.absaetze.forEach { absatz ->
                            Spacer(Modifier.height(6.dp))
                            Text(absatz, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Ausführliches Handbuch im Browser öffnen ›", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.oeffneWeb(HANDBUCH_URL) }.padding(vertical = 8.dp))
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
