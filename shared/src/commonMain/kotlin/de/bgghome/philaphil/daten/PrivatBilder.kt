package de.bgghome.philaphil.daten

import de.bgghome.philaphil.res.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Bilder, die nur ein lokaler Build mitbringt (files/privat/, per .gitignore nie im Repo oder Release).
 * Dateien "<Gebiet>-<MiNr>.jpg" und eine liste.txt mit allen Namen. Fehlt der Ordner, ist die Liste leer.
 * Rang: eigenes Bild vor privatem Bild vor Commons.
 */
object PrivatBilder {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun laden(): Map<Pair<String, String>, OrdnerDatei> = withContext(Dispatchers.IO) {
        val liste = runCatching { Res.readBytes("files/privat/liste.txt").decodeToString() }.getOrNull() ?: return@withContext emptyMap()
        liste.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { name ->
            val gebiet = name.substringBefore('-')
            val miNr = name.substringAfter('-').substringBeforeLast('.').replace('_', ' ')
            if (gebiet.isEmpty() || miNr.isEmpty()) null
            else (gebiet to miNr) to OrdnerDatei(name, Res.getUri("files/privat/$name"), 0)
        }.toMap()
    }
}

/** Quellenzeile unter einem privaten Bild. */
const val PRIVAT_QUELLE = "Bild: briefmarken-bilder.de (© Martin Mißfeldt), nur privat"
