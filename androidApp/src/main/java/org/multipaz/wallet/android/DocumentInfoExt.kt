package org.multipaz.wallet.android

import org.multipaz.compose.document.DocumentInfo
import org.multipaz.mdoc.credential.MdocCredential

/**
 * Returns whether a document can be presented via proximity.
 */
val DocumentInfo.isProximityPresentable: Boolean
    get() {
        credentialInfos.forEach { credentialInfo ->
            if (credentialInfo.credential is MdocCredential) {
                return true
            }
        }
        return false
    }
