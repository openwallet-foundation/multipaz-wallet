package org.multipaz.wallet.android.ui

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.multipaz.util.Logger
import org.multipaz.wallet.shared.Location

private const val TAG = "OpenStreetMap"

/**
 * Reverse geocodes coordinates to a human-readable address string using OpenStreetMap's Nominatim API.
 *
 * @receiver the [Location] to get an address from.
 * @return The formatted display name (e.g., business name, street, city, country) or null if lookup fails.
 */
suspend fun Location.getAddressFromCoordinates(): String? = withContext(Dispatchers.IO) {
    val accuracy: Double? = horizontalAccuracyMeters
    val zoom = when {
        accuracy == null -> 18
        accuracy <= 50 -> 18 // Building
        accuracy <= 250 -> 16 // Street
        accuracy <= 1000 -> 14 // Neighborhood
        accuracy <= 5000 -> 12 // Town/City
        accuracy <= 20000 -> 10 // City/Region
        else -> 8 // County/State
    }

    try {
        val client = HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
                connectTimeoutMillis = 5000
            }
        }
        val response = client.get("https://nominatim.openstreetmap" +
                ".org/reverse?format=jsonv2&lat=${latitude}&lon=${longitude}&zoom=$zoom") {
            header("User-Agent", "MultipazWallet/1.0 (Android)")
        }
        
        if (response.status.isSuccess()) {
            val responseText = response.bodyAsText()
            val jsonObject = JSONObject(responseText)
            jsonObject.optString("display_name").takeIf { it.isNotEmpty() }
        } else {
            null
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Error getting address from coordinates", e)
        null
    }
}

@Composable
fun OpenStreetMap(
    location: Location,
    modifier: Modifier = Modifier
) {
    val htmlData = """
        <html>
        <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" crossorigin=""/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" crossorigin=""></script>
            <style>
                html, body { padding: 0; margin: 0; height: 100%; width: 100%; background-color: #eee; }
                #map { height: 100%; width: 100%; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var lat = ${location.latitude};
                var lng = ${location.longitude};
                var acc = ${location.horizontalAccuracyMeters ?: "null"};

                var map = L.map('map', {
                    zoomControl: true // Show +/- buttons for explicit zoom control
                }).setView([lat, lng], 15); // Set an initial view to prevent Leaflet errors before sizing
                map.attributionControl.setPrefix(false);

                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19,
                    attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                }).addTo(map);

                L.marker([lat, lng]).addTo(map);
                var circle = null;
                if (acc !== null) {
                    circle = L.circle([lat, lng], {
                        radius: acc,
                        color: '#007AFF',
                        fillColor: '#007AFF',
                        fillOpacity: 0.2
                    }).addTo(map);
                }

                // Wait for the WebView to finish layout before adjusting bounds based on accuracy
                setTimeout(function(){ 
                    map.invalidateSize(); 
                    if (circle !== null && acc > 250) {
                        map.fitBounds(circle.getBounds());
                    }
                }, 500);
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                setOnTouchListener { view, event ->
                    // Prevent the outer Compose scroll view from hijacking touch events
                    // when the user is trying to pan/zoom the map.
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE,
                        android.view.MotionEvent.ACTION_UP -> {
                            view.parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    false // Let the WebView consume the event internally
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // OpenStreetMap tile servers will block default WebView User-Agents
                settings.userAgentString = "MultipazWallet/1.0 (Android)"

                val encodedHtml = android.util.Base64.encodeToString(
                    htmlData.toByteArray(),
                    android.util.Base64.NO_PADDING
                )
                loadData(encodedHtml, "text/html", "base64")
            }
        }
    )
}
