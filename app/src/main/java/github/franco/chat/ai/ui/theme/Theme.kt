package github.franco.chat.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Paleta inspirada en Claude: papel calido, texto casi negro y acento arcilla.
private val LightScheme = lightColorScheme(
    primary = Color(0xFFC96442),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5E1D8),
    onPrimaryContainer = Color(0xFF3D1F12),
    secondary = Color(0xFF5E5D59),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF141413),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF141413),
    surfaceVariant = Color(0xFFEFEEE8),
    onSurfaceVariant = Color(0xFF5E5D59),
    outline = Color(0xFFD8D6CF),
    outlineVariant = Color(0xFFEFEEE8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD97757),
    onPrimary = Color(0xFF1F1E1D),
    primaryContainer = Color(0xFF4A2E22),
    onPrimaryContainer = Color(0xFFF5E1D8),
    secondary = Color(0xFFB0AEA5),
    onSecondary = Color(0xFF1F1E1D),
    background = Color(0xFF262624),
    onBackground = Color(0xFFFAF9F5),
    surface = Color(0xFF262624),
    onSurface = Color(0xFFFAF9F5),
    surfaceVariant = Color(0xFF3B3A36),
    onSurfaceVariant = Color(0xFFB0AEA5),
    outline = Color(0xFF4A4944),
    outlineVariant = Color(0xFF3B3A36),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF1F1E1D)
)

// Cuerpo sans y titulos serif: aproximacion a las tipografias de Anthropic (Styrene y Tiempos),
// que no son libres, asi que se usan las familias del sistema.
private val BodyFont = FontFamily.SansSerif
private val TitleFont = FontFamily.Serif

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontFamily = TitleFont, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontFamily = TitleFont, fontSize = 18.sp, lineHeight = 24.sp),
        titleSmall = base.titleSmall.copy(fontFamily = TitleFont, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 18.sp),
        labelMedium = base.labelMedium.copy(fontFamily = BodyFont),
        labelSmall = base.labelSmall.copy(fontFamily = BodyFont)
    )
}

@Composable
fun ChatLobiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
