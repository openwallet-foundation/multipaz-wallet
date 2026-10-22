package org.multipaz.wallet.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreferredLocalesTest {

    @Test
    fun eachTagIsFollowedByItsTruncations() {
        assertEquals(
            listOf("ja-JP", "ja", "en-US", "en"),
            expandPreferredLocales(listOf("ja-JP", "en-US"))
        )
    }

    @Test
    fun exactTagComesBeforeLanguageOnly() {
        // An issuer offering both `ja-JP` and `ja` should get `ja-JP` picked for a `ja-JP` user.
        val expanded = expandPreferredLocales(listOf("ja-JP"))
        assertTrue(expanded.indexOf("ja-JP") < expanded.indexOf("ja"))
    }

    @Test
    fun underscoreFormIsNormalized() {
        assertEquals(
            listOf("ja-JP", "ja", "en-US", "en"),
            expandPreferredLocales(listOf("ja_JP"))
        )
    }

    @Test
    fun androidResourceQualifierRegionIsNormalized() {
        assertEquals(
            listOf("zh-CN", "zh", "en-US", "en"),
            expandPreferredLocales(listOf("zh-rCN"))
        )
    }

    @Test
    fun subtagCaseIsCanonicalized() {
        assertEquals(
            listOf("zh-Hans-CN", "zh-Hans", "zh", "en-US", "en"),
            expandPreferredLocales(listOf("ZH-hans-cn"))
        )
    }

    @Test
    fun truncationNeverEndsOnASingleton() {
        assertEquals(
            listOf("ja-JP-x-foo", "ja-JP", "ja", "en-US", "en"),
            expandPreferredLocales(listOf("ja-JP-x-foo"))
        )
    }

    @Test
    fun posixSuffixesAreDropped() {
        assertEquals(
            listOf("de-DE", "de", "en-US", "en"),
            expandPreferredLocales(listOf("de_DE.UTF-8"))
        )
        assertEquals(
            listOf("de-DE", "de", "en-US", "en"),
            expandPreferredLocales(listOf("de_DE@euro"))
        )
    }

    @Test
    fun duplicatesKeepTheirFirstPosition() {
        // `en` from `en-GB` must stay ahead of the `en-US` fallback.
        assertEquals(
            listOf("en-GB", "en", "ja-JP", "ja", "en-US"),
            expandPreferredLocales(listOf("en-GB", "ja-JP", "en"))
        )
    }

    @Test
    fun englishIsTheLastResort() {
        assertEquals(listOf("fr", "en-US", "en"), expandPreferredLocales(listOf("fr")))
        assertEquals(listOf("en-US", "en"), expandPreferredLocales(emptyList()))
    }

    @Test
    fun englishFallbackIsNotRepeated() {
        assertEquals(listOf("en-US", "en"), expandPreferredLocales(listOf("en-US")))
    }

    @Test
    fun invalidTagsAreSkipped() {
        assertEquals(
            listOf("ja", "en-US", "en"),
            expandPreferredLocales(listOf("", "  ", "und", "*", "12", "ja"))
        )
    }

    @Test
    fun normalizeLanguageTagRejectsNonLanguages() {
        assertNull(normalizeLanguageTag(""))
        assertNull(normalizeLanguageTag("und-JP"))
        assertEquals(listOf("ja", "JP"), normalizeLanguageTag(" ja_jp "))
    }
}
