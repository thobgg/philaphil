package de.bgghome.philaphil.daten

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Companion-Prinzip: ein Entwurf gilt nur, wenn sein Beleg woertlich im Artikel steht. */
class KiTest {
    private val artikel = "Der Schwarze Einser ist die erste Briefmarke des Königreichs Bayern. Sie erschien am 1. November 1849 " +
        "und zeigt eine große Eins in einem Quadrat.\nDie Druckplatte bestand aus 90 Einzelklischees."

    @Test
    fun belegImTextWirdAngenommen() {
        val a = KiGemeinsam.pruefen("""{"satz": "Gedruckt wurde von einer Platte mit 90 Einzelklischees.", "beleg": "Die Druckplatte bestand aus 90 Einzelklischees."}""", artikel, "test")
        assertTrue(a is KiAntwort.Entwurf)
        assertEquals("Gedruckt wurde von einer Platte mit 90 Einzelklischees.", (a as KiAntwort.Entwurf).entwurf.satz)
    }

    @Test
    fun erfundenerBelegWirdVerworfen() {
        val a = KiGemeinsam.pruefen("""{"satz": "Er kostete einen Kreuzer.", "beleg": "Die Marke kostete einen Kreuzer und war schwarz."}""", artikel, "test")
        assertTrue(a is KiAntwort.Fehler)
    }

    @Test
    fun antwortMitTextDrumherum() {
        val a = KiGemeinsam.pruefen("Hier mein Vorschlag:\n```json\n{\"satz\": \"Sie zeigt eine große Eins.\", \"beleg\": \"zeigt eine große Eins in einem Quadrat\"}\n```", artikel, "test")
        assertTrue(a is KiAntwort.Entwurf)
    }

    @Test
    fun leererVorschlagIstKeinEntwurf() {
        assertTrue(KiGemeinsam.pruefen("""{"satz": "", "beleg": ""}""", artikel, "test") is KiAntwort.Fehler)
        assertTrue(KiGemeinsam.pruefen("kein json", artikel, "test") is KiAntwort.Fehler)
    }

    @Test
    fun kuerzenAnAbsatzgrenze() {
        val lang = (1..2000).joinToString("\n") { "Absatz $it mit etwas Text." }
        val k = KiGemeinsam.kuerzen(lang)
        assertTrue(k.length <= KiGemeinsam.HOECHSTENS_ZEICHEN)
        assertTrue(k.endsWith("Text."))
    }

    /** Echter Aufruf nur, wenn PHILAPHIL_KI_TEST=1 und ein Schluessel in ANTHROPIC_API_KEY steht. */
    @Test
    fun echterClaudeAufruf() {
        if (System.getenv("PHILAPHIL_KI_TEST") != "1") return
        val schluessel = System.getenv("ANTHROPIC_API_KEY") ?: return
        val http = okhttp3.OkHttpClient()
        val text = wikipediaText(http, "Schwarzer Einser") ?: error("Wikipedia nicht erreichbar")
        val a = runBlocking { KiBegleiter(schluessel).wusstestDu("Schwarzer Einser", KiGemeinsam.kuerzen(text)) }
        println("KI-Ergebnis: $a")
        assertTrue(a is KiAntwort.Entwurf, a.toString())
    }
}
