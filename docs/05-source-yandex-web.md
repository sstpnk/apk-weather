# Источник данных: pogoda.yandex.ru (RSC/flight-парсинг)

Итог спайка этапа 2 (25.10.2026). Решение Сергея: парсить HTML/RSC pogoda.yandex.ru
(graphql закрыт динамическим токеном — см. «Отклонённые пути»).

## Эндпоинт

`GET https://yandex.ru/pogoda/ru/{city-slug}` — обычная HTML-страница, отдаётся без капчи
(проверено: 200 OK, ~1.25 МБ, заголовки: браузерный User-Agent + `Accept-Language: ru-RU`).

Внутри — Next.js flight-поток: чанки `self.__next_f.push([1,"…escaped…"])`.

## Алгоритм парсинга (прототип: `.analysis/parse_yandex_web.py`)

1. Вырезать чанки `__next_f.push`, unescape (`json.loads('"'+chunk+'"')`), склеить → payload.
2. Разбить payload на строки `^([0-9a-f]+):(.*)$`, каждую JSON-строку распарсить → карта `id → значение`.
3. **Резолвинг ссылок**: строки вида `$hex` и `$Lhex` (ленивые клиентские компоненты) заменять на
   `row_map[hex]` рекурсивно — без этого у дней 1–9 видна только часть данных.
4. Обход дерева React-элементов `["$", type, key, props]`:
   - **Текущая погода**: словарь с ключами `temperature/feelsLike/pressure/humidity/waterTemperature` →
     `temperatureInCelsius, feelsLikeInCelsius, waterTemperature, windSpeedInMetersPerSecond, windGust,
     windDirection, pressure (мм), humidity, condition, cloudiness, precStrength, precType, daytime,
     uvIndex, visibility, roadCondition`.
   - **Почасовая лента**: элементы с `className ~ AppHourlyItem_container__` → время (`\d{1,2}:\d{2}`),
     температура (`[+-]?\d+°`), иконка = `style['--icon']` (индекс CSS-спрайта), плюс a11y-строка
     «12:00: +9°, ясно, Ощущается как +5°.» как резерв.
   - **10 дней**: элементы с `className ~ AppForecastDay_container__` и ключом `data-day`
     (`1_25` = день_1, 25-е число). Заголовок — props `{day, isRed, index}` (тип `$Le8`).
     Части суток — по префиксу `gridArea`: `m-` (утро), `d-` (день), `e-` (вечер), `n-` (ночь):
     `{part}temp` («+6°»), `{part}icon` (--icon), `{part}text` («ясно»), `{part}feels`, `{part}wind`,
     `{part}dir`. Дубли (широкий/узкий рендеры) сливать по `data-day`.
   - **Гео**: словарь с `geoHierarchy`/`geoId` (geo_id 213 = Москва) — город, регион, координаты.

Фикстура живого ответа: `app/src/test/resources/fixtures/yandex_moscow.json`
(снята 25.10.2026 ~12:30 МСК: +11°, ясно, ветер 1 м/с В, давление 758, влажность 50 %).

## Открытые пункты (этап 3)

1. У «сегодня» на сайте отрисована только часть «утро» — день/ночь для сегодня добирать из
   `GET /pogoda/ru/{city}/details` с заголовком `RSC: 1` (в потоке `days`×20, `sunrise`/`sunset`×10,
   `moon`×32, `isRed`×10).
2. Точный `--icon`-индекс → наш вектор: строим маппинг по `condition`-тексту (словом) + времени суток;
   индексы спрайта используем только как подсказку.
3. Дедупликация часов: ключ `(time, temp, icon)` схлопывает одинаковые часы разных суток —
   уточнить по смещению от «сейчас» и по label дня.
4. Значения температур/давления — строки с «°»: парсинг в числа на мапперах DTO.

## Риски и меры

- **CSS-модульные классы с хэшами** (`AppForecastDay_container__AnH4J`) — матчить по стабильному
  префиксу до `__`; при смене вёрстки парсер падает громко (тесты на фикстуре это ловят).
- **Капча/антибот** при агрессивной частоте: кэш 30–60 мин, обновление WorkManager 30–60 мин,
  никаких параллельных запросов, вежливый UA.
- **Смена формата** — тест на фикстуре + интеграционный смок; при поломке — кэш и понятная ошибка в UI.

## Отклонённые пути (почему)

- `POST api.weather.yandex.ru/graphql/query` — 404 без `x-yandex-weather-client/timestamp/token`
  (динамический токен, алгоритм в обфусцированном бандле); мобильный `X-Yandex-API-Key` статически
  в APK не найден (427 кандидатов — мусор).
- Свежие PNG-тайлы (`/frontend/raster-maps/*/raw-tile`, `/frontend/nowcast/new_encoded_tile`) — 404
  без токена; работают только пары `(for_date, gen_time)` из HAR (серверный кэш) — на карту не
  рассчитываем. Базовая карта — OSM (osmdroid).
- `api.open-meteo.com` — не отвечает с этой машины (таймаут SSL, 3 попытки + curl); `api.met.no` —
  200 OK, запасной источник, если веб-Яндекс начнёт капчу (архитектура `WeatherSource` это допускает).
