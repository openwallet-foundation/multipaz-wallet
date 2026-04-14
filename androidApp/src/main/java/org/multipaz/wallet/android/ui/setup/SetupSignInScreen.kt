package org.multipaz.wallet.android.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.compose.text.fromMarkdown
import androidx.compose.ui.text.AnnotatedString
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.signin.SignInWithGoogle
import org.multipaz.wallet.android.ui.ProfilePicture
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientSignedInUser

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch

@Composable
fun SetupSignInScreen(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    onContinueClicked: () -> Unit,
    onSignIn: suspend () -> Unit,
    onSignOut: suspend () -> Unit
) {
    val signedInUser by walletClient.signedInUser.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            signedInUser?.let {
                UserSignedIn(
                    signedInUser = it,
                    isSigningOut = isSigningOut,
                    onContinueClicked = onContinueClicked,
                    onSignOut = {
                        coroutineScope.launch {
                            isSigningOut = true
                            try {
                                onSignOut()
                            } finally {
                                isSigningOut = false
                            }
                        }
                    }
                )
            } ?: UserNotSignedIn(
                settingsModel = settingsModel,
                isSigningIn = isSigningIn,
                onContinueClicked = onContinueClicked,
                onSignIn = {
                    coroutineScope.launch {
                        isSigningIn = true
                        try {
                            onSignIn()
                        } finally {
                            isSigningIn = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun UserSignedIn(
    signedInUser: WalletClientSignedInUser,
    isSigningOut: Boolean,
    onContinueClicked: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        signedInUser.ProfilePicture(size = 96.dp)
        Text(
            text = signedInUser.id,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = stringResource(R.string.setup_sign_in_signed_in_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = AnnotatedString.fromMarkdown(stringResource(R.string.setup_sign_in_signed_in_text)),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(48.dp))

    Button(
        onClick = onContinueClicked,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSigningOut
    ) {
        Text(text = stringResource(R.string.setup_sign_in_signed_in_continue_button))
    }
    TextButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSigningOut
    ) {
        if (isSigningOut) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Text(text = stringResource(R.string.setup_sign_in_signed_in_sign_out_button))
        }
    }
}

@Composable
private fun UserNotSignedIn(
    settingsModel: SettingsModel,
    isSigningIn: Boolean,
    onContinueClicked: () -> Unit,
    onSignIn: () -> Unit
) {
    Icon(
        imageVector = Icons.Outlined.AccountCircle,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = stringResource(R.string.setup_sign_in_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = AnnotatedString.fromMarkdown(stringResource(R.string.setup_sign_in_text)),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(48.dp))

    Button(
        onClick = onSignIn,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSigningIn
    ) {
        if (isSigningIn) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text = stringResource(R.string.setup_sign_in_button))
        }
    }
    TextButton(
        onClick = {
            settingsModel.explicitlySignedOut.value = true
            onContinueClicked()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSigningIn
    ) {
        Text(text = stringResource(R.string.setup_sign_in_skip))
    }
}
