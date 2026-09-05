package org.multipaz.wallet.client.verification

import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tstr
import org.multipaz.crypto.X509CertChain
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

/**
 * Represents a condition, restriction, or endorsement code for a driving privilege as defined in ISO/IEC 18013-5.
 *
 * @property code The code identifier.
 * @property sign Optional sign or symbol qualifier.
 * @property value Optional value or parameter string.
 */
data class DrivingPrivilegeCode(
    val code: String,
    val sign: String? = null,
    val value: String? = null,
)

/**
 * An authorized vehicle category privilege parsed from an ISO/IEC 18013-5 mobile driving license.
 *
 * @property vehicleCategoryCode Vehicle category code (e.g., `A`, `B`, `C`).
 * @property issueDate Date when this driving privilege was issued, or `null` if not specified.
 * @property expiryDate Date when this driving privilege expires, or `null` if not specified.
 * @property codes Condition, restriction, and endorsement codes applicable to this privilege category.
 */
data class DrivingPrivilege(
    val vehicleCategoryCode: String,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val codes: List<DrivingPrivilegeCode> = emptyList(),
)

/**
 * Result of verifying a [DrivingPrivilegesDocumentQuery].
 *
 * @property trustResult Trust verification result for the document signer certificate chain.
 * @property documentType The well-known [DocumentType] that satisfied the query.
 * @property issuingAuthority The authority that issued the driving license.
 * @property issuingCountryCode The country code of the issuer.
 * @property revocationStatus Revocation status of the document, if available.
 * @property certificateChain Signer certificate chain of the document, if available.
 * @property portrait Portrait image bytes of the driver.
 * @property name Full name of the driver.
 * @property birthDate Birth date of the driver.
 * @property drivingPrivileges Raw CBOR [DataItem] encoding the driving privileges structure from the mDL.
 */
data class DrivingPrivilegesDocumentQueryResult(
    override val trustResult: TrustResult,
    override val documentType: DocumentType,
    override val issuingAuthority: String,
    override val issuingCountryCode: String,
    override val revocationStatus: RevocationStatus?,
    override val certificateChain: X509CertChain? = null,

    val portrait: ByteString,
    val name: String,
    val birthDate: LocalDate,
    val drivingPrivileges: DataItem,
): DocumentQueryResult(trustResult, documentType, issuingAuthority, issuingCountryCode, revocationStatus, certificateChain) {

    /**
     * Lazily parsed list of [DrivingPrivilege] entries representing vehicle categories and restrictions.
     */
    val drivingPrivilegesList: List<DrivingPrivilege> by lazy {
        parseDrivingPrivileges(drivingPrivileges)
    }

    companion object {
        /**
         * Parses a raw CBOR [DataItem] representing an ISO/IEC 18013-5 `driving_privileges` structure.
         *
         * @param dataItem The CBOR [DataItem] containing the driving privileges array.
         * @return A list of parsed [DrivingPrivilege] instances.
         */
        fun parseDrivingPrivileges(dataItem: DataItem): List<DrivingPrivilege> {
            val result = mutableListOf<DrivingPrivilege>()
            try {
                val items = dataItem.asArray
                for (item in items) {
                    val map = item.asMap
                    val categoryCode = map[Tstr("vehicle_category_code")]?.asTstr ?: continue
                    val issueDate = try {
                        map[Tstr("issue_date")]?.asDateString
                    } catch (e: Exception) {
                        null
                    }
                    val expiryDate = try {
                        map[Tstr("expiry_date")]?.asDateString
                    } catch (e: Exception) {
                        null
                    }
                    val codesList = mutableListOf<DrivingPrivilegeCode>()
                    map[Tstr("codes")]?.let { codesItem ->
                        try {
                            for (codeItem in codesItem.asArray) {
                                val codeMap = codeItem.asMap
                                val codeStr = codeMap[Tstr("code")]?.asTstr ?: continue
                                val signStr = codeMap[Tstr("sign")]?.asTstr
                                val valueStr = codeMap[Tstr("value")]?.asTstr
                                codesList.add(DrivingPrivilegeCode(codeStr, signStr, valueStr))
                            }
                        } catch (e: Exception) {
                            // ignore malformed code items
                        }
                    }
                    result.add(DrivingPrivilege(categoryCode, issueDate, expiryDate, codesList))
                }
            } catch (e: Exception) {
                // ignore malformed driving privileges CBOR
            }
            return result
        }
    }
}
