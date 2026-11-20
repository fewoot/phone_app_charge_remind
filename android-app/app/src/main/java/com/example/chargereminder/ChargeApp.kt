package com.example.chargereminder

import android.app.Application
import com.example.chargereminder.alarm.Reminders
import com.example.chargereminder.data.Repo
import com.example.chargereminder.notify.Notifications
import com.example.chargereminder.util.Images

class ChargeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Images.cleanup(this, referencedPhotos())
        Notifications.ensureChannel(this)
        Reminders.rescheduleAll(this)
    }

    /** 当前还在用的图片：设备图标 + 背景图；其它都算孤儿，启动时清掉 */
    private fun referencedPhotos(): Set<String> {
        val keep = mutableSetOf<String>()
        Repo.data.devices.forEach { device -> device.photo?.let { keep += it } }
        Repo.data.settings.bgImage?.let { keep += it }
        return keep
    }
}
