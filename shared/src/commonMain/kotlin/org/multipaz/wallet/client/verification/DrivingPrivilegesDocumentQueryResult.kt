package org.multipaz.wallet.client.verification

import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tstr
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

data class DrivingPrivilegeCode(
    val code: String,
    val sign: String? = null,
    val value: String? = null,
)

data class DrivingPrivilege(
    val vehicleCategoryCode: String,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val codes: List<DrivingPrivilegeCode> = emptyList(),
)

data class DrivingPrivilegesDocumentQueryResult(
    override val trustResult: TrustResult,
    override val documentType: DocumentType,
    override val issuingAuthority: String,
    override val issuingCountryCode: String,
    override val revocationStatus: RevocationStatus?,

    val portrait: ByteString,
    val name: String,
    val birthDate: LocalDate,
    val drivingPrivileges: DataItem,
): DocumentQueryResult(trustResult, documentType, issuingAuthority, issuingCountryCode, revocationStatus) {

    val drivingPrivilegesList: List<DrivingPrivilege> by lazy {
        parseDrivingPrivileges(drivingPrivileges)
    }

    companion object {
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
