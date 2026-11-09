package com.example.chargereminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargereminder.data.ChargeRecord
import com.example.chargereminder.data.Device
import com.example.chargereminder.data.TYPE_ONETIME
import com.example.chargereminder.data.avgGap
import com.example.chargereminder.data.due
import com.example.chargereminder.data.intervalDays
import com.example.chargereminder.data.intervalLabel
import com.example.chargereminder.data.progress
import com.example.chargereminder.data.status
import com.example.chargereminder.data.toLocalDate
import com.example.chargereminder.util.formatDateCn
import com.example.chargereminder.util.formatMd
import com.example.chargereminder.util.formatTime
import com.example.chargereminder.util.weekdayCn
import kotlin.math.max

@Composable
fun DetailScreen(
    device: Device,
    colors: AppColors,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCharge: () -> Unit,
    onDelete: () -> Unit
) {
    val status = device.status()
    val ringColor = when (status.tone) {
        "over" -> colors.danger
        "today", "soon" -> colors.amber
        "off" -> colors.text3
        else -> colors.accent
    }
    val due = device.due()

    PageColumn(colors) {
        TopBar(
            title = "设备详情",
            colors = colors,
            onBack = onBack,
            action = {
                Text(
                    text = "编辑",
                    color = colors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProgressRing(
                    diameter = 118.dp,
                    progress = device.progress(),
                    color = ringColor,
                    trackColor = colors.track,
                    strokeWidth = 7.dp
                ) {
                    DeviceIcon(device, 82.dp, colors)
                }
                Spacer(Modifier.height(12.dp))
                Text(device.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Spacer(Modifier.height(8.dp))
                StatusChip(status, colors, large = true)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "下次提醒 ${formatDateCn(due)} ${weekdayCn(due)} · " +
                        formatTime(device.remindHour, device.remindMinute) +
                        (if (device.snoozeDays > 0) "（已推迟 ${device.snoozeDays} 天）" else ""),
                    fontSize = 12.5.sp,
                    color = colors.text2
                )
                if (device.type != TYPE_ONETIME) {
                    Text(
                        text = "上次充电 ${formatDateCn(device.lastChargedAt.toLocalDate())} · 每 ${device.intervalLabel()}",
                        fontSize = 12.5.sp,
                        color = colors.text2
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val avg = device.avgGap()
                StatCard(if (avg == null) "—" else "$avg 天", "平均间隔", colors, Modifier.weight(1f))
                StatCard("${device.history.size}", "已充电次数", colors, Modifier.weight(1f))
                StatCard(if (device.adaptive) "开" else "关", "充电自适应", colors, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            ChartCard(device, colors)

            Spacer(Modifier.height(14.dp))
            HistoryCard(device, colors)

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent)
                    .clickable(onClick = onCharge),
                contentAlignment = Alignment.Center
            ) {
                Text("记录充电", color = colors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.dangerBg)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Text("删除设备", color = colors.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, colors: AppColors, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.5.sp, color = colors.text3)
    }
}

@Composable
private fun ChartCard(device: Device, colors: AppColors) {
    val records = device.history.filter { it.gap != null }.takeLast(6)
    val maxGap = max(device.intervalDays(), records.maxOfOrNull { it.gap ?: 0 } ?: 1)

    SectionCard(colors) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("间隔趋势", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(
                text = "单位：天" + if (device.type != TYPE_ONETIME) " · 目标 ${device.intervalDays()} 天" else "",
                fontSize = 11.sp,
                color = colors.text3
            )
        }

        if (records.size < 2) {
            Text(
                text = "再记录几次充电，就能看到间隔趋势了",
                fontSize = 12.5.sp,
                color = colors.text3,
                modifier = Modifier.padding(bottom = 16.dp, top = 4.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(132.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                records.forEach { record ->
                    val gap = record.gap ?: 0
                    val barHeight = (84f * gap.toFloat() / maxGap.toFloat()).coerceAtLeast(6f).dp
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$gap", fontSize = 9.sp, color = colors.text3)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .height(barHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accent.copy(alpha = 0.85f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(formatMd(record.at.toLocalDate()), fontSize = 9.sp, color = colors.text3)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(device: Device, colors: AppColors) {
    val records = device.history.sortedByDescending { it.at }
    SectionCard(colors) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("充电记录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text("${records.size} 条", fontSize = 11.sp, color = colors.text3)
        }

        if (records.isEmpty()) {
            Text(
                text = "还没有记录，充完电点一下「已充电」就会出现在这里。",
                fontSize = 12.5.sp,
                color = colors.text3,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        } else {
            records.forEachIndexed { index, record ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMd(record.at.toLocalDate()),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = weekdayCn(record.at.toLocalDate()) + " · " +
                            if (record.gap == null) "首次记录" else "间隔 ${record.gap} 天",
                        fontSize = 12.sp,
                        color = colors.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    val chip = historyChip(record)
                    if (chip != null) {
                        Text(
                            text = chip,
                            fontSize = 11.sp,
                            color = colors.text2,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(colors.chip)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                if (index != records.lastIndex) {
                    DividerLine(colors)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun gapOf(record: ChargeRecord): Int = record.gap ?: 0

/** 历史记录右侧的小标签：剩余电量 + 这次间隔怎么变的 */
private fun historyChip(record: ChargeRecord): String? {
    val parts = mutableListOf<String>()
    record.pct?.let { parts += "剩余 $it%" }

    val from = record.fromDays
    val to = record.toDays
    if (from != null && to != null && from != to) {
        val delta = to - from
        parts += "间隔 $from→$to（${if (delta > 0) "+$delta" else "$delta"}）"
    }

    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
