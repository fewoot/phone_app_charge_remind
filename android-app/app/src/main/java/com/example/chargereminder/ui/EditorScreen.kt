package com.example.chargereminder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargereminder.data.Device
import com.example.chargereminder.data.TYPE_ONETIME
import com.example.chargereminder.data.TYPE_RECURRING
import com.example.chargereminder.data.UNIT_DAY
import com.example.chargereminder.data.UNIT_MONTH
import com.example.chargereminder.data.UNIT_WEEK
import com.example.chargereminder.data.toLocalDate
import com.example.chargereminder.data.toMillis
import com.example.chargereminder.util.Images
import com.example.chargereminder.util.formatDateCn
import com.example.chargereminder.util.formatTime
import com.example.chargereminder.util.weekdayCn
import java.time.LocalDate

private val TEMPLATES = listOf(
    Triple("充电宝", "🔋", 14),
    Triple("手表", "⌚", 7),
    Triple("耳机", "🎧", 10),
    Triple("电动牙刷", "🪥", 14),
    Triple("剃须刀", "🪒", 21),
    Triple("相机电池", "📷", 30),
    Triple("扫地机", "🤖", 30),
    Triple("其他", "💡", 14)
)

private val EMOJIS = listOf("🔋", "⌚", "🎧", "🪥", "🪒", "📷", "🤖", "💡", "🎮", "📱", "🔦", "🎒")

private val SWATCHES = listOf("#0B8F6A", "#2563EB", "#C2620F", "#6D4AE0", "#DB2777", "#64748B")

