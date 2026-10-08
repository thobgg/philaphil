package de.bgghome.philaphil.daten

import de.bgghome.philaphil.ui.einordnung
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Heute vor Jahren": Jahrestage aus Wikidata und Ausgabetage, dazu der Einordnungssatz. */
class HeuteTest {
    private val katalog by lazy { runBlocking { Katalog.oeffnen(File(System.getProperty("java.io.tmpdir"), "philaphil-heute-" + System.nanoTime())) } }

    @Test
    fun einsteinAm14Maerz() {
        val tage = katalog.jahrestage("03-14")
        assertTrue(tage.any { it.titel == "Albert Einstein" && it.art == "geburt" && it.datum == "1879-03-14" })
    }

    @Test
    fun ausgabetage() {
        // 14. November 1979: Energie sparen, Paul Klee, Faust, Burgen und Schlösser
        assertTrue(katalog.ausgabenAmTag("11-14").any { it.gebiet == "Bund" && it.mi_nr == "1031" })
    }

    @Test
    fun einordnungOhneLebensdaten() {
        val satz = einordnung("Pierre Degeyter (* 8. Oktober 1848 in Gent, Belgien; † 26. September 1932 in Saint-Denis) war ein belgischer Komponist. Er schrieb die Melodie.")
        assertEquals("Pierre Degeyter war ein belgischer Komponist.", satz)
        assertEquals("Otto Warburg war ein Biochemiker.", einordnung("Otto Warburg (* 8. Oktober 1883) war ein Biochemiker. 1931 erhielt er den Nobelpreis."))
    }
}
