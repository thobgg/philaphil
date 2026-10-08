package de.bgghome.philaphil.daten

import de.bgghome.philaphil.Ablage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bestand: Erhaltungen, Anzahl, Notiz, JSON im Sammlungsordner und Abgleich von einem anderen Geraet. */
class SammlungTest {
    private class TestAblage : Ablage {
        val werte = HashMap<String, String>()
        override fun lesen(schluessel: String) = werte[schluessel]
        override fun schreiben(schluessel: String, wert: String?) { if (wert == null) werte.remove(schluessel) else werte[schluessel] = wert }
    }

    private fun neuerOrdner(name: String) = File(System.getProperty("java.io.tmpdir"), "philaphil-$name-" + System.nanoTime()).apply { mkdirs() }

    @Test
    fun erhaltungenAnzahlUndNotiz() {
        val daten = neuerOrdner("daten"); val sammlung = DateiOrdner(neuerOrdner("sammlung"))
        val s = Sammlung(daten, sammlung, TestAblage())
        s.setzen("Bund", "1031", "**", 1)
        s.setzen("Bund", "1031", "⊙", 2)
        s.notiz("Bund", "1031", "aus Opas Album")
        assertEquals("** ⊙×2", s.uebersicht()[Pair("Bund", "1031")])
        assertEquals("aus Opas Album", s.zurMarke("Bund", "1031").first().notiz)
        s.setzen("Bund", "1031", "**", 0)
        assertEquals(listOf("⊙"), s.zurMarke("Bund", "1031").map { it.erhaltung })
        // JSON liegt im Sammlungsordner und ist lesbar
        val json = File(sammlung.wurzel, BESTAND_JSON).readText()
        assertTrue(json.contains("\"mi_nr\": \"1031\"") && json.contains("aus Opas Album"))
    }

    @Test
    fun abgleichVonAnderemGeraet() {
        val sammlung = DateiOrdner(neuerOrdner("sammlung"))
        // Geraet A schreibt
        Sammlung(neuerOrdner("a"), sammlung, TestAblage()).setzen("Bund", "1019", "**", 1)
        // Geraet B liest denselben Ordner
        val b = Sammlung(neuerOrdner("b"), sammlung, TestAblage())
        assertEquals(1, b.alle().size)
        assertEquals("1019", b.alle().first().mi_nr)
        // Geraet A aendert spaeter (Datei wird neuer) - B gleicht beim naechsten Einlesen ab
        Thread.sleep(1100)
        Sammlung(neuerOrdner("a2"), sammlung, TestAblage()).setzen("Bund", "1020", "⊙", 1)
        b.abgleichen()
        assertEquals(listOf("1019", "1020"), b.alle().map { it.mi_nr })
    }

    @Test
    fun eigeneBilderNachNamensregel() {
        val ordner = DateiOrdner(neuerOrdner("bilder"))
        File(ordner.wurzel, "Bilder").mkdirs()
        listOf("Bund-1031.jpg", "bund_1031-2.PNG", "Bund 1028.webp", "DDR-123.jpg", "Urlaub.jpg", "Bund-Bl._16.jpg").forEach { File(ordner.wurzel, "Bilder/$it").writeBytes(byteArrayOf(1)) }
        val eigene = EigeneBilder(ordner)
        eigene.einlesen()
        assertEquals(listOf("Bund-1031.jpg", "bund_1031-2.PNG"), eigene.fuer("Bund", "1031").map { it.name })
        assertEquals(1, eigene.fuer("Bund", "1028").size)
        assertEquals(1, eigene.fuer("DDR", "123").size)
        assertEquals(1, eigene.fuer("Bund", "Bl. 16").size)
        assertTrue(eigene.fuer("Bund", "9999").isEmpty())
        val neu = eigene.ablegen("Bund", "1031", "jpg", byteArrayOf(2))
        assertEquals("Bund-1031-3.jpg", neu?.name)
    }
}
