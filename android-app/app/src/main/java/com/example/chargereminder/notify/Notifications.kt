package com.example.chargereminder.notify

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
import com.example.chargereminder.MainActivity
import com.example.chargereminder.R
import com.example.chargereminder.alarm.Reminders
import com.example.chargereminder.data.Device
import com.example.chargereminder.data.Repo
import com.example.chargereminder.data.intervalLabel
import com.example.chargereminder.data.status

object Notifications {

    private const val CHANNEL_ID = "reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "充电提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "到日子提醒你给设备充电"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun cancel(context: Context, deviceId: String) {
        NotificationManagerCompat.from(context).cancel(deviceId.hashCode())
    }

    fun showDue(context: Context, device: Device) {
        ensureChannel(context)

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            device.id.hashCode(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val chargedPi = Reminders.pendingIntent(context, device.id, Reminders.ACTION_CHARGED, 1)
        val snoozePi = Reminders.pendingIntent(context, device.id, Reminders.ACTION_SNOOZE, 2)

        val status = device.status()
        val body = when (device.type) {
            "onetime" -> "一次性任务 · ${status.text}"
            else -> "${status.text} · 每 ${device.intervalLabel()}提醒一次"
        }

        val strength = Repo.data.settings.strength
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_charge)
            .setContentTitle("该给「${device.name}」充电了")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (strength == "alarm") NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_REMINDER
            )
            .addAction(R.drawable.ic_stat_charge, "已充电", chargedPi)
            .addAction(0, "推迟 3 天", snoozePi)

        if (canNotify(context)) {
            NotificationManagerCompat.from(context).notify(device.id.hashCode(), builder.build())
        }
    }
}
