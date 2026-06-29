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
import org.multipaz.claim.JsonClaim
import org.multipaz.claim.MdocClaim
import org.multipaz.documenttype.knowntypes.Aadhaar
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.IDPass
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.util.fromBase64Url
import org.multipaz.verification.MdocVerifiedPresentation
import kotlin.time.Instant

internal fun List<MdocClaim>.claimsInNamespace(namespace: String): Map<String, MdocClaim> {
    val claimsInNamespace = mutableMapOf<String, MdocClaim>()
    for (claim in this) {
        if (claim.namespaceName == namespace) {
            claimsInNamespace[claim.dataElementName] = claim
        }
    }
    return claimsInNamespace
}

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
            getResult = { verifiedPresentation, atTime, trustResult ->
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(DrivingLicense.MDL_NAMESPACE)
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.MOBILE_DRIVING_LICENSE,
                    issuingAuthority = ns["issuing_authority"]!!.value.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null, // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
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
            getResult = { verifiedPresentation, atTime, trustResult ->
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(PhotoID.ISO_23220_2_NAMESPACE)
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.PHOTO_ID,
                    issuingAuthority = ns["issuing_authority"]?.value?.asTstr
                        ?: ns["issuing_authority_unicode"]?.value?.asTstr
                        ?: ns["issuing_authority_latin1"]?.value?.asTstr
                        ?: throw IllegalStateException("No issuing_authority found"),
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
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
            getResult = { verifiedPresentation, atTime, trustResult ->
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(EUPersonalID.EUPID_NAMESPACE)
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.EU_PID,
                    issuingAuthority = ns["issuing_authority"]!!.value.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
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
            getResult = { verifiedPresentation, atTime, trustResult ->
                val claims = verifiedPresentation.issuerSignedClaims.associate {
                    it.claimPath.first().jsonPrimitive.content to it
                }
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.EU_PID,
                    issuingAuthority = claims["issuing_authority"]!!.value.jsonPrimitive.content,
                    issuingCountryCode = claims["issuing_country"]!!.value.jsonPrimitive.content,
                    revocationStatus = null, // TODO sdJwtKb.sdJwt.revocationStatus,
                    portrait = ByteString(claims["picture"]!!.value.jsonPrimitive.content.fromBase64Url()),
                    isAgeOver = processAgeOverSdJwtVc(
                        claims = claims,
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
            getResult = { verifiedPresentation, atTime, trustResult ->
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(Aadhaar.AADHAAR_NAMESPACE)
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.AADHAAR,
                    issuingAuthority = "UIDAI",
                    issuingCountryCode = "IN",
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["ResidentImage"]!!.value.asBstr),
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
            getResult = { verifiedPresentation, atTime, trustResult ->
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(DrivingLicense.MDL_NAMESPACE)
                AgeOverDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.GOOGLE_WALLET_IDPASS,
                    issuingAuthority = ns["issuing_authority"]!!.value.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
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
    dataElements: Map<String, MdocClaim>,
    atTime: Instant,
    targetAge: Int,
    ageOverDataElementName: String = "age_over_${targetAge.toDoubleDigits()}",
    ageInYearsDataElementName: String? = "age_in_years",
    birthDateDataElementName: String? = "birth_date",
): Boolean {
    val isAgeOver = dataElements[ageOverDataElementName]?.value?.asBoolean
        ?: ageInYearsDataElementName?.let {
            dataElements[ageInYearsDataElementName]?.value?.asNumber?.let { ageInYears -> ageInYears >= targetAge }
        }
        ?: birthDateDataElementName?.let {
            dataElements[birthDateDataElementName]?.value?.let { birthDateDataElement ->
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
    claims: Map<String, JsonClaim>,
    atTime: Instant,
    targetAge: Int,
    ageOverClaimName: JsonArray = buildJsonArray { add("age_equal_or_over"); add(targetAge.toDoubleDigits()) },
    ageInYearsClaimName: JsonArray? = buildJsonArray { add("age_in_years") },
    birthDateClaimName: JsonArray? = buildJsonArray { add("birthdate") },
): Boolean {

    val isAgeOver = claims[ageOverClaimName[0].jsonPrimitive.content]?.value?.jsonObject
        ?.get(ageOverClaimName[1].jsonPrimitive.content)
        ?.jsonPrimitive?.booleanOrNull
        ?: ageInYearsClaimName?.let {
            claims[ageInYearsClaimName[0].jsonPrimitive.content]?.value?.jsonPrimitive?.intOrNull?.let { ageInYears ->
                ageInYears >= targetAge
            }
        }
        ?: birthDateClaimName?.let {
            claims[birthDateClaimName[0].jsonPrimitive.content]?.value?.jsonPrimitive?.contentOrNull?.let { birthDateString ->
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

