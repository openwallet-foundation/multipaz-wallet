package org.multipaz.wallet.backend

import org.multipaz.server.common.runServer

/**
 * Main entry point to launch the Wallet backend.
 *
 * Build and start the server using
 *
 * ```./gradlew backend:run```
 */
class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runServer(args) { configuration ->
                configureRouting(configuration)
            }
        }
    }
}