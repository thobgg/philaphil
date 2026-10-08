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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.daten.datumLesbar
import de.bgghome.philaphil.daten.wertLesbar
import de.bgghome.philaphil.daten.zahlLesbar
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.db.Thema
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.am_rande
import de.bgghome.philaphil.res.anmerkung_quelle
import de.bgghome.philaphil.res.bild_eigenes
import de.bgghome.philaphil.res.bild_quelle
import de.bgghome.philaphil.res.fakt_auflage
import de.bgghome.philaphil.res.fakt_ausgabetag
import de.bgghome.philaphil.res.fakt_entwurf
import de.bgghome.philaphil.res.fakt_wert
import de.bgghome.philaphil.res.kein_bild
import de.bgghome.philaphil.res.marken_zum_thema
import de.bgghome.philaphil.res.marken_zum_thema_titel
import de.bgghome.philaphil.res.mehr_bei_wikipedia
import de.bgghome.philaphil.res.quelle_daten
import de.bgghome.philaphil.res.text_quelle_wikipedia
import de.bgghome.philaphil.res.themen_titel
import de.bgghome.philaphil.res.weitere_marken
import de.bgghome.philaphil.res.wusstest_du
import org.jetbrains.compose.resources.stringResource

/**
 * Die Marke gross, darunter die Themenkarte: Anlass in einer Zeile, Bildbeschreibung, Fakten,
 * das Hauptthema in drei Saetzen, "Wusstest du?", weitere Themen mit Sprung zur Themenseite.
 */
