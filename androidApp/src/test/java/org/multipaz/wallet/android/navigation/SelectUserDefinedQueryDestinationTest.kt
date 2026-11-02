package org.multipaz.wallet.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.multipaz.wallet.client.verification.UserDefinedQuery

class SelectUserDefinedQueryDestinationTest {
    @Test
    fun testImportedQueryRoundTrip() {
        val query = UserDefinedQuery(
            docType = "jp.go.juminhyo.1",
            namespaces = mapOf("jp.go.juminhyo.1" to listOf("family_name", "given_name")),
            name = "住民票"
        )
        val destination = SelectUserDefinedQueryDestination(query)
        assertNull(destination.index)
        assertEquals(query, destination.importedQuery)
    }

    @Test
    fun testNoImportedQuery() {
        assertNull(SelectUserDefinedQueryDestination(index = 1).importedQuery)
    }
}
