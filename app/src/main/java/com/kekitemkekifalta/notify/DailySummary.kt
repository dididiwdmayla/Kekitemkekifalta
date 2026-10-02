package com.kekitemkekifalta.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kekitemkekifalta.KekitemApp
import com.kekitemkekifalta.MainActivity
import com.kekitemkekifalta.R
import com.kekitemkekifalta.core.DailySummary
import com.kekitemkekifalta.core.StockStatus
import com.kekitemkekifalta.data.SettingsStore
import com.kekitemkekifalta.data.db.ItemEntity
import com.kekitemkekifalta.data.stockState
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * One local summary per day at the configured time.
 * A one-shot work that re-enqueues itself for the next day (no drift), plus KEEP on app start.
 */
class SummaryScheduler(private val context: Context, private val settings: SettingsStore) {

    fun ensureScheduled() = schedule(ExistingWorkPolicy.KEEP)

    fun reschedule() = schedule(ExistingWorkPolicy.REPLACE)

    /** Called by the worker itself: runs after the current execution finishes. */
    fun scheduleNextFromWorker() = schedule(ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun schedule(policy: ExistingWorkPolicy) {
        val workManager = WorkManager.getInstance(context)
        val s = settings.settings.value
        if (!s.notificationsEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = DailySummary.delayUntilNext(System.currentTimeMillis(), s.notificationMinuteOfDay, ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "daily-summary"
    }
}

class DailySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as KekitemApp).container
        val settings = container.settings
        val zone = ZoneId.systemDefault()
        val today = DailySummary.epochDay(System.currentTimeMillis(), zone)
        if (settings.settings.value.notificationsEnabled && settings.lastNotifiedEpochDay != today) {
            val items = container.items.allLiveItems()
            if (SummaryNotifier.post(applicationContext, items)) settings.lastNotifiedEpochDay = today
        }
        container.scheduler.scheduleNextFromWorker()
        return Result.success()
    }
}

object SummaryNotifier {
    private const val CHANNEL_ID = "daily_summary"
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Resumo diário", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "O que está acabando em casa, uma vez por dia."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts the summary; false when there is nothing to say or no permission. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, items: List<ItemEntity>, force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val states = items.mapNotNull { it.stockState(now) }
        val runningLow = states.count { it.status == StockStatus.RUNNING_LOW }
        val probablyOut = states.count { it.status == StockStatus.PROBABLY_OUT }
        val message = DailySummary.message(runningLow, probablyOut)
            ?: if (force) "Tudo em ordem: nada acabando por enquanto." else return false
        if (!canPost(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_KEKIFALTA)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kek)
            .setContentTitle("kekitem: resumo do dia")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
