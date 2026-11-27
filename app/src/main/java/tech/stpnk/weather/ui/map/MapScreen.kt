package tech.stpnk.weather.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import tech.stpnk.weather.Graph
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Интерактивная карта на тайлах Яндекса + анимированный радар осадков. */
@Composable
fun MapScreen(cityName: String, lat: Double, lon: Double, onBack: () -> Unit) {
    val context = LocalContext.current
    val frames by produceState(initialValue = emptyList<RadarFrame>()) {
        value = RadarFeed.frames(Graph.http)
    }
    var frameLabel by remember { mutableStateOf("") }

    val mapView = remember(context, cityName, lat, lon) {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(YandexTileSource())
            setMultiTouchControls(true)
            setMinZoomLevel(3.0)
            setMaxZoomLevel(RADAR_MAX_ZOOM)
            controller.setZoom(9.0)
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
        if (frames.isEmpty()) return@LaunchedEffect
        var index = 0
        while (true) {
            val frame = frames[index % frames.size]
            mapView.replaceRadarOverlay(radarOverlay(context, frame))
            frameLabel = Instant.ofEpochSecond(frame.time)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru")))
            index++
            delay(800)
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
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
            Text(cityName, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(
                if (frameLabel.isEmpty()) "радар…" else "радар $frameLabel",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}
