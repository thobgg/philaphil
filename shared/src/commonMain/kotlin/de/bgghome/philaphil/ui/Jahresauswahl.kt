package de.bgghome.philaphil.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.db.Gebiete
import de.bgghome.philaphil.db.Jahrgaenge
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.jahrgang_waehlen
import de.bgghome.philaphil.res.schliessen
import org.jetbrains.compose.resources.stringResource

/**
 * Oben die Sammelgebiete (kommen aus dem Katalog, also erweiterbar ueber daten/gebiete.json),
 * darunter die Jahrgaenge des gewaehlten Gebiets nach Jahrzehnten - wie die Kapitel eines Geschichtsbuchs.
 *
 * Gebiete mit gemeinsamer `gruppe` (die Besatzungszonen 1945-1949) stehen in der ersten Reihe als ein Eintrag;
 * ist er gewaehlt, folgt eine zweite Reihe mit den Gebieten der Gruppe.
 */
@Composable
fun Jahresauswahl(gebiete: List<Gebiete>, jahrgaenge: List<Jahrgaenge>, aktuell: Jahrgaenge?, onWahl: (Jahrgaenge) -> Unit, onClose: () -> Unit) {
    val vorhandene = remember(gebiete) { gebiete.filter { it.anzahl > 0 } }
    var gebiet by remember { mutableStateOf(aktuell?.gebiet ?: vorhandene.firstOrNull()?.name ?: "Bund") }
    var gruppe by remember { mutableStateOf(vorhandene.firstOrNull { it.name == gebiet }?.gruppe) }
    val jahrzehnte = remember(jahrgaenge, gebiet) { jahrgaenge.filter { it.gebiet == gebiet }.groupBy { (it.jahr / 10) * 10 }.toList() }
    val liste = rememberLazyListState()
    // Ein Tipp auf ein Gebiet schaltet sofort um (auf das zuletzt gezeigte Jahr des Gebiets, sonst das erste);
    // der Dialog bleibt offen, damit man noch ein Jahr waehlen kann. Ohne das wirkte die Auswahl am Handy tot.
    fun gebietWaehlen(name: String) {
        gebiet = name
        val ziel = jahrgaenge.filter { it.gebiet == name }
        (ziel.firstOrNull { it.jahr == aktuell?.jahr } ?: ziel.firstOrNull())?.let(onWahl)
    }
    // Gebiete und Jahrzehnte stehen in EINER rollenden Liste: am Handy nehmen die Gebiete allein schon den
    // halben Bildschirm ein, und der Dialog selbst rollt nicht.
    val kopfZeilen = if (gruppe != null) 3 else 2
    LaunchedEffect(Unit) {
        // Nur beim Oeffnen zum aktuellen Jahrzehnt springen, nicht nach jedem Tipp auf ein Gebiet
        val start = jahrzehnte.indexOfFirst { (jz, _) -> aktuell != null && aktuell.gebiet == gebiet && jz == (aktuell.jahr / 10) * 10 }
        // Nur bei langen Listen (Bund, DDR …) zum Jahrzehnt springen; bei wenigen Jahrzehnten bleiben die Gebiete oben sichtbar
        liste.scrollToItem(if (start < 0 || jahrzehnte.size <= 3) 0 else start + kopfZeilen)
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.jahrgang_waehlen)) },
        text = {
            BoxWithConstraints {
                // Am Handy kurze Namen (Bund, DDR …), sonst die langen; die langen stehen ohnehin im Fenstertitel
                val schmal = maxWidth < 480.dp
                LazyColumn(state = liste, modifier = Modifier.heightIn(max = if (schmal) 640.dp else 480.dp)) {
                item(key = "gebiete") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val gezeigteGruppen = mutableSetOf<String>()
                        vorhandene.forEach { g ->
                            val gr = g.gruppe
                            if (gr == null) {
                                FilterChip(selected = gruppe == null && g.name == gebiet, onClick = { gruppe = null; gebietWaehlen(g.name) }, label = { Text(if (schmal) g.name else g.anzeige) })
                            } else if (gezeigteGruppen.add(gr)) {
                                // Die Gruppe als ein Eintrag; beim Wechsel in die Gruppe bleibt das zuletzt dort gewaehlte Gebiet, sonst das erste
                                FilterChip(selected = gruppe == gr, onClick = {
                                    gruppe = gr
                                    if (vorhandene.firstOrNull { it.name == gebiet }?.gruppe != gr) gebietWaehlen(g.name)
                                }, label = { Text(if (schmal) gr.substringBefore(" ") else gr) })
                            }
                        }
                    }
                }
                gruppe?.let { gr ->
                    item(key = "gruppe") {
                        // Kurze Namen (Bizone, Baden, SBZ …), die langen stehen im Titel des Hauptfensters
                        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            vorhandene.filter { it.gruppe == gr }.forEach { g ->
                                FilterChip(selected = g.name == gebiet, onClick = { gebietWaehlen(g.name) }, label = { Text(g.name) })
                            }
                        }
                    }
                }
                item(key = "trenner") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                items(jahrzehnte, key = { it.first }) { (jahrzehnt, jahre) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("${jahrzehnt}er", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            jahre.forEach { j ->
                                FilterChip(
                                    selected = aktuell?.jahr == j.jahr && aktuell.gebiet == j.gebiet,
                                    onClick = { onWahl(j); onClose() },
                                    label = { Text(j.jahr.toString()) },
                                )
                            }
                        }
                    }
                }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.schliessen)) } },
    )
}
