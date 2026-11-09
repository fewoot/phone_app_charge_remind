package com.example.chargereminder.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.chargereminder.alarm.Reminders
import kotlinx.serialization.json.Json
import java.io.File
import java.time.temporal.ChronoUnit

/**
 * 全部数据只存本地：一个 JSON 文件 + photos/ 里的图片。
 * 用 Compose 的 mutableStateOf 暴露，UI 自动重组。
 */
object Repo {

    private var appContext: Context? = null
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var data by mutableStateOf(AppData())
        private set

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        data = load()
        // 老数据迁移后的默认值立刻落盘
        persist()
    }

    fun context(): Context = appContext ?: error("Repo 尚未初始化")

    private fun file(): File = File(context().filesDir, "data.json")

    private fun load(): AppData = runCatching {
        val f = file()
        if (f.exists()) json.decodeFromString(AppData.serializer(), f.readText()) else AppData()
    }.getOrDefault(AppData()).let { migrate(it) }

    /** v1 -> v2：默认间隔从 14 天改成 7 天（只改仍然用着旧默认值的数据，不动用户自己改过的） */
    private fun migrate(input: AppData): AppData {
        if (input.version >= 2) return input
        val settings = input.settings
        val migrated = if (settings.defaultIntervalValue == 14) {
            settings.copy(defaultIntervalValue = 7)
        } else {
            settings
        }
        return input.copy(settings = migrated, version = 2)
    }

    private fun persist() {
        runCatching { file().writeText(json.encodeToString(AppData.serializer(), data)) }
    }

    fun update(transform: (AppData) -> AppData) {
        data = transform(data)
        persist()
        Reminders.rescheduleAll(context())
    }

    fun device(id: String): Device? = data.devices.firstOrNull { it.id == id }

    fun add(device: Device) = update { it.copy(devices = it.devices + device) }

    fun replace(device: Device) = update { d ->
        d.copy(devices = d.devices.map { if (it.id == device.id) device else it })
    }

    fun remove(id: String) = update { d ->
        d.copy(devices = d.devices.filterNot { it.id == id })
    }

    fun setEnabled(id: String, enabled: Boolean) = update { d ->
        d.copy(devices = d.devices.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun snooze(id: String, days: Int) = update { d ->
        d.copy(devices = d.devices.map { if (it.id == id) it.copy(snoozeDays = it.snoozeDays + days) else it })
    }

    /** 记一次充电，返回给用户看的提示（例如间隔被自适应调整了）。 */
    fun logCharge(id: String, pct: Int?): String? {
        var message: String? = null
        update { d ->
            val dev = d.devices.firstOrNull { it.id == id } ?: return@update d
            val now = System.currentTimeMillis()
            val gap = ChronoUnit.DAYS.between(dev.lastChargedAt.toLocalDate(), now.toLocalDate())
                .toInt().coerceAtLeast(0)

            val result = adaptiveResult(dev, pct, gap)
            val to = result?.to

            var next = dev.copy(
                history = dev.history + ChargeRecord(
                    at = now,
                    pct = pct,
                    gap = gap,
                    fromDays = result?.takeIf { it.changed }?.from,
                    toDays = result?.takeIf { it.changed }?.to
                ),
                lastChargedAt = now,
                snoozeDays = 0
            )

            if (result != null && to != null && to != result.from) {
                next = next.withIntervalDays(to)
                message = "已记录 · 间隔 ${result.from} → $to 天（${result.signText} 天）"
            } else if (next.type == TYPE_ONETIME) {
                message = "已完成"
            } else {
                message = "已记录 · 间隔保持 ${dev.intervalDays()} 天"
            }

            if (next.type == TYPE_ONETIME) {
                next = next.copy(completed = true, enabled = false)
            }

            d.copy(devices = d.devices.map { if (it.id == id) next else it })
        }
        return message
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        data = data.copy(settings = transform(data.settings))
        persist()
        Reminders.rescheduleAll(context())
    }

    fun exportJson(): String = json.encodeToString(AppData.serializer(), data)

    fun importJson(text: String): Boolean {
        val parsed = runCatching { json.decodeFromString(AppData.serializer(), text) }.getOrNull() ?: return false
        data = migrate(parsed)
        persist()
        Reminders.rescheduleAll(context())
        return true
    }
}
