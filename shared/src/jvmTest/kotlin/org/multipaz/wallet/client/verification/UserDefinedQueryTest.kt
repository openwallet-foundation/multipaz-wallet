package org.multipaz.wallet.client.verification

import org.multipaz.cbor.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserDefinedQueryTest {

    @Test
    fun testUserDefinedQueryStructure() {
        val docType = "org.iso.18013.5.1.mDL"
        val namespaces = mapOf(
            "org.iso.18013.5.1" to listOf("given_name", "family_name", "birth_date")
        )
        val query = UserDefinedQuery(docType = docType, namespaces = namespaces)
        assertEquals(1, query.documentQueries.size)

        val docQuery = query.documentQueries.first()
        assertTrue(docQuery is UserDefinedDocumentQuery)
        assertEquals(docType, docQuery.docType)
        assertEquals(namespaces, docQuery.namespaces)

        val requests = docQuery.getRequests()
        assertEquals(1, requests.size)

        val request = requests.first()
        assertTrue(request is IsoMdocRequest)
        assertEquals(docType, request.docType)

        val namespaceElements = request.namespaces["org.iso.18013.5.1"]
        assertTrue(namespaceElements != null)

        val requestedElementNames = namespaceElements.map { it.dataElementName }
        assertEquals(listOf("given_name", "family_name", "birth_date"), requestedElementNames)
    }

    @Test
    fun testUserDefinedQueryCborSerialization() {
        val docType = "org.example.custom_doctype"
        val namespaces = mapOf(
            "org.example.ns1" to listOf("elem1", "elem2"),
            "org.example.ns2" to listOf("elem3")
        )
        val query = UserDefinedQuery(docType = docType, namespaces = namespaces)

        val encoded = query.toCbor()
        val decoded = Query.fromCbor(encoded)

        assertTrue(decoded is UserDefinedQuery)
        assertEquals(docType, decoded.docType)
        assertEquals(namespaces, decoded.namespaces)
    }
}
