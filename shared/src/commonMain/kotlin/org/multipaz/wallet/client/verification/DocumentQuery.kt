package org.multipaz.wallet.client.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.mdoc.request.AlternativeDataElementSet
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.request.DocRequestInfo
import org.multipaz.mdoc.request.ElementReference

@CborSerializable
sealed class DocumentQuery {

    abstract fun getRequests(): List<Request>

    suspend fun addDocRequests(
        deviceRequest: DeviceRequest.Builder,
        intentToRetain: Boolean,
        readerKey: AsymmetricKey.X509Compatible?
    ): Int {
        val requests = getRequests()
        requests.forEach { request ->
            when (request) {
                is IsoMdocRequest -> {
                    val nameSpaces = mutableMapOf<String, Map<String, Boolean>>()
                    request.namespaces.forEach { (namespace, dataElements) ->
                        nameSpaces[namespace] = dataElements.associate { dataElement ->
                            dataElement.dataElementName to intentToRetain
                        }
                    }

                    val alternativeDataElements = mutableListOf<AlternativeDataElementSet>()
                    request.namespaces.forEach { (namespace, dataElementRequests) ->
                        dataElementRequests.forEach { dataElementRequest ->
                            if (dataElementRequest.alternativeDataElements.isNotEmpty()) {
                                val alternativeElementSets = mutableListOf<List<ElementReference>>()
                                dataElementRequest.alternativeDataElements.forEach { alternativeDataElement ->
                                    alternativeElementSets.add(listOf(ElementReference(
                                        namespace = namespace,
                                        dataElement = alternativeDataElement
                                    )))
                                }
                                alternativeDataElements.add(AlternativeDataElementSet(
                                    requestedElement = ElementReference(
                                        namespace = namespace,
                                        dataElement = dataElementRequest.dataElementName
                                    ),
                                    alternativeElementSets = alternativeElementSets
                                ))
                            }
                        }
                    }

                    val docRequestInfo = if (alternativeDataElements.isNotEmpty()) {
                        DocRequestInfo(alternativeDataElements = alternativeDataElements)
                    } else {
                        null
                    }
                    deviceRequest.addDocRequest(
                        docType = request.docType,
                        nameSpaces = nameSpaces,
                        docRequestInfo = docRequestInfo,
                        readerKey = readerKey
                    )
                }

                is SdJwtVcRequest -> {
                    val otherDocumentsNamespace = mutableMapOf<String, Boolean>()
                    val mapping = mutableMapOf<String, JsonArray>()
                    val mdocClaims = request.claims.forEach { claim ->
                        val flattenedPath = claim.claimPath.joinToString(separator = "_") { it.jsonPrimitive.content }
                        val dataElementName = "sdjwtvc_$flattenedPath"
                        mapping[dataElementName] = JsonArray(claim.claimPath)
                        otherDocumentsNamespace[dataElementName] = intentToRetain
                    }

                    val alternativeDataElements = mutableListOf<AlternativeDataElementSet>()
                    request.claims.forEach { claim ->
                        if (claim.alternativeClaims.isNotEmpty()) {
                            val alternativeElementSets = mutableListOf<List<ElementReference>>()
                            claim.alternativeClaims.forEach { alternativeClaim ->
                                val alternativeClaimFlattenedPath = alternativeClaim.joinToString(separator = "_") {
                                    it.jsonPrimitive.content
                                }
                                val alternativeClaimDataElementName = "sdjwtvc_$alternativeClaimFlattenedPath"
                                mapping[alternativeClaimDataElementName] = JsonArray(alternativeClaim)
                                alternativeElementSets.add(listOf(ElementReference(
                                    namespace = "_",
                                    dataElement = alternativeClaimDataElementName
                                )))
                            }
                            val flattenedPath = claim.claimPath.joinToString(separator = "_") { it.jsonPrimitive.content }
                            val dataElementName = "sdjwtvc_$flattenedPath"
                            alternativeDataElements.add(AlternativeDataElementSet(
                                requestedElement = ElementReference(
                                    namespace = "_",
                                    dataElement = dataElementName
                                ),
                                alternativeElementSets = alternativeElementSets
                            ))
                        }
                    }

                    val docRequestInfo = DocRequestInfo(
                        alternativeDataElements = alternativeDataElements,
                        docFormat = "sd-jwt+kb",
                        dataElementIdentifierMapping = mapping
                    )
                    deviceRequest.addDocRequest(
                        docType = request.vct,
                        nameSpaces = mapOf("_" to otherDocumentsNamespace),
                        docRequestInfo = docRequestInfo,
                        readerKey = readerKey
                    )
                }
            }
        }
        return requests.size
    }

    companion object
}

