package tech.stpnk.weather.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
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

/** Превью той же интерактивной карты Яндекс Погоды, что открывается на весь экран. */
@Composable
fun MapPreviewCard(
    cityUrl: String,
    lat: Double,
    lon: Double,
    banner: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val webView = remember(context, cityUrl, lat, lon) {
        createYandexWeatherMap(context, cityUrl, lat, lon)
    }
    DisposableEffect(webView) {
        webView.onResume()
        onDispose { disposeYandexWeatherMap(webView) }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().background(Color(0x1A0A1018)).clickable(onClick = onClick))
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
            ) {
                Text(banner, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Открыть карту",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
