package com.example.chargereminder.util

import java.time.LocalDate

private val WEEK_CN = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun weekdayCn(date: LocalDate): String = WEEK_CN[date.dayOfWeek.value - 1]

fun formatMd(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}"

fun formatDateCn(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
