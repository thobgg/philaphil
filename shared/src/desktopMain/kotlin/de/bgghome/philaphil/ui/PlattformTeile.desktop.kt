package de.bgghome.philaphil.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberBildWaehler(onBild: (File) -> Unit): () -> Unit = remember {
    {
        SwingUtilities.invokeLater {
            val dialog = JFileChooser().apply {
                dialogTitle = "Bild der Marke wählen"
                fileFilter = FileNameExtensionFilter("Bilder (jpg, png, webp)", "jpg", "jpeg", "png", "webp")
            }
            if (dialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) dialog.selectedFile?.let(onBild)
        }
    }
}

@Composable
actual fun rememberOrdnerWaehler(onWahl: (String) -> Unit): () -> Unit = remember {
    {
        SwingUtilities.invokeLater {
            val dialog = JFileChooser().apply {
                dialogTitle = "Sammlungsordner (bestand.json und Bilder) wählen"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            }
            if (dialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) dialog.selectedFile?.let { onWahl(it.path) }
        }
    }
}

@Composable
actual fun rememberKamera(onBild: (File) -> Unit): (() -> Unit)? = null

actual fun bildVorbereiten(quelle: File, maxKante: Int): Pair<ByteArray, String> {
    val endung = quelle.extension.lowercase().let { if (it == "jpeg") "jpg" else it }.ifBlank { "jpg" }
    val bild = runCatching { javax.imageio.ImageIO.read(quelle) }.getOrNull() ?: return quelle.readBytes() to endung
    val faktor = maxKante.toDouble() / maxOf(bild.width, bild.height)
    if (faktor >= 1.0) return quelle.readBytes() to endung
    val b = (bild.width * faktor).toInt(); val h = (bild.height * faktor).toInt()
    val klein = java.awt.image.BufferedImage(b, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
    klein.createGraphics().apply {
        setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        drawImage(bild, 0, 0, b, h, java.awt.Color.WHITE, null); dispose()
    }
    val aus = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(klein, "jpg", aus)
    return aus.toByteArray() to "jpg"
}
