package de.bgghome.philaphil.daten

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.bgghome.philaphil.bestanddb.BestandDb
import java.io.File

/** sqlite-jdbc; Schema anlegen, wenn die Datei neu ist (user_version 0). */
actual fun bestandTreiber(datei: File): SqlDriver {
    datei.parentFile?.mkdirs()
    val treiber = JdbcSqliteDriver("jdbc:sqlite:${datei.absolutePath}")
    val version = treiber.executeQuery(null, "PRAGMA user_version", { c -> c.next(); app.cash.sqldelight.db.QueryResult.Value(c.getLong(0)) }, 0).value ?: 0L
    if (version == 0L) {
        BestandDb.Schema.create(treiber)
        treiber.execute(null, "PRAGMA user_version = ${BestandDb.Schema.version}", 0)
    }
    return treiber
}
