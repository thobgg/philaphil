package de.bgghome.philaphil.ui

import androidx.compose.runtime.Composable
import java.io.File

/**
 * Bild auswaehlen (Android: Fotoauswahl, Desktop: Dateidialog). Liefert eine Funktion, die den Dialog
 * oeffnet; das gewaehlte Bild kommt als Datei (auf Android eine Kopie im Cache) zurueck.
 */
@Composable
expect fun rememberBildWaehler(onBild: (File) -> Unit): () -> Unit

/**
 * Sammlungsordner waehlen. Liefert eine Funktion, die den Dialog oeffnet; zurueck kommt der Wert fuer die
 * Einstellung "sammlung" (Desktop: Pfad, Android: Ordneradresse des Dokumentensystems).
 */
@Composable
expect fun rememberOrdnerWaehler(onWahl: (String) -> Unit): () -> Unit

/** Foto mit der Kamera aufnehmen (nur Android); null, wenn es keine Kamera gibt. */
@Composable
expect fun rememberKamera(onBild: (File) -> Unit): (() -> Unit)?

/**
 * Bild fuers Ablegen vorbereiten: auf hoechstens `maxKante` Pixel verkleinern und als JPEG liefern.
 * Liefert (Bytes, Endung); kleine Bilder bleiben, wie sie sind.
 */
expect fun bildVorbereiten(quelle: File, maxKante: Int = 1600): Pair<ByteArray, String>
