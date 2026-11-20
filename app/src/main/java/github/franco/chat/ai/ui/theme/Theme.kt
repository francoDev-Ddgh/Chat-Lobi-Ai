package github.franco.chat.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Ash = Color(0xFFF2F2F2)
private val Graphite = Color(0xFF1C1C1C)
private val Smoke = Color(0xFF2A2A2A)
private val Mid = Color(0xFF6B6B6B)
private val Line = Color(0xFFD6D6D6)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Ash,
    onPrimaryContainer = Ink,
    secondary = Mid,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Ash,
    onSurfaceVariant = Graphite,
    outline = Line,
    outlineVariant = Ash,
    error = Color(0xFFB3261E),
    onError = Paper
)

private val DarkScheme = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Smoke,
    onPrimaryContainer = Paper,
    secondary = Line,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Graphite,
    onSurfaceVariant = Paper,
    outline = Smoke,
    outlineVariant = Graphite,
    error = Color(0xFFFFB4AB),
    onError = Ink
)

@Composable
fun ChatLobiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
