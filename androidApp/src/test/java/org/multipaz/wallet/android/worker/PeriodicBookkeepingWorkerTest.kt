package org.multipaz.wallet.android.worker

import kotlinx.io.bytestring.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.eventlogger.EventSimple
import org.multipaz.wallet.client.PeriodicBookkeepingEventDetails
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
            trigger = "startup",
            success = true,
            publicDataRefreshed = true,
            publicDataError = null,
            sharedDataRefreshed = false,
            sharedDataError = null,
            refreshedCredentialsCount = 2,
            totalDocumentsChecked = 3,
            refreshedDocumentsCount = 1,
            credentialRefreshErrors = listOf("doc1: warning"),
            readerKeysRefreshedCount = 4,
            readerKeysError = null,
            updatedTrustEntriesCount = 4,
            trustManagersChecked = listOf("tm1", "tm2"),
            trustManagerErrors = emptyList(),
            runtimeDurationMs = 1500L
        )
        val event = EventSimple(
            timestamp = Clock.System.now(),
            data = ByteString(),
            appData = mapOf(PeriodicBookkeepingEventDetails.EVENT_APP_DATA_KEY to details.toDataItem())
        )
        val restoredDetails = PeriodicBookkeepingEventDetails.fromEventSimple(event)
        assertNotNull(restoredDetails)
        assertEquals("startup", restoredDetails!!.trigger)
        assertTrue(restoredDetails.success)
        assertEquals(true, restoredDetails.publicDataRefreshed)
        assertEquals(false, restoredDetails.sharedDataRefreshed)
        assertEquals(2, restoredDetails.refreshedCredentialsCount)
        assertEquals(3, restoredDetails.totalDocumentsChecked)
        assertEquals(1, restoredDetails.refreshedDocumentsCount)
        assertEquals(listOf("doc1: warning"), restoredDetails.credentialRefreshErrors)
        assertEquals(4, restoredDetails.readerKeysRefreshedCount)
        assertEquals(4, restoredDetails.updatedTrustEntriesCount)
        assertEquals(listOf("tm1", "tm2"), restoredDetails.trustManagersChecked)
        assertEquals(1500L, restoredDetails.runtimeDurationMs)
    }
}
