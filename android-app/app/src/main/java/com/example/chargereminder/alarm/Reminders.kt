package com.example.chargereminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.chargereminder.data.Device
import com.example.chargereminder.data.Repo
import com.example.chargereminder.data.due
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒不靠后台常驻：只往系统里塞一条精确闹钟，到点系统把 App 叫醒发通知。
 */
object Reminders {

    const val ACTION_REMIND = "com.example.chargereminder.action.REMIND"
    const val ACTION_CHARGED = "com.example.chargereminder.action.CHARGED"
    const val ACTION_SNOOZE = "com.example.chargereminder.action.SNOOZE"
    const val EXTRA_ID = "device_id"

    fun pendingIntent(context: Context, deviceId: String, action: String, offset: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ID, deviceId)
        }
        return PendingIntent.getBroadcast(
            context,
            deviceId.hashCode() + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun rescheduleAll(context: Context) {
        Repo.data.devices.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, device: Device) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, device.id, ACTION_REMIND, 0)
        am.cancel(pi)
        if (!device.enabled) return

        val at = nextTrigger(device) ?: return
        if (canExact(am)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // 没给精确闹钟权限时退化成不精确闹钟，至少还能响
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, deviceId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, deviceId, ACTION_REMIND, 0))
    }

    fun canExact(am: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

    /** 下一次该响的时间；没有未来时间点时返回 null。 */
    fun nextTrigger(device: Device): Long? {
        if (!device.enabled) return null

        val dueDate = device.due()
        val time = LocalTime.of(device.remindHour, device.remindMinute)
        val target = dueDate.minusDays(device.advanceDays.toLong()).atTime(time)
        val now = LocalDateTime.now()
        val today = LocalDate.now()

        val fireAt: LocalDateTime = when {
            target.isAfter(now) -> target
            !dueDate.isAfter(today) -> {
                if (!Repo.data.settings.dailyOverdue) return null
                today.plusDays(1).atTime(time)
            }
            // 已进入提前提醒窗口但时间点过了：改成到期当天提醒
            else -> dueDate.atTime(time)
        }

        return fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
