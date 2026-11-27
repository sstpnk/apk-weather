package tech.stpnk.weather.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.stpnk.weather.Graph
import tech.stpnk.weather.R
import tech.stpnk.weather.data.prefs.AppSettings
import tech.stpnk.weather.data.prefs.WeatherProvider
import tech.stpnk.weather.domain.model.CurrentWeather
import tech.stpnk.weather.domain.model.DayForecast
import tech.stpnk.weather.domain.model.HourForecast
import tech.stpnk.weather.domain.model.WeatherData
import tech.stpnk.weather.domain.model.WarningPlaque
import tech.stpnk.weather.ui.conditionTextRu
import tech.stpnk.weather.ui.formatPressure
import tech.stpnk.weather.ui.formatTemp
import tech.stpnk.weather.ui.formatWind
import tech.stpnk.weather.ui.map.MapPreviewCard
import tech.stpnk.weather.ui.precipitationOutlook
import tech.stpnk.weather.ui.todayDayNight
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val city = Graph.cities.selected()
    val isDay = current?.daytime != false
    val headerBrush = Brush.verticalGradient(
        if (isDay) listOf(Color(0xFF1E7BD6), Color(0xFF1560BF))
        else listOf(Color(0xFF0B1626), Color(0xFF0A101C))
    )
    // Карта и предупреждения доступны только у Яндекс Погоды.
    val isYandex = settings.weatherProvider == WeatherProvider.YANDEX

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().background(headerBrush).statusBarsPadding().padding(bottom = 16.dp)
            ) {
                TopBar(
                    city = data.place?.city ?: city.name,
                    district = data.place?.district ?: data.place?.province,
                    onOpenCities = onOpenCities,
                    onOpenSettings = onOpenSettings,
                    onRefresh = onRefresh,
                )
                // Слева — температура и состояние, справа — метрики; высоты совпадают
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Температура и иконка одной высоты
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatTemp(current?.temperatureC, settings.tempUnit),
                                color = Color.White,
                                fontSize = 64.sp,
                                lineHeight = 64.sp,
                                fontWeight = FontWeight.Light,
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                painterResource(weatherIconRes(current?.condition, null, isDay)),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Unspecified,
                            )
                        }
                        Text(
                            conditionTextRu(current?.condition) ?: "—",
                            color = Color.White,
                            fontSize = 17.sp,
                        )
                        Text(
                            "Ощущается как: ${formatTemp(current?.feelsLikeC, settings.tempUnit)}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                        )
                    }
                    MetricColumn(
                        current = current,
                        data = data,
                        settings = settings,
                        modifier = Modifier.width(168.dp).fillMaxHeight(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                HourlyStrip(
                    hourly = data.hourly,
                    days = data.days,
                    isDay = isDay,
                    settings = settings,
                )
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                if (isYandex) {
                    Spacer(Modifier.height(12.dp))
                    MapPreviewCard(
                        cityName = data.place?.city ?: city.name,
                        lat = city.lat ?: 55.7522,
                        lon = city.lon ?: 37.6156,
                        banner = precipitationOutlook(data.hourly.mapNotNull { it.summary }),
                        onClick = onOpenMap,
                    )

                    if (data.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cardWidth = maxWidth
                            Row(
                                Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                data.warnings.forEach { warning ->
                                    WarningCard(warning, Modifier.width(cardWidth).fillMaxHeight())
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.section_days),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val todayRange = todayDayNight(data)
                    data.days.forEachIndexed { index, day ->
                        val fallbackDay = if (index == 0) todayRange?.first else null
                        val fallbackNight = if (index == 0) todayRange?.second else null
                        DayRow(index, day, settings, fallbackDay, fallbackNight)
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Время последнего обновления — по центру, мелким шрифтом
                val updated = java.time.Instant.ofEpochMilli(data.fetchedAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                Text(
                    text = if (state.stale) "данные из кеша от $updated" else "обновлено $updated",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
            }
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
private fun MetricColumn(current: CurrentWeather?, data: WeatherData, settings: AppSettings, modifier: Modifier = Modifier) {
    val wind = buildString {
        append(formatWind(current?.windSpeedMs, settings.windUnit))
        windDirectionRu(current?.windDirection)?.let { dir -> if (isNotEmpty()) append(", $dir") }
    }
    val magnetic = buildString {
        append("МП ")
        val currentKp = tech.stpnk.weather.data.magnetic.MagneticFeed.format(data.magneticCurrent)
        val maxKp = tech.stpnk.weather.data.magnetic.MagneticFeed.format(data.magneticMaxToday)
        if (currentKp != null) {
            append(currentKp)
            if (maxKp != null) append(" / $maxKp")
        } else {
            val fallback = data.days.firstOrNull()?.kpText?.replaceFirstChar { it.uppercase() }
            append(fallback ?: "—")
        }
    }
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        MetricIconRow(R.drawable.ic_metric_wind, wind)
        MetricIconRow(R.drawable.ic_metric_pressure, formatPressure(current?.pressureMmHg, settings.pressureUnit))
        // влажность и вода в одной строке (показываем только если есть данные)
        val hasHumidity = current?.humidity != null
        val hasWater = current?.waterTempC != null
        if (hasHumidity || hasWater) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasHumidity) MetricIconRow(R.drawable.ic_metric_humidity, "${current!!.humidity}%")
                if (hasWater) MetricIconRow(R.drawable.ic_metric_water, formatTemp(current!!.waterTempC, settings.tempUnit))
            }
        }
        // УФ и магнитное поле в одной строке (тоже только при наличии данных)
        val hasUv = current?.uvIndex != null
        if (hasUv || magnetic != "МП —") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasUv) MetricIconRow(R.drawable.ic_metric_uv, "УФ ${current!!.uvIndex}", compact = true)
                MetricIconRow(R.drawable.ic_metric_kp, magnetic, compact = true)
            }
        }
    }
}

