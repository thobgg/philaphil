package de.bgghome.philaphil.daten

/**
 * Eigene Bilder im Unterordner Bilder/ des Sammlungsordners - fuer jede Marke, ob im Besitz oder nicht.
 * Dateien nach der Regel "<Gebiet>-<MiNr>.jpg", bei mehreren Bildern "<Gebiet>-<MiNr>-2.jpg". Erlaubt sind
 * jpg, jpeg, png, webp; Gross-/Kleinschreibung und Unterstrich oder Leerzeichen statt Bindestrich sind egal.
 * Eigenes Bild geht vor Commons, Commons vor Platzhalter.
 */
class EigeneBilder(var ordner: Ordner) {
    private var zuordnung: Map<Pair<String, String>, List<OrdnerDatei>> = emptyMap()

    fun einlesen(): Map<Pair<String, String>, List<OrdnerDatei>> {
        val gefunden = HashMap<Pair<String, String>, MutableList<OrdnerDatei>>()
        for (d in ordner.liste(UNTERORDNER)) {
            val m = MUSTER.matchEntire(d.name) ?: continue
            val gebiet = GEBIETE[m.groupValues[1].lowercase()] ?: continue
            val miNr = m.groupValues[2].replace('_', ' ').trim()
            gefunden.getOrPut(gebiet to miNr) { mutableListOf() }.add(d)
        }
        zuordnung = gefunden.mapValues { (_, l) -> l.sortedBy { it.name } }
        return zuordnung
    }

    fun fuer(gebiet: String, miNr: String): List<OrdnerDatei> = zuordnung[gebiet to miNr].orEmpty()

    /** Legt ein neues Bild ab: Bund-1031.jpg, dann Bund-1031-2.jpg … */
    fun ablegen(gebiet: String, miNr: String, endung: String, bytes: ByteArray): OrdnerDatei? {
        val basis = "$gebiet-${miNr.replace(' ', '_')}"
        var n = 1
        while (n < 100) {
            val name = if (n == 1) "$basis.$endung" else "$basis-$n.$endung"
            if (!ordner.existiert(UNTERORDNER, name)) return ordner.neu(UNTERORDNER, name, bytes)
            n++
        }
        return null
    }

    companion object {
        const val UNTERORDNER = "Bilder"
        // Laufende Nummer eines weiteren Bildes nur mit Bindestrich ("-2"), damit "Bl._16" die MiNr "Bl. 16" bleibt
        private val MUSTER = Regex("""(?i)^(bund|berlin|ddr)[-_ ]+(.+?)(?:-(\d{1,2}))?\.(jpe?g|png|webp)$""")
        private val GEBIETE = mapOf("bund" to "Bund", "berlin" to "Berlin", "ddr" to "DDR")
    }
}
