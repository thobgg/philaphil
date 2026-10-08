package de.bgghome.philaphil.ui

import androidx.compose.foundation.layout.Arrangement
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
 */
@Composable
fun Jahresauswahl(gebiete: List<Gebiete>, jahrgaenge: List<Jahrgaenge>, aktuell: Jahrgaenge?, onWahl: (Jahrgaenge) -> Unit, onClose: () -> Unit) {
    var gebiet by remember { mutableStateOf(aktuell?.gebiet ?: gebiete.firstOrNull()?.name ?: "Bund") }
    val jahrzehnte = remember(jahrgaenge, gebiet) { jahrgaenge.filter { it.gebiet == gebiet }.groupBy { (it.jahr / 10) * 10 }.toList() }
    val liste = rememberLazyListState()
    LaunchedEffect(gebiet) {
        val start = jahrzehnte.indexOfFirst { (jz, _) -> aktuell != null && aktuell.gebiet == gebiet && jz == (aktuell.jahr / 10) * 10 }
        liste.scrollToItem(start.coerceAtLeast(0))
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.jahrgang_waehlen)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    gebiete.filter { it.anzahl > 0 }.forEach { g ->
                        FilterChip(selected = g.name == gebiet, onClick = { gebiet = g.name }, label = { Text(g.anzeige) })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(state = liste, modifier = Modifier.heightIn(max = 420.dp)) {
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
