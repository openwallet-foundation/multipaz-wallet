package org.multipaz.wallet.android.navigation

import android.Manifest
import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
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
import org.multipaz.wallet.android.ui.setup.SetupBlePermissionScreen
import org.multipaz.wallet.android.ui.setup.SetupEulaScreen
import org.multipaz.wallet.android.ui.setup.SetupActivityLoggingLocationScreen
import org.multipaz.wallet.android.ui.setup.SetupActivityLoggingScreen
import org.multipaz.wallet.android.ui.setup.SetupDefaultWalletScreen
import org.multipaz.wallet.android.ui.setup.SetupDeviceCheckScreen
import org.multipaz.wallet.android.ui.setup.SetupScreenLockCheckScreen
import org.multipaz.wallet.android.ui.setup.SetupSignInScreen
import org.multipaz.wallet.android.ui.setup.SetupWelcomeScreen
import org.multipaz.wallet.client.WalletClient
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SetupGraph"

fun NavGraphBuilder.setupGraph(
    navController: NavController,
    walletClient: WalletClient,
    documentStore: DocumentStore,
    settingsModel: SettingsModel,
    signInWithGoogle: SignInWithGoogle,
    coroutineScope: CoroutineScope,
    context: Context,
    showToast: (message: String) -> Unit,
    onAppJustLaunched: suspend (WalletClient, DocumentStore) -> Unit,
    onSignIn: suspend (Context, WalletClient, SignInWithGoogle, NavController, Boolean, Boolean) -> Unit,
    onSignOut: suspend (WalletClient, SettingsModel, SignInWithGoogle) -> Unit
) {
    navigation<SetupGraph>(startDestination = SetupWelcomeScreenDestination) {
        composable<SetupWelcomeScreenDestination> {
            SetupWelcomeScreen(
                onContinueClicked = {
                    navController.navigate(SetupDeviceCheckScreenDestination)
                }
            )
        }
        composable<SetupDeviceCheckScreenDestination> {
            SetupDeviceCheckScreen(
                walletClient = walletClient,
                onContinueClicked = {
                    navController.navigate(SetupScreenLockCheckScreenDestination) {
                        popUpTo(SetupDeviceCheckScreenDestination) { inclusive = true }
                    }
                }
            )
        }
        composable<SetupScreenLockCheckScreenDestination> {
            SetupScreenLockCheckScreen(
                onContinueClicked = {
                    navController.navigate(SetupEulaScreenDestination)
                }
            )
        }
        composable<SetupEulaScreenDestination> {
            SetupEulaScreen(
                loadEula = { locale -> walletClient.getEula(locale) },
                declineText = stringResource(R.string.setup_eula_decline_button),
                acceptText = stringResource(R.string.setup_eula_accept_button),
                onAcceptClicked = {
                    navController.navigate(SetupBlePermissionScreenDestination)
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
        composable<SetupBlePermissionScreenDestination> {
            SetupBlePermissionScreen(
                onContinueClicked = {
                    navController.navigate(SetupSignInScreenDestination)
                }
            )
        }
        composable<SetupSignInScreenDestination> {
            SetupSignInScreen(
                walletClient = walletClient,
                settingsModel = settingsModel,
                onContinueClicked = {
                    navController.navigate(SetupActivityLoggingScreenDestination)
                },
                onSignIn = {
                    onSignIn(
                        context,
                        walletClient,
                        signInWithGoogle,
                        navController,
                        true, // explicitSignIn
                        false // resetEncryptionKey
                    )
                },
                onSignOut = {
                    onSignOut(
                        walletClient,
                        settingsModel,
                        signInWithGoogle
                    )
                }
            )
        }
        val continueToWalletRoleOrMainGraph = {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            val roleAvailable = android.os.Build.VERSION.SDK_INT >= 35 && roleManager?.isRoleAvailable(android.app.role.RoleManager.ROLE_WALLET) == true
            
            if (roleAvailable) {
                navController.navigate(SetupDefaultWalletScreenDestination)
            } else {
                settingsModel.firstTimeSetupDone.value = true
                coroutineScope.launch {
                    onAppJustLaunched(walletClient, documentStore)
                }
                navController.navigate(MainGraph) {
                    popUpTo(SetupGraph) { inclusive = true }
                }
            }
        }

        composable<SetupActivityLoggingScreenDestination> {
            SetupActivityLoggingScreen(
                onEnableClicked = {
                    settingsModel.eventLoggingEnabled.value = true
                    navController.navigate(SetupActivityLoggingLocationScreenDestination)
                },
                onDisableClicked = {
                    settingsModel.eventLoggingEnabled.value = false
                    continueToWalletRoleOrMainGraph()
                }
            )
        }
        composable<SetupActivityLoggingLocationScreenDestination> {
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
        composable<SetupDefaultWalletScreenDestination> {
            SetupDefaultWalletScreen(
                onContinueClicked = {
                    settingsModel.firstTimeSetupDone.value = true
                    coroutineScope.launch {
                        onAppJustLaunched(walletClient, documentStore)
                    }
                    navController.navigate(MainGraph) {
                        popUpTo(SetupGraph) { inclusive = true }
                    }
                }
            )
        }
    }
}