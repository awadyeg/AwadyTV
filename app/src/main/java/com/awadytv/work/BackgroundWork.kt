package com.awadytv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.awadytv.data.repository.CatalogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

object BackgroundWork {
    private const val CATALOG_ONCE = "catalog-refresh-once"
    private const val CATALOG_PERIODIC = "catalog-refresh"
    const val EPG_PERIODIC = "epg-refresh"
    const val EPG_ONCE = "epg-refresh-once"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context, epgHours: Int = 6) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            CATALOG_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogRefreshWorker>(12, TimeUnit.HOURS).setConstraints(network).build(),
        )
        scheduleEpg(context, epgHours, ExistingPeriodicWorkPolicy.KEEP)
    }

    fun scheduleEpg(context: Context, hours: Int, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EPG_PERIODIC,
            policy,
            PeriodicWorkRequestBuilder<EpgRefreshWorker>(hours.coerceAtLeast(1).toLong(), TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    fun refreshEpgNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            EPG_ONCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<EpgRefreshWorker>().setConstraints(network).build(),
        )
    }

    /** Stale-while-revalidate: Room content is shown immediately, this refreshes it in the background. */
    fun refreshCatalogIfStale(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            CATALOG_ONCE,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CatalogRefreshWorker>().setConstraints(network).build(),
        )
    }
}

@HiltWorker
class CatalogRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val catalog: CatalogRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { catalog.refreshIfStale() }.fold({ Result.success() }, { Result.retry() })
}

/** Refreshes the EPG every N hours (default 6) from XMLTV, falling back to short EPG for favourite channels. */
@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val profiles: com.awadytv.data.repository.ProfileRepository,
    private val epg: com.awadytv.data.repository.EpgRepository,
    private val live: com.awadytv.data.repository.LiveRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = profiles.currentSession() ?: return Result.success()
        return runCatching {
            epg.refreshFull(session, live.favouriteChannels(session.profileId))
        }.fold({ Result.success() }, { Result.retry() })
    }
}
