package tech.stpnk.weather.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import tech.stpnk.weather.R
import java.io.File

/** Радар RainViewer публикует тайлы только до этого масштаба. */
const val RADAR_MAX_ZOOM = 10.0

/** Общие вещи карт: конфигурация osmdroid, источники тайлов, пин, оверлей радара. */
fun configureOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = "apk-weather/0.4 (https://github.com/sstpnk/apk-weather)"
        // Не cacheDir: система может очистить его между открытием превью и карты.
        osmdroidBasePath = File(context.filesDir, "osmdroid").apply { mkdirs() }
        osmdroidTileCache = File(context.filesDir, "osmdroid/tiles").apply { mkdirs() }
    }
}

/** Растровые тайлы карт Яндекса (публичный core-renderer). */
class YandexTileSource : OnlineTileSourceBase(
    "yandex-map-v2",
    3,
    18,
    256,
    ".png",
    arrayOf("https://core-renderer-tiles.maps.yandex.net/tiles?"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "${baseUrl}l=map&scale=1&lang=ru_RU" +
            "&x=${MapTileIndex.getX(pMapTileIndex)}" +
            "&y=${MapTileIndex.getY(pMapTileIndex)}" +
            "&z=${MapTileIndex.getZoom(pMapTileIndex)}"
}

fun pinDrawable(context: Context): BitmapDrawable {
    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)!!
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return BitmapDrawable(context.resources, bitmap)
}

/** Оверлей радара осадков поверх карты. */
fun radarOverlay(context: Context, frame: RadarFrame): TilesOverlay {
    val provider = MapTileProviderBasic(context, RadarTileSource(frame))
    return TilesOverlay(provider, context).apply {
        loadingBackgroundColor = android.graphics.Color.TRANSPARENT
        loadingLineColor = android.graphics.Color.TRANSPARENT
    }
}

fun MapView.replaceRadarOverlay(overlay: TilesOverlay?) {
    overlays.removeAll { it is TilesOverlay }
    if (overlay != null) overlays.add(overlay)
    invalidate()
}
