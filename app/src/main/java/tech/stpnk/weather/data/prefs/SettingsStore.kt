package tech.stpnk.weather.data.prefs

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class TempUnit { CELSIUS, FAHRENHEIT }

@Serializable
enum class WindUnit { METERS_PER_SECOND, KM_PER_HOUR }

@Serializable
enum class PressureUnit { MMHG, HPA }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.METERS_PER_SECOND,
    val pressureUnit: PressureUnit = PressureUnit.MMHG,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

class SettingsStore(private val kv: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AppSettings = runCatching {
        kv.getString(KEY)?.let { json.decodeFromString(AppSettings.serializer(), it) }
    }.getOrNull() ?: AppSettings()

    fun save(settings: AppSettings) {
        kv.putString(KEY, json.encodeToString(AppSettings.serializer(), settings))
    }

    private companion object {
        const val KEY = "app_settings_v1"
    }
}
