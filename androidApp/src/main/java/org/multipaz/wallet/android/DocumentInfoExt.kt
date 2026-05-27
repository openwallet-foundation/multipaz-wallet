package org.multipaz.wallet.android

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.claim.JsonClaim
import org.multipaz.claim.MdocClaim
import org.multipaz.compose.document.DocumentInfo
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential

/**
 * Returns whether a document can be presented via proximity.
 */
val DocumentInfo.isProximityPresentable: Boolean
    get() {
        credentialInfos.forEach { credentialInfo ->
            if (credentialInfo.credential is MdocCredential ||
                credentialInfo.credential is KeyBoundSdJwtVcCredential) {
                return true
            }
        }
        return false
    }

/**
 * Returns whether a document has a portrait image or not.
 */
val DocumentInfo.hasPortrait: Boolean
    get() {
        credentialInfos.forEach { credentialInfo ->
            credentialInfo.claims.forEach { claim ->
                if (claim.attribute?.type == DocumentAttributeType.Picture) {
                    when (claim) {
                        is JsonClaim -> {
                            claim.claimPath.forEach { claimPathElement ->
                                claimPathElement.jsonPrimitive.contentOrNull?.let { elementStr ->
                                    if (elementStr.lowercase().contains("portrait")) {
                                        return true
                                    }
                                }
                            }
                        }
                        is MdocClaim -> {
                            if (claim.dataElementName.lowercase().contains("portrait")) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }