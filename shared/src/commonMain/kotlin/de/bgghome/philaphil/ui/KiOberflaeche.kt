package de.bgghome.philaphil.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.daten.KiArt
import de.bgghome.philaphil.db.Thema

/** Einstellungen: KI-Begleiter freiwillig einschalten, Anbieter, eigener Schluessel, Modell. */
@Composable
fun KiEinstellungen(zustand: AppZustand, viewModel: AppViewModel) {
    Text("KI-Begleiter (freiwillig)", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Vorschläge für „Wusstest du?“", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = zustand.kiAn, onCheckedChange = { viewModel.kiEinstellen(an = it) })
    }
    Text(
        "Ausgeschaltet geht nichts an eine KI. Eingeschaltet schickt die App auf deinen Tipp hin nur den Wikipedia-Text des Themas " +
            "an den gewählten Anbieter – nie deinen Bestand, deine Bilder oder Notizen. Die KI formuliert daraus einen Satz und muss " +
            "die Stelle im Artikel zitieren, auf der er beruht; steht das Zitat nicht im Text, wird der Vorschlag verworfen. " +
            "Du prüfst jeden Satz, bevor er erscheint. Es gilt dein eigener API-Schlüssel und dein Konto beim Anbieter; ein " +
            "Vorschlag kostet meist Bruchteile eines Cents bis wenige Cent. Der Schlüssel bleibt nur in den Einstellungen dieses Geräts.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!zustand.kiAn) return
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KiArt.entries.forEach { art ->
            FilterChip(selected = zustand.kiArt == art, onClick = { viewModel.kiEinstellen(art = art) }, label = { Text(art.anzeige) })
        }
    }
    Text("Lumo (Proton) folgt, sobald Proton eine offizielle Schnittstelle anbietet.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    var schluessel by remember(zustand.kiArt) { mutableStateOf("") }
    OutlinedTextField(
        value = schluessel, onValueChange = { schluessel = it }, singleLine = true,
        label = { Text(if (zustand.kiSchluesselGesetzt) "API-Schlüssel (gespeichert – zum Ändern neu eingeben)" else "API-Schlüssel") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
    )
    Row {
        OutlinedButton(onClick = { viewModel.kiEinstellen(schluessel = schluessel); schluessel = "" }, enabled = schluessel.isNotBlank()) { Text("Speichern") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { viewModel.oeffneWeb(zustand.kiArt.schluesselSeite) }) { Text("Schlüssel besorgen ›") }
        if (zustand.kiSchluesselGesetzt) TextButton(onClick = { viewModel.kiEinstellen(schluessel = "") }) { Text("Löschen") }
    }
    var modell by remember(zustand.kiArt, zustand.kiModell) { mutableStateOf(zustand.kiModell) }
    OutlinedTextField(
        value = modell, onValueChange = { modell = it }, singleLine = true, label = { Text("Modell") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row {
        OutlinedButton(onClick = { viewModel.kiEinstellen(modell = modell) }, enabled = modell != zustand.kiModell) { Text("Übernehmen") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { viewModel.kiModelleAbrufen() }, enabled = zustand.kiSchluesselGesetzt) { Text("Modelle abrufen") }
        TextButton(onClick = { viewModel.kiEinstellen(modell = zustand.kiArt.standardModell) }) { Text("Standard") }
    }
    if (zustand.kiModelle.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            zustand.kiModelle.take(12).forEach { m ->
                FilterChip(selected = m == zustand.kiModell, onClick = { viewModel.kiEinstellen(modell = m) }, label = { Text(m) })
            }
        }
    }
    zustand.kiMeldung?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

/**
 * Auf der Themenkarte: dein freigegebener Satz, ein KI-Entwurf zur Pruefung oder die Taste fuer einen Vorschlag.
 * Der Katalog-Satz (handgepflegt) hat Vorrang; die Taste erscheint nur ohne Satz und nur nach Opt-in.
 */
@Composable
fun KiBereich(thema: Thema, zustand: AppZustand, viewModel: AppViewModel) {
    val eigener = zustand.eigenerSatz(thema)
    val entwurf = zustand.kiEntwurf?.takeIf { it.first == thema.id }?.second
    if (eigener != null && thema.wusstest_du.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp)) {
                Text("Wusstest du?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(eigener.satz, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (eigener.quelle == "ki-entwurf") "Eigene Ergänzung · KI-Entwurf (${eigener.modell ?: "KI"}), von dir geprüft, aus dem Wikipedia-Artikel"
                        else "Eigene Ergänzung",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                    TextButton(onClick = { viewModel.eigenenSatzEntfernen(thema) }) { Text("Entfernen") }
                }
            }
        }
        return
    }
    if (!zustand.kiAn || !thema.wusstest_du.isNullOrBlank()) return
    Spacer(Modifier.height(10.dp))
    when {
        entwurf != null -> {
            var text by remember(entwurf) { mutableStateOf(entwurf.satz) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("KI-Entwurf – bitte prüfen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Text("Beleg im Artikel: „${entwurf.beleg}“", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("Vorgeschlagen von ${entwurf.modell}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Button(onClick = { viewModel.kiUebernehmen(thema, text) }, enabled = text.isNotBlank()) { Text("Übernehmen") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { viewModel.kiVerwerfen() }) { Text("Verwerfen") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.kiVorschlag(thema) }) { Text("Neuer Vorschlag") }
                    }
                }
            }
        }
        zustand.kiLaeuft -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("${zustand.kiArt.anzeige} liest den Wikipedia-Artikel …", style = MaterialTheme.typography.bodyMedium)
        }
        else -> Column {
            Text("✦ „Wusstest du?“ vorschlagen lassen (${zustand.kiArt.anzeige})",
                Modifier.clickable(enabled = zustand.kiSchluesselGesetzt) { viewModel.kiVorschlag(thema) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (zustand.kiSchluesselGesetzt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!zustand.kiSchluesselGesetzt) Text("Erst in den Einstellungen einen API-Schlüssel eintragen.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            zustand.kiMeldung?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
