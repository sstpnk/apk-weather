package tech.stpnk.weather.data.yandex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Достаёт Next.js flight-поток из HTML-страницы: склеивает чанки
 * `self.__next_f.push([1,"…"])` и снимает JSON-экранирование.
 *
 * Сделано линейным сканером, а не регуляркой: Java-regex с группой
 * `(?:[^"\\]|\\.)*` уходит в StackOverflow на чанках в сотни килобайт.
 */
object FlightExtractor {

    private const val MARKER = "self.__next_f.push([1,\""
    private val json = Json { ignoreUnknownKeys = true }

    fun extract(html: String): String {
        val sb = StringBuilder(html.length / 2)
        var idx = html.indexOf(MARKER)
        while (idx >= 0) {
            var i = idx + MARKER.length
            val raw = StringBuilder()
            while (i < html.length) {
                val c = html[i]
                if (c == '\\') {
                    raw.append(c)
                    if (i + 1 < html.length) {
                        raw.append(html[i + 1])
                        i += 2
                        continue
                    }
                    break
                }
                if (c == '"') break
                raw.append(c)
                i++
            }
            sb.append(unescape(raw.toString()))
            idx = html.indexOf(MARKER, i)
        }
        return sb.toString()
    }

    private fun unescape(raw: String): String = try {
        (json.parseToJsonElement("\"$raw\"") as JsonPrimitive).content
    } catch (e: Exception) {
        raw
    }
}
