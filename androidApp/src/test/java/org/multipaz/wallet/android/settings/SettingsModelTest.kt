package org.multipaz.wallet.android.settings

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.Logger

class SettingsModelTest {
    @Test
    fun testLoggingDebugEnabledDefaultsToFalse() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel = SettingsModel.create(storage)

        assertFalse(settingsModel.loggingDebugEnabled.value)
        assertFalse(Logger.isDebugEnabled)
    }

    @Test
    fun testLoggingDebugEnabledUpdatesLogger() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel = SettingsModel.create(storage)

        settingsModel.loggingDebugEnabled.value = true
        // Allow flow collection to run
        kotlinx.coroutines.delay(100)
        assertTrue(settingsModel.loggingDebugEnabled.value)
        assertTrue(Logger.isDebugEnabled)

        settingsModel.loggingDebugEnabled.value = false
        kotlinx.coroutines.delay(100)
        assertFalse(settingsModel.loggingDebugEnabled.value)
        assertFalse(Logger.isDebugEnabled)
    }

    @Test
    fun testLoggingDebugEnabledPersistsAcrossInstances() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel1 = SettingsModel.create(storage)

        settingsModel1.loggingDebugEnabled.value = true
        kotlinx.coroutines.delay(100)
        assertTrue(Logger.isDebugEnabled)

        val settingsModel2 = SettingsModel.create(storage)
        assertTrue(settingsModel2.loggingDebugEnabled.value)
        assertTrue(Logger.isDebugEnabled)
    }
}
