package de.bgghome.philaphil.daten

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** Sammlungsordner ueber das Android-Dokumentensystem (SAF): ein Ordner, den z. B. Synology Drive spiegelt. */
class DokumentOrdner(private val context: Context, private val baum: Uri) : Ordner {
    private val wurzel: DocumentFile? get() = DocumentFile.fromTreeUri(context, baum)
    override val anzeige: String get() = Uri.decode(baum.lastPathSegment ?: baum.toString()).substringAfter(':').ifBlank { baum.toString() }

    private fun ort(unterordner: String?): DocumentFile? =
        if (unterordner == null) wurzel else wurzel?.findFile(unterordner)?.takeIf { it.isDirectory }

    override fun liste(unterordner: String?): List<OrdnerDatei> =
        ort(unterordner)?.listFiles()?.filter { it.isFile && it.name != null }
            ?.map { OrdnerDatei(it.name!!, it.uri.toString(), it.lastModified()) }.orEmpty()

    override fun lesen(name: String): ByteArray? =
        wurzel?.findFile(name)?.takeIf { it.isFile }?.let { d -> context.contentResolver.openInputStream(d.uri)?.use { it.readBytes() } }

    override fun geaendert(name: String): Long = wurzel?.findFile(name)?.lastModified() ?: 0L

    override fun schreiben(name: String, bytes: ByteArray) {
        val w = wurzel ?: return
        val datei = w.findFile(name) ?: w.createFile("application/json", name) ?: return
        // "wt": kuerzen und neu schreiben, sonst bleiben Reste einer laengeren alten Fassung stehen
        context.contentResolver.openOutputStream(datei.uri, "wt")?.use { it.write(bytes) }
    }

    override fun neu(unterordner: String, name: String, bytes: ByteArray): OrdnerDatei? {
        val w = wurzel ?: return null
        val ordner = w.findFile(unterordner)?.takeIf { it.isDirectory } ?: w.createDirectory(unterordner) ?: return null
        if (ordner.findFile(name) != null) return null
        val mime = when (name.substringAfterLast('.').lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        val datei = ordner.createFile(mime, name) ?: return null
        context.contentResolver.openOutputStream(datei.uri)?.use { it.write(bytes) }
        return OrdnerDatei(datei.name ?: name, datei.uri.toString(), datei.lastModified())
    }

    override fun existiert(unterordner: String?, name: String) = ort(unterordner)?.findFile(name) != null
}
