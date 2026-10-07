package de.bgghome.philaphil.daten

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import de.bgghome.philaphil.AndroidKontext
import de.bgghome.philaphil.db.KatalogDb
import java.io.File

/** Framework-SQLite. Die Datei hat user_version 1 = Schema-Version, darum legt der Treiber nichts an. */
actual fun katalogTreiber(datei: File): SqlDriver =
    AndroidSqliteDriver(KatalogDb.Schema, AndroidKontext.app, datei.absolutePath)
