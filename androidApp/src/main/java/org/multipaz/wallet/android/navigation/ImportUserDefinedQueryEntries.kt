package org.multipaz.wallet.android.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import org.multipaz.provisioning.ProvisioningMetadata
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.verification.ImportUserDefinedQueryCredentialScreen
import org.multipaz.wallet.android.ui.verification.ImportUserDefinedQueryDataElementsScreen
import org.multipaz.wallet.android.ui.verification.ImportUserDefinedQueryUrlScreen
import org.multipaz.wallet.client.WalletClient

/**
 * Gets the entry for [key] if it is one of the destinations for importing a user defined query from OpenID4VCI
 * issuer metadata.
 *
 * The issuer URL is shared with the one entered when adding a credential from an issuer. Once the user has picked
 * the data elements, the import destinations are replaced by [SelectUserDefinedQueryDestination] for a new query
 * made of the selection, which the user can still change before saving it.
 *
 * @return the entry, or `null` if [key] is not an import destination.
 */
internal fun importUserDefinedQueryEntry(
    key: NavKey,
    backStack: MutableList<NavKey>,
    settingsModel: SettingsModel,
    provisioningModel: ProvisioningModel,
    walletClient: WalletClient
): NavEntry<NavKey>? {
    val getIssuerMetadata: suspend (String) -> ProvisioningMetadata = { issuerUrl ->
        provisioningModel.getOpenID4VCIIssuerMetadata(
            issuerUrl = issuerUrl,
            clientPreferences = walletClient.getOpenID4VCIClientPreferences()
        )
    }
    return when (key) {
        is ImportUserDefinedQueryUrlDestination -> NavEntry(key) {
            val issuerUrl by settingsModel.provisioningServerUrl.collectAsState()
            ImportUserDefinedQueryUrlScreen(
                initialIssuerUrl = issuerUrl,
                defaultIssuerUrl = SettingsModel.DEFAULT_PROVISIONING_SERVER_URL,
                onConnect = { issuerUrl ->
                    settingsModel.provisioningServerUrl.value = issuerUrl
                    backStack.add(ImportUserDefinedQueryCredentialDestination(issuerUrl))
                },
                onBackClicked = { backStack.removeAt(backStack.size - 1) }
            )
        }
        is ImportUserDefinedQueryCredentialDestination -> NavEntry(key) {
            ImportUserDefinedQueryCredentialScreen(
                issuerUrl = key.issuerUrl,
                getIssuerMetadata = getIssuerMetadata,
                onCredentialSelected = { credentialId ->
                    backStack.add(ImportUserDefinedQueryDataElementsDestination(key.issuerUrl, credentialId))
                },
                onBackClicked = { backStack.removeAt(backStack.size - 1) }
            )
        }
        is ImportUserDefinedQueryDataElementsDestination -> NavEntry(key) {
            ImportUserDefinedQueryDataElementsScreen(
                issuerUrl = key.issuerUrl,
                credentialId = key.credentialId,
                getIssuerMetadata = getIssuerMetadata,
                onImported = { query ->
                    while (backStack.lastOrNull().isImportDestination()) {
                        backStack.removeAt(backStack.size - 1)
                    }
                    backStack.add(SelectUserDefinedQueryDestination(query))
                },
                onBackClicked = { backStack.removeAt(backStack.size - 1) }
            )
        }
        else -> null
    }
}

private fun NavKey?.isImportDestination(): Boolean =
    this is ImportUserDefinedQueryUrlDestination ||
        this is ImportUserDefinedQueryCredentialDestination ||
        this is ImportUserDefinedQueryDataElementsDestination
