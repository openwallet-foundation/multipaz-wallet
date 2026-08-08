package org.multipaz.wallet.backend

/**
 * Interface for looking up human-readable location strings from IP addresses.
 */
interface IpLocationLookup {
    /**
     * Looks up a human-readable location string for the given IP address.
     *
     * @param ipAddress the IP address to look up, or `null`.
     * @return a location string (e.g. "Mountain View, United States" or "Local Network"), or `null` if unresolvable.
     */
    suspend fun lookup(ipAddress: String?): String?
}
