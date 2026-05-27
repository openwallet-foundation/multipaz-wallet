package org.multipaz.wallet.client.verification

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.CborMap
import org.multipaz.documenttype.knowntypes.Aadhaar
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.IDPass
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.mdoc.issuersigned.IssuerSignedItem
import org.multipaz.util.fromBase64Url
import kotlin.time.Instant

data class AgeOverDocumentQuery(
    val ageOver: Int
): DocumentQuery() {

    override fun getRequests(): List<Request> = listOf(
        // Mobile Driving License
        IsoMdocRequest(
            docType = DrivingLicense.MDL_DOCTYPE,
            namespaces = buildMap {
                put(DrivingLicense.MDL_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(dataElementName = "portrait"),
                    IsoMdocDataElementRequest(
                        dataElementName = "age_over_${ageOver.toDoubleDigits()}",
                        alternativeDataElements = listOf("age_in_years", "birth_date")
                    ),
                    IsoMdocDataElementRequest(dataElementName = "issuing_authority"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_country"),
                ))
            },
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[DrivingLicense.MDL_NAMESPACE]!!
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.MOBILE_DRIVING_LICENSE,
                    issuingAuthority = ns["issuing_authority"]!!.dataElementValue.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.dataElementValue.asTstr,
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["portrait"]!!.dataElementValue.asBstr),
                    isAgeOver = processAgeOver(dataElements = ns, atTime = atTime, targetAge = ageOver),
                )
            }
        ),

        // PhotoID
        IsoMdocRequest(
            docType = PhotoID.PHOTO_ID_DOCTYPE,
            namespaces = buildMap {
                put(PhotoID.ISO_23220_2_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(dataElementName = "portrait"),
                    IsoMdocDataElementRequest(
                        dataElementName = "age_over_${ageOver.toDoubleDigits()}",
                        alternativeDataElements = listOf("age_in_years", "birth_date")
                    ),
                    IsoMdocDataElementRequest(
                        dataElementName = "issuing_authority",
                        alternativeDataElements = listOf("issuing_authority_unicode", "issuing_authority_latin1"),
                    ),
                    IsoMdocDataElementRequest(
                        dataElementName = "issuing_country",
                        alternativeDataElements = listOf("issuing_country_unicode", "issuing_country_latin1"),
                    ),
                ))
            },
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[PhotoID.ISO_23220_2_NAMESPACE]!!
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.PHOTO_ID,
                    issuingAuthority = ns["issuing_authority"]?.dataElementValue?.asTstr
                        ?: ns["issuing_authority_unicode"]?.dataElementValue?.asTstr
                        ?: ns["issuing_authority_latin1"]?.dataElementValue?.asTstr
                        ?: throw IllegalStateException("No issuing_authority found"),
                    issuingCountryCode = ns["issuing_country"]!!.dataElementValue.asTstr,
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["portrait"]!!.dataElementValue.asBstr),
                    isAgeOver = processAgeOver(dataElements = ns, atTime = atTime, targetAge = ageOver),
                )
            }
        ),

        // EU PID (ISO mdoc format)
        IsoMdocRequest(
            docType = EUPersonalID.EUPID_DOCTYPE,
            namespaces = buildMap {
                put(EUPersonalID.EUPID_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(dataElementName = "portrait"),
                    IsoMdocDataElementRequest(
                        dataElementName = "age_over_${ageOver.toDoubleDigits()}",
                        alternativeDataElements = listOf("age_in_years", "birth_date")
                    ),
                    IsoMdocDataElementRequest(dataElementName = "issuing_authority"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_country"),
                ))
            },
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[EUPersonalID.EUPID_NAMESPACE]!!
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.EU_PID,
                    issuingAuthority = ns["issuing_authority"]!!.dataElementValue.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.dataElementValue.asTstr,
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["portrait"]!!.dataElementValue.asBstr),
                    isAgeOver = processAgeOver(dataElements = ns, atTime = atTime, targetAge = ageOver),
                )
            }
        ),

        // EU PID (SD-JWT VC format)
        SdJwtVcRequest(
            vct = EUPersonalID.EUPID_VCT,
            claims = listOf(
                SdJwtVcClaimRequest(buildJsonArray { add("picture") }),
                SdJwtVcClaimRequest(
                    claimPath = buildJsonArray { add("age_equal_or_over"); add(ageOver.toDoubleDigits()) },
                    alternativeClaims = listOf(
                        buildJsonArray { add("age_in_years") },
                        buildJsonArray { add("birthdate") }
                    )
                ),
                SdJwtVcClaimRequest(buildJsonArray { add("issuing_authority") }),
                SdJwtVcClaimRequest(buildJsonArray { add("issuing_country") }),
            ),
            getResult = { sdJwtKb, processedClaims, atTime, trustResult ->
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.EU_PID,
                    issuingAuthority = processedClaims["issuing_authority"]!!.jsonPrimitive.content,
                    issuingCountryCode = processedClaims["issuing_country"]!!.jsonPrimitive.content,
                    revocationStatus = sdJwtKb.sdJwt.revocationStatus,
                    portrait = ByteString(processedClaims["picture"]!!.jsonPrimitive.content.fromBase64Url()),
                    isAgeOver = processAgeOverSdJwtVc(
                        claims = processedClaims,
                        atTime = atTime,
                        targetAge = ageOver,
                    ),
                )
            }
        ),

        // Aadhaar
        IsoMdocRequest(
            docType = Aadhaar.AADHAAR_DOCTYPE,
            namespaces = buildMap {
                put(Aadhaar.AADHAAR_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(dataElementName = "ResidentImage"),
                    IsoMdocDataElementRequest(
                        dataElementName = "AgeAbove${ageOver.toDoubleDigits()}",
                        alternativeDataElements = listOf("Dob")
                    ),
                ))
            },
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[Aadhaar.AADHAAR_NAMESPACE]!!
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.AADHAAR,
                    issuingAuthority = "UIDAI",
                    issuingCountryCode = "IN",
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["ResidentImage"]!!.dataElementValue.asBstr),
                    isAgeOver = processAgeOver(
                        dataElements = ns,
                        atTime = atTime,
                        targetAge = ageOver,
                        ageOverDataElementName = "AgeAbove${ageOver.toDoubleDigits()}",
                        ageInYearsDataElementName = null,
                        birthDateDataElementName = "Dob",
                    ),
                )
            }
        ),

        // Google Wallet ID pass
        IsoMdocRequest(
            docType = IDPass.IDPASS_DOCTYPE,
            namespaces = buildMap {
                put(DrivingLicense.MDL_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(dataElementName = "portrait"),
                    IsoMdocDataElementRequest(
                        dataElementName = "age_over_${ageOver.toDoubleDigits()}",
                        alternativeDataElements = listOf("age_in_years", "birth_date")
                    ),
                    IsoMdocDataElementRequest(dataElementName = "issuing_authority"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_country"),
                ))
            },
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[DrivingLicense.MDL_NAMESPACE]!!
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.GOOGLE_WALLET_IDPASS,
                    issuingAuthority = ns["issuing_authority"]!!.dataElementValue.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.dataElementValue.asTstr,
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["portrait"]!!.dataElementValue.asBstr),
                    isAgeOver = processAgeOver(dataElements = ns, atTime = atTime, targetAge = ageOver),
                )
            }
        ),

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
    dataElements: Map<String, IssuerSignedItem>,
    atTime: Instant,
    targetAge: Int,
    ageOverDataElementName: String = "age_over_${targetAge.toDoubleDigits()}",
    ageInYearsDataElementName: String? = "age_in_years",
    birthDateDataElementName: String? = "birth_date",
): Boolean {
    val isAgeOver = dataElements[ageOverDataElementName]?.dataElementValue?.asBoolean
        ?: ageInYearsDataElementName?.let {
            dataElements[ageInYearsDataElementName]?.dataElementValue?.asNumber?.let { ageInYears -> ageInYears >= targetAge }
        }
        ?: birthDateDataElementName?.let {
            dataElements[birthDateDataElementName]?.dataElementValue?.let { birthDateDataElement ->
                // Handle PhotoID using a map here
                val birthDate = if (birthDateDataElement is CborMap) {
                    birthDateDataElement["birth_date"].asDateString
                } else {
                    birthDateDataElement.asDateString
                }
                val today = atTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
                birthDate.yearsUntil(today) >= targetAge
            }
        }
        ?: throw IllegalStateException(
            "None of ${ageOverDataElementName}, ${ageInYearsDataElementName}, and $birthDateDataElementName " +
                    "data elements found"
        )
    return isAgeOver
}

