package de.bgghome.philaphil.daten

import java.io.File

/**
 * Weitere Bilder nur fuer dich: liegen auf der NAS im Ordner Privat/ und werden beim Abgleich einmal aufs
 * Geraet geholt (Datenordner/privat/). Dateien "<Gebiet>-<MiNr>.jpg", dazu quelle.txt mit der Zeile unter
 * dem Bild. Sie kommen nie ins Repo oder in ein Release. Rang: eigenes Bild vor privatem Bild vor Commons.
 */
object PrivatBilder {
    const val QUELLE_VORGABE = "Bild: private Sammlung"

    fun laden(ordner: File): Map<Pair<String, String>, OrdnerDatei> =
        ordner.listFiles().orEmpty().filter { it.isFile && MUSTER.matches(it.name) }.mapNotNull { d ->
            val gebiet = d.name.substringBefore('-')
            val miNr = d.name.substringAfter('-').substringBeforeLast('.').replace('_', ' ')
            if (gebiet.isEmpty() || miNr.isEmpty()) null else (gebiet to miNr) to OrdnerDatei(d.name, d, d.lastModified())
        }.toMap()

    fun quelle(ordner: File): String =
        File(ordner, Abgleich.QUELLE).takeIf { it.isFile }?.readText()?.lines()?.firstOrNull { it.isNotBlank() }?.trim() ?: QUELLE_VORGABE

    private val MUSTER = Regex("""(?i)^[^-]+-.+\.(jpe?g|png|webp)$""")
}
