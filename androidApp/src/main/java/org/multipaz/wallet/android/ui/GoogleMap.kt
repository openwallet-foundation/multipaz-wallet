package org.multipaz.wallet.android.ui

import android.content.Context
import android.location.Geocoder
import android.location.Address
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.multipaz.util.Logger
import org.multipaz.wallet.shared.Location
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "GoogleMap"


/**
 * Reverse geocodes coordinates to a human-readable address string using Google Maps Geocoder API.
 *
 * @receiver the [Location] to get an address from.
 * @return The formatted display name or null if lookup fails.
 */
suspend fun Location.getAddressFromGoogleMaps(context: Context): String? = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        Logger.w(TAG, "Error geocoding: $errorMessage")
                        continuation.resume(null)
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)
        }

        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            val addressLines = mutableListOf<String>()
            for (i in 0..address.maxAddressLineIndex) {
                addressLines.add(address.getAddressLine(i))
            }
            addressLines.joinToString(", ")
        } else {
            null
        }
    } catch (e: Exception) {
        Logger.w(TAG, "Error getting address from coordinates using Google Maps", e)
        null
    }
}

@Composable
fun GoogleMap(
    location: Location,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            val wrapper = object : FrameLayout(context) {
                override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                    when (ev.action) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        MotionEvent.ACTION_UP -> {
                            parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    if (ev.action == MotionEvent.ACTION_UP) {
                        performClick()
                    }
                    return super.dispatchTouchEvent(ev)
                }

                override fun performClick(): Boolean {
                    super.performClick()
                    return true
                }
            }
            wrapper.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            mapView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            wrapper.addView(mapView)

            mapView.getMapAsync { googleMap ->
                googleMap.uiSettings.isZoomControlsEnabled = true
                val latLng = LatLng(location.latitude, location.longitude)
                googleMap.addMarker(MarkerOptions().position(latLng))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                location.horizontalAccuracyMeters?.let { acc ->
                    googleMap.addCircle(
                        CircleOptions()
                            .center(latLng)
                            .radius(acc.toDouble())
                            .fillColor(0x33007AFF)
                            .strokeColor(0xFF007AFF.toInt())
                            .strokeWidth(2f)
                    )
                }
            }
            wrapper
        }
    )
}
