package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.dao.ScheduledTaskDao
import com.example.data.entity.ScheduledTaskEntity
import java.util.concurrent.TimeUnit

class TaskScheduler(
    private val context: Context,
    private val taskDao: ScheduledTaskDao,
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    suspend fun scheduleTask(task: ScheduledTaskEntity) {
        if (!task.enabled) {
            cancelTask(task.id)
            return
        }
        val now = System.currentTimeMillis()
        val nextRun = runCatching { CronParser.nextRunMillis(task.cron, now) }
            .getOrElse { now + TimeUnit.HOURS.toMillis(24) }

        taskDao.upsert(task.copy(nextRunAt = nextRun))
        val delayMs = (nextRun - now).coerceAtLeast(10_000L)

        if (task.useExactAlarm && canScheduleExactAlarm()) {
            scheduleExactAlarm(task.id, nextRun)
        } else {
            enqueueDelayedWork(task.id, delayMs)
        }
    }

    fun enqueueImmediateWork(taskId: String) {
        val data = Data.Builder()
            .putString(TaskWorker.KEY_TASK_ID, taskId)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork("aruna_task_$taskId", ExistingWorkPolicy.REPLACE, req)
    }

    private fun enqueueDelayedWork(taskId: String, delayMs: Long) {
        val data = Data.Builder()
            .putString(TaskWorker.KEY_TASK_ID, taskId)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork("aruna_task_$taskId", ExistingWorkPolicy.REPLACE, req)
    }

    private fun scheduleExactAlarm(taskId: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ExactTaskAlarmReceiver::class.java).apply {
            putExtra(TaskWorker.KEY_TASK_ID, taskId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    fun cancelTask(taskId: String) {
        workManager.cancelUniqueWork("aruna_task_$taskId")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ExactTaskAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            alarmManager.cancel(pending)
        }
    }

    suspend fun rescheduleAllEnabled() {
        taskDao.getEnabled().forEach { scheduleTask(it) }
    }

    private fun canScheduleExactAlarm(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }
}
