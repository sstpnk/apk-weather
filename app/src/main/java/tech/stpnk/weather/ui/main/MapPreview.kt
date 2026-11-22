package tech.stpnk.weather.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import tech.stpnk.weather.Graph

/**
 * Карточка «Погода на карте»: мини-карта с радаром осадков и плашкой-баннером поверх,
 * целиком кликабельна (как в референсе). Карта внутри не интерактивна — тап открывает экран.
 */
@Composable
fun MapPreviewCard(
    cityName: String,
    lat: Double,
    lon: Double,
    banner: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val frames by produceState(initialValue = emptyList<RadarFrame>()) {
        value = RadarFeed.frames(Graph.http)
    }

    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            setBuiltInZoomControls(false)
            controller.setZoom(8.8)
            controller.setCenter(GeoPoint(lat, lon))
            val marker = Marker(this).apply {
                position = GeoPoint(lat, lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = pinDrawable(context)
                title = cityName
            }
            overlays.add(marker)
        }
    }

    androidx.compose.runtime.LaunchedEffect(frames) {
        val last = frames.lastOrNull() ?: return@LaunchedEffect
        mapView.replaceRadarOverlay(radarOverlay(context, last))
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141A21))
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        // прозрачный слой: перехватывает тапы, карта под ним не скроллится
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
        if (banner != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xD9111317))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(banner, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
