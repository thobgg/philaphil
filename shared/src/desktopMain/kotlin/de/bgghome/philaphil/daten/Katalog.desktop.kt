package de.bgghome.philaphil.daten

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/** sqlite-jdbc (bringt FTS5 mit). Nur lesen - der Katalog wird nie an Ort und Stelle geaendert. */
actual fun katalogTreiber(datei: File): SqlDriver =
    JdbcSqliteDriver("jdbc:sqlite:${datei.absolutePath}")
