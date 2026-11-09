package com.example.chargereminder

import android.app.Application
import com.example.chargereminder.alarm.Reminders
import com.example.chargereminder.data.Repo
import com.example.chargereminder.notify.Notifications

class ChargeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifications.ensureChannel(this)
        Reminders.rescheduleAll(this)
    }
}
