package com.example.chargereminder.data

import kotlinx.serialization.Serializable
import java.util.UUID

const val TYPE_RECURRING = "recurring"
const val TYPE_ONETIME = "onetime"

const val UNIT_DAY = "day"
const val UNIT_WEEK = "week"
const val UNIT_MONTH = "month"

@Serializable
data class ChargeRecord(
    val at: Long,
    val pct: Int? = null,
    val gap: Int? = null,
    /** 这次充电触发的间隔调整：调整前 / 调整后的天数；没调整就是 null */
    val fromDays: Int? = null,
    val toDays: Int? = null
)

@Serializable
data class Device(
    val id: String,
    val name: String,
    val emoji: String = "🔋",
    val photo: String? = null,
    val color: String? = null,
    val type: String = TYPE_RECURRING,
    val intervalValue: Int = 7,
    val unit: String = UNIT_DAY,
    val dueDate: Long? = null,
    val remindHour: Int = 9,
    val remindMinute: Int = 0,
    val advanceDays: Int = 0,
    val adaptive: Boolean = true,
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val snoozeDays: Int = 0,
    val note: String = "",
    val lastChargedAt: Long = 0L,
    val createdAt: Long = 0L,
    val history: List<ChargeRecord> = emptyList()
)

@Serializable
data class Settings(
    val theme: String = "auto",
    val palette: String = "mint",
    val bgType: String = "gradient",
    val bgImage: String? = null,
    val blur: Int = 8,
    val dim: Int = 45,
    val dynamicColor: Boolean = true,
    val defaultIntervalValue: Int = 7,
    val defaultIntervalUnit: String = UNIT_DAY,
    val defaultRemindTime: String = "09:00",
    val defaultAdvance: Int = 0,
    val dndStart: String = "23:00",
    val dndEnd: String = "08:00",
    val strength: String = "notification",
    val dailyOverdue: Boolean = true
)

@Serializable
data class AppData(
    val devices: List<Device> = emptyList(),
    val settings: Settings = Settings(),
    /** 数据版本，用于以后做默认值迁移（老文件没有这个字段时默认 1） */
    val version: Int = 1
)

fun newDevice(): Device {
    val now = System.currentTimeMillis()
    return Device(
        id = UUID.randomUUID().toString(),
        name = "",
        lastChargedAt = now,
        createdAt = now
    )
}
