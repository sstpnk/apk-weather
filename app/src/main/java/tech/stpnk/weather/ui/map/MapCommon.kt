package tech.stpnk.weather.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import tech.stpnk.weather.R
import java.io.File

/** Общие вещи карт: конфигурация osmdroid, пин, оверлей радара. */
fun configureOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = "apk-weather/0.1 (https://github.com/sstpnk/apk-weather)"
        osmdroidBasePath = File(context.filesDir, "osmdroid").apply { mkdirs() }
        osmdroidTileCache = File(context.filesDir, "osmdroid/tiles").apply { mkdirs() }
    }
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
fun radarOverlay(context: Context, frame: RadarFrame, zoomMax: Int = 10): TilesOverlay {
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
