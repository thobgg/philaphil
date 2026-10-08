import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release-Signierung aus keystore.properties (nicht einchecken). Fehlt die Datei, bleibt das Release unsigniert.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "de.bgghome.philaphil"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.bgghome.philaphil"
        minSdk = 26
        targetSdk = 36
        versionCode = (property("philaphil.versionCode") as String).toInt()
        versionName = property("philaphil.versionName") as String
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            vcsInfo { include = false }
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }

    // Bibliotheken des KI-SDK bringen gleichnamige Lizenz- und Metadateien mit; fuer die App unnoetig
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    // Dateiname des Pakets: PhilaAnd-0.1.apk bzw. PhilaAnd-0.1-debug.apk (App heisst weiter PhilaPhil)
    applicationVariants.all {
        val variante = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "PhilaAnd-${variante.versionName}" + (if (variante.buildType.name == "debug") "-debug" else "") + ".apk"
        }
    }
}

dependencies {
    // Der gesamte Code liegt im geteilten Modul; die Huelle hat nur MainActivity, Manifest und Launcher.
    implementation(project(":shared"))
}
