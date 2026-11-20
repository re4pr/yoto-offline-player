package com.homelab.yototablet

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.homelab.yototablet.data.YotoOfflineSyncManager
import com.homelab.yototablet.data.YotoSyncWorker
import com.homelab.yototablet.media.YotoPlayerController
import java.util.concurrent.TimeUnit

class YotoApplication : Application() {

    lateinit var syncManager: YotoOfflineSyncManager
        private set

    lateinit var playerController: YotoPlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        syncManager = YotoOfflineSyncManager(this).apply {
            initialize()
        }
        playerController = YotoPlayerController(this).apply {
            connect()
        }
        schedulePeriodicOfflineSync()
    }

    private fun schedulePeriodicOfflineSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<YotoSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        runCatching {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "yoto_periodic_offline_sync"
    }
}
