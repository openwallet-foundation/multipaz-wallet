package org.multipaz.wallet.android.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.multipaz.util.Logger
import java.util.concurrent.TimeUnit

private const val TAG = "DailyBookkeepingScheduler"

/**
 * Schedules periodic background execution of [DailyBookkeepingWorker] every 24 hours.
 *
 * ### Manual Execution & Testing:
 * You can manually trigger the daily bookkeeping worker during testing/debugging using any of the following methods:
 *
 * 1. **In-App Developer Settings (Recommended)**:
 *    Enable Developer Mode (tap the main screen title 5 times), navigate to **Developer Settings**,
 *    and tap **Run daily bookkeeping** to execute the bookkeeping logic immediately on demand.
 *
 * 2. **Programmatic One-Time Trigger (ADB or Code)**:
 *    To test immediate background execution without waiting for the 24-hour periodic schedule interval:
 *    ```kotlin
 *    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<DailyBookkeepingWorker>().build())
 *    ```
 *
 * 3. **ADB Shell (Command Line Note)**:
 *    `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler <PACKAGE> <JOB_ID>` forces JobScheduler to wake the app,
 *    but WorkManager's `WorkerWrapper` enforces schedule timing for 24h [PeriodicWorkRequestBuilder] instances
 *    (`"Delaying execution for ... because it is being executed before schedule."`). Use option 1 or 2 for immediate manual testing.
 */
object DailyBookkeepingScheduler {
    fun scheduleDailyBookkeeping(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val dailyRequest = PeriodicWorkRequestBuilder<DailyBookkeepingWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyBookkeepingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyRequest
        )
        Logger.i(TAG, "Scheduled daily bookkeeping periodic work (24h interval).")
    }
}
