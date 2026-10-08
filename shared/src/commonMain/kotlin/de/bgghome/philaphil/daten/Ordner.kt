package de.bgghome.philaphil.daten

import java.io.File

/** Eine Datei im Sammlungsordner; `modell` ist das, was Coil anzeigen kann (File oder content://-Adresse). */
data class OrdnerDatei(val name: String, val modell: Any, val geaendert: Long)

/**
 * Der Sammlungsordner: bestand.json und Bilder/. Auf dem Desktop ein normaler Ordner (gern ein Sync-Ordner
 * zum NAS), auf Android ein per Dokumentensystem gewaehlter Ordner, den eine Sync-App spiegelt.
 */
interface Ordner {
    val anzeige: String
    fun liste(unterordner: String? = null): List<OrdnerDatei>
    fun lesen(name: String): ByteArray?
    fun geaendert(name: String): Long
    fun schreiben(name: String, bytes: ByteArray)
    /** Legt eine neue Datei an (Bilder/Bund-1031.jpg); null, wenn es nicht geht. */
    fun neu(unterordner: String, name: String, bytes: ByteArray): OrdnerDatei?
    fun existiert(unterordner: String?, name: String): Boolean
}

/** Sammlungsordner als java.io.File - Desktop, und auf Android der Vorgabeordner unter Android/data. */
class DateiOrdner(val wurzel: File) : Ordner {
    override val anzeige: String get() = wurzel.path
    private fun ort(unterordner: String?) = if (unterordner == null) wurzel else File(wurzel, unterordner)

    override fun liste(unterordner: String?): List<OrdnerDatei> =
        ort(unterordner).listFiles()?.filter { it.isFile }?.map { OrdnerDatei(it.name, it, it.lastModified()) }.orEmpty()

    override fun lesen(name: String): ByteArray? = File(wurzel, name).takeIf { it.isFile }?.readBytes()
    override fun geaendert(name: String): Long = File(wurzel, name).lastModified()
    override fun schreiben(name: String, bytes: ByteArray) {
        wurzel.mkdirs()
        val tmp = File(wurzel, "$name.neu")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(wurzel, name))
    }
    override fun neu(unterordner: String, name: String, bytes: ByteArray): OrdnerDatei? {
        val ziel = File(ort(unterordner).apply { mkdirs() }, name)
        if (ziel.exists()) return null
        ziel.writeBytes(bytes)
        return OrdnerDatei(name, ziel, ziel.lastModified())
    }
    override fun existiert(unterordner: String?, name: String) = File(ort(unterordner), name).exists()
}
