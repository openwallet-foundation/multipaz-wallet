package org.multipaz.wallet.android.ui.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.multipaz.openid.claim.OpenID4VCIClaimDescription
import org.multipaz.openid.claim.OpenID4VCIClaimDisplay

class IssuerMetadataImportTest {

    private fun description(vararg path: Any?, display: List<OpenID4VCIClaimDisplay> = emptyList()) =
        OpenID4VCIClaimDescription(
            path = JsonArray(path.map {
                when (it) {
                    null -> JsonNull
                    is Number -> JsonPrimitive(it)
                    else -> JsonPrimitive(it.toString())
                }
            }),
            display = display
        )

    private val familyNameDisplay = listOf(
        OpenID4VCIClaimDisplay(name = "Family name", locale = "en-US"),
        OpenID4VCIClaimDisplay(name = "氏", locale = "ja-JP")
    )

    @Test
    fun testGroupsByNamespaceInIssuerOrder() {
        val namespaces = importableNamespaces(
            descriptions = listOf(
                description("ns1", "family_name", display = familyNameDisplay),
                description("ns2", "address"),
                description("ns1", "given_name")
            ),
            locales = listOf("en-US")
        )
        assertEquals(
            listOf(
                ImportableNamespace(
                    "ns1",
                    listOf(
                        ImportableDataElement("family_name", "Family name"),
                        ImportableDataElement("given_name", null)
                    )
                ),
                ImportableNamespace("ns2", listOf(ImportableDataElement("address", null)))
            ),
            namespaces
        )
    }

    @Test
    fun testUsesOnlyNamespaceAndDataElementOfLongerPaths() {
        val namespaces = importableNamespaces(
            descriptions = listOf(
                description("ns", "driving_privileges", null, "vehicle_category_code"),
                description("ns", "driving_privileges")
            ),
            locales = emptyList()
        )
        assertEquals(
            listOf(ImportableNamespace("ns", listOf(ImportableDataElement("driving_privileges", null)))),
            namespaces
        )
    }

    @Test
    fun testSkipsPathsNotStartingWithTwoStrings() {
        val namespaces = importableNamespaces(
            descriptions = listOf(
                description("only_one"),
                description("ns", 0),
                description(null, "element"),
                description("ns", "ok")
            ),
            locales = emptyList()
        )
        assertEquals(listOf(ImportableNamespace("ns", listOf(ImportableDataElement("ok", null)))), namespaces)
    }

    @Test
    fun testFirstDescriptionOfDataElementWins() {
        val namespaces = importableNamespaces(
            descriptions = listOf(
                description("ns", "a", display = listOf(OpenID4VCIClaimDisplay("First", "en"))),
                description("ns", "a", display = listOf(OpenID4VCIClaimDisplay("Second", "en")))
            ),
            locales = listOf("en")
        )
        assertEquals("First", namespaces.single().dataElements.single().displayName)
    }

    @Test
    fun testPickDisplayNameExactAndLanguageMatch() {
        assertEquals("氏", pickDisplayName(familyNameDisplay, listOf("ja-JP")))
        assertEquals("氏", pickDisplayName(familyNameDisplay, listOf("ja")))
        assertEquals("氏", pickDisplayName(familyNameDisplay, listOf("ja_JP")))
        assertEquals("Family name", pickDisplayName(familyNameDisplay, listOf("en-GB")))
    }

    @Test
    fun testPickDisplayNameFollowsPreferenceOrder() {
        assertEquals("Family name", pickDisplayName(familyNameDisplay, listOf("fr-FR", "en-US", "ja-JP")))
    }

    @Test
    fun testPickDisplayNameFallbacks() {
        val untagged = listOf(
            OpenID4VCIClaimDisplay(name = "Nom", locale = "fr"),
            OpenID4VCIClaimDisplay(name = "Name", locale = null)
        )
        assertEquals("Name", pickDisplayName(untagged, listOf("de")))
        assertEquals("Family name", pickDisplayName(familyNameDisplay, listOf("de")))
        assertEquals(
            "Nom",
            pickDisplayName(listOf(OpenID4VCIClaimDisplay("Nom", "fr"), OpenID4VCIClaimDisplay("Nome", "it")), listOf("de"))
        )
    }

    @Test
    fun testPickDisplayNameIgnoresEntriesWithoutName() {
        assertNull(pickDisplayName(emptyList(), listOf("en")))
        assertNull(pickDisplayName(listOf(OpenID4VCIClaimDisplay(name = null, locale = "en")), listOf("en")))
        assertEquals(
            "氏",
            pickDisplayName(
                listOf(OpenID4VCIClaimDisplay(name = null, locale = "en"), OpenID4VCIClaimDisplay("氏", "ja")),
                listOf("en")
            )
        )
    }
}
