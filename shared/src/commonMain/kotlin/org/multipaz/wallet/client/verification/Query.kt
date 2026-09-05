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

/**
 * Base class for queries requesting digital credentials from a holder.
 *
 * A [Query] encapsulates the high-level verification requirement consisting of one or more [documentQueries].
 * It provides methods to generate request payloads for different verification transport protocols—such as the
 * W3C Digital Credentials API ([generateDcRequest]) and ISO/IEC 18013-5 proximity engagement ([generateDeviceRequest])—
 * and to process verified presentations returned by a holder ([processVerifiedPresentations]).
 *
 * @property documentQueries The list of [DocumentQuery] requirements that must be satisfied.
 */
@CborSerializable
sealed class Query(
    open val documentQueries: List<DocumentQuery>
) {
    /**
     * Generates a request for the W3C Digital Credentials API (DC API).
     *
     * Constructs a pair of CBOR [DataItem]s: the first is an encoded [DeviceRequest] formatted for DC API
     * presentation, and the second is the `encryptionInfo` structure containing the verifier's ephemeral public key
     * and nonce for response encryption.
     *
     * @param nonce Nonce supplied by the caller/verifier to ensure freshness.
     * @param origin The origin URI of the calling application or web page.
     * @param responseEncryptionKey Public key used by the holder to encrypt the returned credentials.
     * @param readerAuthKey Optional reader authentication key with an X.509 certificate chain.
     * @param intentToRetain Whether the verifier intends to retain the received data elements.
     * @param issuerIdentifiers Optional list of trusted issuer identifier byte strings to restrict accepted issuers.
     * @return A [Pair] containing the encoded device request [DataItem] and the encryption info [DataItem].
     * @throws IllegalArgumentException If [documentQueries] does not contain exactly one document query.
     */
    @Throws(IllegalArgumentException::class)
    suspend fun generateDcRequest(
        nonce: ByteString,
        origin: String,
        responseEncryptionKey: EcPublicKey,
        readerAuthKey: AsymmetricKey.X509Compatible?,
        intentToRetain: Boolean,
        issuerIdentifiers: List<ByteString> = emptyList()
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
            intentToRetain = intentToRetain,
            issuerIdentifiers = issuerIdentifiers
        )

        return Pair(deviceRequest.toDataItem(), encryptionInfo)
    }

    /**
     * Generates an ISO/IEC 18013-5 [DeviceRequest] for proximity verification or custom session transcripts.
     *
     * @param deviceEngagement Optional CBOR [DataItem] representing holder device engagement data.
     * @param sessionTranscript The CBOR [DataItem] encoding the session transcript.
     * @param readerAuthKey Optional reader authentication key with an X.509 certificate chain.
     * @param intentToRetain Whether the verifier intends to retain the received data elements.
     * @param issuerIdentifiers Optional list of trusted issuer identifier byte strings to restrict accepted issuers.
     * @return The constructed [DeviceRequest].
     * @throws IllegalArgumentException If [documentQueries] does not contain exactly one document query.
     */
    @Throws(IllegalArgumentException::class)
    suspend fun generateDeviceRequest(
        deviceEngagement: DataItem?,
        sessionTranscript: DataItem,
        readerAuthKey: AsymmetricKey.X509Compatible?,
        intentToRetain: Boolean,
        issuerIdentifiers: List<ByteString> = emptyList()
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
                },
                issuerIdentifiers = issuerIdentifiers
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

    /**
     * Processes a list of verified presentations returned by the holder and evaluates issuer trust.
     *
     * For each [VerifiedPresentation], verifies the document signer certificate chain using [issuerTrustManager]
     * at [atTime], matches the presentation against the corresponding [IsoMdocRequest] or [SdJwtVcRequest],
     * and constructs the resulting [DocumentQueryResult].
     *
     * @param verifiedPresentation The list of verified credential presentations received from the holder.
     * @param issuerTrustManager Trust manager used to validate issuer certificates.
     * @param atTime The point in time at which certificate validity and revocation should be evaluated.
     * @return A [Result] containing this query and the list of processed [DocumentQueryResult]s.
     */
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

    /**
     * Companion object for CBOR serialization and deserialization of [Query] instances.
     */
    companion object
}
