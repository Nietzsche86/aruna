package com.example.scheduler

import android.Manifest
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
import com.example.MainActivity
import com.example.R

class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val taskChannel = NotificationChannel(
                CHANNEL_TASKS,
                "Tugas Terjadwal Aruna",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Hasil rangkuman dan pengingat tugas terjadwal dari Aruna"
            }
            val approvalChannel = NotificationChannel(
                CHANNEL_APPROVALS,
                "Persetujuan Aksi Aruna",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Konfirmasi interaktif untuk aksi sensitif yang dipicu di latar belakang"
            }
            mgr.createNotificationChannel(taskChannel)
            mgr.createNotificationChannel(approvalChannel)
        }
    }

    fun showTaskResult(taskId: String, title: String, body: String) {
        if (!canPostNotifications()) return
        ensureChannels()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
    }

    fun showApprovalRequest(
        approvalId: String,
        toolName: String,
        summary: String,
    ) {
        if (!canPostNotifications()) return
        ensureChannels()

        val approveIntent = Intent(context, ApprovalReceiver::class.java).apply {
            action = ApprovalReceiver.ACTION_APPROVE
            putExtra(ApprovalReceiver.EXTRA_APPROVAL_ID, approvalId)
        }
        val approvePending = PendingIntent.getBroadcast(
            context,
            (approvalId + "_approve").hashCode(),
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val rejectIntent = Intent(context, ApprovalReceiver::class.java).apply {
            action = ApprovalReceiver.ACTION_REJECT
            putExtra(ApprovalReceiver.EXTRA_APPROVAL_ID, approvalId)
        }
        val rejectPending = PendingIntent.getBroadcast(
            context,
            (approvalId + "_reject").hashCode(),
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            approvalId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Persetujuan Aksi: $toolName")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Aruna meminta izin menjalankan:\n$summary"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPending)
            .addAction(0, context.getString(R.string.action_approve), approvePending)
            .addAction(0, context.getString(R.string.action_reject), rejectPending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(approvalId.hashCode(), notification)
    }

    fun cancelNotification(idKey: String) {
        NotificationManagerCompat.from(context).cancel(idKey.hashCode())
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_TASKS = "aruna_tasks"
        const val CHANNEL_APPROVALS = "aruna_approvals"
    }
}
