package dev.sift.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A deliberately neutral, low-chroma theme.
 *
 * This app's entire job is judging colour. A tinted or high-chroma surface
 * behind a photograph shifts how its colour reads — simultaneous contrast is not
 * a subtle effect at the magnitudes §6.7 works in, and a warm UI would make
 * every graded skin tone look cooler than it is. Near-neutral greys keep the
 * frame the only coloured thing on screen.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C6D2),
    onPrimary = Color(0xFF1A1C1E),
    background = Color(0xFF121314),
    onBackground = Color(0xFFE3E3E4),
    surface = Color(0xFF1A1B1C),
    onSurface = Color(0xFFE3E3E4),
    surfaceVariant = Color(0xFF2A2B2D),
    onSurfaceVariant = Color(0xFFBFC1C3),
    error = Color(0xFFE59A94),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F4A54),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE6E7E9),
    onSurfaceVariant = Color(0xFF44474A),
    error = Color(0xFFA33A33),
)

@Composable
fun SiftTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
