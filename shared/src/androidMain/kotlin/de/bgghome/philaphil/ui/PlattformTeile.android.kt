package de.bgghome.philaphil.ui

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun rememberBildWaehler(onBild: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    val starter = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Inhalt in eine Datei im Cache kopieren; die Endung aus dem MIME-Typ, damit der Zielname stimmt
        val endung = MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: "jpg"
        val ziel = File(context.cacheDir, "auswahl-${System.currentTimeMillis()}.$endung")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { ein -> ziel.outputStream().use { ein.copyTo(it) } }
            onBild(ziel)
        }
    }
    return { starter.launch("image/*") }
}

/** Ordner ueber das Dokumentensystem waehlen; die Berechtigung bleibt ueber Neustarts erhalten. */
@Composable
actual fun rememberOrdnerWaehler(onWahl: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val starter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        onWahl(uri.toString())
    }
    return { starter.launch(null) }
}

/** Kamera: das Foto landet in einer Datei im Cache, die Kamera-App bekommt nur dafuer Schreibrecht (FileProvider). */
@Composable
actual fun rememberKamera(onBild: (File) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val ziel = androidx.compose.runtime.remember { File(context.cacheDir, "kamera-${System.currentTimeMillis()}.jpg") }
    val starter = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok && ziel.length() > 0) onBild(ziel) }
    if (!context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) return null
    return {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", ziel)
        starter.launch(uri)
    }
}

actual fun bildVorbereiten(quelle: File, maxKante: Int): Pair<ByteArray, String> {
    val endung = quelle.extension.lowercase().let { if (it == "jpeg") "jpg" else it }.ifBlank { "jpg" }
    val masse = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(quelle.path, masse)
    val groesste = maxOf(masse.outWidth, masse.outHeight)
    if (groesste <= 0 || groesste <= maxKante) return quelle.readBytes() to endung
    // Erst grob per inSampleSize, dann genau skalieren - spart Speicher bei grossen Fotos
    var probe = 1
    while (groesste / (probe * 2) >= maxKante) probe *= 2
    val roh = android.graphics.BitmapFactory.decodeFile(quelle.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = probe })
        ?: return quelle.readBytes() to endung
    val faktor = maxKante.toFloat() / maxOf(roh.width, roh.height)
    val bild = if (faktor < 1f) android.graphics.Bitmap.createScaledBitmap(roh, (roh.width * faktor).toInt(), (roh.height * faktor).toInt(), true) else roh
    // Drehung aus den EXIF-Daten uebernehmen (Handyfotos liegen oft quer gespeichert)
    val gedreht = runCatching {
        val exif = android.media.ExifInterface(quelle.path)
        val grad = when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        if (grad == 0f) bild else android.graphics.Bitmap.createBitmap(bild, 0, 0, bild.width, bild.height, android.graphics.Matrix().apply { postRotate(grad) }, true)
    }.getOrDefault(bild)
    val aus = java.io.ByteArrayOutputStream()
    gedreht.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, aus)
    return aus.toByteArray() to "jpg"
}
