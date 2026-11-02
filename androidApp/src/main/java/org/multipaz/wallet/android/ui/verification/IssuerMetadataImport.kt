package org.multipaz.wallet.android.ui.verification

import kotlinx.serialization.json.JsonPrimitive
import org.multipaz.openid.claim.OpenID4VCIClaimDescription
import org.multipaz.openid.claim.OpenID4VCIClaimDisplay

/**
 * A data element listed in the claims descriptions of an ISO mdoc credential configuration.
 *
 * @property dataElement the data element identifier.
 * @property displayName the name of the data element in the best matching language, or `null` if the issuer gave
 *   none.
 */
internal data class ImportableDataElement(
    val dataElement: String,
    val displayName: String?
)

/**
 * A namespace listed in the claims descriptions of an ISO mdoc credential configuration.
 *
 * @property namespace the namespace identifier.
 * @property dataElements the data elements in this namespace, in the order the issuer lists them.
 */
internal data class ImportableNamespace(
    val namespace: String,
    val dataElements: List<ImportableDataElement>
)

/**
 * Gets the namespaces and data elements of an ISO mdoc credential configuration from its claims descriptions.
 *
 * For ISO mdoc, the first two components of a claims path are the namespace and the data element identifier.
 * Descriptions whose path does not start with two strings are left out, and only the first description of a data
 * element is used.
 *
 * @param descriptions the claims descriptions from the credential configuration.
 * @param locales the preferred languages, most preferred first, used to pick the display names.
 * @return the namespaces, in the order the issuer first lists them.
 */
internal fun importableNamespaces(
    descriptions: List<OpenID4VCIClaimDescription>,
    locales: List<String>
): List<ImportableNamespace> {
    val namespaces = linkedMapOf<String, LinkedHashMap<String, ImportableDataElement>>()
    for (description in descriptions) {
        val namespace = description.path.getOrNull(0).stringOrNull() ?: continue
        val dataElement = description.path.getOrNull(1).stringOrNull() ?: continue
        val dataElements = namespaces.getOrPut(namespace) { linkedMapOf() }
        if (dataElement !in dataElements) {
            dataElements[dataElement] = ImportableDataElement(
                dataElement = dataElement,
                displayName = pickDisplayName(description.display, locales)
            )
        }
    }
    return namespaces.map { (namespace, dataElements) ->
        ImportableNamespace(namespace, dataElements.values.toList())
    }
}

/**
 * Picks the display name in the best matching language.
 *
 * Each preferred language is tried in order, first for an exact match and then for an entry of the same language
 * in another region or script. When none match, English is used, then an entry without a language, then the first
 * entry.
 *
 * @param displays the display names given by the issuer.
 * @param locales the preferred languages as BCP 47 tags, most preferred first.
 * @return the display name, or `null` if no entry has a name.
 */
internal fun pickDisplayName(displays: List<OpenID4VCIClaimDisplay>, locales: List<String>): String? {
    val named = displays.filter { !it.name.isNullOrBlank() }
    if (named.isEmpty()) {
        return null
    }
    val tagged = named.map { it.locale?.normalizedTag() to it.name!! }
    for (locale in locales) {
        val tag = locale.normalizedTag() ?: continue
        tagged.firstOrNull { it.first == tag }?.let { return it.second }
        val language = tag.substringBefore('-')
        tagged.firstOrNull { it.first?.substringBefore('-') == language }?.let { return it.second }
    }
    tagged.firstOrNull { it.first?.substringBefore('-') == "en" }?.let { return it.second }
    tagged.firstOrNull { it.first == null }?.let { return it.second }
    return tagged.first().second
}

private fun String.normalizedTag(): String? = trim().replace('_', '-').lowercase().ifEmpty { null }

private fun Any?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
