package de.bgghome.philaphil.daten

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Prueft den mitgelieferten Katalog (Bund 1979) ueber den JDBC-Treiber. */
class KatalogTest {
    private val katalog by lazy {
        runBlocking { Katalog.oeffnen(File(System.getProperty("java.io.tmpdir"), "philaphil-test-" + System.nanoTime())) }
    }

    @Test
    fun jahrgang1979Vollstaendig() {
        val marken = katalog.markenImJahr("Bund", 1979)
        assertEquals(33, marken.size)
        assertEquals("1000", marken.first().mi_nr)
        assertEquals("1032", marken.last().mi_nr)     // 1028 ist eine Dauermarke und wird nach Nummer einsortiert
    }

    @Test
    fun hauptthemaMitKurztext() {
        val einstein = katalog.markenImJahr("Bund", 1979).first { it.mi_nr == "1019" }
        val themen = katalog.themenZurMarke(einstein.id)
        val haupt = themen.first { it.haupt }
        assertEquals("Albert Einstein", haupt.thema.titel)
        assertEquals("Q937", haupt.thema.wikidata)
        assertTrue(haupt.thema.kurztext.orEmpty().startsWith("Albert Einstein (* 14. März 1879"))
        assertEquals("wikipedia", haupt.thema.quelle)
    }

    @Test
    fun weitereMarkenZumThema() {
        val wal = katalog.markenImJahr("Bund", 1979).first { it.mi_nr == "1005" }
        val luftfahrt = katalog.themenZurMarke(wal.id).first { it.thema.titel == "Luftfahrt" }
        assertEquals(3, luftfahrt.weitereMarken)
        assertEquals(listOf("1005", "1006", "1007", "1008"), katalog.markenZumThema(luftfahrt.thema.id).map { it.mi_nr })
    }

    @Test
    fun wusstestDuAusHandkorrektur() {
        val energie = katalog.markenImJahr("Bund", 1979).first { it.mi_nr == "1031" }
        val haupt = katalog.themenZurMarke(energie.id).first { it.haupt }
        assertTrue(haupt.thema.wusstest_du.orEmpty().contains("Ölkrise"))
    }

    @Test
    fun sucheFindetUeberThemenUndPraefix() {
        assertEquals(listOf("1019"), katalog.suche("Einst").map { it.mi_nr })
        assertEquals(listOf("1005", "1006", "1007", "1008"), katalog.suche("luftfahrt").map { it.mi_nr })
        assertEquals(listOf("1031"), katalog.suche("Energie sparen").map { it.mi_nr })
        assertTrue(katalog.suche("").isEmpty())
    }
}
