package tech.stpnk.weather.ui.map

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/** Кадр радара осадков RainViewer (бесплатный публичный API, без ключа). */
@Serializable
data class RadarFrame(val time: Long, val path: String)

@Serializable
private data class RadarWrapper(val radar: RadarBlock? = null)

@Serializable
private data class RadarBlock(val past: List<RadarFrame>? = null)

object RadarFeed {

    private const val URL = "https://api.rainviewer.com/public/weather-maps.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cache: List<RadarFrame> = emptyList()
    @Volatile private var cachedAt: Long = 0

    suspend fun frames(client: OkHttpClient): List<RadarFrame> = withContext(Dispatchers.IO) {
        val fresh = System.currentTimeMillis() - cachedAt < 5 * 60_000L
        if (fresh && cache.isNotEmpty()) return@withContext cache
        runCatching {
            val request = Request.Builder().url(URL).header("User-Agent", "tech.stpnk.weather/0.1").build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = json.decodeFromString(RadarWrapper.serializer(), body)
                val frames = parsed.radar?.past.orEmpty().takeLast(8)
                if (frames.isNotEmpty()) {
                    cache = frames
                    cachedAt = System.currentTimeMillis()
                }
                frames
            }
        }.getOrDefault(emptyList())
    }
}

/** Тайлы конкретного кадра радара: {path}/256/{z}/{x}/{y}/2/1_1.png. */
class RadarTileSource(frame: RadarFrame) : OnlineTileSourceBase(
    "rainviewer",
    0,
    10,
    256,
    ".png",
    arrayOf("https://tilecache.rainviewer.com${frame.path}/256/"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/2/1_1.png"
}
