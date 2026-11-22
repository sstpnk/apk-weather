package tech.stpnk.weather.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale

/**
 * Веб-карта Яндекс Погоды. Страница сама запрашивает актуальные радарные и
 * картографические тайлы тем же публичным маршрутом, что используется в браузере.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createYandexWeatherMap(context: Context, cityUrl: String, lat: Double, lon: Double): WebView =
    WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host?.endsWith("yandex.ru") != true
        }
        loadUrl(yandexNowcastUrl(cityUrl, lat, lon))
    }

fun disposeYandexWeatherMap(webView: WebView) {
    webView.apply {
        onPause()
        stopLoading()
        loadUrl("about:blank")
        destroy()
    }
}

private fun yandexNowcastUrl(cityUrl: String, lat: Double, lon: Double): String {
    val base = cityUrl.trimEnd('/')
    val latitude = String.format(Locale.US, "%.6f", lat)
    val longitude = String.format(Locale.US, "%.6f", lon)
    return "$base/maps/nowcast?via=mnc&lat=$latitude&lon=$longitude&ll=$longitude%2C$latitude&z=9"
}
