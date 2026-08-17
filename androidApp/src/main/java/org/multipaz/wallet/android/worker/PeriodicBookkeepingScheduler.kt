package org.multipaz.wallet.android.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.multipaz.util.Logger
import org.multipaz.wallet.android.BuildConfig
import java.util.concurrent.TimeUnit

private const val TAG = "PeriodicBookkeepingScheduler"

/**
 * Schedules periodic background execution of [PeriodicBookkeepingWorker] based on
 * configured interval in hours ([BuildConfig.PERIODIC_BOOKKEEPING_INTERVAL_HOURS]).
 *
 * ### Manual Execution & Testing:
 * You can manually trigger the periodic bookkeeping worker during testing/debugging using any of the following methods:
 *
 * 1. **In-App Developer Settings (Recommended)**:
 *    Enable Developer Mode (tap the main screen title 5 times), navigate to **Developer Settings**,
 *    and tap **Run periodic bookkeeping** to execute the bookkeeping logic immediately on demand.
 *
 * 2. **Programmatic One-Time Trigger (ADB or Code)**:
 *    To test immediate background execution without waiting for the periodic schedule interval:
 *    ```kotlin
 *    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<PeriodicBookkeepingWorker>().build())
 *    ```
 *
 * 3. **ADB Shell (Command Line Note)**:
 *    `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler <PACKAGE> <JOB_ID>` forces JobScheduler to wake the app,
 *    but WorkManager's `WorkerWrapper` enforces schedule timing for periodic [PeriodicWorkRequestBuilder] instances
 *    (`"Delaying execution for ... because it is being executed before schedule."`). Use option 1 or 2 for immediate manual testing.
 */
object PeriodicBookkeepingScheduler {
    fun schedulePeriodicBookkeeping(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val intervalHours = BuildConfig.PERIODIC_BOOKKEEPING_INTERVAL_HOURS
        val periodicRequest = PeriodicWorkRequestBuilder<PeriodicBookkeepingWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(intervalHours, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PeriodicBookkeepingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        Logger.i(TAG, "Scheduled periodic bookkeeping periodic work (${intervalHours}h interval).")
    }
}
