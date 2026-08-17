package org.multipaz.wallet.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.multipaz.wallet.android.App
import org.multipaz.wallet.android.RefreshReason

class PeriodicBookkeepingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = App.getInstance()
        val success = app.refreshWallet(
            reason = RefreshReason.PERIODIC_WORKER
        )
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        const val TAG = "PeriodicBookkeepingWorker"
        const val WORK_NAME = "org.multipaz.wallet.android.PERIODIC_BOOKKEEPING_WORK"
    }
}
