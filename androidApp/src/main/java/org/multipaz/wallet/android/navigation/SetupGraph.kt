package org.multipaz.wallet.android.navigation

import android.Manifest
import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.multipaz.document.DocumentStore
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.signin.SignInWithGoogle
import org.multipaz.wallet.android.ui.setup.SetupActivityLoggingLocationScreen
import org.multipaz.wallet.android.ui.setup.SetupActivityLoggingScreen
import org.multipaz.wallet.android.ui.setup.SetupBlePermissionScreen
import org.multipaz.wallet.android.ui.setup.SetupDefaultWalletScreen
import org.multipaz.wallet.android.ui.setup.SetupDeviceCheckScreen
import org.multipaz.wallet.android.ui.setup.SetupEulaScreen
import org.multipaz.wallet.android.ui.setup.SetupScreenLockCheckScreen
import org.multipaz.wallet.android.ui.setup.SetupSignInScreen
import org.multipaz.wallet.android.ui.setup.SetupWelcomeScreen
import org.multipaz.wallet.client.WalletClient
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SetupGraph"

fun setupGraph(
    backStack: MutableList<NavKey>,
    walletClient: WalletClient,
    documentStore: DocumentStore,
    settingsModel: SettingsModel,
    signInWithGoogle: SignInWithGoogle,
    coroutineScope: CoroutineScope,
    context: Context,
    showToast: (message: String) -> Unit,
    onAppJustLaunched: suspend (WalletClient, DocumentStore) -> Unit,
    onSignIn: suspend (Context, WalletClient, SignInWithGoogle, MutableList<NavKey>, Boolean, Boolean) -> Unit,
    onSignOut: suspend (WalletClient, SettingsModel, SignInWithGoogle) -> Unit
): (NavKey) -> NavEntry<NavKey>? {
    val continueToWalletRoleOrMainGraph = {
        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
        val roleAvailable = android.os.Build.VERSION.SDK_INT >= 35 && roleManager?.isRoleAvailable(android.app.role.RoleManager.ROLE_WALLET) == true

        if (roleAvailable) {
            backStack.add(SetupDefaultWalletScreenDestination)
        } else {
            settingsModel.firstTimeSetupDone.value = true
            coroutineScope.launch {
                onAppJustLaunched(walletClient, documentStore)
            }
            backStack.clear()
            backStack.add(WalletDestination())
        }
    }

    return { key ->
        when (key) {
            is SetupWelcomeScreenDestination -> NavEntry(key) {
                SetupWelcomeScreen(
                    walletClient = walletClient,
                    settingsModel = settingsModel,
                    onContinueClicked = {
                        backStack.add(SetupDeviceCheckScreenDestination)
                    },
                    showToast = showToast
                )
            }
            is SetupDeviceCheckScreenDestination -> NavEntry(key) {
                SetupDeviceCheckScreen(
                    walletClient = walletClient,
                    onContinueClicked = {
                        backStack.removeAll { it is SetupDeviceCheckScreenDestination }
                        backStack.add(SetupScreenLockCheckScreenDestination)
                    }
                )
            }
            is SetupScreenLockCheckScreenDestination -> NavEntry(key) {
                SetupScreenLockCheckScreen(
                    onContinueClicked = {
                        backStack.add(SetupEulaScreenDestination)
                    }
                )
            }
            is SetupEulaScreenDestination -> NavEntry(key) {
                SetupEulaScreen(
                    loadEula = { locale -> walletClient.getEula(locale) },
                    declineText = stringResource(R.string.setup_eula_decline_button),
                    acceptText = stringResource(R.string.setup_eula_accept_button),
                    onAcceptClicked = {
                        backStack.add(SetupBlePermissionScreenDestination)
                    },
                    onDeclineClicked = {
                        coroutineScope.launch {
                            showToast(context.getString(R.string.setup_eula_declined_see_ya_text))
                            delay(2.seconds)
                            exitProcess(0)
                        }
                    },
                )
            }
            is SetupBlePermissionScreenDestination -> NavEntry(key) {
                SetupBlePermissionScreen(
                    onContinueClicked = {
                        backStack.add(SetupSignInScreenDestination)
                    }
                )
            }
            is SetupSignInScreenDestination -> NavEntry(key) {
                SetupSignInScreen(
                    walletClient = walletClient,
                    settingsModel = settingsModel,
                    onContinueClicked = {
                        backStack.add(SetupActivityLoggingScreenDestination)
                    },
                    onSignIn = {
                        coroutineScope.launch {
                            onSignIn(
                                context,
                                walletClient,
                                signInWithGoogle,
                                backStack,
                                true, // explicitSignIn
                                false // resetEncryptionKey
                            )
                        }
                    },
                    onSignOut = {
                        coroutineScope.launch {
                            onSignOut(
                                walletClient,
                                settingsModel,
                                signInWithGoogle
                            )
                        }
                    }
                )
            }
            is SetupActivityLoggingScreenDestination -> NavEntry(key) {
                SetupActivityLoggingScreen(
                    onEnableClicked = {
                        settingsModel.eventLoggingEnabled.value = true
                        backStack.add(SetupActivityLoggingLocationScreenDestination)
                    },
                    onDisableClicked = {
                        settingsModel.eventLoggingEnabled.value = false
                        continueToWalletRoleOrMainGraph()
                    }
                )
            }
            is SetupActivityLoggingLocationScreenDestination -> NavEntry(key) {
                val completeSetup = {
                    continueToWalletRoleOrMainGraph()
                }

                @OptIn(ExperimentalPermissionsApi::class)
                val locationPermissionState = rememberPermissionState(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) { isGranted ->
                    settingsModel.eventLoggingLocationEnabled.value = isGranted
                    completeSetup()
                }

                SetupActivityLoggingLocationScreen(
                    onEnableClicked = {
                        @OptIn(ExperimentalPermissionsApi::class)
                        if (locationPermissionState.status.isGranted) {
                            settingsModel.eventLoggingLocationEnabled.value = true
                            completeSetup()
                        } else {
                            locationPermissionState.launchPermissionRequest()
                        }
                    },
                    onSkipClicked = {
                        settingsModel.eventLoggingLocationEnabled.value = false
                        completeSetup()
                    }
                )
            }
            is SetupDefaultWalletScreenDestination -> NavEntry(key) {
                SetupDefaultWalletScreen(
                    onContinueClicked = {
                        settingsModel.firstTimeSetupDone.value = true
                        coroutineScope.launch {
                            onAppJustLaunched(walletClient, documentStore)
                        }
                        backStack.clear()
                        backStack.add(WalletDestination())
                    }
                )
            }
            else -> null
        }
    }
}
