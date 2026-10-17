package de.bgghome.philaphil.daten

import java.io.File

/** Was ein Abgleich bewegt hat. */
data class AbgleichErgebnis(
    val bestandGeaendert: Boolean = false,
    val bilderHoch: Int = 0,
    val bilderRunter: Int = 0,
    val privatNeu: Int = 0,
    val privatGesamt: Int = 0,
    /** Private Bilder warten aufs WLAN. */
    val privatWartet: Boolean = false,
)

/**
 * Abgleich mit einem WebDAV-Ordner (zentral auf der NAS), damit Tablet, Handy und PC denselben Stand haben.
 * Das Geraet arbeitet weiter offline mit seinem Sammlungsordner; die NAS ist nur die gemeinsame Ablage.
 *
 *  - bestand.json: je Eintrag gilt die juengste Aenderung (siehe zusammenfuehren)
 *  - Bilder/: eigene Fotos in beide Richtungen; was einer Seite fehlt, wird kopiert, geloescht wird nichts
 *  - Privat/: weitere Bilder nur fuer dich (Lizenz erlaubt keine Weitergabe), werden einmal aufs Geraet
 *    geholt und dort dauerhaft gespeichert; quelle.txt enthaelt die Zeile unter dem Bild
 */
class Abgleich(private val dav: WebDav, private val sammlung: Sammlung, private val ordner: Ordner, private val privatOrdner: File) {

    fun bestand(): Boolean {
        // Schreibt ein anderes Geraet gleichzeitig, schlaegt das Schreiben fehl (ETag) und es geht von vorn los
        repeat(5) {
            var fern = dav.holen(BESTAND_JSON)
            // Apache vergibt in der ersten Sekunde nach dem Schreiben eine schwache Kennung (W/…), mit der
            // If-Match immer scheitert: kurz warten und neu lesen
            if (fern?.second?.startsWith("W/") == true) { Thread.sleep(1200); fern = dav.holen(BESTAND_JSON) }
            val fernListe = fern?.first?.let { sammlung.bestandLesen(it.toString(Charsets.UTF_8)) }.orEmpty()
            val lokal = sammlung.eintraege()
            val neu = zusammenfuehren(lokal, fernListe)
            val lokalGeaendert = neu != zusammenfuehren(lokal, emptyList())
            if (lokalGeaendert) sammlung.uebernehmen(neu)
            if (neu == zusammenfuehren(fernListe, emptyList()) && fern != null) return lokalGeaendert
            val bytes = sammlung.bestandJson(neu).toByteArray(Charsets.UTF_8)
            if (dav.schreiben(BESTAND_JSON, bytes, etag = fern?.second?.takeUnless { it.startsWith("W/") }, neu = fern == null)) return lokalGeaendert
        }
        throw WebDavFehler(412, "bestand.json wird gerade von einem anderen Gerät geschrieben")
    }

    /** Eigene Fotos: fehlende in beide Richtungen kopieren. Liefert (hochgeladen, heruntergeladen). */
    fun bilder(): Pair<Int, Int> {
        dav.ordnerAnlegen(EigeneBilder.UNTERORDNER)
        val fern = dav.liste(EigeneBilder.UNTERORDNER).orEmpty().map { it.name }.toSet()
        val lokal = ordner.liste(EigeneBilder.UNTERORDNER).map { it.name }.toSet()
        var hoch = 0; var runter = 0
        for (name in lokal - fern) {
            if (!BILD.matches(name)) continue
            val bytes = ordner.lesenIn(EigeneBilder.UNTERORDNER, name) ?: continue
            if (dav.schreiben("${EigeneBilder.UNTERORDNER}/$name", bytes, neu = true)) hoch++
        }
        for (name in fern - lokal) {
            if (!BILD.matches(name)) continue
            val bytes = dav.holen("${EigeneBilder.UNTERORDNER}/$name")?.first ?: continue
            if (ordner.neu(EigeneBilder.UNTERORDNER, name, bytes) != null) runter++
        }
        return hoch to runter
    }

    /**
     * Private Bilder aufs Geraet holen: neue und geaenderte (andere Groesse), auf der NAS entfernte auch hier
     * entfernen. Fehlt der Ordner auf der NAS, bleibt alles, wie es ist. Liefert (neu geholt, gesamt).
     */
    fun privat(holen: Boolean, fortschritt: (Int, Int) -> Unit = { _, _ -> }): Pair<Int, Int> {
        val fern = dav.liste(PRIVAT) ?: return 0 to (privatOrdner.listFiles()?.size ?: 0)
        privatOrdner.mkdirs()
        val gewollt = fern.filter { BILD.matches(it.name) || it.name == QUELLE }
        val fehlt = gewollt.filter { d -> File(privatOrdner, d.name).let { !it.isFile || (d.groesse >= 0 && it.length() != d.groesse) } }
        val namen = gewollt.map { it.name }.toSet()
        privatOrdner.listFiles()?.filter { it.isFile && it.name !in namen }?.forEach { it.delete() }
        if (!holen) return 0 to gewollt.size
        var n = 0
        for (d in fehlt) {
            val bytes = dav.holen("$PRIVAT/${d.name}")?.first ?: continue
            val tmp = File(privatOrdner, "${d.name}.neu")
            tmp.writeBytes(bytes)
            tmp.renameTo(File(privatOrdner, d.name))
            n++
            fortschritt(n, fehlt.size)
        }
        return n to gewollt.size
    }

    /** Fehlen auf dem Geraet noch private Bilder? (ohne sie zu holen) */
    fun privatFehlt(): Boolean {
        val fern = dav.liste(PRIVAT) ?: return false
        return fern.any { d -> (BILD.matches(d.name) || d.name == QUELLE) && File(privatOrdner, d.name).let { !it.isFile || (d.groesse >= 0 && it.length() != d.groesse) } }
    }

    companion object {
        const val PRIVAT = "Privat"
        const val QUELLE = "quelle.txt"
        private val BILD = Regex("""(?i).+\.(jpe?g|png|webp)$""")
    }
}
