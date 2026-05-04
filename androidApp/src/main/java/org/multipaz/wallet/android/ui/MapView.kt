package org.multipaz.wallet.android.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.multipaz.wallet.android.BuildConfig
import org.multipaz.wallet.shared.Location

@Composable
fun MapView(
    location: Location,
    modifier: Modifier = Modifier
) {
    if (BuildConfig.GOOGLE_MAPS_API_KEY.isNotEmpty()) {
        GoogleMap(location, modifier)
    } else {
        OpenStreetMap(location, modifier)
    }
}

/**
 * Reverse geocodes coordinates to a human-readable address string using the configured map provider.
 */
suspend fun Location.getAddressFromCoordinates(context: Context): String? {
    return if (BuildConfig.GOOGLE_MAPS_API_KEY.isNotEmpty()) {
        getAddressFromGoogleMaps(context)
    } else {
        getAddressFromOpenStreetMap()
    }
}
