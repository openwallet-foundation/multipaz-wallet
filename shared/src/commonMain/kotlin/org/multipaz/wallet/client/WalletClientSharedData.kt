package org.multipaz.wallet.client

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.document.DocumentStore
import org.multipaz.mpzpass.MpzPass
import org.multipaz.util.Logger

private const val TAG = "WalletClientSharedData"

/**
 * Data shared between multiple wallet apps signed in with the same account.
 *
 * This class is immutable.
 *
 * Because newer versions may be processed by older clients, any additions to this
 * data structure MUST be backwards compatible.
 */
@CborSerializable(schemaHash = "RD_59wDzdqBNTwimCSX5TuGG-A18Pzbmm19MsdXAymU")
data class WalletClientSharedData(
    /**
     * A list of imported passes.
     */
    val encodedMpzPasses: List<ByteString>? = null,
) {
    /**
     * Gets all imported passes.
     *
     * @return a list of [MpzPass] instances.
     */
    suspend fun getMpzPasses(): List<MpzPass> {
        return encodedMpzPasses?.map {
            MpzPass.fromDataItem(Cbor.decode(it.toByteArray()))
        } ?: emptyList()
    }

    /**
     * Adds a new imported pass
     *
     * If the pass already exists, it's added a second time.
     *
     * @param pass the pass to add.
     * @return a new [org.multipaz.wallet.client.WalletClientSharedData] with the added pass.
     */
    suspend fun addMpzPass(pass: MpzPass): WalletClientSharedData {
        return copy(
            encodedMpzPasses = encodedMpzPasses.orEmpty() + ByteString(
                Cbor.encode(pass.toDataItem())
            )
        )
    }

    /**
     * Removes an imported pass
     *
     * If the pass doesn't exist the same shared data is returned.
     *
     * @param pass the pass to remove.
     * @return a new [org.multipaz.wallet.client.WalletClientSharedData] with the removed pass.
     */
    suspend fun removeMpzPass(pass: MpzPass): WalletClientSharedData {
        return copy(
            encodedMpzPasses = encodedMpzPasses?.filter {
                val p = MpzPass.fromDataItem(Cbor.decode(it.toByteArray()))
                p.uniqueId != pass.uniqueId
            }?.ifEmpty { null }
        )
    }

    companion object
}

/**
 * Synchronizes a local [DocumentStore] so it's in sync with data in a [WalletClientSharedData].
 *
 * This adds/removes documents for which [sharedData] is the source of truth.
 *
 * @receiver the [DocumentStore] to sync.
 * @param sharedData the [WalletClientSharedData] data to sync to.
 * @param mpzPassIsoMdocDomain The domain string to use when creating ISO mdoc credentials.
 * @param mpzPassSdJwtVcDomain The domain string to use when creating SD-JWT VC credentials.
 * @param mpzPassKeylessSdJwtVcDomain the domain string to use when creating keyless SD-JWT VC credentials.
 */
suspend fun DocumentStore.syncWithSharedData(
    sharedData: WalletClientSharedData,
    mpzPassIsoMdocDomain: String,
    mpzPassSdJwtVcDomain: String,
    mpzPassKeylessSdJwtVcDomain: String
) {
    syncMpzPasses(
        sharedData = sharedData,
        mpzPassIsoMdocDomain = mpzPassIsoMdocDomain,
        mpzPassSdJwtVcDomain = mpzPassSdJwtVcDomain,
        mpzPassKeylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain,
    )
}

private suspend fun DocumentStore.syncMpzPasses(
    sharedData: WalletClientSharedData,
    mpzPassIsoMdocDomain: String = "mdoc",
    mpzPassSdJwtVcDomain: String = "sdjwtvc",
    mpzPassKeylessSdJwtVcDomain: String = "sdjwtvc_keyless"
) {
    val mpzPasses = sharedData.getMpzPasses()

    Logger.i(TAG, "syncMpzPasses: Running")
    // First add / update passes
    mpzPasses.forEach { pass ->
        val documentForPass = listDocuments().find {
            it.mpzPassId == pass.uniqueId
        }
        if (documentForPass == null) {
            importMpzPass(
                mpzPass = pass,
                isoMdocDomain = mpzPassIsoMdocDomain,
                sdJwtVcDomain = mpzPassSdJwtVcDomain,
                keylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain
            )
            Logger.i(TAG, "syncMpzPasses: Imported pass ${pass.uniqueId} at version ${pass.version}")
        } else if (documentForPass.mpzPassVersion!! < pass.version) {
            val oldVersion = documentForPass.mpzPassVersion!!
            importMpzPass(
                mpzPass = pass,
                isoMdocDomain = mpzPassIsoMdocDomain,
                sdJwtVcDomain = mpzPassSdJwtVcDomain,
                keylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain
            )
            Logger.i(TAG, "syncMpzPasses: Updated pass ${pass.uniqueId} from version $oldVersion to ${pass.version}")
        }
    }

    // Then remove passes in DocumentStore which no longer exists in shared data
    for (document in listDocuments()) {
        if (document.mpzPassId == null) {
            continue
        }
        val pass = mpzPasses.find { it.uniqueId == document.mpzPassId }
        if (pass == null) {
            deleteDocument(document.identifier)
            Logger.i(TAG, "syncMpzPasses: Removed pass ${document.mpzPassId} at version ${document.mpzPassVersion}")
        }
    }
}
