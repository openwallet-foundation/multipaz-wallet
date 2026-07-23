package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.verification.MdocVerifiedPresentation

data class DrivingPrivilegesDocumentQuery(
    val unused: Boolean = false
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
                    IsoMdocDataElementRequest(dataElementName = "driving_privileges"),
                ))
            },
            getResult = { verifiedPresentation, atTime, trustResult ->
                verifiedPresentation as MdocVerifiedPresentation
                val ns = verifiedPresentation.issuerSignedClaims.claimsInNamespace(DrivingLicense.MDL_NAMESPACE)
                DrivingPrivilegesDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = DocumentType.MOBILE_DRIVING_LICENSE,
                    issuingAuthority = ns["issuing_authority"]!!.value.asTstr,
                    issuingCountryCode = ns["issuing_country"]!!.value.asTstr,
                    revocationStatus = null, // TODO
                    portrait = ByteString(ns["portrait"]!!.value.asBstr),
                    name = ns["given_name"]!!.value.asTstr + " " +
                            ns["family_name"]!!.value.asTstr,
                    birthDate = ns["birth_date"]!!.value.asDateString,
                    drivingPrivileges = ns["driving_privileges"]!!.value,
                )
            }
        )
    )
}
