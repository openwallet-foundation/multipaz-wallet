package org.multipaz.wallet.client.verification

import kotlinx.datetime.LocalDate
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.addCborArray
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.putCborArray
import org.multipaz.documenttype.knowntypes.DrivingLicense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrivingPrivilegesQueryTest {

    @Test
    fun testDrivingPrivilegesQueryStructure() {
        val query = DrivingPrivilegesQuery()
        assertEquals(1, query.documentQueries.size)

        val docQuery = query.documentQueries.first()
        assertTrue(docQuery is DrivingPrivilegesDocumentQuery)

        val requests = docQuery.getRequests()
        assertEquals(1, requests.size)

        val request = requests.first()
        assertTrue(request is IsoMdocRequest)
        assertEquals(DrivingLicense.MDL_DOCTYPE, request.docType)

        val mdlNamespaceElements = request.namespaces[DrivingLicense.MDL_NAMESPACE]
        assertTrue(mdlNamespaceElements != null)

        val requestedElementNames = mdlNamespaceElements.map { it.dataElementName }
        assertTrue(requestedElementNames.contains("portrait"))
        assertTrue(requestedElementNames.contains("given_name"))
        assertTrue(requestedElementNames.contains("family_name"))
        assertTrue(requestedElementNames.contains("birth_date"))
        assertTrue(requestedElementNames.contains("issuing_authority"))
        assertTrue(requestedElementNames.contains("issuing_country"))
        assertTrue(requestedElementNames.contains("driving_privileges"))
        assertTrue(!requestedElementNames.contains("resident_address"))
    }

    @Test
    fun testParseDrivingPrivileges() {
        val cborData = buildCborArray {
            addCborMap {
                put("vehicle_category_code", "B")
                put("issue_date", Tagged(1004, Tstr("2020-01-15")))
                put("expiry_date", Tagged(1004, Tstr("2030-01-15")))
                putCborArray("codes") {
                    addCborMap {
                        put("code", "71")
                        put("value", "123456")
                    }
                }
            }
        }

        val parsed = DrivingPrivilegesDocumentQueryResult.parseDrivingPrivileges(cborData)
        assertEquals(1, parsed.size)
        val privilege = parsed.first()
        assertEquals("B", privilege.vehicleCategoryCode)
        assertEquals(LocalDate.parse("2020-01-15"), privilege.issueDate)
        assertEquals(LocalDate.parse("2030-01-15"), privilege.expiryDate)
        assertEquals(1, privilege.codes.size)
        assertEquals("71", privilege.codes.first().code)
        assertEquals("123456", privilege.codes.first().value)
    }
}
