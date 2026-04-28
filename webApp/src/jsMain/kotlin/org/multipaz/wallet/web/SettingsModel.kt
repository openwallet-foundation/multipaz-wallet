package org.multipaz.wallet.web

import kotlinx.browser.localStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class DarkMode(val label: String) {
    AUTO("Automatic"),
    LIGHT("Light"),
    DARK("Dark")
}

class SettingsModel private constructor() {
    companion object {
        fun create(): SettingsModel {
            val instance = SettingsModel()
            instance.init()
            return instance
        }
    }

    val darkMode = MutableStateFlow(DarkMode.AUTO)
    val devMode = MutableStateFlow(true)
    val provisioningServerUrl = MutableStateFlow("https://issuer.multipaz.org/issuer")

    private fun init() {
        bind(darkMode, "darkMode", DarkMode.AUTO) { DarkMode.valueOf(it) }
        bind(devMode, "devMode", true) { it.toBoolean() }
        bind(provisioningServerUrl, "provisioningServerUrl", "https://issuer.multipaz.org/issuer") { it }
    }

    private fun <T> bind(
        variable: MutableStateFlow<T>,
        key: String,
        defaultValue: T,
        parser: (String) -> T
    ) {
        val savedValue = localStorage.getItem(key)
        variable.value = if (savedValue != null) {
            try {
                parser(savedValue)
            } catch (e: Exception) {
                defaultValue
            }
        } else {
            defaultValue
        }

        CoroutineScope(Dispatchers.Main).launch {
            variable.collect { newValue ->
                localStorage.setItem(key, newValue.toString())
            }
        }
    }
}
