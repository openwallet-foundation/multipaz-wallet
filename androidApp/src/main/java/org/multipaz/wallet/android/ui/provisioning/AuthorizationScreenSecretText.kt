package org.multipaz.wallet.android.ui.provisioning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.compose.PassphraseEntryField
import org.multipaz.provisioning.AuthorizationChallenge
import org.multipaz.provisioning.AuthorizationResponse
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.wallet.android.R
import org.multipaz.wallet.client.WalletClient

private const val TAG = "AuthorizationScreenSecretText"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizationScreenSecretText(
    provisioningModel: ProvisioningModel,
    challenge: AuthorizationChallenge.SecretText,
    onCloseClicked: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val headline = if (challenge.request.isNumeric) {
        stringResource(R.string.provisioning_auth_pin_title)
    } else {
        stringResource(R.string.provisioning_auth_passphrase_title)
    }
    val explainer = if (challenge.request.isNumeric) {
        stringResource(R.string.provisioning_auth_pin_explainer)
    } else {
        stringResource(R.string.provisioning_auth_passphrase_explainer)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(text = headline) },
                navigationIcon = {
                    IconButton(onClick = onCloseClicked) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.provisioning_auth_secret_cancel_description)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val lengthFixed = (challenge.request.length != null)
            val constraints = challenge.request.length?.let {
                PassphraseConstraints(
                    minLength = it,
                    maxLength = it,
                    requireNumerical = challenge.request.isNumeric
                )
            } ?: PassphraseConstraints(
                    minLength = 0,
                    maxLength = Int.MAX_VALUE,
                    requireNumerical = challenge.request.isNumeric
            )

            Text(
                text = explainer,
                style = MaterialTheme.typography.bodySmall,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PassphraseEntryField(
                    constraints = constraints,
                    checkWeakPassphrase = false
                ) { passphrase, meetsRequirements, donePressed ->
                    if ((lengthFixed && meetsRequirements) || (meetsRequirements && donePressed)) {
                        coroutineScope.launch {
                            provisioningModel.provideAuthorizationResponse(
                                AuthorizationResponse.SecretText(
                                    id = challenge.id,
                                    secret = passphrase
                                )
                            )
                        }
                    }
                }
                challenge.request.description?.let {
                    Text(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
