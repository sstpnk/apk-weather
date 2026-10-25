package tech.stpnk.weather.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import tech.stpnk.weather.R
import java.io.File

/** Карта города: osmdroid + OSM-тайлы, пин города. Погодные слои — после разблокировки API. */
@Composable
fun MapScreen(cityName: String, lat: Double, lon: Double, onBack: () -> Unit) {
    val context = LocalContext.current

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = "tech.stpnk.weather/0.1"
            osmdroidBasePath = File(context.cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles").apply { mkdirs() }
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(10.5)
            controller.setCenter(GeoPoint(lat, lon))
            val marker = Marker(this)
            marker.position = GeoPoint(lat, lon)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = cityName
            marker.icon = pinDrawable(context)
            overlays.add(marker)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xCC0D0F12))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Text(cityName, color = Color.White, fontSize = 17.sp)
        }
    }
}

private fun pinDrawable(context: Context): BitmapDrawable {
    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)!!
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return BitmapDrawable(context.resources, bitmap)
}
