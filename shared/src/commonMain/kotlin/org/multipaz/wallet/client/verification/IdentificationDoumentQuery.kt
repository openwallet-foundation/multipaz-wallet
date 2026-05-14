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
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[DrivingLicense.MDL_NAMESPACE]!!
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.MOBILE_DRIVING_LICENSE,
                    issuingAuthority = ns["issuing_authority"]!!.dataElementValue.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.dataElementValue.asTstr,
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["portrait"]!!.dataElementValue.asBstr),
                    name = ns["given_name"]!!.dataElementValue.asTstr + " " +
                            ns["family_name"]!!.dataElementValue.asTstr,
                    birthDate = ns["birth_date"]!!.dataElementValue.asDateString,
                    streetAddress = ns["resident_address"]?.dataElementValue?.asTstr,
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
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[PhotoID.ISO_23220_2_NAMESPACE]!!
                val familyName = ns["family_name"]?.dataElementValue?.asTstr
                    ?: ns["family_name_unicode"]?.dataElementValue?.asTstr
                    ?: ns["family_name_latin1"]?.dataElementValue?.asTstr
                    ?: throw IllegalStateException("No family_name found")
                val givenName = ns["given_name"]?.dataElementValue?.asTstr
                    ?: ns["given_name_unicode"]?.dataElementValue?.asTstr
                    ?: ns["given_name_latin1"]?.dataElementValue?.asTstr
                    ?: throw IllegalStateException("No given_name found")
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.PHOTO_ID,
                    issuingAuthority = ns["issuing_authority"]?.dataElementValue?.asTstr
                        ?: ns["issuing_authority_unicode"]?.dataElementValue?.asTstr
                        ?: ns["issuing_authority_latin1"]?.dataElementValue?.asTstr
                        ?: throw IllegalStateException("No issuing_authority found"),
                    issuingCountryCode = ns["issuing_country"]!!.dataElementValue.asTstr,
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["portrait"]!!.dataElementValue.asBstr),
                    name = "$givenName $familyName",
                    birthDate = ns["birth_date"]!!.dataElementValue.asMap[Tstr("birth_date")]!!.asDateString,
                    streetAddress = ns["resident_address"]?.dataElementValue?.asTstr,
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
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[EUPersonalID.EUPID_NAMESPACE]!!
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.EU_PID,
                    issuingAuthority = ns["issuing_authority"]!!.dataElementValue.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.dataElementValue.asTstr,
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["portrait"]!!.dataElementValue.asBstr),
                    name = ns["given_name"]!!.dataElementValue.asTstr + " " +
                            ns["family_name"]!!.dataElementValue.asTstr,
                    birthDate = ns["birth_date"]!!.dataElementValue.asDateString,
                    streetAddress = ns["resident_address"]?.dataElementValue?.asTstr,
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
            getResult = { sdJwtKb, processedClaims, atTime, trustResult ->
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.EU_PID,
                    issuingAuthority = processedClaims["issuing_authority"]!!.jsonPrimitive.content,
                    issuingCountryCode = processedClaims["issuing_country"]!!.jsonPrimitive.content,
                    revocationStatus = sdJwtKb.sdJwt.revocationStatus,
                    portrait = ByteString(processedClaims["picture"]!!.jsonPrimitive.content.fromBase64Url()),
                    name = processedClaims["given_name"]!!.jsonPrimitive.content + " " +
                            processedClaims["family_name"]!!.jsonPrimitive.content,
                    birthDate = LocalDate.parse(processedClaims["birthdate"]!!.jsonPrimitive.content),
                    streetAddress = processedClaims["address"]?.jsonObject?.get("formatted")?.jsonPrimitive?.contentOrNull,
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
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[Aadhaar.AADHAAR_NAMESPACE]!!
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.AADHAAR,
                    issuingAuthority = "UIDAI",
                    issuingCountryCode = "IN",
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["ResidentImage"]!!.dataElementValue.asBstr),
                    name = ns["ResidentName"]!!.dataElementValue.asTstr,
                    birthDate = ns["Dob"]!!.dataElementValue.asDateString,
                    streetAddress = ns["Address"]?.dataElementValue?.asTstr,
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
            getResult = { document, atTime, trustResult ->
                val ns = document.issuerNamespaces.data[DrivingLicense.MDL_NAMESPACE]!!
                IdentificationDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.GOOGLE_WALLET_IDPASS,
                    issuingAuthority = ns["issuing_authority"]!!.dataElementValue.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.dataElementValue.asTstr,
                    revocationStatus = document.mso.revocationStatus,
                    portrait = ByteString(ns["portrait"]!!.dataElementValue.asBstr),
                    name = ns["given_name"]!!.dataElementValue.asTstr + " " +
                            ns["family_name"]!!.dataElementValue.asTstr,
                    birthDate = ns["birth_date"]!!.dataElementValue.asDateString,
                    streetAddress = ns["resident_address"]?.dataElementValue?.asTstr,
                )
            }
        ),
    )

}

