package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.mdoc.request.AlternativeDataElementSet
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.request.DocRequestInfo
import org.multipaz.mdoc.request.ElementReference

/**
 * Base class representing a requirement for a specific document or credential in a verification interaction.
 *
 * A [DocumentQuery] defines the logical claims or data elements needed to satisfy a requirement (such as identity,
 * driving privileges, or age verification). It can be satisfied by multiple credential formats and profiles, represented
 * by a list of format-specific [Request] instances (such as [IsoMdocRequest] or [SdJwtVcRequest]) returned by [getRequests].
 */
@CborSerializable
sealed class DocumentQuery {

    /**
     * Returns the list of format-specific candidate [Request]s that can satisfy this document query.
     *
     * @return A list of candidate [Request]s (e.g. [IsoMdocRequest], [SdJwtVcRequest]).
     */
    abstract fun getRequests(): List<Request>

    /**
     * Adds document requests corresponding to this query to the given [DeviceRequest.Builder].
     *
     * Configures the requested document types, namespaces, data elements, alternatives, retention intent,
     * and reader authentication keys for each candidate format supported by this query.
     *
     * @param deviceRequest The [DeviceRequest.Builder] to which the document requests will be added.
     * @param intentToRetain Whether the verifier intends to retain the requested data elements.
     * @param readerKey Optional reader authentication key with an X.509 certificate chain.
     * @param issuerIdentifiers Optional list of trusted issuer identifier byte strings.
     * @return The number of document requests added to the builder.
     */
    suspend fun addDocRequests(
        deviceRequest: DeviceRequest.Builder,
        intentToRetain: Boolean,
        readerKey: AsymmetricKey.X509Compatible?,
        issuerIdentifiers: List<ByteString> = emptyList()
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

                    val docRequestInfo = if (alternativeDataElements.isNotEmpty() || issuerIdentifiers.isNotEmpty()) {
                        DocRequestInfo(
                            alternativeDataElements = alternativeDataElements,
                            issuerIdentifiers = issuerIdentifiers
                        )
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
                        issuerIdentifiers = issuerIdentifiers,
                        docFormat = "dc+sd-jwt",
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

    /**
     * Companion object for CBOR serialization and deserialization of [DocumentQuery] instances.
     */
    companion object
}

