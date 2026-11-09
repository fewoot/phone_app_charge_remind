package com.example.chargereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chargereminder.data.Repo

/** 重启 / 改时间 / 改时区 / 应用更新后，把所有闹钟重新排一遍。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        Reminders.rescheduleAll(context)
    }
}
