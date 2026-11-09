package com.example.chargereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chargereminder.data.Repo
import com.example.chargereminder.notify.Notifications

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return

        when (intent.action) {
            Reminders.ACTION_CHARGED -> {
                Repo.logCharge(id, null)
                Notifications.cancel(context, id)
            }

            Reminders.ACTION_SNOOZE -> {
                Repo.snooze(id, 3)
                Notifications.cancel(context, id)
            }

            else -> {
                val device = Repo.device(id) ?: return
                if (!device.enabled) return
                if (Notifications.canNotify(context)) {
                    Notifications.showDue(context, device)
                }
                // 没充电就继续排下一次（逾期每天提醒 / 或不再提醒）
                Repo.device(id)?.let { Reminders.schedule(context, it) }
            }
        }
    }
}
