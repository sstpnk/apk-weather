package tech.stpnk.weather.ui

import tech.stpnk.weather.R

/**
 * Презентационные форматтеры: температуры, направление ветра, тексты условий.
 * Чистый Kotlin — тестируется без Android.
 */

fun formatTemp(value: Int?): String = when {
    value == null -> "—"
    value > 0 -> "+$value°"
    else -> "$value°"
}

private val WIND_DIR_RU = mapOf(
    "NORTH" to "С",
    "NORTHEAST" to "СВ",
    "EAST" to "В",
    "SOUTHEAST" to "ЮВ",
    "SOUTH" to "Ю",
    "SOUTHWEST" to "ЮЗ",
    "WEST" to "З",
    "NORTHWEST" to "СЗ",
)

fun windDirectionRu(code: String?): String? = WIND_DIR_RU[code?.uppercase()]

private val CONDITION_RU = mapOf(
    "CLEAR" to "Ясно",
    "PARTLY_CLOUDY" to "Малооблачно",
    "CLOUDY" to "Облачно с прояснениями",
    "OVERCAST" to "Пасмурно",
    "LIGHT_RAIN" to "Небольшой дождь",
    "RAIN" to "Дождь",
    "HEAVY_RAIN" to "Сильный дождь",
    "SHOWERS" to "Ливень",
    "LIGHT_SNOW" to "Небольшой снег",
    "SNOW" to "Снег",
    "SNOWFALL" to "Снегопад",
    "SLEET" to "Дождь со снегом",
    "THUNDERSTORM" to "Гроза",
    "THUNDERSTORM_WITH_RAIN" to "Дождь с грозой",
    "HAIL" to "Град",
    "FOG" to "Туман",
    "MIST" to "Дымка",
    "DUST" to "Пыль",
    "SMOKE" to "Дым",
)

fun conditionTextRu(code: String?, fallback: String? = null): String? {
    CONDITION_RU[code?.uppercase()]?.let { return it }
    return fallback?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() }
}

/** Векторная иконка погоды под условие: код > русское слово > дефолт. */
fun weatherIconRes(condition: String?, conditionText: String?, daytime: Boolean?): Int {
    val code = condition?.uppercase()
    val text = (conditionText ?: "").lowercase()
    val isDay = daytime != false
    return when {
        code == "CLEAR" -> if (isDay) R.drawable.ic_w_clear_day else R.drawable.ic_w_clear_night
        code == "PARTLY_CLOUDY" -> if (isDay) R.drawable.ic_w_partly_day else R.drawable.ic_w_partly_night
        code == "CLOUDY" -> R.drawable.ic_w_partly_day
        code == "OVERCAST" -> R.drawable.ic_w_overcast
        code?.contains("THUNDER") == true -> R.drawable.ic_w_storm
        code == "SLEET" || code?.contains("SNOW") == true -> R.drawable.ic_w_snow
        code?.contains("RAIN") == true || code == "SHOWERS" -> R.drawable.ic_w_rain
        code in setOf("FOG", "MIST", "SMOKE", "DUST") -> R.drawable.ic_w_fog
        text.contains("гроз") -> R.drawable.ic_w_storm
        text.contains("ливень") || text.contains("дожд") || text.contains("морос") -> R.drawable.ic_w_rain
        text.contains("снег") || text.contains("снеж") || text.contains("град") -> R.drawable.ic_w_snow
        text.contains("туман") || text.contains("дымка") -> R.drawable.ic_w_fog
        text.contains("пасмурно") -> R.drawable.ic_w_overcast
        text.contains("облач") -> if (isDay) R.drawable.ic_w_partly_day else R.drawable.ic_w_partly_night
        text.contains("ясно") -> if (isDay) R.drawable.ic_w_clear_day else R.drawable.ic_w_clear_night
        else -> R.drawable.ic_w_cloudy
    }
}

private val PRECIP_KEYWORDS = listOf("дожд", "ливень", "гроз", "снег", "морос", "град", "осадк")

/** Баннер осадков по ближайшим часам ленты (сводки содержат текст условия). */
fun precipitationOutlook(hourlySummaries: List<String>): String? {
    val near = hourlySummaries.take(3).joinToString(" ").lowercase()
    return if (near.isEmpty()) null
    else if (PRECIP_KEYWORDS.any { near.contains(it) }) "В ближайшие часы возможны осадки"
    else "В ближайшие 2 часа осадков не ожидается"
}
