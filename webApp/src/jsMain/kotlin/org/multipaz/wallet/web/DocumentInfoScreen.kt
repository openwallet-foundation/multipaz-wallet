package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.multipaz.claim.Claim
import org.multipaz.claim.JsonClaim
import org.multipaz.claim.MdocClaim
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.util.fromBase64Url
import react.FC
import react.Props
import react.PropsWithChildren
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.useMemo
import web.cssom.ClassName

external interface DocumentInfoScreenProps : Props {
    var documentId: String
    var documentModel: DocumentModel
    var settingsModel: SettingsModel
    var onBack: () -> Unit
    var onDelete: () -> Unit
}

val DocumentInfoScreen = FC<DocumentInfoScreenProps> { props ->
    val documentInfos = useFlow(props.documentModel.documentInfos)
    val documentInfo = documentInfos.find { it.document.identifier == props.documentId }
    
    if (documentInfo == null) {
        div {
            +"Document not found"
        }
        return@FC
    }

    val credentialInfo = documentInfo.credentialInfos.firstOrNull()
    val typeDisplayName = documentInfo.document.typeDisplayName ?: "Document"

    div {
        className = ClassName("min-h-screen bg-slate-100 dark:bg-slate-900 flex flex-col transition-colors duration-300")

        // Header
        AppBar {
            title = typeDisplayName
            settingsModel = props.settingsModel
            leftContent = FC {
                button {
                    className = ClassName("p-2 -ml-2 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-full transition-colors")
                    onClick = { props.onBack() }
                    BackIcon { }
                }
            }
            actions = FC {
                button {
                    className = ClassName("p-2 -mr-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 rounded-full transition-all focus:outline-none")
                    onClick = { props.onDelete() }
                    TrashIcon { }
                }
            }
        }

        main {
            className = ClassName("flex-grow max-w-2xl mx-auto px-4 sm:px-6 py-8 w-full space-y-8")

            if (credentialInfo != null) {
                // Claims List
                div {
                    className = ClassName("space-y-4")
                    h2Section { +"Claims" }
                    FloatingItemList {
                        val jsonIgnoredClaims = setOf("iss", "vct", "iat", "nbf", "exp", "cnf", "status")
                        credentialInfo.claims.forEach { claim ->
                            if (claim is JsonClaim) {
                                val name = claim.claimPath[0].jsonPrimitive.content
                                if (jsonIgnoredClaims.contains(name)) {
                                    return@forEach
                                }
                            }
                            FloatingItemHeadingAndContent {
                                heading = claim.displayName
                                RenderClaimValue {
                                    this.claim = claim
                                }
                            }
                        }
                    }
                }

                // Certificate Info
                div {
                    className = ClassName("space-y-4")
                    h2Section { +"Certificate Information" }
                    FloatingItemList {
                        var certSignedDate: Instant? = null
                        var certValidFrom: Instant? = null
                        var certValidUntil: Instant? = null
                        var certExpectedUpdate: Instant? = null
                        
                        val credential = credentialInfo.credential
                        if (credential is MdocCredential) {
                            val mso = credential.mso
                            certSignedDate = mso.signedAt
                            certValidFrom = mso.validFrom
                            certValidUntil = mso.validUntil
                            certExpectedUpdate = mso.expectedUpdate
                        } else {
                            val claims = credentialInfo.claims.filterIsInstance<JsonClaim>()
                            certSignedDate = claims.getInstant("iat")
                            certValidFrom = claims.getInstant("nbf")
                            certValidUntil = claims.getInstant("exp")
                        }

                        FloatingItemHeadingAndDate {
                            heading = "Signed At"
                            date = certSignedDate
                        }
                        FloatingItemHeadingAndDate {
                            heading = "Valid From"
                            date = certValidFrom
                        }
                        FloatingItemHeadingAndDate {
                            heading = "Valid Until"
                            date = certValidUntil
                        }
                        FloatingItemHeadingAndDate {
                            heading = "Expected Update"
                            date = certExpectedUpdate
                        }
                    }
                }
            } else {
                div {
                    className = ClassName("text-center py-12 text-slate-500")
                    +"No credential information available"
                }
            }
            
            // Bottom spacing
            div { className = ClassName("h-8") }
        }
    }
}

private val h2Section = FC<PropsWithChildren> { props ->
    h1 {
        className = ClassName("text-sm font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest px-1 transition-colors duration-300")
        +props.children
    }
}

external interface RenderClaimValueProps : Props {
    var claim: Claim
}

val RenderClaimValue = FC<RenderClaimValueProps> { props ->
    val claim = props.claim
    val bytes = useMemo(claim) {
        try {
            if (claim.attribute?.type == DocumentAttributeType.Picture) {
                if (claim is MdocClaim) {
                    ByteString(claim.value.asBstr)
                } else if (claim is JsonClaim) {
                    ByteString(claim.value.jsonPrimitive.content.fromBase64Url())
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    if (claim.attribute?.type == DocumentAttributeType.Picture) {
        val imageUrl = useImageUri(bytes)
        if (imageUrl != null) {
            img {
                src = imageUrl
                className = ClassName("max-h-64 w-auto rounded-2xl border border-slate-100 dark:border-slate-700 shadow-sm object-contain bg-slate-50 dark:bg-slate-900 transition-colors duration-300")
            }
        } else {
            div {
                className = ClassName("text-slate-500 italic")
                +"[Image decoding error]"
            }
        }
    } else {
        div {
            className = ClassName("text-slate-900 dark:text-white transition-colors duration-300")
            +claim.render()
        }
    }
}

private fun List<JsonClaim>.getInstant(claimName: String): Instant? {
    val claim = find { it.claimPath.size == 1 && it.claimPath[0].jsonPrimitive.content == claimName }
    claim?.value?.jsonPrimitive?.longOrNull?.let {
        return Instant.fromEpochSeconds(it)
    }
    return null
}