@Composable
fun MarkenDetail(zustand: AppZustand, viewModel: AppViewModel, gezeigt: Marke? = null) {
    val marke = gezeigt ?: zustand.marke ?: return
    // Themen und Bestand gehoeren zur gewaehlten Marke; Nachbarseiten im Wischen zeigen sie erst nach dem Einrasten
    val aktuell = zustand.marke?.id == marke.id
    val themen = if (aktuell) zustand.themen else emptyList()
    val hauptthema = themen.firstOrNull { it.haupt }
    val bild = zustand.bild(marke)
    val eigenes = zustand.eigene(marke).firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        MarkenBild(
            eigenes?.modell ?: bild?.vorschauUrl, marke,
            Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)
                .clickable(enabled = eigenes != null || bild != null) { viewModel.vollbild(true) },
        )
        Spacer(Modifier.height(6.dp))
        if (eigenes != null) {
            Text(stringResource(Res.string.bild_eigenes) + " · " + eigenes.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (bild != null) {
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

        // Erzaehlende Fussnoten der Wikipedia-Liste: kleine Geschichten zur Marke
        if (!marke.anmerkung.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(Res.string.am_rande), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    marke.anmerkung.split("\n\n").forEach { absatz ->
                        Text(absatz, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.anmerkung_quelle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (aktuell) Bestandskarte(zustand, viewModel, marke)
        Spacer(Modifier.height(16.dp))
        EigeneBilderZeile(zustand, viewModel, marke)

        // Das Hauptthema erzaehlt: Kurztext aus der Wikipedia, dazu "Wusstest du?"
        hauptthema?.let { haupt ->
            Spacer(Modifier.height(20.dp))
            ThemaText(haupt.thema, viewModel, mitTitel = true, zustand = zustand)
            if (haupt.weitereMarken > 0) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.marken_zum_thema, haupt.weitereMarken), color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { viewModel.themaOeffnen(haupt.thema) })
            }
        }

        if (themen.size > 1) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(Res.string.themen_titel), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                themen.forEach { t ->
                    AssistChip(
                        onClick = { viewModel.themaOeffnen(t.thema) },
                        label = {
                            Text(if (t.weitereMarken > 0) stringResource(Res.string.weitere_marken, t.thema.titel, t.weitereMarken) else t.thema.titel)
                        },
                        colors = if (t.haupt) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else AssistChipDefaults.assistChipColors(),
                    )
                }
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

/** Kurztext, "Wusstest du?" und der Link zur Wikipedia - fuer Themenkarte und Themenseite. */
@Composable
fun ThemaText(thema: Thema, viewModel: AppViewModel, mitTitel: Boolean, zustand: AppZustand? = null) {
    if (mitTitel) {
        Text(thema.titel, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
    }
    if (!thema.kurztext.isNullOrBlank()) {
        Text(thema.kurztext, style = MaterialTheme.typography.bodyLarge)
        if (thema.quelle == "wikipedia") {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.text_quelle_wikipedia), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (!thema.wusstest_du.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp)) {
                Text(stringResource(Res.string.wusstest_du), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(thema.wusstest_du, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (thema.wusstest_quelle == "ki-entwurf") {
                    Spacer(Modifier.height(4.dp))
                    Text("KI-Entwurf, am Wikipedia-Artikel belegt · Fehler? Bitte melden", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.clickable {
                            viewModel.oeffneWeb("mailto:thomas@bgg-mail.de?subject=" + java.net.URLEncoder.encode("PhilaPhil: Wusstest du? zu " + thema.titel, "UTF-8").replace("+", "%20") +
                                "&body=" + java.net.URLEncoder.encode("Satz: " + thema.wusstest_du + "\n\nWas stimmt nicht?\n", "UTF-8").replace("+", "%20"))
                        })
                }
            }
        }
    }
    zustand?.let { KiBereich(thema, it, viewModel) }
    thema.artikel_url?.let { url ->
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.mehr_bei_wikipedia), color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { viewModel.oeffneWeb(url) })
    }
}

/** Themenseite: das Thema mit Text und alle Marken dazu ueber alle Jahrgaenge. */
@Composable
fun ThemaSeite(zustand: AppZustand, viewModel: AppViewModel) {
    val seite = zustand.themaSeite ?: return
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(20.dp)) {
                ThemaText(seite.thema, viewModel, mitTitel = false, zustand = zustand)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(Res.string.marken_zum_thema_titel, seite.marken.size), style = MaterialTheme.typography.titleMedium)
            }
        }
        items(seite.marken, key = { it.id }) { marke ->
            MarkenZeile(marke, zustand, gewaehlt = zustand.marke?.id == marke.id, mitJahr = true, onClick = { viewModel.zurMarke(marke) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun Fakt(name: String, wert: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(name, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.bodyLarge)
    }
}


/**
 * Markenkarte mit Wischen: links und rechts liegen die Nachbarn in der Liste (Jahrgang, Suchtreffer, Filter).
 * Rastet eine Seite ein, wird sie zur gewaehlten Marke - Liste, Titel und Themen folgen.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MarkenPager(zustand: AppZustand, viewModel: AppViewModel) {
    val liste = zustand.liste
    val marke = zustand.marke ?: return
    val index = liste.indexOfFirst { it.id == marke.id }
    if (index < 0) { MarkenDetail(zustand, viewModel); return }
    val aktuelleListe = androidx.compose.runtime.rememberUpdatedState(liste)
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = index) { aktuelleListe.value.size }
    // Auswahl von aussen (Tipp in der Liste): Pager nachziehen
    androidx.compose.runtime.LaunchedEffect(index) { if (pager.currentPage != index) pager.scrollToPage(index) }
    // Gewischt: neue Marke waehlen, sobald die Seite eingerastet ist
    androidx.compose.runtime.LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { seite ->
            aktuelleListe.value.getOrNull(seite)?.let { if (it.id != viewModel.zustand.value.marke?.id) viewModel.waehlen(it) }
        }
    }
    androidx.compose.foundation.pager.HorizontalPager(state = pager, beyondViewportPageCount = 1, key = { liste[it].id }) { seite ->
        MarkenDetail(zustand, viewModel, gezeigt = liste[seite])
    }
}
