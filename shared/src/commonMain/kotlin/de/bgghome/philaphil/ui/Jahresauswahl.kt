package de.bgghome.philaphil.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.db.Jahrgaenge
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.jahrgang_waehlen
import de.bgghome.philaphil.res.schliessen
import org.jetbrains.compose.resources.stringResource

/** Alle Jahrgaenge nach Jahrzehnten, wie die Kapitel eines Geschichtsbuchs. */
@Composable
fun Jahresauswahl(jahrgaenge: List<Jahrgaenge>, aktuell: Jahrgaenge?, onWahl: (Jahrgaenge) -> Unit, onClose: () -> Unit) {
    val jahrzehnte = remember(jahrgaenge) { jahrgaenge.groupBy { Pair(it.gebiet, (it.jahr / 10) * 10) }.toList() }
    val start = jahrzehnte.indexOfFirst { (k, _) -> aktuell != null && k.first == aktuell.gebiet && k.second == (aktuell.jahr / 10) * 10 }
    val liste = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.jahrgang_waehlen)) },
        text = {
            LazyColumn(state = liste, modifier = Modifier.heightIn(max = 480.dp)) {
                items(jahrzehnte, key = { it.first }) { (schluessel, jahre) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("${schluessel.first} · ${schluessel.second}er", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(4.dp))
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
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.schliessen)) } },
    )
}
