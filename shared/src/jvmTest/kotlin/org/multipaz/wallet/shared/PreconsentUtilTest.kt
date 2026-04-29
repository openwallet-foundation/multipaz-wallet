package org.multipaz.wallet.shared

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentAttributeSensitivity
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.openid.dcql.DcqlQuery
import org.multipaz.request.Requester
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.wallet.client.DocumentPreconsentApprovedReader
import org.multipaz.wallet.client.DocumentPreconsentSetting
import org.multipaz.wallet.client.checkPreconsent
import org.multipaz.wallet.client.setPreconsentSetting
import org.multipaz.asn1.ASN1Integer
import org.multipaz.credential.Credential
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.crypto.buildX509Cert
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.util.truncateToWholeSeconds
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PreconsentUtilTest {

    private fun ageAndNameQuery(): DcqlQuery {
        val queryJson = """
                        {
                          "credentials": [
                            {
                              "id": "mdl",
                              "format": "mso_mdoc",
                              "meta": {
                                "doctype_value": "${DrivingLicense.MDL_DOCTYPE}"
                              },
                              "claims": [
                                {"id": "a", "path": ["${DrivingLicense.MDL_NAMESPACE}", "given_name"]},
                                {"id": "b", "path": ["${DrivingLicense.MDL_NAMESPACE}", "age_over_18"]},
                                {"id": "c", "path": ["${DrivingLicense.MDL_NAMESPACE}", "age_in_years"]},
                                {"id": "d", "path": ["${DrivingLicense.MDL_NAMESPACE}", "birth_date"]}
                              ],
                              "claim_sets": [
                                ["a", "b"],
                                ["a", "c"],
                                ["a", "d"]
                              ]
                            }
                          ]
                        }
                    """
        return DcqlQuery.fromJsonString(queryJson)
    }

    private fun ageQuery(): DcqlQuery {
        val queryJson = """
            {
              "credentials": [
                {
                  "id": "mdl",
                  "format": "mso_mdoc",
                  "meta": {
                    "doctype_value": "${DrivingLicense.MDL_DOCTYPE}"
                  },
                  "claims": [
                    {"id": "b", "path": ["${DrivingLicense.MDL_NAMESPACE}", "age_over_18"]},
                    {"id": "c", "path": ["${DrivingLicense.MDL_NAMESPACE}", "age_in_years"]},
                    {"id": "d", "path": ["${DrivingLicense.MDL_NAMESPACE}", "birth_date"]}
                  ],
                  "claim_sets": [
                    ["b"],
                    ["c"],
                    ["d"]
                  ]
                }
              ]
            }
        """
        return DcqlQuery.fromJsonString(queryJson)
    }

    @Test
    fun testAlwaysRequireConsent() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        val cpd = ageAndNameQuery().execute(presentmentSource = harness.presentmentSource)
        
        harness.docMdl.setPreconsentSetting(DocumentPreconsentSetting.AlwaysRequireConsent)
        
        assertEquals(
            null,
            cpd.checkPreconsent(requester = Requester())
        )
    }

    @Test
    fun testNeverRequireConsent() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        val cpd = ageAndNameQuery().execute(presentmentSource = harness.presentmentSource)
        
        harness.docMdl.setPreconsentSetting(DocumentPreconsentSetting.NeverRequireConsent)
        
        assertEquals(
            harness.docMdl.identifier,
            cpd.checkPreconsent(requester = Requester())?.matches?.firstOrNull()?.credential?.document?.identifier
        )
    }

    @Test
    fun testRequestComplexityBased() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        // 1. Complexity matches (age only)
        val cpd1 = ageQuery().execute(presentmentSource = harness.presentmentSource)
        harness.docMdl.setPreconsentSetting(DocumentPreconsentSetting.RequestComplexityBased(
            approvedSensitivity = DocumentAttributeSensitivity.PORTRAIT_IMAGE
        ))
        assertEquals(
            harness.docMdl.identifier,
            cpd1.checkPreconsent(requester = Requester())?.matches?.firstOrNull()?.credential?.document?.identifier
        )

        // 2. Complexity doesn't match (age + name)
        val cpd2 = ageAndNameQuery().execute(presentmentSource = harness.presentmentSource)
        assertEquals(
            null,
            cpd2.checkPreconsent(requester = Requester())
        )
    }

    @Test
    fun testReaderIdentityBased() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        val cpd = ageQuery().execute(presentmentSource = harness.presentmentSource)
        
        val readerRootCert = harness.readerRootKey.certChain.certificates.first()

        // 1. Identity matches
        harness.docMdl.setPreconsentSetting(DocumentPreconsentSetting.ReaderIdentityBased(
            approvedReaders = listOf(
                DocumentPreconsentApprovedReader(
                    metadata = TrustMetadata(),
                    certificate = readerRootCert,
                    approvedSensitivity = null
                )
            )
        ))
        assertEquals(
            harness.docMdl.identifier,
            cpd.checkPreconsent(requester = Requester(
                certChain = harness.readerRootKey.certChain
            ))?.matches?.firstOrNull()?.credential?.document?.identifier
        )

        // 2. Identity doesn't match
        val otherReaderRootKey = AsymmetricKey.ephemeral()
        val otherReaderRootCert = buildX509Cert(
            publicKey = otherReaderRootKey.publicKey,
            signingKey = otherReaderRootKey,
            serialNumber = ASN1Integer.fromRandom(128),
            subject = X500Name.fromName("CN=Other Root"),
            issuer = X500Name.fromName("CN=Other Root"),
            validFrom = Clock.System.now(),
            validUntil = Clock.System.now() + 1.days,
        ) {}
        assertEquals(
            null,
            cpd.checkPreconsent(requester = Requester(
                certChain = X509CertChain(listOf(otherReaderRootCert))
            ))
        )
    }

    @Test
    fun testMultipleDocuments() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        val queryJson = """
            {
              "credentials": [
                {
                  "id": "mdl",
                  "format": "mso_mdoc",
                  "meta": {
                    "doctype_value": "${DrivingLicense.MDL_DOCTYPE}"
                  },
                  "claims": [
                    {"id": "b", "path": ["${DrivingLicense.MDL_NAMESPACE}", "age_over_18"]}
                  ],
                  "claim_sets": [ ["b"] ]
                },
                {
                  "id": "pid",
                  "format": "mso_mdoc",
                  "meta": {
                    "doctype_value": "${PhotoID.PHOTO_ID_DOCTYPE}"
                  },
                  "claims": [
                    {"id": "b", "path": ["${PhotoID.ISO_23220_2_NAMESPACE}", "age_over_18"]}
                  ],
                  "claim_sets": [ ["b"] ]
                }
              ],
              "credential_sets": [
                { "options": [ ["mdl"], ["pid"] ] }
              ]
            }
        """
        val cpd = DcqlQuery.fromJsonString(queryJson).execute(presentmentSource = harness.presentmentSource)
        
        // Approve only mDL
        harness.docMdl.setPreconsentSetting(DocumentPreconsentSetting.NeverRequireConsent)
        harness.docPhotoId.setPreconsentSetting(DocumentPreconsentSetting.AlwaysRequireConsent)
        
        val selection = cpd.checkPreconsent(requester = Requester())
        assertEquals(1, selection?.matches?.size)
        assertEquals(harness.docMdl.identifier, selection?.matches?.firstOrNull()?.credential?.document?.identifier)
    }

    @Test
    fun testDomainRewriter() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        val queryJson = """
        {
          "credentials": [
            {
              "id": "pid",
              "format": "mso_mdoc",
              "meta": {
                "doctype_value": "${PhotoID.PHOTO_ID_DOCTYPE}"
              },
              "claims": [
                {"id": "a", "path": ["${PhotoID.ISO_23220_2_NAMESPACE}", "age_over_18"]}
              ],
              "claim_sets": [ ["a"] ]
            }
          ],
          "credential_sets": [
            { "options": [ [ "pid" ] ] }
          ]
        }
        """
        val cpd = DcqlQuery.fromJsonString(queryJson).execute(presentmentSource = harness.presentmentSource)
        
        harness.docPhotoId.setPreconsentSetting(DocumentPreconsentSetting.NeverRequireConsent)
        
        assertEquals(
            null,
            cpd.checkPreconsent(
                requester = Requester(),
                domainRewriter = { "mdoc_noauth" }
            )
        )

        // Positive test: docPhotoId2 has both "mdoc" and "mdoc_noauth" domains.
        // We force it to use docPhotoId2 by setting docPhotoId to AlwaysRequireConsent.
        harness.docPhotoId.setPreconsentSetting(DocumentPreconsentSetting.AlwaysRequireConsent)
        harness.docPhotoId2.setPreconsentSetting(DocumentPreconsentSetting.NeverRequireConsent)

        val selection = cpd.checkPreconsent(
            requester = Requester(),
            domainRewriter = { "mdoc_noauth" }
        )
        
        // The selection should have succeeded, and the domain of the selected credential should be "mdoc_noauth"
        assertEquals(
            "mdoc_noauth",
            selection?.matches?.firstOrNull()?.credential?.domain
        )
        assertEquals(
            harness.docPhotoId2.identifier,
            selection?.matches?.firstOrNull()?.credential?.document?.identifier
        )
    }

    @Test
    fun testReaderIdentityBased_ChainMatch() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        val cpd = ageQuery().execute(presentmentSource = harness.presentmentSource)
        
        val now = Clock.System.now()
        val rootKey = AsymmetricKey.ephemeral()
        val rootCert = buildX509Cert(
            publicKey = rootKey.publicKey,
            signingKey = rootKey,
            serialNumber = ASN1Integer.fromRandom(128),
            subject = X500Name.fromName("CN=Root"),
            issuer = X500Name.fromName("CN=Root"),
            validFrom = (now - 1.days).truncateToWholeSeconds(),
            validUntil = (now + 2.days).truncateToWholeSeconds(),
        ) {
            setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
            setBasicConstraints(ca = true, pathLenConstraint = 1)
            includeSubjectKeyIdentifier()
            includeAuthorityKeyIdentifierAsSubjectKeyIdentifier()
        }

        val leafKey = AsymmetricKey.ephemeral()
        val leafCert = buildX509Cert(
            publicKey = leafKey.publicKey,
            signingKey = rootKey,
            serialNumber = ASN1Integer.fromRandom(128),
            subject = X500Name.fromName("CN=Leaf"),
            issuer = X500Name.fromName("CN=Root"),
            validFrom = (now - 1.days).truncateToWholeSeconds(),
            validUntil = (now + 2.days).truncateToWholeSeconds(),
        ) {
            includeSubjectKeyIdentifier()
            setAuthorityKeyIdentifierToCertificate(rootCert)
        }

        // Approve the root reader
        harness.docMdl.setPreconsentSetting(DocumentPreconsentSetting.ReaderIdentityBased(
            approvedReaders = listOf(
                DocumentPreconsentApprovedReader(
                    metadata = TrustMetadata(),
                    certificate = rootCert,
                    approvedSensitivity = null
                )
            )
        ))

        // 1. Chain contains root -> should match (direct match or root match)
        val result1 = cpd.checkPreconsent(requester = Requester(
            certChain = X509CertChain(listOf(leafCert, rootCert))
        ))
        assertNotNull(result1, "Result 1 should not be null")
        assertEquals(
            harness.docMdl.identifier,
            result1.matches.firstOrNull()?.credential?.document?.identifier
        )

        // 2. Chain only contains leaf -> should match via AKI
        val result2 = cpd.checkPreconsent(requester = Requester(
            certChain = X509CertChain(listOf(leafCert))
        ))
        assertNotNull(result2, "Result 2 should not be null")
        assertEquals(
            harness.docMdl.identifier,
            result2.matches.firstOrNull()?.credential?.document?.identifier
        )
    }

    private fun setUsageCount(credential: Credential, count: Int) {
        var clazz: Class<*>? = credential.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField("usageCount")
                field.isAccessible = true
                field.set(credential, count)
                return
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException("usageCount")
    }

    @Test
    fun testDomainRewriter_UsageCount() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        // Add two credentials to the same domain in docMdl
        // provisionStandardDocuments already added one to "mdoc" domain.
        
        MdocCredential.create(
            document = harness.docMdl,
            asReplacementForIdentifier = null,
            domain = "mdoc",
            secureArea = harness.softwareSecureArea,
            docType = DrivingLicense.MDL_DOCTYPE,
            createKeySettings = CreateKeySettings()
        )
        
        val credentials = harness.docMdl.getCredentials().filter { it.domain == "mdoc" }
        assertEquals(2, credentials.size)
        
        val cred1 = credentials[0]
        val cred2 = credentials[1]
        
        setUsageCount(cred1, 10)
        setUsageCount(cred2, 5)
        
        harness.docMdl.setPreconsentSetting(DocumentPreconsentSetting.NeverRequireConsent)
        
        val query = ageQuery()
        val cpd = query.execute(presentmentSource = harness.presentmentSource)
        
        val selection = cpd.checkPreconsent(requester = Requester())
        assertNotNull(selection)
        assertEquals(cred2.identifier, selection.matches.first().credential.identifier)
        assertEquals(5, selection.matches.first().credential.usageCount)
        
        // Swap usage counts and check again
        setUsageCount(cred1, 3)
        
        val selection2 = cpd.checkPreconsent(requester = Requester())
        assertNotNull(selection2)
        assertEquals(cred1.identifier, selection2.matches.first().credential.identifier)
        assertEquals(3, selection2.matches.first().credential.usageCount)
    }
}
