package org.multipaz.wallet.android.ui.viewers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.util.toBase64Url

class JwtViewerScreenTest {

    @Test
    fun parseStandardJwt() {
        val header = """{"alg":"ES256","typ":"JWT"}""".encodeToByteArray().toBase64Url()
        val payload = """{"sub":"user123","iss":"https://example.com"}""".encodeToByteArray().toBase64Url()
        val signature = "fake_signature"
        val token = "$header.$payload.$signature"

        val result = parseToken(token)
        assertTrue(result is ParsedToken.StandardJwt)
        val standardJwt = result as ParsedToken.StandardJwt
        assertTrue(standardJwt.jwt.headerPrettyJson.contains(""""alg": "ES256""""))
        assertTrue(standardJwt.jwt.payloadPrettyJson.contains(""""sub": "user123""""))
        assertEquals("fake_signature", standardJwt.jwt.signature)
    }

    @Test
    fun parseSdJwtWithoutKeyBinding() {
        val header = """{"alg":"ES256","typ":"dc+sd-jwt"}""".encodeToByteArray().toBase64Url()
        val payload = """{"iss":"https://issuer.example.com","_sd":["hash1","hash2"]}""".encodeToByteArray().toBase64Url()
        val signature = "issuer_signature"
        val issuerJwt = "$header.$payload.$signature"

        val disc1 = """["salt123","family_name","Smith"]""".encodeToByteArray().toBase64Url()
        val disc2 = """["salt456","DE"]""".encodeToByteArray().toBase64Url()

        val sdJwtString = "$issuerJwt~$disc1~$disc2~"

        val result = parseToken(sdJwtString)
        assertTrue(result is ParsedToken.SdJwtToken)
        val sdJwt = result as ParsedToken.SdJwtToken
        assertTrue(sdJwt.issuerJwt.headerPrettyJson.contains(""""typ": "dc+sd-jwt""""))
        assertTrue(sdJwt.issuerJwt.payloadPrettyJson.contains(""""iss": "https://issuer.example.com""""))
        assertEquals("issuer_signature", sdJwt.issuerJwt.signature)

        assertEquals(2, sdJwt.disclosures.size)
        assertEquals(0, sdJwt.disclosures[0].index)
        assertEquals("family_name", sdJwt.disclosures[0].claimName)
        assertTrue(sdJwt.disclosures[0].prettyJson.contains(""""family_name""""))

        assertEquals(1, sdJwt.disclosures[1].index)
        assertNull(sdJwt.disclosures[1].claimName)
        assertTrue(sdJwt.disclosures[1].prettyJson.contains(""""DE""""))

        assertNull(sdJwt.keyBindingJwt)
    }

    @Test
    fun parseSdJwtWithKeyBinding() {
        val header = """{"alg":"ES256","typ":"dc+sd-jwt"}""".encodeToByteArray().toBase64Url()
        val payload = """{"iss":"https://issuer.example.com"}""".encodeToByteArray().toBase64Url()
        val issuerJwt = "$header.$payload.sig"

        val disc1 = """["salt","given_name","Alice"]""".encodeToByteArray().toBase64Url()

        val kbHeader = """{"alg":"ES256","typ":"kb+jwt"}""".encodeToByteArray().toBase64Url()
        val kbPayload = """{"nonce":"12345","aud":"https://verifier.example.com"}""".encodeToByteArray().toBase64Url()
        val kbJwtString = "$kbHeader.$kbPayload.kb_sig"

        val sdJwtKbString = "$issuerJwt~$disc1~$kbJwtString"

        val result = parseToken(sdJwtKbString)
        assertTrue(result is ParsedToken.SdJwtToken)
        val sdJwt = result as ParsedToken.SdJwtToken

        assertEquals(1, sdJwt.disclosures.size)
        assertEquals("given_name", sdJwt.disclosures[0].claimName)

        val kbJwt = sdJwt.keyBindingJwt
        assertNotNull(kbJwt)
        assertTrue(kbJwt!!.headerPrettyJson.contains(""""typ": "kb+jwt""""))
        assertTrue(kbJwt.payloadPrettyJson.contains(""""nonce": "12345""""))
        assertEquals("kb_sig", kbJwt.signature)
    }

    @Test
    fun parseMalformedToken() {
        val result = parseToken("not-a-valid-token")
        assertTrue(result is ParsedToken.ParseError)
        val error = result as ParsedToken.ParseError
        assertEquals("not-a-valid-token", error.rawToken)
    }
}
