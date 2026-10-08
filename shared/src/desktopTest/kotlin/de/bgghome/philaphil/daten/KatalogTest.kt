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
    fun alleJahrgaenge() {
        val jahre = katalog.jahrgaenge.filter { it.gebiet == "Bund" }.map { it.jahr }
        assertEquals(1949L, jahre.first())
        assertTrue(jahre.last() >= 2026L)
        assertEquals(jahre.size, jahre.distinct().size)
        assertTrue(katalog.jahrgaenge.filter { it.gebiet == "Bund" }.sumOf { it.anzahl } > 3800)
        assertEquals(43, katalog.markenImJahr("Bund", 1978).size)
        val block = katalog.markenImJahr("Bund", 1978).first { it.mi_nr == "959" }
        assertEquals("Block 16", block.block)
        assertEquals(listOf("1037", "1038"), katalog.markenImJahr("Bund", 1980).filter { it.art == "Dauermarke" }.map { it.mi_nr })
    }

    @Test
    fun vierGebiete() {
        val gebiete = katalog.gebiete
        assertEquals(listOf("Bund", "Berlin", "DDR", "Reich"), gebiete.map { it.name })
        assertTrue(gebiete.all { it.anzahl > 800 })
        // Reich beginnt 1872 mit MiNr 1, DDR 1949, Berlin 1948
        assertEquals("1", katalog.markenImJahr("Reich", 1872).first().mi_nr)
        assertTrue(katalog.markenImJahr("DDR", 1965).any { it.mi_nr == "1084" })
        assertTrue(katalog.markenImJahr("Berlin", 1965).isNotEmpty())
        // Die Suche geht ueber alle Gebiete: Albert Schweitzer steht in der DDR 1965 (MiNr 1084)
        assertTrue(katalog.suche("Schweitzer").any { it.gebiet == "DDR" && it.mi_nr == "1084" })
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
        assertTrue(luftfahrt.weitereMarken >= 11)
        assertEquals(listOf("964", "965", "966", "967", "1005"), katalog.markenZumThema(luftfahrt.thema.id).filter { it.gebiet == "Bund" }.map { it.mi_nr }.take(5))
    }

    @Test
    fun kiSatzGekennzeichnet() {
        val einstein = katalog.markenImJahr("Bund", 1979).first { it.mi_nr == "1019" }
        val haupt = katalog.themenZurMarke(einstein.id).first { it.haupt }
        assertEquals("ki-entwurf", haupt.thema.wusstest_quelle)
        assertTrue(!haupt.thema.wusstest_du.isNullOrBlank())
    }

    @Test
    fun wusstestDuAusHandkorrektur() {
        val energie = katalog.markenImJahr("Bund", 1979).first { it.mi_nr == "1031" }
        val haupt = katalog.themenZurMarke(energie.id).first { it.haupt }
        assertTrue(haupt.thema.wusstest_du.orEmpty().contains("Ölkrise"))
        assertEquals("eigen", haupt.thema.wusstest_quelle)
    }

    @Test
    fun sucheFindetUeberThemenUndPraefix() {
        assertTrue("1019" in katalog.suche("Einst").map { it.mi_nr })   // 1979, dazu 1999 und 2005
        assertTrue(katalog.suche("luftfahrt").size >= 12)      // Jugendmarken 1978, 1979, 1980 und mehr
        assertEquals(listOf("1031"), katalog.suche("Energie sparen").map { it.mi_nr })
        assertTrue(katalog.suche("").isEmpty())
    }
}
