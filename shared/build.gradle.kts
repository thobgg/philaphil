// Geteilter Kern: Katalog-Datenbank (SQLDelight), Commons-Bilder, Oberflaeche.
// Wird von :app (Android) und :desktop (Linux/Windows) benutzt - Muster wie app4webtrees.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi", "-Xexpect-actual-classes")
    }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // api statt implementation: die Huellen bauen ihre Oberflaeche mit denselben Compose-Artefakten.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.components.resources)
            api("org.jetbrains.compose.material:material-icons-core:1.7.3")
            api(libs.kotlinx.serialization.json)
            // Beide Ziele sind JVM: commonMain darf OkHttp und java.io.File benutzen.
            api(libs.okhttp)
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.coil.compose)
            api(libs.coil.network.okhttp)
            api(libs.compose.ui.backhandler)
            api(libs.sqldelight.runtime)
            // KI-Begleiter: nur nach Opt-in mit eigenem Schluessel aktiv
            implementation(libs.anthropic.java)
        }
        androidMain.dependencies {
            api(libs.androidx.core.ktx)
            api(libs.androidx.activity.compose)
            api(libs.androidx.documentfile)
            api(libs.sqldelight.android.driver)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Dispatchers.Main auf der JVM (Swing/AWT-Thread) - ohne ihn stirbt der erste viewModelScope.launch.
                api(libs.kotlinx.coroutines.swing)
                api(libs.sqldelight.sqlite.driver)
            }
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
        }
    }
}

sqldelight {
    databases {
        create("KatalogDb") {
            packageName.set("de.bgghome.philaphil.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
            // FTS5-Tabellen braucht der neuere Dialekt.
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${libs.versions.sqldelight.get()}")
            // Die Datenbank kommt fertig gebaut mit (tools/katalog_bauen.py); SQLDelight legt sie nie selbst an.
            verifyMigrations.set(false)
        }
        // Eigener Bestand: zweite Datei, die bei Katalog-Updates erhalten bleibt. Legt SQLDelight selbst an.
        create("BestandDb") {
            packageName.set("de.bgghome.philaphil.bestanddb")
            srcDirs.setFrom("src/commonMain/sqldelight-bestand")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${libs.versions.sqldelight.get()}")
            verifyMigrations.set(false)
        }
    }
}

compose.resources {
    packageOfResClass = "de.bgghome.philaphil.res"
    publicResClass = true
}

android {
    namespace = "de.bgghome.philaphil.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "VERSION_NAME", "\"${property("philaphil.versionName")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
