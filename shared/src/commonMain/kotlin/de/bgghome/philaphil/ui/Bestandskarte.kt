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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.bgghome.philaphil.daten.ERHALTUNGEN
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.bestand_notiz
import de.bgghome.philaphil.res.bestand_titel
import de.bgghome.philaphil.res.bild_aus_galerie
import de.bgghome.philaphil.res.bild_datei_waehlen
import de.bgghome.philaphil.res.bild_foto
import de.bgghome.philaphil.res.bild_hinzufuegen
import de.bgghome.philaphil.res.bild_quelle_titel
import de.bgghome.philaphil.res.eigene_bilder
import org.jetbrains.compose.resources.stringResource

/** Die vier Erhaltungen mit Anzahl und eine Notiz - der eigene Bestand zu dieser Marke. */
@Composable
fun Bestandskarte(zustand: AppZustand, viewModel: AppViewModel, marke: Marke) {
    val eintraege = zustand.eintraege
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(Res.string.bestand_titel), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ERHALTUNGEN.forEach { e ->
                    val eintrag = eintraege.firstOrNull { it.erhaltung == e }
                    FilterChip(
                        selected = eintrag != null,
                        onClick = { viewModel.erhaltungSetzen(marke, e, if (eintrag == null) 1 else 0) },
                        label = { Text(e, fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
            // Anzahl je gewaehlter Erhaltung: " ** 2 " mit Minus und Plus
            eintraege.filter { it.erhaltung in ERHALTUNGEN }.forEach { eintrag ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(eintrag.erhaltung, Modifier.width(44.dp), fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { viewModel.erhaltungSetzen(marke, eintrag.erhaltung, eintrag.anzahl - 1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
                    Text(eintrag.anzahl.toString(), Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedButton(onClick = { viewModel.erhaltungSetzen(marke, eintrag.erhaltung, eintrag.anzahl + 1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Notiz: lokal gehalten, damit das Tippen fluessig bleibt; das ViewModel schreibt verzoegert
            var notiz by remember(marke.id) { mutableStateOf(eintraege.firstNotNullOfOrNull { it.notiz }.orEmpty()) }
            LaunchedEffect(eintraege) { val n = eintraege.firstNotNullOfOrNull { it.notiz }.orEmpty(); if (n.isNotEmpty() && notiz.isEmpty()) notiz = n }
            OutlinedTextField(
                value = notiz, onValueChange = { notiz = it; viewModel.notizSetzen(marke, it) },
                label = { Text(stringResource(Res.string.bestand_notiz)) }, minLines = 1, maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Eigene Bilder der Marke und die Taste zum Hinzufuegen (Handy: Auswahlblatt Foto/Galerie, Desktop: Dateidialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EigeneBilderZeile(zustand: AppZustand, viewModel: AppViewModel, marke: Marke) {
    val bilder = zustand.eigene(marke)
    var blatt by remember { mutableStateOf(false) }
    val galerie = rememberBildWaehler { viewModel.bildHinzufuegen(marke, it) }
    val kamera = rememberKamera { viewModel.bildHinzufuegen(marke, it) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.eigene_bilder), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { if (kamera != null) blatt = true else galerie() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.bild_hinzufuegen))
            }
        }
        if (bilder.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bilder, key = { it.name }) { b ->
                    AsyncImage(model = b.modell, contentDescription = b.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 96.dp, height = 112.dp).clickable { viewModel.vollbild(true) })
                }
            }
        }
    }

    if (blatt) {
        ModalBottomSheet(onDismissRequest = { blatt = false }) {
            Text(stringResource(Res.string.bild_quelle_titel), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            ListItem(headlineContent = { Text(stringResource(Res.string.bild_foto)) }, modifier = Modifier.clickable { blatt = false; kamera?.invoke() })
            ListItem(headlineContent = { Text(stringResource(Res.string.bild_aus_galerie)) }, modifier = Modifier.clickable { blatt = false; galerie() })
            Spacer(Modifier.height(24.dp))
        }
    }
}
