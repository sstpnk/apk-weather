package tech.stpnk.weather.ui.cities

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import tech.stpnk.weather.ui.theme.WeatherTheme

class CitiesActivity : ComponentActivity() {

    private val viewModel: CitiesViewModel by lazy { CitiesViewModel() }

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.useMyLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherTheme {
                CitiesScreen(
                    viewModel = viewModel,
                    onRequestLocation = {
                        locationPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}
