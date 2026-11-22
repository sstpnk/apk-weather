package tech.stpnk.weather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A65),
    onPrimary = Color(0xFF2A1204),
    primaryContainer = Color(0xFF6B2E14),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFF8FB4D9),
    onSecondary = Color(0xFF0E1B29),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFE9EEF4),
    surface = Color(0xFF1C1F24),
    onSurface = Color(0xFFE9EEF4),
    surfaceVariant = Color(0xFF262B32),
    onSurfaceVariant = Color(0xFFB6C0CC),
    outline = Color(0xFF5A6470),
    error = Color(0xFFFFB4AB),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFE64A19),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF3E6D9C),
    onSecondary = Color.White,
    background = Color(0xFFF2F5F9),
    onBackground = Color(0xFF14181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14181D),
    surfaceVariant = Color(0xFFE6EBF2),
    onSurfaceVariant = Color(0xFF3C4652),
    outline = Color(0xFF8A94A2),
    error = Color(0xFFB3261E),
)

@Composable
fun WeatherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}
