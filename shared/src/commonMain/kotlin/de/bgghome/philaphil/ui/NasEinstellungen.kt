package de.bgghome.philaphil.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Abgleich mit der NAS per WebDAV: Bestand, eigene Fotos und private Bilder zentral, damit Tablet, Handy
 * und PC denselben Stand haben. Ohne Zugang bleibt alles wie bisher auf dem Geraet.
 */
@Composable
fun NasEinstellungen(zustand: AppZustand, viewModel: AppViewModel) {
    var adresse by remember(zustand.nasAdresse) { mutableStateOf(zustand.nasAdresse) }
    var benutzer by remember(zustand.nasBenutzer) { mutableStateOf(zustand.nasBenutzer) }
    var passwort by remember { mutableStateOf("") }

    Text("Abgleich mit der NAS", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text("Bestand, eigene Fotos und private Bilder liegen zentral in einem WebDAV-Ordner, etwa auf der Synology " +
        "(Paket „WebDAV Server“) oder in Nextcloud. Jedes Gerät arbeitet offline weiter und gleicht beim Start und " +
        "nach Änderungen ab. Private Bilder (Ordner Privat/) holt es einmal im WLAN und behält sie.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(value = adresse, onValueChange = { adresse = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Adresse") }, placeholder = { Text("https://meine-nas.example.de/home/PhilaPhil") })
    OutlinedTextField(value = benutzer, onValueChange = { benutzer = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Benutzer") })
    OutlinedTextField(value = passwort, onValueChange = { passwort = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(if (zustand.nasPasswortGesetzt) "Passwort (gespeichert – zum Ändern neu eingeben)" else "Passwort") },
        visualTransformation = PasswordVisualTransformation())
    Spacer(Modifier.height(6.dp))
    Row {
        OutlinedButton(
            onClick = { viewModel.nasEinstellen(adresse, benutzer, passwort); passwort = "" },
            enabled = adresse.isNotBlank() && benutzer.isNotBlank() && (passwort.isNotEmpty() || zustand.nasPasswortGesetzt) && !zustand.abgleichLaeuft,
        ) { Text("Speichern und abgleichen") }
        if (zustand.nasEingerichtet) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = viewModel::nasTrennen) { Text("Trennen") }
        }
    }
    zustand.abgleichPrivat?.let { (n, g) ->
        LinearProgressIndicator(progress = { if (g == 0) 0f else n.toFloat() / g }, modifier = Modifier.fillMaxWidth())
        Text("Private Bilder: $n / $g", style = MaterialTheme.typography.bodySmall)
    }
    // Gleich unter dem Knopf, damit man sieht, dass etwas passiert
    val meldung = if (zustand.abgleichLaeuft && zustand.abgleichPrivat == null) "Abgleich läuft …" else zustand.abgleichMeldung
    meldung?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
    if (zustand.nasEingerichtet) {
        Row {
            TextButton(onClick = { viewModel.abgleichen() }, enabled = !zustand.abgleichLaeuft) { Text("Jetzt abgleichen") }
            TextButton(onClick = { viewModel.abgleichen(privatErzwingen = true) }, enabled = !zustand.abgleichLaeuft) { Text("Private Bilder auch ohne WLAN") }
        }
    }
    zustand.nasZertifikatFrage?.let { name ->
        // Eigenes Fenster, damit die Frage nicht unten in den Einstellungen verschwindet
        AlertDialog(
            onDismissRequest = { viewModel.nasZertifikatAnnehmen(false) },
            title = { Text("Zertifikat der NAS") },
            text = {
                Text("Die NAS meldet sich mit einem gültigen Zertifikat für „$name“, die Adresse lautet aber anders " +
                    "(im Heimnetz üblich). Diesem Zertifikat unter dieser Adresse vertrauen?")
            },
            confirmButton = { TextButton(onClick = { viewModel.nasZertifikatAnnehmen(true) }) { Text("Vertrauen") } },
            dismissButton = { TextButton(onClick = { viewModel.nasZertifikatAnnehmen(false) }) { Text("Nein") } },
        )
    }
}
