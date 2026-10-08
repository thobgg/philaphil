package de.bgghome.philaphil.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.daten.datumLesbar
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.heute_leer
import de.bgghome.philaphil.res.heute_quelle
import de.bgghome.philaphil.res.tag_vor
import de.bgghome.philaphil.res.tag_zurueck
import org.jetbrains.compose.resources.stringResource

private val MONATE = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

/** "Otto Warburg (Biochemiker)" -> "Otto Warburg": Begriffsklaerung gehoert nicht in den Satz. */
private fun ohneKlammer(titel: String) = titel.replace(Regex("""\s*\([^()]*\)$"""), "")

/**
 * Erster Satz des Kurztexts ohne die Lebensdaten-Klammer: "Pierre Degeyter war ein belgischer Komponist …".
 * Ein Punkt nach einer Zahl ("8. Oktober") ist kein Satzende.
 */
fun einordnung(kurztext: String): String {
    val t = kurztext.replaceFirst(Regex("""\s*\([^()]*\)"""), "")
    for (m in Regex("""[.!?](?=\s+[A-ZÄÖÜ„0-9]|$)""").findAll(t)) {
        val davor = t.substring(0, m.range.first).takeLastWhile { !it.isWhitespace() }
        if (davor.isNotEmpty() && (davor.last().isDigit() || davor.length <= 2)) continue
        return t.substring(0, m.range.last + 1)
    }
    return t
}

/** Ein Satz wie "Vor 147 Jahren wurde Albert Einstein geboren." */
private fun ueberschrift(e: HeuteEintrag, heuteJahr: Int): String {
    val jahr = e.datum.take(4).toIntOrNull() ?: return e.thema?.titel.orEmpty()
    val vor = heuteJahr - jahr
    val zeit = when {
        vor == 0 -> "Heute"
        vor == 1 -> "Vor einem Jahr"
        vor > 0 -> "Vor $vor Jahren"
        else -> "In ${-vor} Jahren"
    }
    val titel = ohneKlammer(e.thema?.titel.orEmpty())
    return when (e.art) {
        "geburt" -> "$zeit wurde $titel geboren."
        "tod" -> "$zeit starb $titel."
        "gruendung" -> "$zeit: Gründung von $titel."
        "beginn" -> "$zeit begann: $titel."
        "ereignis" -> "$zeit: $titel."
        "ausgabe" -> if (e.marken.size == 1) "$zeit erschien diese Marke." else "$zeit erschienen diese ${e.marken.size} Marken."
        else -> "$zeit: $titel"
    }
}

/**
 * "Heute vor Jahren" wie in My Photo Diary: Jahrestage der Themen (Geburt, Tod, Gruendung … aus Wikidata)
 * und Marken, die an diesem Tag erschienen. Jeden Tag eine kleine Geschichte; blaettern mit den Pfeilen.
 */
@Composable
fun HeuteAnsicht(zustand: AppZustand, viewModel: AppViewModel, breit: Boolean) {
    val seite = zustand.heute ?: return
    val tag = seite.tag
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.heuteOeffnen(tag.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.tag_zurueck))
                }
                Text("${tag.dayOfMonth}. ${MONATE[tag.monthValue - 1]}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                IconButton(onClick = { viewModel.heuteOeffnen(tag.plusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.tag_vor))
                }
            }
        }
        if (seite.eintraege.isEmpty()) {
            item { Text(stringResource(Res.string.heute_leer), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(seite.eintraege, key = { it.art + it.datum + (it.thema?.id ?: 0) }) { e ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = if (e.art == "ausgabe") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(vertical = 10.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ueberschrift(e, java.time.LocalDate.now().year), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(datumLesbar(e.datum), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Ein Satz Einordnung aus dem Kurztext des Themas
                        e.thema?.kurztext?.let { k -> Text(einordnung(k), style = MaterialTheme.typography.bodyMedium) }
                    }
                    Spacer(Modifier.height(6.dp))
                    e.marken.take(6).forEach { m ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarkenZeile(m, zustand, gewaehlt = false, mitJahr = true, onClick = { viewModel.heuteZurMarke(m) })
                    }
                }
            }
        }
        item {
            Text(stringResource(Res.string.heute_quelle), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
