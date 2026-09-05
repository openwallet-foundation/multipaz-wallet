package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.verification.MdocVerifiedPresentation

/**
 * A [DocumentQuery] requesting driving privileges, driver identity, and portrait from a mobile driving license (mDL).
 *
 * Requests the `org.iso.18013.5.1` namespace elements from an ISO/IEC 18013-5 mdoc, including `driving_privileges`,
 * `portrait`, `given_name`, `family_name`, `birth_date`, `issuing_authority`, and `issuing_country`.
 *
 * @property unused Reserved for future options.
 */
data class DrivingPrivilegesDocumentQuery(
    val unused: Boolean = false
): DocumentQuery() {

    /**
     * Returns the list of format-specific requests for driving privileges verification.
     *
     * @return A list containing the [IsoMdocRequest] for mDL driving privileges.
     */
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
                    revocationStatus = verifiedPresentation.revocationStatus,
                    certificateChain = verifiedPresentation.documentSignerCertChain,
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
