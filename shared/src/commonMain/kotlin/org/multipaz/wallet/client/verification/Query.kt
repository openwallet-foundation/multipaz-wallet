package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.addCborArray
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPublicKey
import org.multipaz.mdoc.engagement.Capability
import org.multipaz.mdoc.engagement.DeviceEngagement
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.request.DeviceRequestInfo
import org.multipaz.mdoc.request.DocumentSet
import org.multipaz.mdoc.request.UseCase
import org.multipaz.mdoc.request.buildDeviceRequest
import org.multipaz.mdoc.response.DeviceResponse
import org.multipaz.sdjwt.SdJwtKb
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.util.Logger
import org.multipaz.util.generateAllPaths
import org.multipaz.util.toBase64Url
import org.multipaz.util.zlibInflate
import org.multipaz.verification.JsonVerifiedPresentation
import org.multipaz.verification.MdocVerifiedPresentation
import org.multipaz.verification.VerifiedPresentation
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "Query"

@CborSerializable
sealed class Query(
    open val documentQueries: List<DocumentQuery>
) {
    suspend fun generateDcRequest(
        nonce: ByteString,
        origin: String,
        responseEncryptionKey: EcPublicKey,
        readerAuthKey: AsymmetricKey.X509Compatible?,
        intentToRetain: Boolean
    ): Pair<DataItem, DataItem> {
        val encryptionInfo = buildCborArray {
            add("dcapi")
            addCborMap {
                put("nonce", nonce.toByteArray())
                put("recipientPublicKey", responseEncryptionKey.toCoseKey().toDataItem())
            }
        }
        val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()
        val dcapiInfo = buildCborArray {
            add(base64EncryptionInfo)
            add(origin)
        }
        val dcapiInfoDigest = Crypto.digest(Algorithm.SHA256, Cbor.encode(dcapiInfo))
        val sessionTranscript = buildCborArray {
            add(Simple.NULL) // DeviceEngagementBytes
            add(Simple.NULL) // EReaderKeyBytes
            addCborArray {
                add("dcapi")
                add(dcapiInfoDigest)
            }
        }
        val deviceRequest = generateDeviceRequest(
            deviceEngagement = null,
            sessionTranscript = sessionTranscript,
            readerAuthKey = readerAuthKey,
            intentToRetain = intentToRetain
        )

        return Pair(deviceRequest.toDataItem(), encryptionInfo)
    }

    suspend fun generateDeviceRequest(
        deviceEngagement: DataItem?,
        sessionTranscript: DataItem,
        readerAuthKey: AsymmetricKey.X509Compatible?,
        intentToRetain: Boolean
    ): DeviceRequest = buildDeviceRequest(
        sessionTranscript = sessionTranscript
    ) {
        require(documentQueries.size == 1) {
            "Only one document query is supported at this time"
        }

        var hasReaderAuthAll = false
        deviceEngagement?.let {
            val de = DeviceEngagement.fromDataItem(it)
            hasReaderAuthAll = de.capabilities[Capability.READER_AUTH_ALL_SUPPORT]?.asBoolean ?: false
        }
        var numDocRequests = 0
        val allDocRequestIds = mutableListOf<MutableList<Int>>()

        documentQueries.forEach { documentQuery ->
            val docRequestIds = mutableListOf<Int>()
            val numDocRequestsAdded = documentQuery.addDocRequests(
                deviceRequest = this,
                intentToRetain = intentToRetain,
                // If ReaderAuthAll is available, don't sign the individual DocRequests
                readerKey = if (hasReaderAuthAll) {
                    null
                } else {
                    readerAuthKey
                }
            )
            docRequestIds.addAll(numDocRequests until numDocRequestsAdded)
            allDocRequestIds.add(docRequestIds)
            numDocRequests += numDocRequestsAdded
        }

        val documentSets = mutableListOf<DocumentSet>()
        allDocRequestIds.map { it.size }.generateAllPaths().forEach { path ->
            val docRequestIds = path.mapIndexed { index, i ->
                allDocRequestIds[index][i]
            }
            documentSets.add(DocumentSet(docRequestIds = docRequestIds))
        }

        setDeviceRequestInfo(
            DeviceRequestInfo.fromValues(
                useCases = listOf(
                    UseCase(
                        mandatory = true,
                        documentSets = documentSets,
                        purposeHints = emptyMap()
                    )
                )
            )
        )

        readerAuthKey?.let {
            addReaderAuthAll(readerAuthKey)
        }
    }

    suspend fun processVerifiedPresentations(
        verifiedPresentation: List<VerifiedPresentation>,
        issuerTrustManager: TrustManagerInterface,
        atTime: Instant = Clock.System.now()
    ): Result {
        val responseDocuments = mutableListOf<DocumentQueryResult>()
        for (verifiedPresentation in verifiedPresentation) {
            val trustResult = issuerTrustManager.verify(
                chain = verifiedPresentation.documentSignerCertChain!!.certificates,
                atTime = atTime
            )
            when (verifiedPresentation) {
                is JsonVerifiedPresentation -> {
                    var sdJwtVcRequest: SdJwtVcRequest? = null
                    findSdJwtVcRequestLoop@ for (documentQuery in documentQueries) {
                        for (request in documentQuery.getRequests()) {
                            if (request is SdJwtVcRequest && request.vct == verifiedPresentation.vct) {
                                sdJwtVcRequest = request
                                break@findSdJwtVcRequestLoop
                            }
                        }
                    }
                    if (sdJwtVcRequest != null) {
                        responseDocuments.add(
                            sdJwtVcRequest.getResult(
                                verifiedPresentation,
                                atTime,
                                trustResult,
                            )
                        )
                    } else {
                        Logger.w(TAG, "No SD-JWT VC request found for document type ${verifiedPresentation.vct}")
                    }
                }
                is MdocVerifiedPresentation -> {
                    var isoMdocRequest: IsoMdocRequest? = null
                    findIsoMdocRequestLoop@ for (documentQuery in documentQueries) {
                        for (request in documentQuery.getRequests()) {
                            if (request is IsoMdocRequest && request.docType == verifiedPresentation.docType) {
                                isoMdocRequest = request
                                break@findIsoMdocRequestLoop
                            }
                        }
                    }
                    if (isoMdocRequest != null) {
                        responseDocuments.add(
                            isoMdocRequest.getResult(
                                verifiedPresentation,
                                atTime,
                                trustResult,
                            )
                        )
                    } else {
                        Logger.w(TAG, "No ISO mdoc request found for document type ${verifiedPresentation.docType}")
                    }
                }
            }
        }

        return Result(
            query = this,
            documents = responseDocuments
        )
    }

    companion object
}
