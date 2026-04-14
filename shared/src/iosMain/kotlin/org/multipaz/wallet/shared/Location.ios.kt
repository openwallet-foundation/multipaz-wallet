package org.multipaz.wallet.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2D
import platform.Foundation.NSDate
import platform.Foundation.NSSelectorFromString
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.time.Instant

@OptIn(ExperimentalForeignApi::class)
fun Location.Companion.fromCLLocation(location: CLLocation): Location {
    val isLocationValid = location.horizontalAccuracy >= 0.0
    val isAltitudeValid = location.verticalAccuracy >= 0.0
    val isSpeedValid = location.speed >= 0.0
    val isCourseValid = location.course >= 0.0

    return Location(
        latitude = location.coordinate.useContents { latitude },
        longitude = location.coordinate.useContents { longitude },

        // Convert NSDate's timeIntervalSince1970 (seconds) to milliseconds for Instant
        timestamp = Instant.fromEpochMilliseconds((location.timestamp.timeIntervalSince1970 * 1000).toLong()),

        altitudeMeters = if (isAltitudeValid) {
            // Call respondsToSelector on the instance, passing an Objective-C selector
            if (location.respondsToSelector(NSSelectorFromString("ellipsoidalAltitude"))) {
                location.ellipsoidalAltitude
            } else {
                location.altitude
            }
        } else null,

        mslAltitudeMeters = if (isAltitudeValid) location.altitude else null,

        horizontalAccuracyMeters = if (isLocationValid) location.horizontalAccuracy else null,
        verticalAccuracyMeters = if (isAltitudeValid) location.verticalAccuracy else null,

        speedMetersPerSecond = if (isSpeedValid) location.speed else null,
        speedAccuracyMetersPerSecond = if (location.speedAccuracy >= 0.0) location.speedAccuracy else null,

        bearingDegrees = if (isCourseValid) location.course else null,
        bearingAccuracyDegrees = if (location.courseAccuracy >= 0.0) location.courseAccuracy else null
    )
}

@OptIn(ExperimentalForeignApi::class)
fun Location.toCLLocation(): CLLocation {
    // 1. Construct the C-struct for coordinates
    val coordinate = cValue<CLLocationCoordinate2D> {
        latitude = this@toCLLocation.latitude
        longitude = this@toCLLocation.longitude
    }

    // 2. Convert Kotlin Instant back to NSDate (seconds since 1970)
    val nsDate = NSDate.dateWithTimeIntervalSince1970(this.timestamp.toEpochMilliseconds() / 1000.0)

    // 3. Map missing data to iOS's expected "-1.0" for invalid/missing measurements
    val altitudeToUse = this.altitudeMeters ?: 0.0
    val horizontalAccuracyToUse = this.horizontalAccuracyMeters ?: -1.0
    val verticalAccuracyToUse = this.verticalAccuracyMeters ?: -1.0

    val speedToUse = this.speedMetersPerSecond ?: -1.0
    val speedAccuracyToUse = this.speedAccuracyMetersPerSecond ?: -1.0

    val courseToUse = this.bearingDegrees ?: -1.0
    val courseAccuracyToUse = this.bearingAccuracyDegrees ?: -1.0

    // 4. Construct the CLLocation using the most comprehensive initializer (iOS 13.4+)
    return CLLocation(
        coordinate = coordinate,
        altitude = altitudeToUse,
        horizontalAccuracy = horizontalAccuracyToUse,
        verticalAccuracy = verticalAccuracyToUse,
        course = courseToUse,
        courseAccuracy = courseAccuracyToUse,
        speed = speedToUse,
        speedAccuracy = speedAccuracyToUse,
        timestamp = nsDate
    )
}