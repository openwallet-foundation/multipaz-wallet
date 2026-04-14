package org.multipaz.wallet.shared

import org.multipaz.cbor.annotation.CborSerializable
import kotlin.time.Instant

/**
 * A platform-agnostic representation of a geographic location.
 *
 * This unified model bridges Android and iOS. Any optional property (`Double?`) will be `null`
 * if the underlying platform provider could not determine or validate that specific data point.
 */
@CborSerializable
data class Location(
    /**
     * Latitude of the location in degrees.
     *
     * Positive values indicate Northern Hemisphere, negative values indicate Southern Hemisphere.
     */
    val latitude: Double,

    /**
     * Longitude of the location in degrees.
     *
     * Positive values indicate Eastern Hemisphere, negative values indicate Western Hemisphere.
     */
    val longitude: Double,

    /**
     * The exact moment in time that this location fix was generated.
     */
    val timestamp: Instant,

    /**
     * The altitude of the location in meters above the WGS84 reference ellipsoid.
     * * Note: This is not the same as elevation above sea level.
     * Returns `null` if altitude is not available.
     */
    val altitudeMeters: Double?,

    /**
     * The altitude of the location in meters above Mean Sea Level (MSL).
     * * Returns `null` if MSL altitude is not available (common on older Android
     * and iOS versions where only ellipsoidal altitude is provided).
     */
    val mslAltitudeMeters: Double?,

    /**
     * The estimated horizontal accuracy radius in meters.
     * * Represents the radius of 68% confidence. In other words, there is a 68% probability
     * that the true location is within this distance of the reported latitude and longitude.
     * Returns `null` if horizontal accuracy is not available.
     */
    val horizontalAccuracyMeters: Double?,

    /**
     * The estimated vertical accuracy in meters.
     * * Represents the 68% confidence interval for the altitude.
     * Returns `null` if vertical accuracy is not available.
     */
    val verticalAccuracyMeters: Double?,

    /**
     * The speed over ground in meters per second.
     * Returns `null` if speed is not available.
     */
    val speedMetersPerSecond: Double?,

    /**
     * The estimated accuracy of the speed in meters per second (68% confidence).
     * Returns `null` if speed accuracy is not available.
     */
    val speedAccuracyMetersPerSecond: Double?,

    /**
     * The horizontal direction of travel of this device in degrees.
     * * Ranges from 0.0 to 360.0, where 0 is true North, 90 is East, 180 is South, and 270 is West.
     * Returns `null` if bearing (course) is not available.
     */
    val bearingDegrees: Double?,

    /**
     * The estimated accuracy of the bearing in degrees (68% confidence).
     * Returns `null` if bearing accuracy is not available.
     */
    val bearingAccuracyDegrees: Double?
) {
    companion object
}