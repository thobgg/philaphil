package de.bgghome.philaphil.daten

import de.bgghome.philaphil.Ablage
import okhttp3.OkHttpClient
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Abgleich mit der NAS: Zusammenfuehren je Eintrag, und mit einem WebDAV-Server zwei Geraete gegeneinander. */
class AbgleichTest {
    private class TestAblage : Ablage {
        val werte = HashMap<String, String>()
        override fun lesen(schluessel: String) = werte[schluessel]
        override fun schreiben(schluessel: String, wert: String?) { if (wert == null) werte.remove(schluessel) else werte[schluessel] = wert }
    }

    private fun neuerOrdner(name: String) = File(System.getProperty("java.io.tmpdir"), "philaphil-$name-" + System.nanoTime()).apply { mkdirs() }

    @Test
    fun juengsteAenderungGilt() {
        val tablet = listOf(BestandEintrag("Bund", "1031", "**", 1, geaendert = "2026-10-17T10:00"),
            BestandEintrag("Bund", "1019", "⊙", 0, geaendert = "2026-10-17T10:05"))       // am Tablet entfernt
        val handy = listOf(BestandEintrag("Bund", "1019", "⊙", 1, geaendert = "2026-10-16T09:00"),
            BestandEintrag("Bund", "1000", "FDC", 1, geaendert = "2026-10-17T11:00"))
        val z = zusammenfuehren(tablet, handy)
        assertEquals(listOf("1000", "1019", "1031"), z.map { it.mi_nr })
        assertEquals(0L, z.first { it.mi_nr == "1019" }.anzahl)            // die Loeschung ist juenger
    }

    /** Laeuft nur mit einem WebDAV-Server: DAV_TEST_URL, DAV_TEST_BENUTZER, DAV_TEST_PASSWORT. */
    @Test
    fun zweiGeraeteUeberWebDav() {
        val url = System.getenv("DAV_TEST_URL") ?: return
        val dav = WebDav(OkHttpClient(), url, System.getenv("DAV_TEST_BENUTZER"), System.getenv("DAV_TEST_PASSWORT"))
        dav.pruefen()

        fun geraet(name: String): Triple<Sammlung, DateiOrdner, File> {
            val ordner = DateiOrdner(neuerOrdner("$name-sammlung"))
            return Triple(Sammlung(neuerOrdner("$name-daten"), ordner, TestAblage()), ordner, neuerOrdner("$name-privat"))
        }
        val (tablet, tabletOrdner, tabletPrivat) = geraet("tablet")
        val (handy, handyOrdner, handyPrivat) = geraet("handy")

        // Tablet traegt ein und legt ein Foto ab
        tablet.setzen("Bund", "1031", "**", 1)
        tablet.setzen("Bund", "1019", "⊙", 2)
        tabletOrdner.neu(EigeneBilder.UNTERORDNER, "Bund-1031.jpg", byteArrayOf(1, 2, 3))
        val a = Abgleich(dav, tablet, tabletOrdner, tabletPrivat)
        a.bestand(); a.bilder()

        // Handy holt alles, traegt selbst etwas ein und entfernt 1019
        val b = Abgleich(dav, handy, handyOrdner, handyPrivat)
        assertTrue(b.bestand())
        assertEquals("⊙×2", handy.uebersicht()[Pair("Bund", "1019")])
        assertEquals(0 to 1, b.bilder())
        assertTrue(File(handyOrdner.wurzel, "Bilder/Bund-1031.jpg").isFile)
        Thread.sleep(1100)                          // Zeitstempel auf Sekunden
        handy.setzen("Bund", "1000", "FDC", 1)
        handy.setzen("Bund", "1019", "⊙", 0)
        b.bestand()

        // Tablet sieht beides
        assertTrue(a.bestand())
        assertEquals(setOf("1000", "1031"), tablet.uebersicht().keys.map { it.second }.toSet())

        // Private Bilder: einmal holen, beim zweiten Mal nichts mehr
        val (neu, gesamt) = b.privat(holen = true)
        assertTrue(gesamt > 0 && neu == gesamt)
        assertEquals(0, b.privat(holen = true).first)
        assertTrue(PrivatBilder.laden(handyPrivat).isNotEmpty())
    }
}
