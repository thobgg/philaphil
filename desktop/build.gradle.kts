// Linux/Windows-Huelle: ein Fenster um den geteilten Kern (:shared), native Pakete via jpackage.
// Gebaut wird je System, auf dem es laeuft: deb unter Linux, msi/exe unter Windows (WiX noetig).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

// Windows-Installer verlangen x.y.z und ersetzen nur bei hoeherer Nummer: dritte Stelle = Zahl der Commits (wie app4webtrees).
val versionName = property("philaphil.versionName") as String
val desktopBuild = property("philaphil.desktopBuild") as String
val commitZahl: String = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
        .standardOutput.asText.get().trim().takeIf { it.toIntOrNull() != null }
}.getOrNull() ?: desktopBuild
val windowsVersion = if (versionName.count { it == '.' } == 1) "$versionName.$commitZahl" else versionName

// Ein Code, zwei Paketnamen: PhilaWin unter Windows, PhilaTux unter Linux (wie wtWin/wtTux bei app4webtrees).
// Jedes Paket wird auf seinem eigenen System gebaut, darum entscheidet das System, auf dem Gradle laeuft.
val osName = System.getProperty("os.name").orEmpty()
val paketName = if (osName.startsWith("Windows")) "PhilaWin" else "PhilaTux"

compose.desktop {
    application {
        mainClass = "de.bgghome.philaphil.desktop.MainKt"
        jvmArgs += "-Dphilaphil.versionName=$versionName"
        jvmArgs += "-Dphilaphil.build=$commitZahl"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Exe)
            // NIE mehr aendern, sobald das erste Paket verteilt ist (Installationsordner, Startmenue).
            packageName = paketName
            modules("java.instrument", "java.prefs", "java.sql", "jdk.unsupported")
            packageVersion = versionName
            description = "$paketName - PhilaPhil, Briefmarken als Zeitgeschichte"
            vendor = "bgg-home.de (thomas@bgg-mail.de)"

            linux {
                menuGroup = "Office"
                packageName = "philatux"
                appRelease = desktopBuild
                iconFile.set(project.file("icons/app.png"))
            }
            windows {
                packageVersion = windowsVersion
                perUserInstall = true
                menuGroup = "PhilaWin"
                shortcut = true
                dirChooser = true
                // NIE aendern: nur mit gleicher Kennung ersetzt eine neue Version die alte.
                upgradeUuid = "6f2c1a84-9d3e-4b7a-a1c5-2e8f0d7b9c13"
                iconFile.set(project.file("icons/app.ico"))
            }
        }
    }
}