@Composable
fun EditorScreen(
    initial: Device,
    isNew: Boolean,
    colors: AppColors,
    onBack: () -> Unit,
    onSave: (Device) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(initial.id) { mutableStateOf(initial) }
    // 单独存输入框里的文本，允许暂时的空值 / 0，保存时再校验
    var intervalText by remember(initial.id) { mutableStateOf(initial.intervalValue.toString()) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = Images.saveUri(context, uri, 320, true)
            if (name != null) draft = draft.copy(photo = name)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val name = Images.saveBitmap(context, bitmap, 320, true)
            if (name != null) draft = draft.copy(photo = name)
        }
    }

    fun pickTime(hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h, m) }, hour, minute, true).show()
    }

    fun pickDate(initialDate: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).show()
    }

    Box(Modifier.fillMaxSize()) {
        PageColumn(colors) {
            TopBar(if (isNew) "添加设备" else "编辑设备", colors, onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 104.dp)
            ) {
                FieldLabel("设备名称", colors)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    placeholder = { Text("例如：充电宝", color = colors.text3) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                FieldLabel("快捷模板", colors)
                TemplateGrid(colors) { name, emoji, interval ->
                    draft = draft.copy(
                        name = name,
                        emoji = emoji,
                        photo = null,
                        intervalValue = interval,
                        unit = UNIT_DAY
                    )
                    intervalText = interval.toString()
                }

                Spacer(Modifier.height(18.dp))
                FieldLabel("类型", colors)
                Segmented(
                    options = listOf(TYPE_RECURRING to "定期充电", TYPE_ONETIME to "一次性"),
                    selected = draft.type,
                    onSelect = { value ->
                        draft = if (value == TYPE_ONETIME) {
                            draft.copy(
                                type = value,
                                dueDate = draft.dueDate ?: LocalDate.now().plusDays(7).toMillis()
                            )
                        } else {
                            draft.copy(type = value)
                        }
                    },
                    colors = colors
                )

                Spacer(Modifier.height(18.dp))
                if (draft.type == TYPE_RECURRING) {
                    FieldLabel("间隔", colors)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { text ->
                                val filtered = text.filter { it.isDigit() }.take(3)
                                intervalText = filtered
                                draft = draft.copy(intervalValue = filtered.toIntOrNull() ?: 0)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.width(96.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Segmented(
                            options = listOf(UNIT_DAY to "天", UNIT_WEEK to "周", UNIT_MONTH to "月"),
                            selected = draft.unit,
                            onSelect = { draft = draft.copy(unit = it) },
                            colors = colors,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(3, 7, 14, 30).forEach { days ->
                            val selected = draft.unit == UNIT_DAY && draft.intervalValue == days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.accent.copy(alpha = 0.15f) else colors.chip)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) colors.accent.copy(alpha = 0.35f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        draft = draft.copy(intervalValue = days, unit = UNIT_DAY)
                                        intervalText = days.toString()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$days 天",
                                    fontSize = 12.sp,
                                    color = if (selected) colors.accent else colors.text2,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点上面的数字快速填入，也可以自己改；单位在右边选（周/月会换算成天）",
                        fontSize = 11.5.sp,
                        color = colors.text3
                    )

                    Spacer(Modifier.height(18.dp))
                    FieldLabel("上次充电日期（用于校准）", colors)
                    ClickableField(
                        text = formatDateCn(draft.lastChargedAt.toLocalDate()) + " " +
                            weekdayCn(draft.lastChargedAt.toLocalDate()),
                        colors = colors
                    ) {
                        pickDate(draft.lastChargedAt.toLocalDate()) { date ->
                            draft = draft.copy(lastChargedAt = date.toMillis() + 9 * 60 * 60 * 1000)
                        }
                    }
                } else {
                    FieldLabel("到期日", colors)
                    ClickableField(
                        text = draft.dueDate?.let {
                            val date = it.toLocalDate()
                            formatDateCn(date) + " " + weekdayCn(date)
                        } ?: "选择日期",
                        colors = colors
                    ) {
                        pickDate(draft.dueDate?.toLocalDate() ?: LocalDate.now().plusDays(7)) { date ->
                            draft = draft.copy(dueDate = date.toMillis())
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("一次性任务完成后会自动标记为已完成", fontSize = 11.5.sp, color = colors.text3)
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("提醒时间", colors)
                        ClickableField(formatTime(draft.remindHour, draft.remindMinute), colors) {
                            pickTime(draft.remindHour, draft.remindMinute) { h, m ->
                                draft = draft.copy(remindHour = h, remindMinute = m)
                            }
                        }
                    }
                    Column(Modifier.weight(1.2f)) {
                        FieldLabel("提前提醒", colors)
                        Segmented(
                            options = listOf("0" to "当天", "1" to "1 天", "3" to "3 天"),
                            selected = draft.advanceDays.toString(),
                            onSelect = { draft = draft.copy(advanceDays = it.toIntOrNull() ?: 0) },
                            colors = colors
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                FieldLabel("图标", colors)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPreview(draft, colors)
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillButton(
                            text = "📷 拍照",
                            onClick = { camera.launch(null) },
                            background = colors.accent.copy(alpha = 0.13f),
                            foreground = colors.accent,
                            height = 34.dp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(
                                text = "相册",
                                onClick = {
                                    gallery.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                background = colors.accent.copy(alpha = 0.13f),
                                foreground = colors.accent,
                                height = 34.dp
                            )
                            if (draft.photo != null) {
                                PillButton(
                                    text = "清除",
                                    onClick = { draft = draft.copy(photo = null) },
                                    background = colors.chip,
                                    foreground = colors.text2,
                                    height = 34.dp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                EmojiGrid(selected = draft.emoji, colors = colors) { emoji ->
                    draft = draft.copy(emoji = emoji, photo = null)
                }

                Spacer(Modifier.height(20.dp))
                FieldLabel("颜色", colors)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SWATCHES.forEach { hex ->
                        val selected = (draft.color ?: SWATCHES[0]) == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex) ?: colors.accent)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) colors.text else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { draft = draft.copy(color = hex) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionCard(colors) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("充电自适应", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.text)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "记录剩余电量后，自动微调下次间隔",
                                fontSize = 11.5.sp,
                                color = colors.text3
                            )
                        }
                        AppSwitch(draft.adaptive, { draft = draft.copy(adaptive = it) })
                    }
                }

                Spacer(Modifier.height(18.dp))
                FieldLabel("备注（可选）", colors)
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it) },
                    placeholder = { Text("例如：放在书桌抽屉里", color = colors.text3) },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent)
                    .clickable {
                        when {
                            draft.name.isBlank() ->
                                Toast.makeText(context, "先给设备起个名字", Toast.LENGTH_SHORT).show()
                            draft.type == TYPE_RECURRING && draft.intervalValue <= 0 ->
                                Toast.makeText(context, "间隔要大于 0 天", Toast.LENGTH_SHORT).show()
                            draft.type == TYPE_RECURRING && draft.intervalValue > 365 ->
                                Toast.makeText(context, "间隔最多 365 天", Toast.LENGTH_SHORT).show()
                            else -> onSave(draft)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("保存", color = colors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TemplateGrid(colors: AppColors, onPick: (String, String, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        TEMPLATES.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { (name, emoji, interval) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.chip)
                            .clickable { onPick(name, emoji, interval) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$emoji $name", fontSize = 11.5.sp, color = colors.text2, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EmojiGrid(selected: String, colors: AppColors, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EMOJIS.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { emoji ->
                    val isSelected = emoji == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelected) colors.accent.copy(alpha = 0.15f) else colors.chip)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) colors.accent.copy(alpha = 0.4f) else Color.Transparent,
                                shape = RoundedCornerShape(11.dp)
                            )
                            .clickable { onPick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 19.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPreview(device: Device, colors: AppColors) {
    val context = LocalContext.current
    val bitmap = remember(device.photo) {
        Images.load(context, device.photo, 200)?.asImageBitmap()
    }
    val tint = parseHexColor(device.color) ?: colors.accent
    Box(
        modifier = Modifier
            .size(66.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(device.emoji, fontSize = 30.sp)
        }
    }
}

@Composable
private fun ClickableField(text: String, colors: AppColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 15.sp, color = colors.text)
    }
}
