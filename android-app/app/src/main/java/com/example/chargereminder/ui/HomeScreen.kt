package com.example.chargereminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargereminder.data.AppData
import com.example.chargereminder.data.Device
import com.example.chargereminder.data.TYPE_ONETIME
import com.example.chargereminder.data.daysLeft
import com.example.chargereminder.data.due
import com.example.chargereminder.data.intervalLabel
import com.example.chargereminder.data.progress
import com.example.chargereminder.data.status
import com.example.chargereminder.data.toLocalDate
import com.example.chargereminder.util.formatDateCn
import com.example.chargereminder.util.formatMd
import com.example.chargereminder.util.formatTime
import com.example.chargereminder.util.weekdayCn
import java.time.LocalDate

@Composable
fun HomeScreen(
    data: AppData,
    colors: AppColors,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onCharge: (String) -> Unit
) {
    val today = LocalDate.now()
    val devices = remember(data.devices) {
        data.devices.sortedWith(compareBy({ if (it.enabled) 0 else 1 }, { it.daysLeft() }))
    }
    val onPhoto = data.settings.bgType == "image" && data.settings.bgImage != null
    val titleColor = if (onPhoto) Color.White else colors.text
    val subColor = if (onPhoto) Color.White.copy(alpha = 0.82f) else colors.text2

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${today.monthValue}月${today.dayOfMonth}日 ${weekdayCn(today)}",
                        fontSize = 12.5.sp,
                        color = subColor
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "充电提醒",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                }
                IconChip(Icons.Filled.Settings, onOpenSettings, colors, "设置")
            }

            Spacer(Modifier.height(12.dp))

            if (devices.isNotEmpty()) {
                SummaryRow(devices, colors, onPhoto)
                Spacer(Modifier.height(12.dp))
            }

            if (devices.isEmpty()) {
                EmptyState(colors, onAdd)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(device, colors, onOpenDetail, onToggle, onCharge)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 26.dp)
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(colors.accent2, colors.accent)))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加设备",
                tint = colors.onAccent,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun SummaryRow(devices: List<Device>, colors: AppColors, onPhoto: Boolean) {
    val overdue = devices.count { it.enabled && it.daysLeft() < 0 }
    val todayCount = devices.count { it.enabled && it.daysLeft() == 0 }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OverviewChip("${devices.size} 台设备", colors, onPhoto, colors.text2, if (onPhoto) Color.White.copy(alpha = 0.18f) else colors.chip)
        if (overdue > 0) {
            OverviewChip("$overdue 台逾期", colors, onPhoto, colors.amber, if (onPhoto) Color.Black.copy(alpha = 0.35f) else colors.amberBg)
        }
        if (todayCount > 0) {
            OverviewChip("$todayCount 台今天", colors, onPhoto, colors.amber, if (onPhoto) Color.Black.copy(alpha = 0.35f) else colors.amberBg)
        }
        if (overdue == 0 && todayCount == 0) {
            OverviewChip("全部正常", colors, onPhoto, colors.accent, if (onPhoto) Color.Black.copy(alpha = 0.35f) else colors.accent.copy(alpha = 0.13f))
        }
    }
}

@Composable
private fun OverviewChip(text: String, colors: AppColors, onPhoto: Boolean, fg: Color, bg: Color) {
    Text(
        text = text,
        color = if (onPhoto) Color.White else fg,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun DeviceCard(
    device: Device,
    colors: AppColors,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onCharge: (String) -> Unit
) {
    val status = device.status()
    val ringColor = when (status.tone) {
        "over" -> colors.danger
        "today", "soon" -> colors.amber
        "off" -> colors.text3
        else -> colors.accent
    }
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.cardBorder, shape)
            .clickable { onOpen(device.id) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                diameter = 58.dp,
                progress = device.progress(),
                color = ringColor,
                trackColor = colors.track,
                strokeWidth = 4.dp
            ) {
                DeviceIcon(device, 44.dp, colors)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusChip(status, colors)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = metaLine(device),
                    fontSize = 11.5.sp,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = dueLine(device),
                    fontSize = 11.sp,
                    color = colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ChevronRight(colors)
        }

        Spacer(Modifier.height(9.dp))
        DividerLine(colors)
        Spacer(Modifier.height(7.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AppSwitch(device.enabled, { onToggle(device.id, it) })
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (device.enabled) "已启用" else "已停用",
                fontSize = 11.5.sp,
                color = colors.text2
            )
            Spacer(Modifier.weight(1f))
            PillButton(
                text = if (device.type == TYPE_ONETIME) "完成" else "已充电",
                onClick = { onCharge(device.id) },
                background = colors.accent,
                foreground = colors.onAccent,
                height = 30.dp
            )
        }
    }
}

private fun metaLine(device: Device): String {
    if (device.type == TYPE_ONETIME) return "一次性任务"
    val base = "上次 ${formatMd(device.lastChargedAt.toLocalDate())} · 每 ${device.intervalLabel()}"
    return if (device.snoozeDays > 0) "$base · 已推迟 ${device.snoozeDays} 天" else base
}

private fun dueLine(device: Device): String {
    val due = device.due()
    val base = "${formatDateCn(due)} ${weekdayCn(due)} · ${formatTime(device.remindHour, device.remindMinute)} 提醒"
    return if (device.advanceDays > 0) "$base · 提前 ${device.advanceDays} 天" else base
}
