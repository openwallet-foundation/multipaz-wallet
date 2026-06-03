package org.multipaz.wallet.client.verification

import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Tstr
import org.multipaz.documenttype.knowntypes.Aadhaar
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.IDPass
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.util.fromBase64Url
import org.multipaz.verification.MdocVerifiedPresentation

data class IdentificationDocumentQuery(
    val requestStreetAddress: Boolean
): DocumentQuery() {

    override fun getRequests(): List<Request> = listOf(
        // Mobile Driving License
        IsoMdocRequest(
            docType = DrivingLicense.MDL_DOCTYPE,
            namespaces = buildMap {
                put(DrivingLicense.MDL_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(dataElementName = "portrait"),
                    IsoMdocDataElementRequest(dataElementName = "given_name"),
                    IsoMdocDataElementRequest(dataElementName = "family_name"),
                    IsoMdocDataElementRequest(dataElementName = "birth_date"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_authority"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_country"),
                ) + if (requestStreetAddress) {
                    listOf(
                        IsoMdocDataElementRequest(
                            dataElementName = "resident_address"
                        )
                    )
                } else {
                    emptyList()
                })
            },
            getResult = { verifiedPresentation, atTime, trustResult ->
                verifiedPresentation as MdocVerifiedPresentation
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(DrivingLicense.MDL_NAMESPACE)
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.MOBILE_DRIVING_LICENSE,
                    issuingAuthority = ns["issuing_authority"]!!.value.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
                    name = ns["given_name"]!!.value.asTstr + " " +
                            ns["family_name"]!!.value.asTstr,
                    birthDate = ns["birth_date"]!!.value.asDateString,
                    streetAddress = ns["resident_address"]?.value?.asTstr,
                )
            }
        ),

        // PhotoID
        IsoMdocRequest(
            docType = PhotoID.PHOTO_ID_DOCTYPE,
            namespaces = buildMap {
                put(PhotoID.ISO_23220_2_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(
                        dataElementName = "portrait",
                    ),
                    IsoMdocDataElementRequest(
                        dataElementName = "given_name",
                        alternativeDataElements = listOf("given_name_unicode", "given_name_latin1"),
                    ),
                    IsoMdocDataElementRequest(
                        dataElementName = "family_name",
                        alternativeDataElements = listOf("family_name_unicode", "family_name_latin1"),
                    ),
                    IsoMdocDataElementRequest(
                        dataElementName = "birth_date",
                    ),
                    IsoMdocDataElementRequest(
                        dataElementName = "issuing_authority",
                        alternativeDataElements = listOf("issuing_authority_unicode", "issuing_authority_latin1"),
                    ),
                    IsoMdocDataElementRequest(
                        dataElementName = "issuing_country",
                        alternativeDataElements = listOf("issuing_country_unicode", "issuing_country_latin1"),
                    ),
                ) + if (requestStreetAddress) {
                    listOf(
                        IsoMdocDataElementRequest(
                            dataElementName = "resident_address"
                        )
                    )
                } else {
                    emptyList()
                })
            },
            getResult = { verifiedPresentation, atTime, trustResult ->
                verifiedPresentation as MdocVerifiedPresentation
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(PhotoID.ISO_23220_2_NAMESPACE)
                val familyName = ns["family_name"]?.value?.asTstr
                    ?: ns["family_name_unicode"]?.value?.asTstr
                    ?: ns["family_name_latin1"]?.value?.asTstr
                    ?: throw IllegalStateException("No family_name found")
                val givenName = ns["given_name"]?.value?.asTstr
                    ?: ns["given_name_unicode"]?.value?.asTstr
                    ?: ns["given_name_latin1"]?.value?.asTstr
                    ?: throw IllegalStateException("No given_name found")
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.PHOTO_ID,
                    issuingAuthority = ns["issuing_authority"]?.value?.asTstr
                        ?: ns["issuing_authority_unicode"]?.value?.asTstr
                        ?: ns["issuing_authority_latin1"]?.value?.asTstr
                        ?: throw IllegalStateException("No issuing_authority found"),
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
                    name = "$givenName $familyName",
                    birthDate = ns["birth_date"]!!.value.asMap[Tstr("birth_date")]!!.asDateString,
                    streetAddress = ns["resident_address"]?.value?.asTstr,
                )
            }
        ),

        // EU PID (ISO mdoc format)
        IsoMdocRequest(
            docType = EUPersonalID.EUPID_DOCTYPE,
            namespaces = buildMap {
                put(EUPersonalID.EUPID_NAMESPACE, listOf(
                    // NOTE: `portrait` is not mandatory for EU PID but practically useless without it.
                    IsoMdocDataElementRequest(dataElementName = "portrait"),
                    IsoMdocDataElementRequest(dataElementName = "given_name"),
                    IsoMdocDataElementRequest(dataElementName = "family_name"),
                    IsoMdocDataElementRequest(dataElementName = "birth_date"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_authority"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_country"),
                ) + if (requestStreetAddress) {
                    listOf(
                        IsoMdocDataElementRequest(
                            dataElementName = "resident_address"
                        )
                    )
                } else {
                    emptyList()
                })
            },
            getResult = { verifiedPresentation, atTime, trustResult ->
                verifiedPresentation as MdocVerifiedPresentation
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(EUPersonalID.EUPID_NAMESPACE)
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.EU_PID,
                    issuingAuthority = ns["issuing_authority"]!!.value.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
                    name = ns["given_name"]!!.value.asTstr + " " +
                            ns["family_name"]!!.value.asTstr,
                    birthDate = ns["birth_date"]!!.value.asDateString,
                    streetAddress = ns["resident_address"]?.value?.asTstr,
                )
            }
        ),
        
        // EU PID (SD-JWT VC format)
        SdJwtVcRequest(
            vct = EUPersonalID.EUPID_VCT,
            claims = listOf(
                SdJwtVcClaimRequest(buildJsonArray { add("picture") }),
                SdJwtVcClaimRequest(buildJsonArray { add("given_name") }),
                SdJwtVcClaimRequest(buildJsonArray { add("family_name") }),
                SdJwtVcClaimRequest(buildJsonArray { add("birthdate") }),
                SdJwtVcClaimRequest(buildJsonArray { add("issuing_authority") }),
                SdJwtVcClaimRequest(buildJsonArray { add("issuing_country") }),
            ) + if (requestStreetAddress) {
                listOf(
                    SdJwtVcClaimRequest(buildJsonArray { add("address"); add("formatted") })
                )
            } else {
                emptyList()
            },
            getResult = { verifiedPresentation, atTime, trustResult ->
                val claims = verifiedPresentation.issuerSignedClaims.associate {
                    it.claimPath.first().jsonPrimitive.content to it
                }
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.EU_PID,
                    issuingAuthority = claims["issuing_authority"]!!.value.jsonPrimitive.content,
                    issuingCountryCode = claims["issuing_country"]!!.value.jsonPrimitive.content,
                    revocationStatus = null, // TODO sdJwtKb.sdJwt.revocationStatus,
                    portrait = ByteString(claims["picture"]!!.value.jsonPrimitive.content.fromBase64Url()),
                    name = claims["given_name"]!!.value.jsonPrimitive.content + " " +
                            claims["family_name"]!!.value.jsonPrimitive.content,
                    birthDate = LocalDate.parse(claims["birthdate"]!!.value.jsonPrimitive.content),
                    streetAddress = claims["address"]?.value?.jsonObject?.get("formatted")?.jsonPrimitive?.contentOrNull,
                )
            }
        ),

        // Aadhaar
        IsoMdocRequest(
            docType = Aadhaar.AADHAAR_DOCTYPE,
            namespaces = buildMap {
                put(Aadhaar.AADHAAR_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(dataElementName = "ResidentName"),
                    IsoMdocDataElementRequest(dataElementName = "Dob"),
                    IsoMdocDataElementRequest(dataElementName = "ResidentImage"),
                ) + if (requestStreetAddress) {
                    listOf(
                        IsoMdocDataElementRequest(
                            dataElementName = "Address"
                        )
                    )
                } else {
                    emptyList()
                })
            },
            getResult = { verifiedPresentation, atTime, trustResult ->
                verifiedPresentation as MdocVerifiedPresentation
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(Aadhaar.AADHAAR_NAMESPACE)
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.AADHAAR,
                    issuingAuthority = "UIDAI",
                    issuingCountryCode = "IN",
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["ResidentImage"]!!.value.asBstr),
                    name = ns["ResidentName"]!!.value.asTstr,
                    birthDate = ns["Dob"]!!.value.asDateString,
                    streetAddress = ns["Address"]?.value?.asTstr,
                )
            }
        ),

        // Google Wallet ID pass
        IsoMdocRequest(
            docType = IDPass.IDPASS_DOCTYPE,
            namespaces = buildMap {
                put(DrivingLicense.MDL_NAMESPACE, listOf(
                    IsoMdocDataElementRequest(dataElementName = "portrait"),
                    IsoMdocDataElementRequest(dataElementName = "given_name"),
                    IsoMdocDataElementRequest(dataElementName = "family_name"),
                    IsoMdocDataElementRequest(dataElementName = "birth_date"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_authority"),
                    IsoMdocDataElementRequest(dataElementName = "issuing_country"),
                ) + if (requestStreetAddress) {
                    listOf(
                        IsoMdocDataElementRequest(
                            dataElementName = "resident_address"
                        )
                    )
                } else {
                    emptyList()
                })
            },
            getResult = { verifiedPresentation, atTime, trustResult ->
                verifiedPresentation as MdocVerifiedPresentation
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(DrivingLicense.MDL_NAMESPACE)
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.GOOGLE_WALLET_IDPASS,
                    issuingAuthority = ns["issuing_authority"]!!.value.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null,  // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
                    name = ns["given_name"]!!.value.asTstr + " " +
                            ns["family_name"]!!.value.asTstr,
                    birthDate = ns["birth_date"]!!.value.asDateString,
                    streetAddress = ns["resident_address"]?.value?.asTstr,
                )
            }
        ),
    )

}

