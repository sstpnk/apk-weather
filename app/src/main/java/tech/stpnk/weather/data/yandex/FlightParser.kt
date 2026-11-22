package tech.stpnk.weather.data.yandex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import tech.stpnk.weather.domain.model.CurrentWeather
import tech.stpnk.weather.domain.model.DayForecast
import tech.stpnk.weather.domain.model.DayPartForecast
import tech.stpnk.weather.domain.model.GeoPlace
import tech.stpnk.weather.domain.model.HourForecast
import tech.stpnk.weather.domain.model.WarningPlaque
import tech.stpnk.weather.domain.model.WeatherData
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Разбор flight-потока pogoda.yandex.ru (см. docs/05-source-yandex-web.md).
 * Работает без сети: на вход — склеенный payload из [FlightExtractor].
 */
class FlightParser(private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) }) {

    private val rowRegex = Regex("""^([0-9a-fA-F]*):(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private val hourTimeRegex = Regex("""^\d{1,2}:00$""")
    private val tempRegex = Regex("""^[+\-\u2212]?\d+°$""")
    private val embeddedTempRegex = Regex("""(?:^|[\s,:])([+\-\u2212]?\d{1,2})°""")

    fun parse(payload: String, sourceUrl: String = ""): WeatherData {
        val rowMap = LinkedHashMap<String, JsonElement>()
        for (line in payload.split('\n')) {
            if (line.isEmpty()) continue
            val m = rowRegex.matchEntire(line) ?: continue
            val id = m.groupValues[1]
            if (id.isEmpty()) continue
            val value = runCatching { Json.parseToJsonElement(m.groupValues[2]) }.getOrNull() ?: continue
            rowMap[id] = value
        }
        val rows = rowMap.values.map { resolve(it, rowMap, 0) }

        val fact = findFirstObject(rows) { obj ->
            listOf("temperature", "feelsLike", "pressure", "humidity", "waterTemperature").all(obj::containsKey)
        }
        val geo = findFirstObject(rows) { obj ->
            obj.containsKey("geoHierarchy") || (obj.containsKey("geoId") && obj.containsKey("lat") && obj.containsKey("lon"))
        }

        val extras = parseDayExtras(allStrings(rows))
        val days = parseDays(rows).mapIndexed { index, day ->
            val extra = extras.getOrNull(index) ?: return@mapIndexed day
            day.copy(sunrise = extra.sunrise, sunset = extra.sunset, uvText = extra.uv, kpText = extra.kp)
        }

        return WeatherData(
            place = geo?.let(::toPlace),
            current = fact?.let(::toCurrent),
            hourly = parseHourly(rows),
            days = days,
            warnings = parseWarnings(rows),
            fetchedAt = System.currentTimeMillis(),
            sourceUrl = sourceUrl,
        )
    }

    private data class DayExtra(val sunrise: String?, val sunset: String?, val uv: String?, val kp: String?)

    private val sunRegex = Regex("восход\\s+(\\d{2}:\\d{2})\\s*<br\\s*/?>\\s*закат\\s+(\\d{2}:\\d{2})")
    private val uvRegex = Regex("уф-индекс:\\s*(\\d+),\\s*([^<]+?)(?:<br|$)", RegexOption.IGNORE_CASE)
    private val kpRegex = Regex("магнитное поле:\\s*([^<]+?)(?:<br|$)", RegexOption.IGNORE_CASE)

    /** Восход/закат, УФ и магнитное поле живут в a11y-строках дней (по одной на день, по порядку). */
    private fun parseDayExtras(strings: List<String>): List<DayExtra> {
        val result = mutableListOf<DayExtra>()
        for (text in strings) {
            if (!text.contains("восход")) continue
            val sun = sunRegex.find(text) ?: continue
            val uv = uvRegex.find(text)?.groupValues?.let { "${it[1]}, ${it[2].trim()}" }
            val kp = kpRegex.find(text)?.groupValues?.get(1)
                ?.replace("cлабая", "слабая")
                ?.replace("Cлабая", "Слабая")
                ?.trim()
            result += DayExtra(sun.groupValues[1], sun.groupValues[2], uv, kp)
        }
        return result
    }

    /** Плашки предупреждений: объект с ключом warningsPersonal → массив {title, text, iconName}. */
    private fun parseWarnings(rows: List<JsonElement>): List<WarningPlaque> {
        val holder = findFirstObject(rows) { it.containsKey("warningsPersonal") } ?: return emptyList()
        val array = holder["warningsPersonal"] as? JsonArray ?: return emptyList()
        val order = mapOf("Сегодня" to 0, "Завтра" to 1, "На следующей неделе" to 2)
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val title = obj.stringOf("title") ?: return@mapNotNull null
            val text = obj.stringOf("text") ?: return@mapNotNull null
            WarningPlaque(title = title, text = text, icon = obj.stringOf("iconName"))
        }.sortedBy { order[it.title] ?: 3 }
    }

    private fun allStrings(rows: List<JsonElement>): List<String> {
        val out = ArrayList<String>(256)
        fun collect(node: JsonElement) {
            when (node) {
                is JsonPrimitive -> if (node.isString && !node.content.startsWith("$")) out += node.content
                is JsonArray -> node.forEach { collect(it) }
                is JsonObject -> node.values.forEach { collect(it) }
            }
        }
        rows.forEach { collect(it) }
        return out
    }

    // ---------- ссылки ----------

    /** «$hex» или «$Lhex» — ссылка на строку потока; возвращает hex-идентификатор. */
    private fun refId(content: String): String? {
        if (!content.startsWith("$")) return null
        val body = content.removePrefix("$").removePrefix("L")
        return body.takeIf { it.isNotEmpty() && it.all(::isHexDigit) }
    }

    private fun isHexDigit(ch: Char): Boolean = ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'

    private fun resolve(node: JsonElement, rowMap: Map<String, JsonElement>, depth: Int): JsonElement {
        if (depth > 12) return node
        return when (node) {
            is JsonPrimitive -> {
                if (!node.isString) return node
                val id = refId(node.content) ?: return node
                rowMap[id]?.let { resolve(it, rowMap, depth + 1) } ?: node
            }
            is JsonArray -> JsonArray(node.map { resolve(it, rowMap, depth + 1) })
            is JsonObject -> JsonObject(node.mapValues { resolve(it.value, rowMap, depth + 1) })
        }
    }

    // ---------- обход ----------

    private fun forEachObject(node: JsonElement, action: (JsonObject) -> Unit) {
        when (node) {
            is JsonObject -> {
                action(node)
                node.values.forEach { forEachObject(it, action) }
            }
            is JsonArray -> node.forEach { forEachObject(it, action) }
            else -> Unit
        }
    }

    private fun findFirstObject(rows: List<JsonElement>, predicate: (JsonObject) -> Boolean): JsonObject? {
        for (row in rows) {
            var found: JsonObject? = null
            forEachObject(row) { obj ->
                if (found == null && predicate(obj)) found = obj
            }
            if (found != null) return found
        }
        return null
    }

    /** Текстовое содержимое элемента: только строки-дети; ["$", type, key, props] рекурсируем в props. */
    private fun textOf(obj: JsonObject): String {
        val sb = StringBuilder()
        fun go(el: JsonElement?) {
            when (el) {
                null -> Unit
                is JsonPrimitive -> if (el.isString && !el.content.startsWith("$")) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(el.content)
                }
                is JsonArray -> {
                    val props = el.getOrNull(3) as? JsonObject
                    val isElement = el.size >= 4 &&
                        (el[0] as? JsonPrimitive)?.content == "$" &&
                        props != null
                    if (isElement) go(props["children"]) else el.forEach { go(it) }
                }
                is JsonObject -> el["children"]?.let { go(it) }
            }
        }
        go(obj["children"])
        return sb.toString().trim()
    }

    private fun collectStrings(obj: JsonObject, out: MutableList<String>) {
        fun go(el: JsonElement?) {
            when (el) {
                null -> Unit
                is JsonPrimitive -> if (el.isString && !el.content.startsWith("$")) out += el.content
                is JsonArray -> {
                    val props = el.getOrNull(3) as? JsonObject
                    val isElement = el.size >= 4 &&
                        (el[0] as? JsonPrimitive)?.content == "$" &&
                        props != null
                    if (isElement) go(props["children"]) else el.forEach { go(it) }
                }
                is JsonObject -> el["children"]?.let { go(it) }
            }
        }
        go(obj["children"])
    }

    // ---------- маппинг ----------

    private fun toCurrent(fact: JsonObject): CurrentWeather {
        val temp = fact.intOf("temperatureInCelsius", "temperature")
        return CurrentWeather(
            temperatureC = temp ?: 0,
            feelsLikeC = fact.intOf("feelsLikeInCelsius", "feelsLike"),
            waterTempC = fact.intOf("waterTemperature"),
            windSpeedMs = fact.doubleOf("windSpeedInMetersPerSecond", "windSpeed"),
            windGustMs = fact.doubleOf("windGust"),
            windDirection = fact.stringOf("windDirection"),
            pressureMmHg = fact.intOf("pressure"),
            humidity = fact.intOf("humidity"),
            condition = fact.stringOf("condition"),
            daytime = when (fact.stringOf("daytime")) {
                "DAY" -> true
                "NIGHT" -> false
                else -> null
            },
            uvIndex = fact.intOf("uvIndex"),
            visibilityM = fact.intOf("visibility"),
        )
    }

    private fun toPlace(geo: JsonObject): GeoPlace {
        val hierarchy = geo["geoHierarchy"] as? JsonObject
        fun name(key: String): String? = (hierarchy?.get(key) as? JsonObject)?.stringOf("name")
        return GeoPlace(
            geoId = geo.intOf("geoId"),
            city = name("locality"),
            district = name("district"),
            province = name("province"),
            country = name("country"),
            lat = geo.doubleOf("lat"),
            lon = geo.doubleOf("lon"),
        )
    }

    private fun parseHourly(rows: List<JsonElement>): List<HourForecast> {
        val out = LinkedHashMap<String, HourForecast>()
        for (row in rows) forEachObject(row) { obj ->
            val cn = obj.stringOf("className") ?: return@forEachObject
            if (!cn.contains("AppHourlyItem_container__")) return@forEachObject
            val strings = ArrayList<String>(8)
            collectStrings(obj, strings)
            // Внутри карточки часа могут быть восход/закат вида «16:12».
            // Прогноз Яндекса дискретен по часам, поэтому берём только HH:00.
            val time = strings.firstOrNull { hourTimeRegex.matches(it) } ?: return@forEachObject
            // В отдельных часах Яндекс кладёт температуру только в a11y-строку,
            // а не отдельным текстовым узлом (так выпадал, например, 05:00).
            val temp = strings.firstNotNullOfOrNull(::hourlyTemperature)
            val icon = obj.iconIndex()
            val a11y = strings.lastOrNull { it.length > 12 && it.contains(':') }
            // Flight содержит промежуточные оболочки с одним временем. Это не
            // прогнозная ячейка: настоящая приходит ниже с температурой/иконкой.
            if (temp == null && icon == null && a11y == null) return@forEachObject
            val key = "$time|$temp|$icon"
            out.putIfAbsent(key, HourForecast(time = time, temperatureC = temp, icon = icon, summary = a11y))
        }
        return out.values.take(48)
    }

    private fun parseDays(rows: List<JsonElement>): List<DayForecast> {
        class Acc {
            var label: String? = null
            var isRed: Boolean = false
            val parts = LinkedHashMap<String, MutableMap<String, JsonElement>>()
        }

        val acc = LinkedHashMap<String, Acc>()
        for (row in rows) forEachObject(row) { obj ->
            val cn = obj.stringOf("className") ?: return@forEachObject
            if (!cn.contains("AppForecastDay_container__") || !obj.containsKey("data-day")) return@forEachObject
            val key = obj.stringOf("data-day") ?: return@forEachObject
            val entry = acc.getOrPut(key) { Acc() }
            labelOf(obj)?.let { lab ->
                entry.label = entry.label ?: lab.first
                entry.isRed = entry.isRed || lab.second
            }
            collectParts(obj, entry.parts)
        }

        val base = today().toEpochDay()
        return acc.entries
            .sortedBy { it.key.substringBefore('_').toIntOrNull() ?: Int.MAX_VALUE }
            .take(10)
            .mapNotNull { (dataDay, entry) ->
                val ordinal = dataDay.substringBefore('_').toIntOrNull() ?: return@mapNotNull null
                DayForecast(
                    epochDay = base + (ordinal - 1),
                    label = entry.label?.replace("&nbsp;", " "),
                    isRed = entry.isRed,
                    parts = entry.parts.mapValues { (_, fields) ->
                        DayPartForecast(
                            temperatureC = (fields["temp"] as? JsonPrimitive)?.content?.let(::parseTemp),
                            feelsLikeC = (fields["feels"] as? JsonPrimitive)?.content?.let(::parseTemp),
                            icon = (fields["icon"] as? JsonPrimitive)?.content?.toIntOrNull(),
                            conditionText = fields["text"]?.stringContent(),
                            windText = fields["wind"]?.stringContent(),
                            windDirection = fields["dir"]?.stringContent(),
                        )
                    },
                )
            }
    }

    private fun collectParts(day: JsonObject, parts: MutableMap<String, MutableMap<String, JsonElement>>) {
        forEachObject(day) { obj ->
            val style = obj["style"] as? JsonObject ?: return@forEachObject
            val ga = (style["gridArea"] as? JsonPrimitive)?.content ?: return@forEachObject
            if (ga.endsWith("-title") || ga.length < 3) return@forEachObject
            val part = ga.substring(0, 1)
            val field = ga.substring(2)
            val bucket = parts.getOrPut(part) { LinkedHashMap() }
            if (field == "icon") {
                val icon = obj.iconIndex()
                if (icon != null) bucket.putIfAbsent("icon", JsonPrimitive(icon))
            } else {
                val text = textOf(obj)
                if (text.isNotEmpty()) bucket.putIfAbsent(field, JsonPrimitive(text))
            }
        }
    }

    private fun labelOf(day: JsonObject): Pair<String, Boolean>? {
        var result: Pair<String, Boolean>? = null
        forEachObject(day) { obj ->
            if (result == null && obj.containsKey("isRed") && obj.containsKey("day") && obj.containsKey("index")) {
                val lab = obj.stringOf("day")
                if (lab != null) result = lab to (obj["isRed"]?.jsonPrimitive?.booleanOrNull ?: false)
            }
        }
        return result
    }

    // ---------- утилиты ----------

    private fun JsonObject.intOf(vararg keys: String): Int? = keys.firstNotNullOfOrNull { k ->
        (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    }

    private fun JsonObject.doubleOf(vararg keys: String): Double? = keys.firstNotNullOfOrNull { k ->
        (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.intOrNull?.toDouble() }
    }

    private fun JsonObject.stringOf(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.iconIndex(): Int? {
        val style = this["style"] as? JsonObject ?: return null
        return style.intOf("--icon")
    }

    private fun JsonElement.stringContent(): String? = (this as? JsonPrimitive)?.content

    private fun parseTemp(text: String): Int? {
        val cleaned = text.replace('°', ' ').replace('\u2212', '-').trim()
        return cleaned.takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
    }

    private fun hourlyTemperature(text: String): Int? =
        if (tempRegex.matches(text)) parseTemp(text)
        else embeddedTempRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()
}