private fun processAgeOverSdJwtVc(
    claims: JsonObject,
    atTime: Instant,
    targetAge: Int,
    ageOverClaimName: JsonArray = buildJsonArray { add("age_equal_or_over"); add(targetAge.toDoubleDigits()) },
    ageInYearsClaimName: JsonArray? = buildJsonArray { add("age_in_years") },
    birthDateClaimName: JsonArray? = buildJsonArray { add("birthdate") },
): Boolean {

    val isAgeOver = claims[ageOverClaimName[0].jsonPrimitive.content]?.jsonObject
        ?.get(ageOverClaimName[1].jsonPrimitive.content)
        ?.jsonPrimitive?.booleanOrNull
        ?: ageInYearsClaimName?.let {
            claims[ageInYearsClaimName[0].jsonPrimitive.content]?.jsonPrimitive?.intOrNull?.let { ageInYears ->
                ageInYears >= targetAge
            }
        }
        ?: birthDateClaimName?.let {
            claims[birthDateClaimName[0].jsonPrimitive.content]?.jsonPrimitive?.contentOrNull?.let { birthDateString ->
                val birthDate = LocalDate.parse(birthDateString)
                val today = atTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
                birthDate.yearsUntil(today) >= targetAge
            }
        }
        ?: throw IllegalStateException(
            "None of ${ageOverClaimName}, ${ageInYearsClaimName}, and $birthDateClaimName claims found"
        )
    return isAgeOver
}

