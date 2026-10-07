package de.bgghome.philaphil.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Warme, ruhige Farben wie altes Albumpapier: kein Markt, kein Feed.
private val Hell = lightColorScheme(
    primary = Color(0xFF7A4A1E),          // Siegellack-Braun
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E3CF),
    onPrimaryContainer = Color(0xFF3A2208),
    secondary = Color(0xFF5B6B4F),        // Albumgruen
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EAD6),
    onSecondaryContainer = Color(0xFF1E2A14),
    tertiary = Color(0xFF8A2F2F),
    background = Color(0xFFFBF7F0),       // Papier
    onBackground = Color(0xFF231A10),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF231A10),
    surfaceVariant = Color(0xFFEFE6D8),
    onSurfaceVariant = Color(0xFF52453A),
    outline = Color(0xFF8A7A68),
    outlineVariant = Color(0xFFD9CDBD),
)

private val Dunkel = darkColorScheme(
    primary = Color(0xFFE8B98A),
    onPrimary = Color(0xFF3A2208),
    primaryContainer = Color(0xFF5A3614),
    onPrimaryContainer = Color(0xFFF3E3CF),
    secondary = Color(0xFFC2D0B2),
    onSecondary = Color(0xFF1E2A14),
    secondaryContainer = Color(0xFF3E4C33),
    onSecondaryContainer = Color(0xFFE2EAD6),
    tertiary = Color(0xFFE5A3A3),
    background = Color(0xFF1C1814),
    onBackground = Color(0xFFEDE4D8),
    surface = Color(0xFF24201B),
    onSurface = Color(0xFFEDE4D8),
    surfaceVariant = Color(0xFF3A332C),
    onSurfaceVariant = Color(0xFFD3C7B8),
    outline = Color(0xFF9C8D7B),
    outlineVariant = Color(0xFF4E453B),
)

/** Etwas groessere Grundschrift - die App soll sich auch am Tablet und fuer aeltere Sammler gut lesen. */
private val Schrift = Typography().let { t ->
    t.copy(
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        titleMedium = t.titleMedium.copy(fontSize = 18.sp),
        titleLarge = t.titleLarge.copy(fontSize = 24.sp),
    )
}

@Composable
fun PhilaTheme(dunkel: Boolean? = null, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dunkel ?: isSystemInDarkTheme()) Dunkel else Hell,
        typography = Schrift,
        content = content,
    )
}
