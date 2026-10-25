package tech.stpnk.weather.data.cache

import kotlinx.serialization.json.Json
import tech.stpnk.weather.domain.model.WeatherData
import java.io.File

/** Файловый JSON-кэш снимков погоды. Ключ — URL города. */
class WeatherCache(
    private val dir: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun read(key: String): WeatherData? = runCatching {
        fileFor(key).takeIf { it.exists() }?.readText()?.let { json.decodeFromString(WeatherData.serializer(), it) }
    }.getOrNull()

    fun write(key: String, data: WeatherData) {
        dir.mkdirs()
        fileFor(key).writeText(json.encodeToString(WeatherData.serializer(), data))
    }

    fun ageMs(key: String): Long? =
        fileFor(key).takeIf { it.exists() }?.let { System.currentTimeMillis() - it.lastModified() }

    private fun fileFor(key: String): File =
        File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")
}
