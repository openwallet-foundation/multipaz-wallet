package org.multipaz.wallet.android.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.multipaz.eventlogger.EventSimple
import org.multipaz.wallet.client.DailyBookkeepingEventDetails
import org.multipaz.wallet.client.fromDataItem
import org.multipaz.wallet.client.toDataItem
import kotlin.time.Clock

class DailyBookkeepingWorkerTest {

    @Test
    fun constants_areCorrect() {
        assertEquals("DailyBookkeepingWorker", DailyBookkeepingWorker.TAG)
        assertEquals("org.multipaz.wallet.android.DAILY_BOOKKEEPING_WORK", DailyBookkeepingWorker.WORK_NAME)
    }

    @Test
    fun schedulerObject_exists() {
        assertNotNull(DailyBookkeepingScheduler)
    }

    @Test
    fun testDailyBookkeepingEventDetailsInAppData() {
        val details = DailyBookkeepingEventDetails(
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
        assertNotNull(event)
        val dataItem = event.appData["DailyBookkeepingEventDetails"]
        assertNotNull(dataItem)
        val restoredDetails = DailyBookkeepingEventDetails.fromDataItem(dataItem!!)
        assertEquals(true, restoredDetails.publicDataRefreshed)
        assertEquals(false, restoredDetails.sharedDataRefreshed)
        assertEquals(2, restoredDetails.refreshedCredentialsCount)
        assertEquals(true, restoredDetails.readerKeysRefreshed)
        assertEquals(1500L, restoredDetails.runtimeDurationMs)
    }
}
