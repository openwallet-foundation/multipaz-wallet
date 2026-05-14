package org.multipaz.wallet.client

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.mdoc.issuersigned.IssuerSignedItem
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
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.trustmanagement.TrustResult
import org.multipaz.util.Logger
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "AgeOverQuery"

data class AgeOverQuery(
    val ageOver: Int
): ReaderQuery(
    id = "agequery_$ageOver",
    displayName = "Age over $ageOver"
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
                    "portrait" to intentToRetain,
                    "age_over_${ageOver.toDoubleDigits()}" to intentToRetain
                )
            ),
            docRequestInfo = DocRequestInfo(
                alternativeDataElements = listOf(AlternativeDataElementSet(
                    requestedElement = ElementReference(
                        namespace = DrivingLicense.MDL_NAMESPACE,
                        dataElement = "age_over_18",
                    ),
                    alternativeElementSets = listOf(
                        listOf(ElementReference(
                            namespace = DrivingLicense.MDL_NAMESPACE,
                            dataElement = "age_in_years",
                        )),
                        listOf(ElementReference(
                            namespace = DrivingLicense.MDL_NAMESPACE,
                            dataElement = "birth_date",
                        ))
                    )
                ))
            ),
            readerKey = readerAuthKey
        )
        addDocRequest(
            docType = PhotoID.PHOTO_ID_DOCTYPE,
            nameSpaces = mapOf(
                PhotoID.ISO_23220_2_NAMESPACE to mapOf(
                    "portrait" to intentToRetain,
                    "age_over_${ageOver.toDoubleDigits()}" to intentToRetain
                )
            ),
            docRequestInfo = null,
            readerKey = readerAuthKey
        )
        addDocRequest(
            docType = EUPersonalID.EUPID_DOCTYPE,
            nameSpaces = mapOf(
                EUPersonalID.EUPID_NAMESPACE to mapOf(
                    "portrait" to intentToRetain,
                    "age_over_${ageOver.toDoubleDigits()}" to intentToRetain
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
                    val dataElements = document.issuerNamespaces.data[DrivingLicense.MDL_NAMESPACE]
                        ?: throw IllegalStateException("${DrivingLicense.MDL_NAMESPACE} namespace not found")
                    val portrait = dataElements["portrait"]?.dataElementValue?.asBstr
                        ?: throw IllegalStateException("Portrait not found")
                    val isAgeOver = processAgeOver(
                        dataElements = dataElements,
                        atTime = atTime,
                        targetAge = ageOver
                    )
                    return Result(
                        query = this,
                        trustResult = trustResult,
                        isAgeOver = isAgeOver,
                        portrait = ByteString(portrait)
                    )
                }
                PhotoID.PHOTO_ID_DOCTYPE -> {
                    val dataElements = document.issuerNamespaces.data[PhotoID.ISO_23220_2_NAMESPACE]
                        ?: throw IllegalStateException("${PhotoID.ISO_23220_2_NAMESPACE} namespace not found")
                    val portrait = dataElements["portrait"]?.dataElementValue?.asBstr
                        ?: throw IllegalStateException("Portrait not found")
                    val isAgeOver = processAgeOver(
                        dataElements = dataElements,
                        atTime = atTime,
                        targetAge = ageOver
                    )
                    return Result(
                        query = this,
                        trustResult = trustResult,
                        isAgeOver = isAgeOver,
                        portrait = ByteString(portrait)
                    )
                }
                EUPersonalID.EUPID_DOCTYPE -> {
                    val dataElements = document.issuerNamespaces.data[EUPersonalID.EUPID_NAMESPACE]
                        ?: throw IllegalStateException("${EUPersonalID.EUPID_NAMESPACE} namespace not found")
                    val portrait = dataElements["portrait"]?.dataElementValue?.asBstr
                        ?: throw IllegalStateException("Portrait not found")
                    val isAgeOver = processAgeOver(
                        dataElements = dataElements,
                        atTime = atTime,
                        targetAge = ageOver
                    )
                    return Result(
                        query = this,
                        trustResult = trustResult,
                        isAgeOver = isAgeOver,
                        portrait = ByteString(portrait)
                    )
                }
                else -> {
                    Logger.w(TAG, "Unexpected document type ${document.docType}")
                }
            }
        }
        throw IllegalStateException("Did not find any known documents in the response")
    }

    data class Result(
        val query: AgeOverQuery,
        val trustResult: TrustResult,
        val isAgeOver: Boolean,
        val portrait: ByteString,
    )
}

private fun Int.toDoubleDigits(): String {
    require(this >= 0)
    if (this < 10) {
        return "0" + toString()
    }
    return toString()
}

private fun processAgeOver(
    dataElements:  Map<String, IssuerSignedItem>,
    atTime: Instant,
    targetAge: Int,
    ageOverDataElementName: String = "age_over_${targetAge.toDoubleDigits()}",
    ageInYearsDataElementName: String = "age_in_years",
    birthDateDataElementName: String = "birth_date",
): Boolean {
    val isAgeOver = dataElements[ageOverDataElementName]?.dataElementValue?.asBoolean
        ?: dataElements[ageInYearsDataElementName]?.dataElementValue?.asNumber?.let { ageInYears ->
            ageInYears >= targetAge
        }
        ?: dataElements[birthDateDataElementName]?.dataElementValue?.let { birthDateDataElement ->
            // Handle PhotoID using a map here
            val birthDate = if (birthDateDataElement is CborMap) {
                birthDateDataElement["birth_date"].asDateString
            } else {
                birthDateDataElement.asDateString
            }
            val today = atTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
            birthDate.yearsUntil(today) >= targetAge
        }
        ?: throw IllegalStateException(
            "None of ${ageOverDataElementName}, ${ageInYearsDataElementName}, and $birthDateDataElementName " +
                    "data elements found"
        )
    return isAgeOver
}
