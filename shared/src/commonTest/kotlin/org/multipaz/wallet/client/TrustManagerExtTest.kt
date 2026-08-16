package org.multipaz.wallet.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.crypto.buildX509Cert
import org.multipaz.mdoc.rical.Rical
import org.multipaz.mdoc.rical.RicalCertificateInfo
import org.multipaz.mdoc.rical.SignedRical
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.mdoc.vical.Vical
import org.multipaz.mdoc.vical.VicalCertificateInfo
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.TrustEntryRical
import org.multipaz.trustmanagement.TrustEntryVical
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.truncateToWholeSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class TrustManagerExtTest {

    private suspend fun createTestVical(
        issueId: Long?,
        vicalUrl: String? = "https://example.com/vical.cbor"
    ): Pair<ByteArray, X509Cert> {
        val now = Clock.System.now().truncateToWholeSeconds()
        val validFrom = now - 10.minutes
        val validUntil = now + 10.minutes

        val iacaKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val iacaCert = MdocUtil.generateIacaCertificate(
            iacaKey = AsymmetricKey.anonymous(iacaKey),
            subject = X500Name.fromName("CN=Test VICAL IACA"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = validFrom,
            validUntil = validUntil,
            issuerAltNameUrl = "https://example.com/altname",
            crlUrl = "https://example.com/crl"
        )

        val vicalKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val vicalCert = buildX509Cert(
            publicKey = vicalKey.publicKey,
            signingKey = AsymmetricKey.anonymous(vicalKey, vicalKey.curve.defaultSigningAlgorithm),
            serialNumber = ASN1Integer(1),
            subject = X500Name.fromName("CN=Test VICAL Provider"),
            issuer = X500Name.fromName("CN=Test VICAL Provider"),
            validFrom = validFrom,
            validUntil = validUntil
        ) {
            includeSubjectKeyIdentifier()
            setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
        }

        val vical = Vical(
            version = "1",
            vicalProvider = "Test VICAL Provider",
            date = now,
            nextUpdate = null,
            vicalIssueID = issueId,
            certificateInfos = listOf(
                VicalCertificateInfo(
                    certificate = iacaCert,
                    docTypes = listOf("org.iso.18013.5.1.mDL")
                )
            ),
            notAfter = null,
            vicalUrl = vicalUrl,
            extensions = emptyMap(),
        )

        val signedVical = SignedVical(vical, X509CertChain(listOf(vicalCert)))
        val bytes = signedVical.generate(
            AsymmetricKey.X509CertifiedExplicit(
                privateKey = vicalKey,
                certChain = X509CertChain(listOf(vicalCert))
            )
        )
        return Pair(bytes, vicalCert)
    }

    private suspend fun createTestRical(
        id: Long?,
        ricalUrl: String? = "https://example.com/rical.cbor"
    ): Pair<ByteArray, X509Cert> {
        val now = Clock.System.now().truncateToWholeSeconds()
        val validFrom = now - 10.minutes
        val validUntil = now + 10.minutes

        val caKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val caCert = buildX509Cert(
            publicKey = caKey.publicKey,
            signingKey = AsymmetricKey.anonymous(caKey, caKey.curve.defaultSigningAlgorithm),
            serialNumber = ASN1Integer(1),
            subject = X500Name.fromName("CN=Test RICAL CA"),
            issuer = X500Name.fromName("CN=Test RICAL CA"),
            validFrom = validFrom,
            validUntil = validUntil
        ) {
            includeSubjectKeyIdentifier()
            setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
        }

        val ricalKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val ricalCert = buildX509Cert(
            publicKey = ricalKey.publicKey,
            signingKey = AsymmetricKey.anonymous(ricalKey, ricalKey.curve.defaultSigningAlgorithm),
            serialNumber = ASN1Integer(2),
            subject = X500Name.fromName("CN=Test RICAL Provider"),
            issuer = X500Name.fromName("CN=Test RICAL Provider"),
            validFrom = validFrom,
            validUntil = validUntil
        ) {
            includeSubjectKeyIdentifier()
            setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
        }

        val rical = Rical(
            type = Rical.RICAL_TYPE_READER_AUTHENTICATION,
            version = "1.0",
            provider = "Test RICAL Provider",
            date = now,
            nextUpdate = null,
            notAfter = null,
            certificateInfos = listOf(
                RicalCertificateInfo(
                    certificate = caCert,
                    isTrustAnchor = true
                )
            ),
            id = id,
            latestRicalUrl = ricalUrl,
            extensions = emptyMap()
        )

        val signedRical = SignedRical(rical, X509CertChain(listOf(ricalCert)))
        val bytes = signedRical.generate(
            AsymmetricKey.X509CertifiedExplicit(
                privateKey = ricalKey,
                certChain = X509CertChain(listOf(ricalCert))
            )
        )
        return Pair(bytes, ricalCert)
    }

    @Test
    fun updateVical_alreadyUpToDate() = runTest {
        val (vicalBytes, _) = createTestVical(issueId = 2L)
        val trustManager = TrustManager(EphemeralStorage())
        val entry = trustManager.addVical(ByteString(vicalBytes), TrustMetadata())

        val mockEngine = MockEngine { request ->
            assertEquals("https://example.com/vical.cbor", request.url.toString())
            respond(
                content = ByteReadChannel(vicalBytes),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/cbor")
            )
        }
        val httpClient = HttpClient(mockEngine)

        val result = trustManager.updateTrustEntry(entry, httpClient)
        assertEquals(TrustEntryUpdateResult.AlreadyUpToDate, result)
    }

    @Test
    fun updateVical_success() = runTest {
        val (oldBytes, _) = createTestVical(issueId = 1L)
        val (newBytes, _) = createTestVical(issueId = 2L)

        val trustManager = TrustManager(EphemeralStorage())
        val entry = trustManager.addVical(ByteString(oldBytes), TrustMetadata(displayName = "Test VICAL"))

        val mockEngine = MockEngine { request ->
            assertEquals("https://example.com/vical.cbor", request.url.toString())
            respond(
                content = ByteReadChannel(newBytes),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/cbor")
            )
        }
        val httpClient = HttpClient(mockEngine)

        val result = trustManager.updateTrustEntry(entry, httpClient)
        assertTrue(result is TrustEntryUpdateResult.Updated)
        assertEquals("VICAL", result.listType)
        assertEquals(2L, result.issueId)
        assertEquals(1L, result.previousIssueId)

        val updatedEntries = trustManager.getEntries()
        assertEquals(1, updatedEntries.size)
        val updatedEntry = updatedEntries.first() as TrustEntryVical
        val parsed = SignedVical.parse(updatedEntry.encodedSignedVical.toByteArray(), disableSignatureVerification = true)
        assertEquals(2L, parsed.vical.vicalIssueID)
        assertEquals("Test VICAL", updatedEntry.metadata.displayName)
    }

    @Test
    fun updateVical_noUpdateUrl() = runTest {
        val (vicalBytes, _) = createTestVical(issueId = 1L, vicalUrl = null)
        val trustManager = TrustManager(EphemeralStorage())
        val entry = trustManager.addVical(ByteString(vicalBytes), TrustMetadata())

        val mockEngine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val httpClient = HttpClient(mockEngine)

        val result = trustManager.updateTrustEntry(entry, httpClient)
        assertEquals(TrustEntryUpdateResult.NoUpdateUrl, result)
    }

    @Test
    fun updateRical_success() = runTest {
        val (oldBytes, _) = createTestRical(id = 10L)
        val (newBytes, _) = createTestRical(id = 20L)

        val trustManager = TrustManager(EphemeralStorage())
        val entry = trustManager.addRical(ByteString(oldBytes), TrustMetadata(displayName = "Test RICAL"))

        val mockEngine = MockEngine { request ->
            assertEquals("https://example.com/rical.cbor", request.url.toString())
            respond(
                content = ByteReadChannel(newBytes),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/cbor")
            )
        }
        val httpClient = HttpClient(mockEngine)

        val result = trustManager.updateTrustEntry(entry, httpClient)
        assertTrue(result is TrustEntryUpdateResult.Updated)
        assertEquals("RICAL", result.listType)
        assertEquals(20L, result.issueId)
        assertEquals(10L, result.previousIssueId)

        val updatedEntries = trustManager.getEntries()
        assertEquals(1, updatedEntries.size)
        val updatedEntry = updatedEntries.first() as TrustEntryRical
        val parsed = SignedRical.parse(updatedEntry.encodedSignedRical.toByteArray(), disableSignatureVerification = true)
        assertEquals(20L, parsed.rical.id)
        assertEquals("Test RICAL", updatedEntry.metadata.displayName)
    }

    @Test
    fun updateEntries_batch() = runTest {
        val (vicalOldBytes, _) = createTestVical(issueId = 1L, vicalUrl = "https://example.com/vical.cbor")
        val (vicalNewBytes, _) = createTestVical(issueId = 2L, vicalUrl = "https://example.com/vical.cbor")
        val (ricalBytes, _) = createTestRical(id = 10L, ricalUrl = "https://example.com/rical.cbor")

        val trustManager = TrustManager(EphemeralStorage())
        trustManager.addVical(ByteString(vicalOldBytes), TrustMetadata())
        trustManager.addRical(ByteString(ricalBytes), TrustMetadata())

        val mockEngine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/vical.cbor" -> respond(
                    content = ByteReadChannel(vicalNewBytes),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/cbor")
                )
                "https://example.com/rical.cbor" -> respond(
                    content = ByteReadChannel(ricalBytes),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/cbor")
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val httpClient = HttpClient(mockEngine)

        val updatedCount = trustManager.updateEntries(httpClient)
        assertEquals(1, updatedCount)
    }
}
