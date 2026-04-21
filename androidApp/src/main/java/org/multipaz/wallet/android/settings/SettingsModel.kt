package org.multipaz.wallet.android.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemDateTimeString
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509CertChain
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import kotlin.time.Instant

class SettingsModel private constructor(
    private val readOnly: Boolean
) {
    private lateinit var settingsTable: StorageTable

    companion object {
        const val DEFAULT_PROVISIONING_SERVER_URL = "https://issuer.multipaz.org/issuer"

        private val tableSpec = StorageTableSpec(
            name = "Settings",
            supportPartitions = false,
            supportExpiration = false
        )

        /**
         * Asynchronous construction.
         *
         * @param storage the [org.multipaz.storage.Storage] backing the settings.
         * @param readOnly if `false`, won't monitor all the settings and write to storage when they change.
         */
        suspend fun create(
            storage: Storage,
            readOnly: Boolean = false
        ): SettingsModel {
            val instance = SettingsModel(readOnly)
            instance.settingsTable = storage.getTable(tableSpec)
            instance.init()
            return instance
        }
    }

    private data class BoundItem<T>(
        val variable: MutableStateFlow<T>,
        val defaultValue: T
    ) {
        fun resetValue() {
            variable.value = defaultValue
        }
    }

    private val boundItems = mutableListOf<BoundItem<*>>()

    private suspend inline fun<reified T> bind(
        variable: MutableStateFlow<T>,
        key: String,
        defaultValue: T
    ) {
        val value = settingsTable.get(key)?.let {
            val dataItem = Cbor.decode(it.toByteArray())
            if (dataItem == Simple.NULL) {
                null
            } else {
                when (T::class) {
                    Instant::class -> dataItem.asDateTimeString
                    Long::class -> dataItem.asNumber
                    Boolean::class -> dataItem.asBoolean
                    String::class -> dataItem.asTstr
                    ByteString::class -> ByteString(dataItem.asBstr)
                    List::class -> dataItem.asArray.map { item -> (item as Tstr).value }
                    EcCurve::class -> EcCurve.entries.find { it.name == dataItem.asTstr }
                    EcPrivateKey::class -> EcPrivateKey.fromDataItem(dataItem)
                    X509CertChain::class -> X509CertChain.fromDataItem(dataItem)
                    else -> { throw IllegalStateException("Type not supported") }
                }
            }
        } ?: defaultValue
        variable.value = value as T

        if (!readOnly) {
            CoroutineScope(Dispatchers.Default).launch {
                variable.asStateFlow().collect { newValue ->
                    val dataItem = if (newValue == null) {
                        Simple.NULL
                    } else {
                        when (T::class) {
                            Instant::class -> (newValue as Instant).toDataItemDateTimeString()
                            Long::class -> (newValue as Long).toDataItem()
                            Boolean::class -> (newValue as Boolean).toDataItem()
                            String::class -> (newValue as String).toDataItem()
                            ByteString::class -> (newValue as ByteString).toByteArray().toDataItem()
                            List::class -> {
                                buildCborArray {
                                    (newValue as List<*>).forEach { add(Tstr(it as String)) }
                                }
                            }
                            EcCurve::class -> (newValue as EcCurve).name.toDataItem()
                            EcPrivateKey::class -> (newValue as EcPrivateKey).toDataItem()
                            X509CertChain::class -> (newValue as X509CertChain).toDataItem()
                            else -> { throw IllegalStateException("Type not supported") }
                        }
                    }
                    if (settingsTable.get(key) == null) {
                        settingsTable.insert(key, ByteString(Cbor.encode(dataItem)))
                    } else {
                        settingsTable.update(key, ByteString(Cbor.encode(dataItem)))
                    }
                }
            }
        }
        boundItems.add(BoundItem(variable, defaultValue))
    }

    fun resetSettings() {
        boundItems.forEach { it.resetValue() }
    }

    // TODO: use something like KSP to avoid having to repeat settings name three times..
    //

    private suspend fun init() {
        bind(explicitlySignedOut, "explicitlySignedOut", false)
        bind(devMode, "devMode", false)
        bind(walletBackendUrl, "walletBackendUrl", null)
        bind(firstTimeSetupDone, "firstTimeSetupDone", false)
        bind(eventLoggingEnabled, "eventLoggingEnabled", true)
        bind(eventLoggingLocationEnabled, "eventLoggingLocationEnabled", true)
        bind(provisioningServerUrl, "provisioningServerUrl", DEFAULT_PROVISIONING_SERVER_URL)
    }

    val explicitlySignedOut = MutableStateFlow<Boolean>(false)
    val devMode = MutableStateFlow<Boolean>(false)
    val walletBackendUrl = MutableStateFlow<String?>(null)
    val firstTimeSetupDone = MutableStateFlow<Boolean>(false)
    val eventLoggingEnabled = MutableStateFlow<Boolean>(true)
    val eventLoggingLocationEnabled = MutableStateFlow<Boolean>(false)
    val provisioningServerUrl = MutableStateFlow<String>(DEFAULT_PROVISIONING_SERVER_URL)
}
