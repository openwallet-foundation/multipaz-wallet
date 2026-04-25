package org.multipaz.wallet.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Bstr
import org.multipaz.claim.JsonClaim
import org.multipaz.claim.MdocClaim
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mpzpass.MpzPass
import org.multipaz.sdjwt.SdJwt
import org.multipaz.util.fromBase64Url
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.useEffect
import react.useState
import web.cssom.ClassName

external interface PassDetailScreenProps : Props {
    var pass: MpzPass
    var documentTypeRepository: DocumentTypeRepository
    var onBack: () -> Unit
}

val PassDetailScreen = FC<PassDetailScreenProps> { props ->
    val pass = props.pass
    val (sdJwtClaims, setSdJwtClaims) = useState<List<JsonClaim>>(emptyList())
    
    val mdoc = pass.isoMdoc.firstOrNull()
    val sdJwtVc = pass.sdJwtVc.firstOrNull()

    // Equivalent to LaunchedEffect(sdJwtVc) in Compose
    useEffect(sdJwtVc) {
        if (sdJwtVc != null) {
            val scope = CoroutineScope(Dispatchers.Main)
            scope.launch {
                val parsedClaims = parseSdJwtClaims(sdJwtVc.compactSerialization, props.documentTypeRepository)
                setSdJwtClaims(parsedClaims)
            }
        }
    }

    div {
        className = ClassName("flex-grow max-w-2xl mx-auto px-4 sm:px-6 py-10 w-full")

        // Back Button & Title
        div {
            className = ClassName("flex items-center space-x-4 mb-8")
            button {
                className = ClassName("p-2 -ml-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-full transition-all focus:outline-none")
                onClick = { props.onBack() }
                ArrowLeftIcon()
            }
            h1 {
                className = ClassName("text-3xl font-bold text-slate-900")
                +(pass.name ?: "Pass details")
            }
        }

        // Section 1: Pass Information
        div {
            className = ClassName("mb-10")
            h2 {
                className = ClassName("text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3 ml-4")
                +"Pass Information"
            }
            FloatingItemList {
                FloatingItem {
                    title = "Name"
                    subtitle = pass.name ?: "Untitled"
                }
                FloatingItem {
                    title = "Type"
                    subtitle = pass.typeName ?: "Multipaz Pass"
                }
                FloatingItem {
                    title = "Version"
                    subtitle = pass.version.toString()
                }
                FloatingItem {
                    title = "Update URL"
                    subtitle = pass.updateUrl ?: "Not set"
                }
            }
        }

        // Section 2: Credential Information
        div {
            className = ClassName("mb-10")
            h2 {
                className = ClassName("text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3 ml-4")
                +"Credential Information"
            }
            FloatingItemList {
                if (mdoc != null) {
                    FloatingItem {
                        title = "Format"
                        subtitle = "ISO/IEC 18013-5 (mdoc)"
                    }
                    FloatingItem {
                        title = "DocType"
                        subtitle = mdoc.docType
                    }
                } else if (sdJwtVc != null) {
                    FloatingItem {
                        title = "Format"
                        subtitle = "IETF SD-JWT VC"
                    }
                    FloatingItem {
                        title = "VCT (Type)"
                        subtitle = sdJwtVc.vct
                    }
                }
            }
        }

        // Section 3: Claims / Data Elements
        div {
            className = ClassName("mb-10")
            h2 {
                className = ClassName("text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3 ml-4")
                +(if (mdoc != null) "Data Elements" else "Claims")
            }
            FloatingItemList {
                if (mdoc != null) {
                    val documentType = props.documentTypeRepository.getDocumentTypeForMdoc(mdoc.docType)
                    mdoc.issuerNamespaces.data.forEach { (namespace, issuerSignedItems) ->
                        issuerSignedItems.forEach { (dataElementName, issuerSignedItem) ->
                            val documentAttribute = documentType?.mdocDocumentType?.namespaces[namespace]?.dataElements[dataElementName]
                            val mdocClaim = MdocClaim(
                                displayName = documentAttribute?.attribute?.displayName ?: dataElementName,
                                attribute = documentAttribute?.attribute,
                                docType = mdoc.docType,
                                namespaceName = namespace,
                                dataElementName = dataElementName,
                                value = issuerSignedItem.dataElementValue
                            )
                            if (mdocClaim.attribute?.type == DocumentAttributeType.Picture && mdocClaim.value is Bstr) {
                                val bytes = (mdocClaim.value as Bstr).value
                                FloatingItemPicture {
                                    title = mdocClaim.displayName
                                    picture = ByteString(bytes)
                                }
                            } else {
                                FloatingItem {
                                    title = mdocClaim.displayName
                                    subtitle = mdocClaim.render()
                                }
                            }
                        }
                    }
                } else if (sdJwtVc != null) {
                    sdJwtClaims.forEach { jsonClaim ->
                        if (jsonClaim.attribute?.type == DocumentAttributeType.Picture) {
                            val bytes = jsonClaim.value.jsonPrimitive.content.fromBase64Url()
                            FloatingItemPicture {
                                title = jsonClaim.displayName
                                picture = ByteString(bytes)
                            }
                        } else {
                            FloatingItem {
                                title = jsonClaim.displayName
                                subtitle = jsonClaim.render()
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun parseSdJwtClaims(
    compactSerialization: String,
    documentTypeRepository: DocumentTypeRepository
): List<JsonClaim> {
    val sdJwt = SdJwt.fromCompactSerialization(compactSerialization)
    val claims = mutableListOf<JsonClaim>()
    if (sdJwt.x5c != null && sdJwt.credentialType != null) {
        val documentType = documentTypeRepository.getDocumentTypeForJson(sdJwt.credentialType!!)
        val processedPayload = sdJwt.verify(sdJwt.x5c!!.certificates.first().ecPublicKey)
        for ((key, value) in processedPayload) {

            val documentAttribute = documentType?.jsonDocumentType?.claims[key]
            val jsonClaim = JsonClaim(
                displayName = documentAttribute?.displayName ?: key,
                attribute = documentAttribute,
                vct = sdJwt.credentialType!!,
                claimPath = buildJsonArray { add(key) },
                value = value
            )
            claims.add(jsonClaim)
        }
    }
    return claims
}

val ArrowLeftIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-6 w-6"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M10 19l-7-7m0 0l7-7m-7 7h18"
        }
    }
}
