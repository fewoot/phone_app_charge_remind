package com.example.chargereminder.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Status(val tone: String, val text: String)

fun Device.intervalDays(): Int = when (unit) {
    UNIT_WEEK -> intervalValue * 7
    UNIT_MONTH -> intervalValue * 30
    else -> intervalValue
}.coerceAtLeast(1)

fun Device.due(): LocalDate =
    if (type == TYPE_ONETIME) {
        (dueDate ?: System.currentTimeMillis()).toLocalDate()
    } else {
        lastChargedAt.toLocalDate().plusDays((intervalDays() + snoozeDays).toLong())
    }

fun Device.daysLeft(): Int = ChronoUnit.DAYS.between(LocalDate.now(), due()).toInt()

fun Device.progress(): Float =
    if (type == TYPE_ONETIME) {
        val total = max(1L, ChronoUnit.DAYS.between(createdAt.toLocalDate(), due()))
        (1f - daysLeft().toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        val elapsed = ChronoUnit.DAYS.between(lastChargedAt.toLocalDate(), LocalDate.now()).toFloat()
        (elapsed / intervalDays().toFloat()).coerceIn(0f, 1f)
    }

fun Device.status(): Status {
    if (type == TYPE_ONETIME && completed) return Status("done", "已完成")
    if (!enabled) return Status("off", "已停用")
    val n = daysLeft()
    return when {
        n > 3 -> Status("ok", "还有 $n 天")
        n > 0 -> Status("soon", "还有 $n 天")
        n == 0 -> Status("today", "今天该充了")
        else -> Status("over", "逾期 ${-n} 天")
    }
}

fun Device.intervalLabel(): String = when (unit) {
    UNIT_WEEK -> "$intervalValue 周"
    UNIT_MONTH -> "$intervalValue 个月"
    else -> "$intervalValue 天"
}

fun Device.avgGap(): Int? {
    val gaps = history.mapNotNull { it.gap }
    if (gaps.isEmpty()) return null
    return (gaps.sum().toFloat() / gaps.size).roundToInt()
}

data class AdaptiveResult(
    val from: Int,
    /** 调整后的天数；null 表示这次不调整 */
    val to: Int?,
    val daysUsed: Int,
    val actualPct: Int,
    /** 按原计划，在第 daysUsed 天时「应该」还剩多少 */
    val expectedPct: Int,
    /** 按实测耗电速度，到 20% 需要多少天（可能带小数，仅展示） */
    val estimatedRaw: Float,
    val estimatedDays: Int,
    val message: String
) {
    val changed: Boolean get() = to != null && to != from
    val delta: Int get() = (to ?: from) - from
    val signText: String get() = if (delta > 0) "+$delta" else "$delta"
}

/**
 * 充电自适应：不只看「剩多少电」，而是按「用了几天 + 剩多少」算实际耗电速度，
 * 推到 20% 需要多少天，再和当前间隔比较。
 *
 * 这样提前充电不会被误判：
 * 计划 14 天从 100% 用到 20%，第 7 天充时理论剩 60%；
 * 实际剩 60% → 符合预期，保持；实际剩 80% → 放电偏慢，才拉长。
 *
 * 库里永远只存整数天；estimatedRaw 只是算出来给用户看的小数。
 */
fun adaptiveResult(d: Device, pct: Int?, daysUsed: Int): AdaptiveResult? {
    if (d.type != TYPE_RECURRING || !d.adaptive || pct == null) return null

    val from = d.intervalDays()
    val target = 20
    val usable = 100 - target

    if (daysUsed <= 0) {
        return AdaptiveResult(
            from = from,
            to = null,
            daysUsed = 0,
            actualPct = pct,
            expectedPct = 100,
            estimatedRaw = from.toFloat(),
            estimatedDays = from,
            message = "上次充电就在今天，这次不参与自适应，间隔保持 $from 天。"
        )
    }

    // 按原计划线性放电，第 daysUsed 天应该剩多少
    val expected = (100f - usable.toFloat() * daysUsed / from).roundToInt().coerceIn(0, 100)

    // 还没到计划时间、电量又很高：更像临时补电，这次不参与学习
    if (pct > 84 && daysUsed < from) {
        return AdaptiveResult(
            from = from,
            to = null,
            daysUsed = daysUsed,
            actualPct = pct,
            expectedPct = expected,
            estimatedRaw = from.toFloat(),
            estimatedDays = from,
            message = "第 $daysUsed 天充时还剩 $pct%，更像一次补电，这次不参与自适应（间隔保持 $from 天）。"
        )
    }

    val consumed = (100 - pct).coerceAtLeast(1)
    val rate = consumed.toFloat() / daysUsed
    val estimatedRaw = usable / rate
    val estimated = estimatedRaw.roundToInt()

    // 一次最多调整 ±30%，避免某一次异常耗电把间隔带跑偏
    val low = max(1, (from * 0.7f).roundToInt())
    val high = min(365, (from * 1.3f).roundToInt())
    val nextRaw = when {
        estimated > from -> max(from + 1, estimated)
        estimated < from -> min(from - 1, estimated)
        else -> from
    }
    val next = nextRaw.coerceIn(low, high)

    val rawText = String.format(Locale.US, "%.1f", estimatedRaw)

    if (next == from) {
        return AdaptiveResult(
            from = from,
            to = null,
            daysUsed = daysUsed,
            actualPct = pct,
            expectedPct = expected,
            estimatedRaw = estimatedRaw,
            estimatedDays = estimated,
            message = "第 $daysUsed 天充时剩 $pct%（计划此时约 $expected%），" +
                "按这个速度约 $rawText 天到 $target%，和当前 $from 天一致，间隔保持。"
        )
    }

    val slower = next > from
    val sign = if (slower) "+" else ""
    val message = buildString {
        append("第 $daysUsed 天充时剩 $pct%（计划此时约 $expected%），放电比预期")
        append(if (slower) "慢" else "快")
        append("；按这个速度约 $rawText 天到 $target%，")
        if (next != nextRaw) {
            append("一次最多调整 30%，本次先调到 $next 天（$sign${next - from}）。")
        } else {
            append("本次调到 $next 天（$sign${next - from}）。")
        }
    }
    return AdaptiveResult(
        from = from,
        to = next,
        daysUsed = daysUsed,
        actualPct = pct,
        expectedPct = expected,
        estimatedRaw = estimatedRaw,
        estimatedDays = estimated,
        message = message
    )
}

fun Device.withIntervalDays(days: Int): Device {
    val perUnit = when (unit) {
        UNIT_WEEK -> 7
        UNIT_MONTH -> 30
        else -> 1
    }
    return if (days % perUnit == 0) {
        copy(intervalValue = days / perUnit)
    } else {
        copy(unit = UNIT_DAY, intervalValue = days)
    }
}

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.toMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun parseTime(text: String): LocalTime =
    runCatching { LocalTime.parse(text) }.getOrDefault(LocalTime.of(9, 0))
