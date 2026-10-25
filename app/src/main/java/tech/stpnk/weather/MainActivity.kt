package tech.stpnk.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import tech.stpnk.weather.ui.main.MainScreen
import tech.stpnk.weather.ui.main.MainViewModel
import tech.stpnk.weather.ui.theme.WeatherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherTheme {
                MainScreen(viewModel)
            }
        }
    }
}
