package org.multipaz.wallet.client

import org.multipaz.request.Requester

val Requester.isProximityReader: Boolean
    get() = appId == null && origin == null