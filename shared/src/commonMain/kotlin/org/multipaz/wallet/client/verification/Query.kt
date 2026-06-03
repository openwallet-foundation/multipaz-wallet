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
    ): JsonObject = buildJsonObject {
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
        val base64DeviceRequest = Cbor.encode(deviceRequest.toDataItem()).toBase64Url()
        val orgIsoMdocData = buildJsonObject {
            put("deviceRequest", base64DeviceRequest)
            put("encryptionInfo", base64EncryptionInfo)
        }
        putJsonArray("requests") {
            addJsonObject {
                put("protocol", "org-iso-mdoc")
                put("data", orgIsoMdocData)
            }
        }
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
            DeviceRequestInfo(
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

    suspend fun processDeviceResponse(
        deviceResponse: DeviceResponse,
        sessionTranscript: DataItem,
        eReaderKey: AsymmetricKey,
        issuerTrustManager: TrustManagerInterface,
        atTime: Instant = Clock.System.now()
    ): Result {
        deviceResponse.verify(
            sessionTranscript = sessionTranscript,
            eReaderKey = eReaderKey,
            atTime = atTime
        )
        val responseDocuments = mutableListOf<DocumentQueryResult>()

        for (document in deviceResponse.documents) {
            val trustResult = issuerTrustManager.verify(
                chain = document.issuerCertChain.certificates,
                atTime = atTime
            )
            var isoMdocRequest: IsoMdocRequest? = null
            findIsoMdocRequestLoop@ for (documentQuery in documentQueries) {
                for (request in documentQuery.getRequests()) {
                    if (request is IsoMdocRequest && request.docType == document.docType) {
                        isoMdocRequest = request
                        break@findIsoMdocRequestLoop
                    }
                }
            }
            if (isoMdocRequest != null) {
                responseDocuments.add(
                    isoMdocRequest.getResult(
                        document,
                        atTime,
                        trustResult,
                    )
                )
            } else {
                Logger.w(TAG, "No ISO mdoc request found for document type ${document.docType}")
            }
        }

        for (otherDocument in deviceResponse.otherDocuments) {
            for (documentQuery in documentQueries) {
                if (otherDocument.docFormat == "sd-jwt+kb") {
                    val sdJwtKbCompactSerialization = otherDocument.data.toByteArray().zlibInflate().decodeToString()
                    val sdJwtKb = SdJwtKb.fromCompactSerialization(sdJwtKbCompactSerialization)
                    val trustResult = issuerTrustManager.verify(
                        chain = sdJwtKb.sdJwt.x5c!!.certificates,
                        atTime = atTime
                    )
                    // This was already verified above in DeviceResponse.verify()...
                    val processedClaims = sdJwtKb.verify(
                        issuerKey = sdJwtKb.sdJwt.x5c!!.certificates.first().ecPublicKey,
                        checkNonce = { nonce -> true },
                        checkAudience = { aud -> true },
                        checkCreationTime = { creationTime -> true },
                    )
                    val vct = processedClaims["vct"]!!.jsonPrimitive.content
                    var sdJwtVcRequest: SdJwtVcRequest? = null
                    findSdJWtVcRequestLoop@ for (request in documentQuery.getRequests()) {
                        if (request is SdJwtVcRequest && request.vct == vct) {
                            sdJwtVcRequest = request
                            break@findSdJWtVcRequestLoop
                        }
                    }
                    if (sdJwtVcRequest != null) {
                        responseDocuments.add(
                            sdJwtVcRequest.getResult(
                                sdJwtKb,
                                processedClaims,
                                atTime,
                                trustResult,
                            )
                        )
                    } else {
                        Logger.w(TAG, "No SD-JWT VC request found for VCT $vct")
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
