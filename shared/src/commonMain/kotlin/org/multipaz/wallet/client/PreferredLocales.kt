package org.multipaz.wallet.client

/**
 * Converts a list of preferred languages into the list to use for
 * [org.multipaz.provisioning.openid4vci.OpenID4VCIClientPreferences.locales].
 *
 * Issuer metadata identifies the language of each `display` object with a BCP 47 language
 * tag such as `ja-JP`, and the SDK selects among them by exact string match against this list.
 * To let a preference for `ja-JP` also match an issuer which only provides `ja`, each tag is
 * followed by its truncations as in RFC 4647 Lookup, so `[ja-JP, en-US]` becomes
 * `[ja-JP, ja, en-US, en]`.
 *
 * Platform spellings are normalized to BCP 47 first: `ja_JP` becomes `ja-JP` and the Android
 * resource qualifier form `zh-rCN` becomes `zh-CN`. Subtags use the conventional case, that is
 * lowercase language, titlecase script and uppercase region.
 *
 * `en-US` and `en` are appended as a last resort when not already present, so that English is
 * preferred over whichever language the issuer happens to list first.
 *
 * @param preferred language tags in order of preference, e.g. from `LocaleList.getDefault()`
 *   on Android or `Locale.preferredLanguages` on iOS.
 * @return the expanded list, without duplicates.
 */
internal fun expandPreferredLocales(preferred: List<String>): List<String> {
    val result = mutableSetOf<String>()
    for (tag in preferred) {
        val subtags = normalizeLanguageTag(tag) ?: continue
        var n = subtags.size
        while (n > 0) {
            result.add(subtags.subList(0, n).joinToString("-"))
            n--
            // RFC 4647 Lookup: never end on a singleton such as the `x` in `ja-x-foo`.
            if (n > 0 && subtags[n - 1].length == 1) {
                n--
            }
        }
    }
    result.add("en-US")
    result.add("en")
    return result.toList()
}

/**
 * Normalizes [tag] to BCP 47 subtags, or returns `null` if it does not name a language.
 */
internal fun normalizeLanguageTag(tag: String): List<String>? {
    // Drop POSIX encoding and modifier suffixes such as `ja_JP.UTF-8` or `de_DE@euro`.
    val core = tag.trim().substringBefore('.').substringBefore('@')
    val parts = core.replace('_', '-').split('-').filter { it.isNotEmpty() }
    if (parts.isEmpty()) {
        return null
    }
    val language = parts[0].lowercase()
    if (language == "und" || language == "*" || !language.all { it in 'a'..'z' }) {
        return null
    }
    val subtags = mutableListOf(language)
    for (part in parts.drop(1)) {
        subtags.add(
            when {
                // Android resource qualifier region, e.g. the `rCN` in `zh-rCN`.
                part.length == 3 && (part[0] == 'r' || part[0] == 'R') && part.drop(1).all { it.isLetter() } ->
                    part.drop(1).uppercase()
                part.length == 2 && part.all { it.isLetter() } -> part.uppercase()
                part.length == 4 && part.all { it.isLetter() } ->
                    part.lowercase().replaceFirstChar { it.uppercase() }
                else -> part.lowercase()
            }
        )
    }
    return subtags
}
