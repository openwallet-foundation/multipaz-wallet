package org.multipaz.wallet.shared

import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.EcCurve
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Pkcs12Test {

    private fun loadTestPkcs12(): ByteString {
        val stream = Pkcs12Test::class.java.getResourceAsStream("/bob_bobson_key_passphrase_xyz123.p12")
            ?: Pkcs12Test::class.java.classLoader?.getResourceAsStream("bob_bobson_key_passphrase_xyz123.p12")
        if (stream != null) {
            return ByteString(stream.use { it.readBytes() })
        }
        val file = sequenceOf(
            File("shared/src/jvmTest/resources/bob_bobson_key_passphrase_xyz123.p12"),
            File("src/jvmTest/resources/bob_bobson_key_passphrase_xyz123.p12"),
            File("bob_bobson_key_passphrase_xyz123.p12"),
            File("../bob_bobson_key_passphrase_xyz123.p12")
        ).firstOrNull { it.exists() } ?: throw IllegalStateException("Test p12 file not found")
        return ByteString(file.readBytes())
    }

    @Test
    fun testParsePkcs12Valid() {
        val p12Bytes = loadTestPkcs12()
        val (privateKey, certChain) = parsePkcs12(p12Bytes, "xyz123")

        assertEquals(EcCurve.P256, privateKey.curve)
        assertEquals(2, certChain.certificates.size)

        val leafCert = certChain.certificates[0]
        assertEquals("CN=Bob Bobson,O=Utopia Brewery,C=ZZ", leafCert.subject.name)
        assertEquals("CN=Utopia Brewery Reader CA,O=Utopia Brewery,C=ZZ", leafCert.issuer.name)

        val caCert = certChain.certificates[1]
        assertEquals("CN=Utopia Brewery Reader CA,O=Utopia Brewery,C=ZZ", caCert.subject.name)
        assertEquals("CN=Utopia Brewery Reader CA,O=Utopia Brewery,C=ZZ", caCert.issuer.name)
    }

    @Test
    fun testParsePkcs12WrongPassphrase() {
        val p12Bytes = loadTestPkcs12()
        assertFailsWith<WrongPassphraseException> {
            parsePkcs12(p12Bytes, "wrong_passphrase")
        }
    }

    @Test
    fun testParsePkcs12InvalidData() {
        val invalidBytes = ByteString("not a valid pkcs12 file".encodeToByteArray())
        assertFailsWith<IllegalArgumentException> {
            parsePkcs12(invalidBytes, "xyz123")
        }
    }
}
