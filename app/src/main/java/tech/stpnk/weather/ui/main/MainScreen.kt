package tech.stpnk.weather.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.stpnk.weather.R
import tech.stpnk.weather.data.prefs.AppSettings
import tech.stpnk.weather.domain.model.CurrentWeather
import tech.stpnk.weather.domain.model.DayForecast
import tech.stpnk.weather.domain.model.HourForecast
import tech.stpnk.weather.ui.conditionTextRu
import tech.stpnk.weather.ui.formatPressure
import tech.stpnk.weather.ui.formatTemp
import tech.stpnk.weather.ui.formatWind
import tech.stpnk.weather.ui.precipitationOutlook
import tech.stpnk.weather.ui.weatherIconRes
import tech.stpnk.weather.ui.windDirectionRu

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    onOpenCities: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            MainUiState.Loading -> LoadingScreen()
            is MainUiState.Error -> ErrorScreen(s.message, onRetry = { viewModel.refresh(force = true) })
            is MainUiState.Content -> ContentScreen(
                state = s,
                settings = settings,
                onRefresh = { viewModel.refresh(force = true) },
                onOpenCities = onOpenCities,
                onOpenSettings = onOpenSettings,
                onOpenMap = onOpenMap,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun ContentScreen(
    state: MainUiState.Content,
    settings: AppSettings,
    onRefresh: () -> Unit,
    onOpenCities: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val data = state.data
    val current = data.current
    val isDay = current?.daytime != false
    val headerBrush = Brush.verticalGradient(
        if (isDay) listOf(Color(0xFF1E7BD6), Color(0xFF1560BF))
        else listOf(Color(0xFF0B1626), Color(0xFF0A101C))
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(headerBrush).statusBarsPadding().padding(bottom = 20.dp)
        ) {
            TopBar(
                city = data.place?.city,
                district = data.place?.district ?: data.place?.province,
                onOpenCities = onOpenCities,
                onOpenSettings = onOpenSettings,
                onRefresh = onRefresh,
            )
            HeroBlock(current = current, isDay = isDay, settings = settings)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                MetricColumn(current, settings)
                HourlyStrip(hourly = data.hourly, isDay = isDay, settings = settings, modifier = Modifier.weight(1f))
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            val outlook = precipitationOutlook(data.hourly.mapNotNull { it.summary })
            if (outlook != null) {
                Spacer(Modifier.height(12.dp))
                NowcastBanner(outlook)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenMap,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Показать на карте")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.section_days),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.days.forEach { day -> DayRow(day, settings) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.attribution) + if (state.stale) " · ${stringResource(R.string.stale_note)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun TopBar(
    city: String?,
    district: String?,
    onOpenCities: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenCities) {
            Icon(Icons.Filled.Menu, contentDescription = "Города", tint = Color.White)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                city ?: "Москва",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!district.isNullOrBlank()) {
                Text(district, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = Color.White)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Color.White)
        }
    }
}

@Composable
private fun HeroBlock(current: CurrentWeather?, isDay: Boolean, settings: AppSettings) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatTemp(current?.temperatureC, settings.tempUnit),
            color = Color.White,
            fontSize = 76.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            painterResource(weatherIconRes(current?.condition, null, isDay)),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = Color.Unspecified,
        )
    }
    Text(
        conditionTextRu(current?.condition) ?: "—",
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        color = Color.White,
        fontSize = 18.sp,
    )
    Text(
        "Ощущается как: ${formatTemp(current?.feelsLikeC, settings.tempUnit)}",
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
}

@Composable
private fun MetricColumn(current: CurrentWeather?, settings: AppSettings) {
    val wind = buildString {
        append(formatWind(current?.windSpeedMs, settings.windUnit))
        windDirectionRu(current?.windDirection)?.let { dir -> if (isNotEmpty()) append(", $dir") }
    }
    Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        MetricRow("Ветер", wind)
        MetricRow("Давление", formatPressure(current?.pressureMmHg, settings.pressureUnit))
        MetricRow("Влажность", current?.humidity?.let { "$it%" } ?: "—")
        current?.waterTempC?.let { MetricRow("Вода", formatTemp(it, settings.tempUnit)) }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.width(66.dp))
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun HourlyStrip(hourly: List<HourForecast>, isDay: Boolean, settings: AppSettings, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(hourly) { hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hour.time, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Icon(
                    painterResource(weatherIconRes(null, hourCondition(hour), isDay)),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatTemp(hour.temperatureC, settings.tempUnit),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Условие из a11y-строки ячейки: «12:00: +9°, ясно, Ощущается как +5°.» → «ясно». */
private fun hourCondition(hour: HourForecast): String? {
    val summary = hour.summary ?: return null
    val m = Regex("""°, (.+?), """).find(summary) ?: return null
    return m.groupValues[1]
}

@Composable
private fun NowcastBanner(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun DayRow(day: DayForecast, settings: AppSettings) {
    val dayPart = day.parts["d"]
    val nightPart = day.parts["n"]
    val (weekday, date) = splitLabel(day.label)
    val accent = MaterialTheme.colorScheme.primary
    val isAccentDay = day.isRed || weekday == "Сегодня" || weekday == "Завтра"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                date?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
                Text(
                    weekday,
                    fontSize = 16.sp,
                    color = if (isAccentDay) accent else MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                painterResource(weatherIconRes(null, dayPart?.conditionText ?: "", true)),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = Color.Unspecified,
            )
            Spacer(Modifier.width(20.dp))
            TempColumn("День", dayPart?.temperatureC, settings)
            Spacer(Modifier.width(14.dp))
            TempColumn("Ночь", nightPart?.temperatureC, settings)
        }
    }
}

@Composable
private fun TempColumn(label: String, value: Int?, settings: AppSettings) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Text(formatTemp(value, settings.tempUnit), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun splitLabel(label: String?): Pair<String, String?> {
    if (label.isNullOrBlank()) return "—" to null
    val idx = label.indexOf(',')
    return if (idx < 0) label.trim() to null
    else label.substring(0, idx).trim() to label.substring(idx + 1).trim()
}
