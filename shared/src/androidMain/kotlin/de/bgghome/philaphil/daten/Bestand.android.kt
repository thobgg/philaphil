package de.bgghome.philaphil.daten

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import de.bgghome.philaphil.AndroidKontext
import de.bgghome.philaphil.bestanddb.BestandDb
import java.io.File

/** Framework-SQLite; der Treiber legt das Schema bei einer neuen Datei selbst an. */
actual fun bestandTreiber(datei: File): SqlDriver =
    AndroidSqliteDriver(BestandDb.Schema, AndroidKontext.app, datei.absolutePath)
