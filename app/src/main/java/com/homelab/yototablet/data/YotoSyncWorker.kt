package com.homelab.yototablet.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.homelab.yototablet.YotoApplication

class YotoSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? YotoApplication ?: return Result.failure()
        val state = app.syncManager.runSyncSuspending(forceRefresh = true)
        return if (state.isServerReachable) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
