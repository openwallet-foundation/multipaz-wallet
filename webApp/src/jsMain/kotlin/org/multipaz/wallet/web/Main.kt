package org.multipaz.wallet.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.util.Platform
import org.multipaz.wallet.client.WalletClient
import io.ktor.client.engine.js.Js
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.addKnownTypes
import org.multipaz.util.Logger
import react.create
import react.dom.client.createRoot
import web.dom.Element

private const val TAG = "Main"

fun main() {
    js("require('./style.css')")
    window.onload = {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val storage = Platform.storage
                val secureArea = Platform.getSecureArea(storage)
                val backendUrl = window.location.origin
                
                val walletClient = WalletClient.create(
                    url = backendUrl,
                    secret = BuildConfig.BACKEND_SECRET,
                    storage = storage,
                    secureArea = secureArea,
                    httpClientEngineFactory = Js
                )

                val documentTypeRepository = DocumentTypeRepository()
                documentTypeRepository.addKnownTypes()
                
                val googleSignIn = GoogleSignIn(BuildConfig.BACKEND_CLIENT_ID)
                googleSignIn.initialize()

                val rootElement = document.getElementById("root") ?: error("No root element found")
                val root = createRoot(rootElement.unsafeCast<Element>())
                root.render(App.create {
                    this.walletClient = walletClient
                    this.googleSignIn = googleSignIn
                    this.documentTypeRepository = documentTypeRepository
                })
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to initialize application", e)
                val root = document.getElementById("root")
                if (root != null) {
                    root.innerHTML = "<h1>Error initializing application</h1><p>${e.message}</p>"
                }
            }
        }
    }
}
