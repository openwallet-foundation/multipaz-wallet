package org.multipaz.wallet.client

import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.mdoc.request.AlternativeDataElementSet
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.request.DeviceRequestInfo
import org.multipaz.mdoc.request.DocRequestInfo
import org.multipaz.mdoc.request.DocumentSet
import org.multipaz.mdoc.request.ElementReference
import org.multipaz.mdoc.request.UseCase
import org.multipaz.mdoc.request.buildDeviceRequest
import org.multipaz.mdoc.response.DeviceResponse
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.trustmanagement.TrustResult
import org.multipaz.util.Logger
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "IdentificationQuery"

data object IdentificationQuery: ReaderQuery(
    id = "identification",
    displayName = "Identification"
) {
    override suspend fun generateDeviceRequest(
        sessionTranscript: DataItem,
        readerAuthKey: AsymmetricKey.X509Compatible?,
        intentToRetain: Boolean
    ): DeviceRequest = buildDeviceRequest(
        sessionTranscript = sessionTranscript,
        deviceRequestInfo = DeviceRequestInfo(
            useCases = listOf(
                UseCase(
                    mandatory = true,
                    documentSets = listOf(
                        DocumentSet(docRequestIds = listOf(0)),
                        DocumentSet(docRequestIds = listOf(1)),
                        DocumentSet(docRequestIds = listOf(2)),
                    ),
                    purposeHints = emptyMap()
                )
            )
        )
    ) {
        addDocRequest(
            docType = DrivingLicense.MDL_DOCTYPE,
            nameSpaces = mapOf(
                DrivingLicense.MDL_NAMESPACE to mapOf(
                    "family_name" to intentToRetain,
                    "given_name" to intentToRetain,
                    "birth_date" to intentToRetain,
                    "portrait" to intentToRetain,
                )
            ),
            docRequestInfo = null,
            readerKey = readerAuthKey
        )
        addDocRequest(
            docType = PhotoID.PHOTO_ID_DOCTYPE,
            nameSpaces = mapOf(
                PhotoID.ISO_23220_2_NAMESPACE to mapOf(
                    "family_name" to intentToRetain,
                    "given_name" to intentToRetain,
                    "birth_date" to intentToRetain,
                    "portrait" to intentToRetain,
                )
            ),
            docRequestInfo = DocRequestInfo(
                alternativeDataElements = listOf(
                    AlternativeDataElementSet(
                        requestedElement = ElementReference(
                            namespace = PhotoID.ISO_23220_2_NAMESPACE,
                            dataElement = "family_name",
                        ),
                        alternativeElementSets = listOf(
                            listOf(ElementReference(
                                namespace = PhotoID.ISO_23220_2_NAMESPACE,
                                dataElement = "family_name_unicode",
                            )),
                            listOf(ElementReference(
                                namespace = PhotoID.ISO_23220_2_NAMESPACE,
                                dataElement = "family_name_latin1",
                            ))
                        )
                    ),
                    AlternativeDataElementSet(
                        requestedElement = ElementReference(
                            namespace = PhotoID.ISO_23220_2_NAMESPACE,
                            dataElement = "given_name",
                        ),
                        alternativeElementSets = listOf(
                            listOf(ElementReference(
                                namespace = PhotoID.ISO_23220_2_NAMESPACE,
                                dataElement = "given_name_unicode",
                            )),
                            listOf(ElementReference(
                                namespace = PhotoID.ISO_23220_2_NAMESPACE,
                                dataElement = "given_name_latin1",
                            ))
                        )
                    ))
            ),
            readerKey = readerAuthKey
        )
        addDocRequest(
            docType = EUPersonalID.EUPID_DOCTYPE,
            nameSpaces = mapOf(
                EUPersonalID.EUPID_NAMESPACE to mapOf(
                    "family_name" to intentToRetain,
                    "given_name" to intentToRetain,
                    "birth_date" to intentToRetain,
                    // NOTE: `portrait` is not mandatory for EU PID but practically useless without it.
                    "portrait" to intentToRetain
                )
            ),
            docRequestInfo = null,
            readerKey = readerAuthKey
        )
    }

    suspend fun processResponse(
        deviceResponse: DeviceResponse,
        sessionTranscript: DataItem,
        eReaderKey: AsymmetricKey,
        issuerTrustManager: TrustManagerInterface,
        atTime: Instant = Clock.System.now()
    ): Result {
        Logger.iCbor(TAG, "deviceResponse", deviceResponse.toDataItem())
        deviceResponse.verify(
            sessionTranscript = sessionTranscript,
            eReaderKey = eReaderKey,
            atTime = atTime
        )
        for (document in deviceResponse.documents) {
            val trustResult = issuerTrustManager.verify(
                chain = document.issuerCertChain.certificates,
                atTime = atTime
            )
            when (document.docType) {
                DrivingLicense.MDL_DOCTYPE -> {
                    val ns = document.issuerNamespaces.data[DrivingLicense.MDL_NAMESPACE]!!
                    return Result(
                        query = this,
                        trustResult = trustResult,
                        portrait = ByteString(ns.get("portrait")!!.dataElementValue.asBstr),
                        familyName = ns.get("family_name")!!.dataElementValue.asTstr,
                        givenName = ns.get("given_name")!!.dataElementValue.asTstr,
                        birthDate = ns.get("birth_date")!!.dataElementValue.asDateString,
                    )
                }
                PhotoID.PHOTO_ID_DOCTYPE -> {
                    val ns = document.issuerNamespaces.data[PhotoID.ISO_23220_2_NAMESPACE]!!
                    return Result(
                        query = this,
                        trustResult = trustResult,
                        portrait = ByteString(ns.get("portrait")!!.dataElementValue.asBstr),
                        familyName = ns.get("family_name")?.dataElementValue?.asTstr
                            ?: ns.get("family_name_unicode")?.dataElementValue?.asTstr
                            ?: ns.get("family_name_latin1")?.dataElementValue?.asTstr
                            ?: throw IllegalStateException("No family_name found"),
                        givenName = ns.get("given_name")?.dataElementValue?.asTstr
                            ?: ns.get("given_name_unicode")?.dataElementValue?.asTstr
                            ?: ns.get("given_name_latin1")?.dataElementValue?.asTstr
                            ?: throw IllegalStateException("No given_name found"),
                        birthDate = ns.get("birth_date")!!.dataElementValue.asMap[Tstr("birth_date")]!!.asDateString,
                    )
                }
                EUPersonalID.EUPID_DOCTYPE -> {
                    val ns = document.issuerNamespaces.data[EUPersonalID.EUPID_NAMESPACE]!!
                    return Result(
                        query = this,
                        trustResult = trustResult,
                        portrait = ByteString(ns.get("portrait")!!.dataElementValue.asBstr),
                        familyName = ns.get("family_name")!!.dataElementValue.asTstr,
                        givenName = ns.get("given_name")!!.dataElementValue.asTstr,
                        birthDate = ns.get("birth_date")!!.dataElementValue.asDateString,
                    )
                }
                else -> {
                    Logger.w(TAG, "Unexpected document type ${document.docType}")
                }
            }
        }
        throw IllegalStateException("Did not find any known documents in the response")
    }

    // TODO: add address
    data class Result(
        val query: IdentificationQuery,
        val trustResult: TrustResult,

        val portrait: ByteString,
        val familyName: String,
        val givenName: String,
        val birthDate: LocalDate,
    )
}

// TODO: add to Multipaz
val DataItem.asDateStringWithDateTimeSupport: LocalDate
    get() {
        require(this is Tagged)
        require(this.taggedItem is Tstr)
        when (this.tagNumber) {
            Tagged.DATE_TIME_STRING -> return LocalDate.parse((this.taggedItem as Tstr).value.substring(0, 10))
            Tagged.FULL_DATE_STRING -> return LocalDate.parse((this.taggedItem as Tstr).value)
            else -> throw IllegalStateException("Unexpected tag number ${this.tagNumber}")
        }
    }
