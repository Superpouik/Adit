package fr.pouik.audit.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Repli quand la couleur dynamique n'est pas disponible (API 30). Un vert
// sombre : l'app se tient à bout de bras en magasin, souvent sous des néons.
private val ClairPalette = lightColorScheme(
    primary = Color(0xFF1F5C4A),
    secondary = Color(0xFF4A6B5E),
    tertiary = Color(0xFF7C5A2C),
    error = Color(0xFFB3401F),
)

private val SombrePalette = darkColorScheme(
    primary = Color(0xFF8FD4BC),
    secondary = Color(0xFFB0CCC0),
    tertiary = Color(0xFFF0CDA8),
    error = Color(0xFFFFB4A0),
)

@Composable
fun ThemeAudit(sombre: Boolean = isSystemInDarkTheme(), contenu: @Composable () -> Unit) {
    val contexte = LocalContext.current
    val palette = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (sombre) dynamicDarkColorScheme(contexte) else dynamicLightColorScheme(contexte)
        sombre -> SombrePalette
        else -> ClairPalette
    }
    MaterialTheme(colorScheme = palette, content = contenu)
}
