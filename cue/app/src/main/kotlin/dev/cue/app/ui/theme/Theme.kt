package dev.cue.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Deliberately quiet.
 *
 * The screen's whole job is to show four short pieces of text and let you copy
 * one. Anything decorative competes with the only content that matters, and the
 * app is opened mid-conversation with an actual person waiting.
 */
private val Ink = Color(0xFF12100E)
private val Paper = Color(0xFFF7F4EF)
private val Amber = Color(0xFFC1743A)
private val Sage = Color(0xFF5B6B58)

private val dark = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Sage,
    background = Ink,
    onBackground = Paper,
    surface = Color(0xFF1B1815),
    onSurface = Paper,
    surfaceVariant = Color(0xFF262220),
    onSurfaceVariant = Color(0xFFC9C2B8),
    error = Color(0xFFCF6679),
)

private val light = lightColorScheme(
    primary = Color(0xFF8F4E1E),
    onPrimary = Paper,
    secondary = Sage,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFDF9),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE4DA),
    onSurfaceVariant = Color(0xFF4A443E),
)

/**
 * Drafts are rendered in the same register they will be sent in: no title case,
 * no letter-spacing, nothing that would make a lowercase message look styled.
 */
private val typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    ),
)

@Composable
fun CueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) dark else light,
        typography = typography,
        content = content,
    )
}
