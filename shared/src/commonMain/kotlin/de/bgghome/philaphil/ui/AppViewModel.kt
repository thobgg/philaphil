package de.bgghome.philaphil.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bgghome.philaphil.Plattform
import de.bgghome.philaphil.daten.BildInfo
import de.bgghome.philaphil.daten.CommonsBilder
import de.bgghome.philaphil.daten.Katalog
import de.bgghome.philaphil.daten.MarkenThema
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.db.Quelle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Der ganze Zustand der Oberflaeche an einer Stelle - die Hüllen zeigen ihn nur an. */
data class AppZustand(
    val laedt: Boolean = true,
    val fehler: String? = null,
    val gebiet: String = "Bund",
    val jahr: Long = 1979,
    val marken: List<Marke> = emptyList(),
    val quelle: Quelle? = null,
    /** Commons-Angaben je Dateiname; fehlt ein Eintrag, zeigt die Liste den Platzhalter. */
    val bilder: Map<String, BildInfo> = emptyMap(),
    /** Gewaehlte Marke (Index in marken) - am Handy eine eigene Seite, am Tablet die rechte Spalte. */
    val gewaehlt: Int? = null,
    val themen: List<MarkenThema> = emptyList(),
    /** Vollbild mit Wischen und Zoom ueber alle Marken mit Bild. */
    val vollbild: Boolean = false,
) {
    val marke: Marke? get() = gewaehlt?.let { marken.getOrNull(it) }
    fun bild(marke: Marke?): BildInfo? = marke?.commons_datei?.let { bilder[it] }
}

class AppViewModel(val plattform: Plattform) : ViewModel() {
    private val _zustand = MutableStateFlow(AppZustand())
    val zustand: StateFlow<AppZustand> = _zustand

    private var katalog: Katalog? = null
    private val commons = CommonsBilder(plattform.http, plattform.datenOrdner)

    init {
        viewModelScope.launch {
            try {
                val k = Katalog.oeffnen(plattform.datenOrdner).also { katalog = it }
                val z = _zustand.value
                val marken = withContext(Dispatchers.IO) { k.markenImJahr(z.gebiet, z.jahr) }
                val quelle = withContext(Dispatchers.IO) { k.quelle(z.gebiet, z.jahr) }
                _zustand.update { it.copy(laedt = false, marken = marken, quelle = quelle) }
                // Bildadressen und Lizenzen nachladen - erst aus der Ablage, sonst von Commons.
                val infos = commons.infos(marken.mapNotNull { it.commons_datei })
                _zustand.update { it.copy(bilder = infos) }
            } catch (e: Exception) {
                _zustand.update { it.copy(laedt = false, fehler = e.message ?: e.toString()) }
            }
        }
    }

    fun waehlen(index: Int?) {
        _zustand.update { it.copy(gewaehlt = index, themen = emptyList()) }
        val marke = index?.let { _zustand.value.marken.getOrNull(it) } ?: return
        viewModelScope.launch {
            val themen = withContext(Dispatchers.IO) { katalog?.themenZurMarke(marke.id).orEmpty() }
            // Nur eintragen, wenn inzwischen keine andere Marke gewaehlt wurde.
            _zustand.update { if (it.gewaehlt == index) it.copy(themen = themen) else it }
        }
    }

    fun vollbild(an: Boolean) = _zustand.update { it.copy(vollbild = an) }

    fun zurueck(): Boolean {
        val z = _zustand.value
        return when {
            z.vollbild -> { vollbild(false); true }
            z.gewaehlt != null -> { waehlen(null); true }
            else -> false
        }
    }

    fun oeffneWeb(url: String) = plattform.oeffneWeb(url)
}
