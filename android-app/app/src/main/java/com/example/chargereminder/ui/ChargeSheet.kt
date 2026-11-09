package com.example.chargereminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargereminder.data.Device
import com.example.chargereminder.data.TYPE_RECURRING
import com.example.chargereminder.data.adaptiveResult
import com.example.chargereminder.data.intervalLabel
import com.example.chargereminder.data.toLocalDate
import com.example.chargereminder.util.formatMd
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
fun ChargeSheet(
    device: Device,
    colors: AppColors,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var pct by remember(device.id) { mutableStateOf(25f) }
    var unknown by remember(device.id) { mutableStateOf(false) }

    val pctInt = pct.roundToInt()
    val daysUsed = ChronoUnit.DAYS.between(device.lastChargedAt.toLocalDate(), LocalDate.now())
        .toInt().coerceAtLeast(0)
    val adaptive = if (unknown) null else adaptiveResult(device, pctInt, daysUsed)

    val hint = when {
        unknown -> "不记录剩余电量，下次间隔保持 ${device.intervalLabel()}。"
        device.type != TYPE_RECURRING || !device.adaptive ->
            "充电自适应已关闭，下次间隔保持 ${device.intervalLabel()}。"
        adaptive != null -> adaptive.message
        else -> "剩余 $pctInt%，间隔保持 ${device.intervalLabel()}。"
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(onClick = onDismiss)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(4.dp)
                    .fillMaxWidth(0.12f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.text3.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(14.dp))
            Text("记录这次充电", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "${device.name} · 上次 ${formatMd(device.lastChargedAt.toLocalDate())}",
                fontSize = 12.5.sp,
                color = colors.text2
            )

            Spacer(Modifier.height(18.dp))
            Text("现在还剩多少电？（可选）", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.text2)
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (unknown) "不记录" else "$pctInt%",
                fontSize = if (unknown) 26.sp else 40.sp,
                fontWeight = FontWeight.Bold,
                color = if (unknown) colors.text3 else colors.text,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Slider(
                value = pct,
                onValueChange = {
                    if (!unknown) pct = it
                },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(10, 20, 30, 50).forEach { value ->
                    val selected = !unknown && pctInt == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.accent.copy(alpha = 0.15f) else colors.chip)
                            .clickable {
                                unknown = false
                                pct = value.toFloat()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$value%",
                            fontSize = 12.sp,
                            color = if (selected) colors.accent else colors.text2,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (unknown) colors.accent.copy(alpha = 0.15f) else colors.chip)
                        .clickable { unknown = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "不知道",
                        fontSize = 12.sp,
                        color = if (unknown) colors.accent else colors.text2,
                        fontWeight = if (unknown) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = hint,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.chip)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(colors.chip)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", fontSize = 14.sp, color = colors.text2, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(colors.accent)
                        .clickable { onConfirm(if (unknown) null else pctInt) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存", fontSize = 15.sp, color = colors.onAccent, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
