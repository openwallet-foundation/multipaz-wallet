package org.multipaz.wallet.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.multipaz.wallet.android.App
import org.multipaz.wallet.client.runDailyBookkeeping

class DailyBookkeepingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = App.getInstance()
        val success = app.walletClient.runDailyBookkeeping(
            documentStore = app.documentStore,
            provisioningModel = app.provisioningModel,
            eventLogger = app.eventLogger
        )
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        const val TAG = "DailyBookkeepingWorker"
        const val WORK_NAME = "org.multipaz.wallet.android.DAILY_BOOKKEEPING_WORK"
    }
}
