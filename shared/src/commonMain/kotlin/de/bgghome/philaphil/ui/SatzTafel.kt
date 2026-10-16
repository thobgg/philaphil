package de.bgghome.philaphil.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.daten.wertLesbar
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.satz_kopf
import de.bgghome.philaphil.res.satz_nach_nummer
import de.bgghome.philaphil.res.satz_nach_wert
import org.jetbrains.compose.resources.stringResource

/**
 * Satz-Tafel: alle Werte eines Satzes nebeneinander wie auf einem Albumblatt.
 * Dauermarkenserien laufen ueber Jahre und sind nach Ausgabejahr gruppiert. Ein Tipp oeffnet die Marke.
 */
@Composable
fun SatzTafel(zustand: AppZustand, viewModel: AppViewModel) {
    val seite = zustand.satzSeite ?: return
    var nachWert by remember(seite) { mutableStateOf(false) }
    val sortiert = if (nachWert) seite.marken.sortedWith(compareBy({ it.jahr }, { wertZahl(it) }, { it.sortier_nr })) else seite.marken
    val gruppen = if (seite.dauerserie) sortiert.groupBy { it.jahr } else mapOf(null to sortiert)
    val jahre = seite.marken.map { it.jahr }.distinct()
    val zeitraum = if (jahre.size > 1) "${jahre.first()}–${jahre.last()}" else jahre.first().toString()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(130.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(stringResource(Res.string.satz_kopf, seite.marken.size, zeitraum), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !nachWert, onClick = { nachWert = false }, label = { Text(stringResource(Res.string.satz_nach_nummer)) })
                    FilterChip(selected = nachWert, onClick = { nachWert = true }, label = { Text(stringResource(Res.string.satz_nach_wert)) })
                }
            }
        }
        gruppen.forEach { (jahr, marken) ->
            if (jahr != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "jahr-$jahr") {
                    Text(jahr.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(marken, key = { it.id }) { marke -> TafelFeld(marke, zustand, onClick = { viewModel.zurMarke(marke) }) }
        }
    }
}

@Composable
private fun TafelFeld(marke: Marke, zustand: AppZustand, onClick: () -> Unit) {
    val gewaehlt = zustand.marke?.id == marke.id
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        MarkenBild(zustand.vorschau(marke), marke, Modifier.fillMaxWidth().aspectRatio(0.82f))
        Spacer(Modifier.height(4.dp))
        Text(wertLesbar(marke), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            color = if (gewaehlt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (!marke.bild_beschreibung.isNullOrBlank()) {
            Text(marke.bild_beschreibung, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        }
        val bestand = zustand.bestandText(marke)
        Text("MiNr. ${marke.mi_nr}" + (bestand?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            color = if (bestand != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
}

/** Nennwert in der kleinsten Einheit zum Sortieren: "40+20" Pf -> 40, "1" DM -> 100. */
private fun wertZahl(marke: Marke): Double {
    val zahl = marke.wert.orEmpty().substringBefore('+').replace(',', '.').trim().toDoubleOrNull() ?: return Double.MAX_VALUE
    val faktor = when (marke.waehrung?.trim()) { "DM", "M", "MDN", "RM", "€", "EUR", "Euro" -> 100.0; else -> 1.0 }
    return zahl * faktor
}
