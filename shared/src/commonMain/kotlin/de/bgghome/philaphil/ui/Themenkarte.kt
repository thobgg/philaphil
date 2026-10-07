package de.bgghome.philaphil.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.daten.datumLesbar
import de.bgghome.philaphil.daten.wertLesbar
import de.bgghome.philaphil.daten.zahlLesbar
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.bild_quelle
import de.bgghome.philaphil.res.fakt_auflage
import de.bgghome.philaphil.res.fakt_ausgabetag
import de.bgghome.philaphil.res.fakt_entwurf
import de.bgghome.philaphil.res.fakt_wert
import de.bgghome.philaphil.res.kein_bild
import de.bgghome.philaphil.res.mehr_bei_wikipedia
import de.bgghome.philaphil.res.quelle_daten
import de.bgghome.philaphil.res.themen_titel
import de.bgghome.philaphil.res.weitere_marken
import org.jetbrains.compose.resources.stringResource

/**
 * Die Marke gross, darunter die Themenkarte: Anlass in einer Zeile, Bildbeschreibung, Fakten,
 * Themen mit Link zur Wikipedia. Kurztext und "Wusstest du?" folgen in Stufe 2.
 */
@Composable
fun MarkenDetail(zustand: AppZustand, viewModel: AppViewModel) {
    val marke = zustand.marke ?: return
    val bild = zustand.bild(marke)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        MarkenBild(
            bild?.vorschauUrl, marke,
            Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)
                .clickable(enabled = bild != null) { viewModel.vollbild(true) },
        )
        Spacer(Modifier.height(6.dp))
        if (bild != null) {
            Text(
                stringResource(Res.string.bild_quelle, bild.lizenz, bild.urheber ?: "Wikimedia Commons"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { viewModel.oeffneWeb(bild.seiteUrl) },
            )
        } else {
            Text(stringResource(Res.string.kein_bild), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(18.dp))
        Text(marke.anlass.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (!marke.bild_beschreibung.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(marke.bild_beschreibung, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Fakt(stringResource(Res.string.fakt_ausgabetag), datumLesbar(marke.ausgabetag))
                Fakt(stringResource(Res.string.fakt_wert), wertLesbar(marke))
                if (marke.auflage != null) Fakt(stringResource(Res.string.fakt_auflage), zahlLesbar(marke.auflage))
                if (!marke.entwerfer.isNullOrBlank()) Fakt(stringResource(Res.string.fakt_entwurf), marke.entwerfer)
            }
        }

        if (zustand.themen.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(Res.string.themen_titel), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                zustand.themen.forEach { t ->
                    AssistChip(
                        onClick = { t.thema.artikel_url?.let(viewModel::oeffneWeb) },
                        label = {
                            Text(if (t.weitereMarken > 0) stringResource(Res.string.weitere_marken, t.thema.titel, t.weitereMarken) else t.thema.titel)
                        },
                        colors = if (t.haupt) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else AssistChipDefaults.assistChipColors(),
                    )
                }
            }
            zustand.themen.firstOrNull { it.haupt }?.thema?.artikel_url?.let { url ->
                Spacer(Modifier.height(10.dp))
                Text(stringResource(Res.string.mehr_bei_wikipedia), color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { viewModel.oeffneWeb(url) })
            }
        }

        zustand.quelle?.let { q ->
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.quelle_daten, q.titel, q.lizenz ?: "CC BY-SA 4.0"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { viewModel.oeffneWeb(q.url) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Fakt(name: String, wert: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(name, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.bodyLarge)
    }
}
