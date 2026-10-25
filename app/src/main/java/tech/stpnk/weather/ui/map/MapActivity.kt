package tech.stpnk.weather.ui.map

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import tech.stpnk.weather.Graph
import tech.stpnk.weather.ui.theme.WeatherTheme

class MapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val city = Graph.cities.selected()
        enableEdgeToEdge()
        setContent {
            WeatherTheme {
                MapScreen(
                    cityName = city.name,
                    lat = city.lat ?: 55.7522,
                    lon = city.lon ?: 37.6156,
                    onBack = { finish() },
                )
            }
        }
    }
}
