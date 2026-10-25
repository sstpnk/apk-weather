# План работ v2 (решения от 25.10.2026 внесены)

Основание: `docs/01-research.md`, `docs/02-architecture.md`. Репозиторий: https://github.com/sstpnk/apk-weather (push настроен, токен через git credential manager).

## Правила движения

- Каждый этап закрывается проверкой: `assembleDebug` + `testDebugUnitTest` (+`assembleRelease` где уместно), замер APK, для UI — смок на устройстве.
- Тесты — на фикстурах из HAR/спайков (реальные ответы источников).
- Системные установки — только с разрешения Сергея (SDK уже установлен — ок).
- Этап 2 может использовать REA (`rea` CLI, headless JADX + JDK 17) для извлечения GraphQL-схем из APK.

## Этапы

### Этап 0–1. Инфраструктура и скелет — выполняется сейчас
- Готово: Gradle 8.10.2 + AGP 8.7.3 + Kotlin 2.0.21, Compose-скелет (MainActivity, тема, иконка), CI (`android-ci.yml`), README, `.gitignore` (референсы/анализ не публикуются), `local.properties` с SDK.
- Проверка: первая сборка `:app:assembleDebug` зелёная, размер зафиксирован как база, push в main прошёл, CI зелёный.

### Этап 2. Спайки источников — ЗАВЕРШЁН (25.10.2026)

Результаты (детали в `docs/05-source-yandex-web.md`):
- GraphQL `api.weather.yandex.ru/graphql/query` закрыт динамическим токеном (`x-yandex-weather-token` веб-клиента;
  мобильный `X-Yandex-API-Key` в APK статикой не лежит) — путь отклонён **по решению Сергея выбран парсинг HTML/RSC**.
- Свежие PNG-тайлы nowcast/raster-maps без токена — 404 (серверный кэш отдаёт только пары из HAR); погодные слои
  карты отложены, базовая карта — OSM.
- **Рабочий путь**: `GET /pogoda/ru/{city}` → flight-поток → fact + почасовая + 10 дней (прототип
  `.analysis/parse_yandex_web.py`, фикстура `app/src/test/resources/fixtures/yandex_moscow.json`).
- Open-Meteo geocoding — работает (Москва: 55.75204, 37.61781); `api.open-meteo.com` с этой машины не отвечает;
  `api.met.no` — 200 OK, зафиксирован как резервный источник.
- Проверка: фикстура снята с живых данных, прототип печатает домен-модель Москвы (10 дней × части суток, 39+ часовых ячеек).

### Этап 3. Слой данных — ЗАВЕРШЁН (25.10.2026)

- Домен-модели `WeatherData/CurrentWeather/HourForecast/DayForecast/GeoPlace` (kotlinx-serialization).
- `FlightExtractor` — линейный сканер чанков `__next_f` (regex заменили из-за StackOverflow на больших чанках).
- `FlightParser` — строки `hex:JSON`, резолвинг `$`/`$L`-ссылок, fact/почасовая/10 дней с частями суток.
- `YandexWebSource` (OkHttp) → `WeatherCache` (файлы, JSON) → `WeatherRepository` (cache-first, TTL 30 мин,
  при ошибке сети — устаревший кэш, без кэша — Failed).
- `Graph` — ручной DI, `App` подключён в манифесте.
- Тесты (10, все зелёные): парсер на gz-фикстуре страницы (текущая погода, место geo_id 213, 10 дней
  с частями m/d/e/n, почасовая лента) + репозиторий (fresh/stale/force/failed).
- Проверка: `gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL.

### Этап 4. Главный экран — ЗАВЕРШЁН (25.10.2026)

- `MainViewModel` (StateFlow: Loading/Content/Error) поверх репозитория; refresh-кнопка в шапке.
- Экран по дизайн-спеке: шапка-градиент (день/ночь по `daytime`), герой 76 sp + иконка, метрики
  (ветер с направлением СВ/ЮВ…, давление, влажность, вода), почасовая лента (LazyRow), баннер осадков
  (по ключевым словам сводок ближайших часов), 10 дней с акцентом «Сегодня/Завтра» и выходных,
  атрибуция + пометка «данные из кэша» для stale.
- 10 векторных погодных иконок + маппер `weatherIconRes` (код условия → русское слово → дефолт).
- Форматтеры `Format.kt` (температуры ±°, направления, тексты условий) покрыты тестами (FormatTest).
- Размеры: release **1 112 КБ** (было 668 КБ на скелете), debug 11 963 КБ; тесты: 15 зелёных.
- Проверка: `assembleDebug + assembleRelease + testDebugUnitTest` — BUILD SUCCESSFUL.

### Этап 5. Карта погоды — ЗАВЕРШЁН (25.10.2026)

- `MapActivity` + `MapScreen`: osmdroid 6.1.20 (AndroidView) + тайлы OSM (MAPNIK), пин города
  (вектор → bitmap), тайловый кэш в cacheDir/osmdroid, свой UA для OSM-политики.
- Координаты — из записи города (этап 6), дефолт — Москва. Погодные слои/таймлайн — отложены
  (нужен токен Яндекса, см. этап 2), карта не блокируется.
- Сборка зелёная; визуальная проверка на устройстве — при установке APK.

### Этап 6. Города, геолокация, настройки — ЗАВЕРШЁН (25.10.2026)

- **Города**: `CitiesActivity` — список с выбором/удалением, «Моё местоположение» (LocationManager:
  last-known + getCurrentLocation на API 30+ через suspend-обёртку, разрешения через
  ActivityResultContracts), поиск через Open-Meteo geocoding (ru-имена + en-имена для слага),
  слаг-кандидаты (англ./транслит) проверяются живой страницей Яндекса (спайк: suggest-эндпоинты
  мертвы, кириллические слаги — 404).
- **Настройки**: `SettingsActivity` — единицы (°C/°F, м/с|км/ч, мм рт. ст.|гПа), тема
  (светлая/тёмная/системная) с мгновенным применением (recreate), хранение в SharedPreferences
  (JSON, KeyValueStore-абстракция — тестируется без Android).
- `Format` стал единиц-зависимым; главный экран перечитывает настройки и выбранный город в onResume.
- Тесты: 21 зелёный (+ транслит/slugify, + CitiesStore на fake-KV).
- Размеры: release **1 491 КБ** (osmdroid добавил ~380 КБ), debug 12 219 КБ.
- Проверка: `assembleDebug + assembleRelease + testDebugUnitTest` — BUILD SUCCESSFUL.

### Этап 7. Виджеты и уведомления
- Малый и большой виджеты (RemoteViews), периодическое обновление WorkManager, ежедневное уведомление «Погода на сегодня», виджет в шторке.
- Проверка: виджеты на реальном лаунчере, уведомления после перезагрузки, замер APK.

### Этап 8. Харденинг и релиз
- R8 full mode, resource shrinking, удаление неиспользуемого, отчёт размера `docs/size-report.md` (ориентир ≤ 5 МБ universal).
- Подпись: keystore Сергея → `local.properties` (`keystore.*`), GitHub Release с APK/AAB.
- Проверка: финальный прогон тестов/CI, установка release на устройство, чек-лист функций референса.

## Что нужно от Сергея

- К этапу 8: файл keystore + пароли/alias (в `local.properties`, не коммитится).
- Опционально к этапу 2: разрешение на запуск `npx rea-agents setup` для постоянной регистрации REA как MCP-скилла (сейчас CLI доступен и этого достаточно).
