package tech.stpnk.weather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра-заготовка под референс: сине-ночной фон, тёплый оранжевый акцент.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A65),
    onPrimary = Color(0xFF1B120E),
    background = Color(0xFF0B0F14),
    surface = Color(0xFF141A21),
    onBackground = Color(0xFFE9EEF4),
    onSurface = Color(0xFFE9EEF4)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFE64A19),
    onPrimary = Color.White,
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF14181D),
    onSurface = Color(0xFF14181D)
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
