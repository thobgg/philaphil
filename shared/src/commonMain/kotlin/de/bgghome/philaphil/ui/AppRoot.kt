package de.bgghome.philaphil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.bgghome.philaphil.daten.datumLesbar
import de.bgghome.philaphil.daten.wertLesbar
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.app_titel
import de.bgghome.philaphil.res.fehler_laden
import de.bgghome.philaphil.res.jahrgang_titel
import de.bgghome.philaphil.res.jahrgang_untertitel
import de.bgghome.philaphil.res.zurueck
import org.jetbrains.compose.resources.stringResource

/** Ab dieser Breite (Tablet, Desktop) stehen Liste und Marke nebeneinander. */
private val BREIT_AB = 840.dp

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val zustand by viewModel.zustand.collectAsState()

    BackHandler(enabled = zustand.vollbild || zustand.gewaehlt != null) { viewModel.zurueck() }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val breit = maxWidth >= BREIT_AB
        when {
            zustand.vollbild && zustand.marke != null ->
                Bildbetrachter(zustand, viewModel, onClose = { viewModel.vollbild(false) })
            !breit && zustand.marke != null ->
                MarkenSeite(zustand, viewModel, onZurueck = { viewModel.waehlen(null) })
            else -> Scaffold(topBar = { JahrgangKopf(zustand) }, containerColor = MaterialTheme.colorScheme.background) { innen ->
                Row(Modifier.padding(innen).fillMaxSize()) {
                    Box(if (breit) Modifier.width(420.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        Markenliste(zustand, onWahl = viewModel::waehlen)
                    }
                    if (breit) {
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            val marke = zustand.marke
                            if (marke != null) MarkenDetail(zustand, viewModel)
                            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.jahrgang_untertitel), style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JahrgangKopf(zustand: AppZustand) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(Res.string.jahrgang_titel, zustand.gebiet, zustand.jahr.toString()))
                Text(stringResource(Res.string.app_titel), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun Markenliste(zustand: AppZustand, onWahl: (Int) -> Unit) {
    when {
        zustand.laedt -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        zustand.fehler != null -> Text(stringResource(Res.string.fehler_laden, zustand.fehler), Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.error)
        else -> LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(zustand.marken, key = { _, m -> m.id }) { index, marke ->
                MarkenZeile(marke, zustand, gewaehlt = zustand.gewaehlt == index, onClick = { onWahl(index) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun MarkenZeile(marke: Marke, zustand: AppZustand, gewaehlt: Boolean, onClick: () -> Unit) {
    val hintergrund = if (gewaehlt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
    Row(
        Modifier.fillMaxWidth().background(hintergrund).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkenBild(zustand.bild(marke)?.vorschauUrl, marke, Modifier.size(width = 72.dp, height = 84.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(marke.anlass.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (!marke.bild_beschreibung.isNullOrBlank()) {
                Text(marke.bild_beschreibung, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text("MiNr. ${marke.mi_nr} · ${wertLesbar(marke)} · ${datumLesbar(marke.ausgabetag)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Vorschau oder Platzhalter (Rahmen mit MiNr.), wenn Commons kein Bild hat. */
@Composable
fun MarkenBild(url: String?, marke: Marke, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = marke.anlass, contentScale = contentScale, modifier = Modifier.fillMaxSize().padding(2.dp))
        } else {
            Text(marke.mi_nr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Handy: die gewaehlte Marke als eigene Seite mit Zurueck-Pfeil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkenSeite(zustand: AppZustand, viewModel: AppViewModel, onZurueck: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MiNr. ${zustand.marke?.mi_nr.orEmpty()}") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.zurueck)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen -> Box(Modifier.padding(innen)) { MarkenDetail(zustand, viewModel) } }
}
