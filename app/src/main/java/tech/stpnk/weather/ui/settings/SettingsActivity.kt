package tech.stpnk.weather.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tech.stpnk.weather.Graph
import tech.stpnk.weather.data.prefs.ThemeMode
import tech.stpnk.weather.ui.theme.WeatherTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Graph.settings
        enableEdgeToEdge()
        setContent {
            var settings by remember { mutableStateOf(store.load()) }
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WeatherTheme(darkTheme = dark) {
                SettingsScreen(
                    settings = settings,
                    onChange = { new ->
                        val themeChanged = new.theme != settings.theme
                        store.save(new)
                        settings = new
                        if (themeChanged) recreate()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}