@Composable
private fun MetricIconRow(iconRes: Int, value: String, compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(if (compact) 15.dp else 17.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            color = Color.White,
            fontSize = if (compact) 13.sp else 14.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun HourlyStrip(
    hourly: List<HourForecast>,
    days: List<DayForecast>,
    isDay: Boolean,
    settings: AppSettings,
) {
    val cells = remember(hourly, days) { buildHourCells(hourly, days) }
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(cells) { cell ->
            when (cell) {
                is HourCell.Sun -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // время сверху, иконка ниже, подпись ещё ниже — как у соседних ячеек
                    Text(cell.time, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        painterResource(if (cell.rise) R.drawable.ic_sunrise else R.drawable.ic_sunset),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(if (cell.rise) "восход" else "закат", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
                is HourCell.Hour -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cell.data.time, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        painterResource(weatherIconRes(null, hourCondition(cell.data), isDay)),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = Color.Unspecified,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatTemp(cell.data.temperatureC, settings.tempUnit),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Ячейки ленты: часы + маркеры восхода/заката по таймлайну (для сегодня и завтра). */
private sealed interface HourCell {
    data class Hour(val data: HourForecast) : HourCell
    data class Sun(val time: String, val rise: Boolean) : HourCell
}

private fun buildHourCells(hourly: List<HourForecast>, days: List<DayForecast>): List<HourCell> {
    if (hourly.isEmpty()) return emptyList()
    val out = mutableListOf<HourCell>()
    var dayIndex = 0
    var prevHour: Int? = null
    var segmentFirst: String? = null
    val riseDone = mutableSetOf<Int>()
    val setDone = mutableSetOf<Int>()
    for (hour in hourly) {
        val hh = hour.time.substringBefore(':').toIntOrNull() ?: continue
        if (prevHour != null && hh < prevHour) {
            dayIndex++
            segmentFirst = null
        }
        if (segmentFirst == null) segmentFirst = hour.time
        prevHour = hh

        val day = days.getOrNull(dayIndex)
        day?.sunrise?.let { sr ->
            if (dayIndex !in riseDone && hour.time >= sr && sr > segmentFirst) {
                out += HourCell.Sun(sr, rise = true)
                riseDone.add(dayIndex)
            }
        }
        day?.sunset?.let { ss ->
            if (dayIndex !in setDone && hour.time >= ss && ss > segmentFirst) {
                out += HourCell.Sun(ss, rise = false)
                setDone.add(dayIndex)
            }
        }
        out += HourCell.Hour(hour)
    }
    return out
}

/** Условие из a11y-строки ячейки: «12:00: +9°, ясно, Ощущается как +5°.» → «ясно». */
private fun hourCondition(hour: HourForecast): String? {
    val summary = hour.summary ?: return null
    val m = Regex("""°, (.+?), """).find(summary) ?: return null
    return m.groupValues[1]
}

@Composable
private fun WarningCard(warning: WarningPlaque, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF2F6FD0)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(warningIcon(warning.icon)),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${warning.title}: ${warning.text}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun warningIcon(code: String?): Int {
    val c = code ?: ""
    return when {
        c.contains("th") -> R.drawable.ic_w_storm
        c.contains("ra") && c.contains("sn") -> R.drawable.ic_w_snow
        c.contains("ra") -> R.drawable.ic_w_rain
        c.contains("sn") -> R.drawable.ic_w_snow
        c.contains("ovc") -> R.drawable.ic_w_overcast
        else -> R.drawable.ic_w_cloudy
    }
}

@Composable
private fun DayRow(
    dayIndex: Int,
    day: DayForecast,
    settings: AppSettings,
    fallbackDayTemp: Int?,
    fallbackNightTemp: Int?,
) {
    val dayPart = day.parts["d"]
    val nightPart = day.parts["n"]
    val accent = MaterialTheme.colorScheme.primary
    val isAccentDay = day.isRed

    // Даты и дни недели считаем сами — подпись со страницы может пропадать
    val date = java.time.LocalDate.ofEpochDay(day.epochDay)
    val weekday = when (dayIndex) {
        0 -> "Сегодня"
        1 -> "Завтра"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.forLanguageTag("ru")))
            .replaceFirstChar { it.uppercase() }
    }
    val dateText = date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.forLanguageTag("ru")))

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
                Text(dateText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
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
            TempColumn("День", dayPart?.temperatureC ?: fallbackDayTemp, settings)
            Spacer(Modifier.width(14.dp))
            TempColumn("Ночь", nightPart?.temperatureC ?: fallbackNightTemp, settings)
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
