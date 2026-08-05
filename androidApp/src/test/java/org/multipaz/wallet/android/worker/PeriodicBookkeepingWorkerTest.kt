package org.multipaz.wallet.android.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.multipaz.eventlogger.EventSimple
import org.multipaz.wallet.client.PeriodicBookkeepingEventDetails
import org.multipaz.wallet.client.fromDataItem
import org.multipaz.wallet.client.toDataItem
import kotlin.time.Clock

class PeriodicBookkeepingWorkerTest {

    @Test
    fun constants_areCorrect() {
        assertEquals("PeriodicBookkeepingWorker", PeriodicBookkeepingWorker.TAG)
        assertEquals("org.multipaz.wallet.android.PERIODIC_BOOKKEEPING_WORK", PeriodicBookkeepingWorker.WORK_NAME)
    }

    @Test
    fun schedulerObject_exists() {
        assertNotNull(PeriodicBookkeepingScheduler)
    }

    @Test
    fun testPeriodicBookkeepingEventDetailsInAppData() {
        val details = PeriodicBookkeepingEventDetails(
            publicDataRefreshed = true,
            sharedDataRefreshed = false,
            refreshedCredentialsCount = 2,
            readerKeysRefreshed = true,
            runtimeDurationMs = 1500L
        )
        val event = EventSimple(
            timestamp = Clock.System.now(),
            data = kotlinx.io.bytestring.ByteString(),
            appData = mapOf("PeriodicBookkeepingEventDetails" to details.toDataItem())
        )
        assertNotNull(event)
        val dataItem = event.appData["PeriodicBookkeepingEventDetails"]
        assertNotNull(dataItem)
        val restoredDetails = PeriodicBookkeepingEventDetails.fromDataItem(dataItem!!)
        assertEquals(true, restoredDetails.publicDataRefreshed)
        assertEquals(false, restoredDetails.sharedDataRefreshed)
        assertEquals(2, restoredDetails.refreshedCredentialsCount)
        assertEquals(true, restoredDetails.readerKeysRefreshed)
        assertEquals(1500L, restoredDetails.runtimeDurationMs)
    }

    @Test
    fun testBackwardsCompatibilityDailyBookkeepingEventDetails() {
        val details = PeriodicBookkeepingEventDetails(
            publicDataRefreshed = true,
            sharedDataRefreshed = false,
            refreshedCredentialsCount = 2,
            readerKeysRefreshed = true,
            runtimeDurationMs = 1500L
        )
        val event = EventSimple(
            timestamp = Clock.System.now(),
            data = kotlinx.io.bytestring.ByteString(),
            appData = mapOf("DailyBookkeepingEventDetails" to details.toDataItem())
        )
        val dataItem = event.appData["PeriodicBookkeepingEventDetails"]
            ?: event.appData["DailyBookkeepingEventDetails"]
        assertNotNull(dataItem)
        val restoredDetails = PeriodicBookkeepingEventDetails.fromDataItem(dataItem!!)
        assertEquals(true, restoredDetails.publicDataRefreshed)
        assertEquals(1500L, restoredDetails.runtimeDurationMs)
    }
}
