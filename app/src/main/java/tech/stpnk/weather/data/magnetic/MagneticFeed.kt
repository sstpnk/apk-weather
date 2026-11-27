package tech.stpnk.weather.data.magnetic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Магнитное поле: NOAA SWPC, планетарный Kp-индекс (публичный фид без ключа).
 * Kp одинаков для планеты, поэтому к городу не привязан. Кэш 30 минут.
 */
object MagneticFeed {

    @Serializable
    private data class KpRow(
        @SerialName("time_tag") val timeTag: String,
        @SerialName("Kp") val kp: Double? = null,
    )

    data class Today(val current: Double?, val maxToday: Double?)

    private const val URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cache: Today? = null
    @Volatile private var cachedAt: Long = 0

    suspend fun today(client: OkHttpClient): Today? = withContext(Dispatchers.IO) {
        val fresh = System.currentTimeMillis() - cachedAt < 30 * 60_000L
        if (fresh && cache != null) return@withContext cache
        runCatching {
            val request = Request.Builder().url(URL).header("User-Agent", "tech.stpnk.weather/0.1").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext cache
                val rows = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(KpRow.serializer()), response.body?.string() ?: "")
                    .filter { it.kp != null }
                if (rows.isEmpty()) return@withContext cache
                val today = LocalDate.now().toString()
                val todays = rows.filter { it.timeTag.startsWith(today) }.mapNotNull { it.kp }
                val current = rows.lastOrNull()?.kp
                val maxToday = todays.maxOrNull() ?: rows.takeLast(8).mapNotNull { it.kp }.maxOrNull()
                Today(current = current, maxToday = maxToday).also {
                    cache = it
                    cachedAt = System.currentTimeMillis()
                }
            }
        }.getOrNull()
    }

    fun format(value: Double?): String? = value?.let {
        val rounded = (it * 10).roundToInt() / 10.0
        if (rounded % 1.0 == 0.0) rounded.toInt().toString()
        else String.format(Locale.US, "%.1f", rounded)
    }
}
