package tech.stpnk.weather

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tech.stpnk.weather.data.prefs.AppSettings
import tech.stpnk.weather.data.prefs.ThemeMode
import tech.stpnk.weather.ui.cities.CitiesActivity
import tech.stpnk.weather.ui.main.MainScreen
import tech.stpnk.weather.ui.main.MainViewModel
import tech.stpnk.weather.ui.map.MapActivity
import tech.stpnk.weather.ui.settings.SettingsActivity
import tech.stpnk.weather.ui.theme.WeatherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var settings by mutableStateOf(AppSettings())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Graph.settings.load()
        enableEdgeToEdge()
        setContent {
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WeatherTheme(darkTheme = dark) {
                MainScreen(
                    viewModel = viewModel,
                    settings = settings,
                    onOpenCities = { startActivity(Intent(this, CitiesActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenMap = { startActivity(Intent(this, MapActivity::class.java)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val latest = Graph.settings.load()
        val themeChanged = latest.theme != settings.theme
        settings = latest
        if (themeChanged) recreate()
        viewModel.refreshIfCityChanged()
    }
}
