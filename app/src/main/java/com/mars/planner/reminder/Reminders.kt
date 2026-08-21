package com.mars.planner.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mars.planner.MainActivity
import com.mars.planner.MarsApplication
import com.mars.planner.R
import com.mars.planner.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object ReminderChannels {
    const val TASKS = "task_reminders"
    const val DIGEST = "daily_digest"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(TASKS, context.getString(R.string.channel_task_reminders), NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(DIGEST, context.getString(R.string.channel_daily_digest), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

object ReminderScheduler {
    fun scheduleTaskReminder(
        context: Context,
        taskId: Long,
        title: String,
        atMillis: Long,
        snoozeMinutes: Int = 30
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SNOOZE_MIN, snoozeMinutes)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply { action = ACTION_FIRE }
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java).cancel(pending)
    }

    fun scheduleDigestWorkers(context: Context) {
        val request = PeriodicWorkRequestBuilder<DigestWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "mars_digest",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    const val ACTION_FIRE = "com.mars.planner.REMINDER_FIRE"
    const val ACTION_DONE = "com.mars.planner.REMINDER_DONE"
    const val ACTION_SNOOZE = "com.mars.planner.REMINDER_SNOOZE"
    const val ACTION_OPEN = "com.mars.planner.REMINDER_OPEN"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_SNOOZE_MIN = "snooze_min"
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderChannels.ensure(context)
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> {
                val snooze = intent.getIntExtra(ReminderScheduler.EXTRA_SNOOZE_MIN, 30)
                showTaskNotification(context, taskId, title, snooze)
            }
            ReminderScheduler.ACTION_DONE -> {
                val pending = goAsync()
                Thread {
                    try {
                        val app = context.applicationContext as MarsApplication
                        runBlocking {
                            app.container.tasks.updateStatus(taskId, TaskStatus.DONE)
                        }
                        NotificationManagerCompat.from(context).cancel(taskId.toInt())
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(ReminderScheduler.EXTRA_SNOOZE_MIN, 30)
                val at = System.currentTimeMillis() + minutes * 60_000L
                ReminderScheduler.scheduleTaskReminder(context, taskId, title, at)
                NotificationManagerCompat.from(context).cancel(taskId.toInt())
            }
            ReminderScheduler.ACTION_OPEN -> {
                val open = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_task_id", taskId)
                }
                context.startActivity(open)
            }
        }
    }

    private fun showTaskNotification(
        context: Context,
        taskId: Long,
        title: String,
        snoozeMinutes: Int = 30
    ) {
        val openIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 1).toInt(),
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_OPEN
                putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                putExtra(ReminderScheduler.EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 2).toInt(),
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_DONE
                putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                putExtra(ReminderScheduler.EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 3).toInt(),
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_SNOOZE
                putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                putExtra(ReminderScheduler.EXTRA_TITLE, title)
                putExtra(ReminderScheduler.EXTRA_SNOOZE_MIN, snoozeMinutes)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderChannels.TASKS)
            .setSmallIcon(R.drawable.ic_stat_mars)
            .setContentTitle("Напоминание Марса")
            .setContentText(title.ifBlank { "Пора заняться задачей" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(0, "Выполнено", doneIntent)
            .addAction(0, "Перенести", snoozeIntent)
            .addAction(0, "Открыть", openIntent)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderChannels.ensure(context)
        ReminderScheduler.scheduleDigestWorkers(context)
    }
}

class DigestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ReminderChannels.ensure(applicationContext)
        val app = applicationContext as MarsApplication
        val settings = app.container.settings.settings.first()
        val now = LocalDateTime.now()
        val today = LocalDate.now().toEpochDay()
        val tasks = app.container.tasks.exportSnapshot().first
            .filter { it.dueDateEpochDay == today && it.parentTaskId == null }
            .filter { it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED }

        val morningTime = LocalTime.of(settings.morningReminderHour, settings.morningReminderMinute)
        val eveningTime = LocalTime.of(settings.eveningReminderHour, settings.eveningReminderMinute)
        val withinMorning = kotlin.math.abs(java.time.Duration.between(now.toLocalTime(), morningTime).toMinutes()) <= 30
        val withinEvening = kotlin.math.abs(java.time.Duration.between(now.toLocalTime(), eveningTime).toMinutes()) <= 30

        if (settings.morningReminderEnabled && withinMorning) {
            val text = if (tasks.isEmpty()) {
                "На сегодня задач нет — можно добавить важные."
            } else {
                tasks.take(5).joinToString("\n") { "• ${it.title}" }
            }
            notifyDigest(1, "Утро с Марсом", text)
        }
        if (settings.eveningReminderEnabled && withinEvening) {
            notifyDigest(2, "Вечерний итог", "Подведи итог дня: что сделано, что перенести на завтра.")
        }
        return Result.success()
    }

    private fun notifyDigest(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(applicationContext, ReminderChannels.DIGEST)
            .setSmallIcon(R.drawable.ic_stat_mars)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        }
    }
}

fun nextReminderMillis(dateEpochDay: Long, timeMinutes: Int): Long {
    val date = LocalDate.ofEpochDay(dateEpochDay)
    val time = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
    return LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
