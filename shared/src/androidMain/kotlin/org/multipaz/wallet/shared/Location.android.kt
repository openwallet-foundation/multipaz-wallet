package org.multipaz.wallet.shared

import android.os.Build
import kotlin.time.Instant

fun Location.Companion.fromAndroidLocation(location: android.location.Location): Location {
    return Location(
        latitude = location.latitude,
        longitude = location.longitude,

        // Convert epoch milliseconds to Instant
        timestamp = Instant.fromEpochMilliseconds(location.time),

        altitudeMeters = if (location.hasAltitude()) location.altitude else null,
        mslAltitudeMeters = if (android.os.Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude()) {
            location.mslAltitudeMeters
        } else null,

        horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
        verticalAccuracyMeters = if (android.os.Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters.toDouble()
        } else null,

        speedMetersPerSecond = if (location.hasSpeed()) location.speed.toDouble() else null,
        speedAccuracyMetersPerSecond = if (android.os.Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond.toDouble()
        } else null,

        bearingDegrees = if (location.hasBearing()) location.bearing.toDouble() else null,
        bearingAccuracyDegrees = if (android.os.Build.VERSION.SDK_INT >= 26 && location.hasBearingAccuracy()) {
            location.bearingAccuracyDegrees.toDouble()
        } else null
    )
}

fun Location.toAndroidLocation(provider: String = "fused"): android.location.Location {
    val androidLocation = android.location.Location(provider)

    // 1. Core Properties
    androidLocation.latitude = this.latitude
    androidLocation.longitude = this.longitude
    androidLocation.time = this.timestamp.toEpochMilliseconds()

    // 2. Base Optional Properties (Available on all API levels)
    this.altitudeMeters?.let { androidLocation.altitude = it }
    this.horizontalAccuracyMeters?.let { androidLocation.accuracy = it.toFloat() }
    this.speedMetersPerSecond?.let { androidLocation.speed = it.toFloat() }
    this.bearingDegrees?.let { androidLocation.bearing = it.toFloat() }

    // 3. API 26+ Optional Properties (Accuracies)
    if (Build.VERSION.SDK_INT >= 26) {
        this.verticalAccuracyMeters?.let { androidLocation.verticalAccuracyMeters = it.toFloat() }
        this.speedAccuracyMetersPerSecond?.let { androidLocation.speedAccuracyMetersPerSecond = it.toFloat() }
        this.bearingAccuracyDegrees?.let { androidLocation.bearingAccuracyDegrees = it.toFloat() }
    }

    // 4. API 34+ Optional Properties (Mean Sea Level)
    if (Build.VERSION.SDK_INT >= 34) {
        this.mslAltitudeMeters?.let { androidLocation.mslAltitudeMeters = it }
    }

    return androidLocation
}
