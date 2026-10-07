package de.bgghome.philaphil.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.bgghome.philaphil.daten.BildInfo
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.bild_quelle
import de.bgghome.philaphil.res.schliessen
import org.jetbrains.compose.resources.stringResource

private const val MAX_ZOOM = 6f
private const val DOPPELTIPP_ZOOM = 2.5f

/**
 * Vollbild: Wischen durch alle Marken des Jahrgangs, die ein Bild haben; Kneifzoom und Doppeltipp
 * wie in app4webtrees. Ein Tipp blendet Kopf- und Fusszeile aus, damit die Marke allein steht.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bildbetrachter(zustand: AppZustand, viewModel: AppViewModel, onClose: () -> Unit) {
    val mitBild = remember(zustand.marken, zustand.bilder) {
        zustand.marken.withIndex().mapNotNull { (i, m) -> zustand.bild(m)?.let { Triple(i, m, it) } }
    }
    if (mitBild.isEmpty()) { onClose(); return }
    val start = mitBild.indexOfFirst { it.first == zustand.gewaehlt }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { mitBild.size }
    var leiste by remember { mutableStateOf(true) }
    var gezoomt by remember { mutableStateOf(false) }

    // Die Auswahl in der Liste folgt dem Wischen, damit Liste und Themenkarte nach dem Schliessen passen.
    LaunchedEffect(pager.currentPage) { viewModel.waehlen(mitBild[pager.currentPage].first) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, userScrollEnabled = !gezoomt, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { seite ->
            val (_, marke, bild) = mitBild[seite]
            ZoomBild(marke, bild, onTap = { leiste = !leiste }, onZoom = { if (seite == pager.currentPage) gezoomt = it })
        }
        AnimatedVisibility(leiste, Modifier.align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).safeDrawingPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.schliessen), tint = Color.White) }
                Text("${pager.currentPage + 1} / ${mitBild.size}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
        AnimatedVisibility(leiste, Modifier.align(Alignment.BottomCenter)) {
            val (_, marke, bild) = mitBild[pager.currentPage]
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).safeDrawingPadding().padding(12.dp)) {
                Text("MiNr. ${marke.mi_nr} · ${marke.anlass.orEmpty()}", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                if (!marke.bild_beschreibung.isNullOrBlank()) Text(marke.bild_beschreibung, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.bild_quelle, bild.lizenz, bild.urheber ?: "Wikimedia Commons"),
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Ein Bild mit Kneifzoom, Verschieben und Doppeltipp. Zoom und Lage gehoeren zum Bild - beim Wechsel beginnt das naechste bei 1x. */
@Composable
private fun ZoomBild(marke: Marke, bild: BildInfo, onTap: () -> Unit, onZoom: (Boolean) -> Unit) {
    var skala by remember(bild) { mutableFloatStateOf(1f) }
    var lage by remember(bild) { mutableStateOf(Offset.Zero) }
    var groesse by remember { mutableStateOf(IntSize.Zero) }

    // Das vergroesserte Bild darf nicht aus dem Rahmen rutschen.
    fun begrenzen(o: Offset, s: Float): Offset {
        val maxX = (groesse.width * (s - 1f)) / 2f
        val maxY = (groesse.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun setzen(neueSkala: Float, neueLage: Offset) {
        skala = neueSkala.coerceIn(1f, MAX_ZOOM)
        lage = if (skala > 1f) begrenzen(neueLage, skala) else Offset.Zero
        onZoom(skala > 1f)
    }

    val transform = rememberTransformableState { zoom, pan, _ -> setzen(skala * zoom, lage + pan) }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { groesse = it }
            .pointerInput(bild) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tipp ->
                        if (skala > 1f) setzen(1f, Offset.Zero)
                        else {
                            // Die angetippte Stelle bleibt unter dem Finger.
                            val mitte = Offset(groesse.width / 2f, groesse.height / 2f)
                            setzen(DOPPELTIPP_ZOOM, (tipp - mitte) * (1f - DOPPELTIPP_ZOOM))
                        }
                    },
                )
            }
            // Ein Finger schiebt nur das vergroesserte Bild; bei 1x geht die Bewegung an den Pager (wischen).
            .transformable(transform, canPan = { skala > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            // Vollbild: das Original von Commons, die Vorschau ist schon im Cache und ueberbrueckt.
            model = bild.originalUrl, contentDescription = marke.anlass, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = skala; scaleY = skala
                translationX = lage.x; translationY = lage.y
            },
        )
    }
}
