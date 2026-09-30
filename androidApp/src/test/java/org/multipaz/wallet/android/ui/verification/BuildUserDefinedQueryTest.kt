package org.multipaz.wallet.android.ui.verification

import org.junit.Assert.assertEquals
import org.junit.Test
import org.multipaz.wallet.client.verification.UserDefinedQuery

class BuildUserDefinedQueryTest {

    @Test
    fun testValidInput() {
        val input = buildUserDefinedQuery(
            name = " Residence record ",
            docType = " jp.go.juminhyo.1 ",
            namespaces = listOf("jp.go.juminhyo.1" to listOf(" family_name ", "given_name"))
        )
        assertEquals(
            UserDefinedQueryInput.Valid(
                UserDefinedQuery(
                    docType = "jp.go.juminhyo.1",
                    namespaces = mapOf("jp.go.juminhyo.1" to listOf("family_name", "given_name")),
                    name = "Residence record"
                )
            ),
            input
        )
    }

    @Test
    fun testBlankNameIsNull() {
        val input = buildUserDefinedQuery(
            name = "  ",
            docType = "jp.go.juminhyo.1",
            namespaces = listOf("jp.go.juminhyo.1" to listOf("family_name"))
        ) as UserDefinedQueryInput.Valid
        assertEquals(null, input.query.name)
    }

    @Test
    fun testNamespacesWithoutDataElementsAreLeftOut() {
        val input = buildUserDefinedQuery(
            name = "",
            docType = "jp.go.juminhyo.1",
            namespaces = listOf(
                "jp.go.juminhyo.1" to listOf("family_name", " "),
                "org.iso.23220.1" to listOf(""),
                " " to listOf("given_name")
            )
        ) as UserDefinedQueryInput.Valid
        assertEquals(mapOf("jp.go.juminhyo.1" to listOf("family_name")), input.query.namespaces)
    }

    @Test
    fun testRepeatedNamespacesAreMerged() {
        val input = buildUserDefinedQuery(
            name = "",
            docType = "jp.go.juminhyo.1",
            namespaces = listOf(
                "jp.go.juminhyo.1" to listOf("family_name"),
                "jp.go.juminhyo.1 " to listOf("given_name", "family_name")
            )
        ) as UserDefinedQueryInput.Valid
        assertEquals(mapOf("jp.go.juminhyo.1" to listOf("family_name", "given_name")), input.query.namespaces)
    }

    @Test
    fun testMissingDocTypeAndDataElements() {
        assertEquals(
            UserDefinedQueryInput.Invalid(missingDocType = true, missingDataElements = true),
            buildUserDefinedQuery(name = "", docType = " ", namespaces = listOf("jp.go.juminhyo.1" to listOf("")))
        )
        assertEquals(
            UserDefinedQueryInput.Invalid(missingDocType = false, missingDataElements = true),
            buildUserDefinedQuery(name = "", docType = "jp.go.juminhyo.1", namespaces = emptyList())
        )
        assertEquals(
            UserDefinedQueryInput.Invalid(missingDocType = true, missingDataElements = false),
            buildUserDefinedQuery(name = "", docType = "", namespaces = listOf("ns" to listOf("element")))
        )
    }
}
