package de.bgghome.philaphil.daten

import app.cash.sqldelight.db.SqlDriver
import de.bgghome.philaphil.db.KatalogDb
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.db.Thema
import de.bgghome.philaphil.res.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.File

/** Oeffnet die SQLite-Datei mit dem Treiber der Plattform (Android: Framework-SQLite, Desktop: sqlite-jdbc). */
expect fun katalogTreiber(datei: File): SqlDriver

/** Ein Thema mit der Angabe, ob es das Hauptthema der Marke ist. */
data class MarkenThema(val thema: Thema, val haupt: Boolean, val weitereMarken: Int)

/**
 * Der Katalog: wird bei Updates komplett ersetzt, darum liegt er getrennt vom eigenen Bestand.
 * Die Datei kommt als Ressource mit (tools/katalog_bauen.py) und wird beim Start in den Datenordner
 * kopiert, sobald sie sich von der dort liegenden unterscheidet.
 */
class Katalog private constructor(private val db: KatalogDb) {

    val jahrgaenge get() = db.katalogQueries.jahrgaenge().executeAsList()
    val gebiete get() = db.katalogQueries.gebiete().executeAsList()

    fun markenImJahr(gebiet: String, jahr: Long): List<Marke> =
        db.katalogQueries.markenImJahr(gebiet, jahr).executeAsList()

    fun themenZurMarke(markeId: Long): List<MarkenThema> =
        db.katalogQueries.themenZurMarke(markeId).executeAsList().map { z ->
            MarkenThema(
                thema = Thema(z.id, z.wikidata, z.titel, z.artikel_url, z.kurztext, z.wusstest_du, z.wusstest_quelle, z.quelle, z.geladen_am),
                haupt = z.haupt == 1L,
                weitereMarken = (db.katalogQueries.anzahlMarkenZumThema(z.id).executeAsOne() - 1).toInt(),
            )
        }

    fun markenZumThema(themaId: Long): List<Marke> = db.katalogQueries.markenZumThema(themaId).executeAsList()

    fun quelle(gebiet: String, jahr: Long) = db.katalogQueries.quelle(gebiet, jahr).executeAsOneOrNull()

    fun info(schluessel: String): String? = db.katalogQueries.info(schluessel).executeAsOneOrNull()?.wert

    fun alleCommonsDateien(): List<String> = db.katalogQueries.alleCommonsDateien().executeAsList().filterNotNull()

    fun markeNachNummer(gebiet: String, miNr: String): Marke? = db.katalogQueries.markeNachNummer(gebiet, miNr).executeAsOneOrNull()

    /** Themen mit Jahrestag an einem Tag ("10-08"), jeweils mit Art und Datum. */
    fun jahrestage(monatTag: String) = db.katalogQueries.jahrestageThemen(monatTag).executeAsList()

    fun ausgabenAmTag(monatTag: String): List<Marke> = db.katalogQueries.ausgabenAmTag(monatTag).executeAsList()

    fun jahrInfo(jahr: Long) = db.katalogQueries.jahrInfo(jahr).executeAsOneOrNull()
    fun ereignisse(jahr: Long) = db.katalogQueries.ereignisseImJahr(jahr).executeAsList()

    fun thema(id: Long): Thema? = db.katalogQueries.thema(id).executeAsOneOrNull()

    fun suche(text: String): List<Marke> {
        val woerter = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (woerter.isEmpty()) return emptyList()
        // Jedes Wort als Praefix, damit "Einst" schon Einstein findet.
        val ausdruck = woerter.joinToString(" ") { "\"" + it.replace("\"", "") + "\"*" }
        return runCatching { db.katalogQueries.suche(ausdruck).executeAsList() }.getOrElse {
            // Kein FTS5 auf dieser Plattform: einfache LIKE-Suche mit dem ganzen Text.
            val muster = "%" + text.trim() + "%"
            db.katalogQueries.sucheEinfach(muster, muster, muster, muster).executeAsList()
        }
    }

    companion object {
        @OptIn(ExperimentalResourceApi::class)
        suspend fun oeffnen(datenOrdner: File): Katalog = withContext(Dispatchers.IO) {
            val datei = File(datenOrdner, "katalog.db")
            val mitgeliefert = Res.readBytes("files/katalog.db")
            // Neu kopieren, wenn Groesse oder Inhalt abweichen (der Katalog wird nie an Ort und Stelle veraendert).
            if (!datei.isFile || datei.length() != mitgeliefert.size.toLong() || !datei.readBytes().contentEquals(mitgeliefert)) {
                datenOrdner.mkdirs()
                File(datenOrdner, "katalog.db.neu").apply { writeBytes(mitgeliefert) }.let { neu ->
                    datei.delete(); neu.renameTo(datei)
                }
            }
            Katalog(KatalogDb(katalogTreiber(datei)))
        }
    }
}

/** "1979-01-11" -> "11. Januar 1979"; unvollstaendige Angaben bleiben, wie sie sind. */
fun datumLesbar(iso: String?): String {
    if (iso == null) return ""
    val teile = iso.split("-")
    val monate = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    val monat = teile.getOrNull(1)?.toIntOrNull()?.let { monate.getOrNull(it - 1) } ?: return iso
    return when (teile.size) {
        3 -> "${teile[2].trimStart('0')}. $monat ${teile[0]}"
        2 -> "$monat ${teile[0]}"          // nur Monat bekannt (Dauermarken der 1950er)
        else -> iso
    }
}

/** 31900000 -> "31.900.000" */
fun zahlLesbar(zahl: Long?): String = zahl?.toString()?.reversed()?.chunked(3)?.joinToString(".")?.reversed() ?: ""

/** "40+20" + "Pf" -> "40 + 20 Pf" */
fun wertLesbar(marke: Marke): String =
    marke.wert.orEmpty().replace("+", " + ") + (marke.waehrung?.let { " $it" } ?: "")
