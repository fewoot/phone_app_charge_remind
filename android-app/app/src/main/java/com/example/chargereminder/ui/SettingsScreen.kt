package com.example.chargereminder.ui

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chargereminder.data.AppData
import com.example.chargereminder.data.Repo
import com.example.chargereminder.data.Settings
import com.example.chargereminder.data.parseTime
import com.example.chargereminder.notify.Notifications
import com.example.chargereminder.util.Images
import com.example.chargereminder.util.formatTime
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    data: AppData,
    colors: AppColors,
    onBack: () -> Unit,
    onSettings: ((Settings) -> Settings) -> Unit
) {
    val context = LocalContext.current
    val settings = data.settings

    var refresh by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationOn = remember(refresh) { Notifications.canNotify(context) }
    val exactOn = remember(refresh) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }
    val batteryOn = remember(refresh) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = Images.saveUri(context, uri, 1600, false)
            if (name != null) {
                onSettings { it.copy(bgType = "image", bgImage = name) }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(Repo.exportJson().toByteArray())
                }
            }.isSuccess
            Toast.makeText(context, if (ok) "备份已导出" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val ok = text != null && Repo.importJson(text)
            Toast.makeText(context, if (ok) "导入成功" else "文件格式不对", Toast.LENGTH_SHORT).show()
        }
    }

    var showClear by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }

    PageColumn(colors) {
        TopBar("设置", colors, onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = 28.dp)
        ) {
            GroupTitle("外观", colors)
            SectionCard(colors) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("主题", fontSize = 14.sp, color = colors.text, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = listOf("auto" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                        selected = settings.theme,
                        onSelect = { value -> onSettings { it.copy(theme = value) } },
                        colors = colors
                    )
                }
                DividerLine(colors)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("配色", fontSize = 14.sp, color = colors.text, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Palettes.forEach { palette ->
                            val selected = settings.palette == palette.key
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(palette.light)
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) colors.text else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onSettings { it.copy(palette = palette.key) } }
                            )
                        }
                    }
                }
                DividerLine(colors)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("背景", fontSize = 14.sp, color = colors.text, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = listOf("solid" to "纯色", "gradient" to "渐变", "image" to "图片"),
                        selected = settings.bgType,
                        onSelect = { value -> onSettings { it.copy(bgType = value) } },
                        colors = colors
                    )
                    if (settings.bgType == "image") {
                        Spacer(Modifier.height(10.dp))
                        val preview = remember(settings.bgImage) {
                            Images.load(context, settings.bgImage, 800)?.asImageBitmap()
                        }
                        if (preview != null) {
                            Image(
                                bitmap = preview,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accent.copy(alpha = 0.13f))
                                .clickable {
                                    bgPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (settings.bgImage == null) "从相册选择图片" else "更换背景图片",
                                color = colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        SliderRow("模糊", settings.blur.toFloat(), 0f..25f, "${settings.blur}", colors) { value ->
                            onSettings { it.copy(blur = value.roundToInt()) }
                        }
                        SliderRow("暗化", settings.dim.toFloat(), 0f..70f, "${settings.dim}%", colors) { value ->
                            onSettings { it.copy(dim = value.roundToInt()) }
                        }
                    }
                }
                DividerLine(colors)
                SwitchRow(
                    title = "动态取色",
                    desc = "Android 12+ 跟随系统壁纸取色",
                    checked = settings.dynamicColor,
                    colors = colors
                ) { value -> onSettings { it.copy(dynamicColor = value) } }
            }

            Spacer(Modifier.height(18.dp))
            GroupTitle("提醒", colors)
            SectionCard(colors) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("默认间隔", fontSize = 14.sp, color = colors.text, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = settings.defaultIntervalValue.toString(),
                            onValueChange = { text ->
                                val value = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                                onSettings { it.copy(defaultIntervalValue = value.coerceIn(1, 365)) }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(88.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Segmented(
                            options = listOf("day" to "天", "week" to "周", "month" to "月"),
                            selected = settings.defaultIntervalUnit,
                            onSelect = { value -> onSettings { it.copy(defaultIntervalUnit = value) } },
                            colors = colors,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                DividerLine(colors)
                ValueRow(
                    title = "默认提醒时间",
                    value = settings.defaultRemindTime,
                    colors = colors
                ) {
                    val time = parseTime(settings.defaultRemindTime)
                    TimePickerDialog(context, { _, h, m ->
                        onSettings { it.copy(defaultRemindTime = formatTime(h, m)) }
                    }, time.hour, time.minute, true).show()
                }
                DividerLine(colors)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("默认提前提醒", fontSize = 14.sp, color = colors.text, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = listOf("0" to "当天", "1" to "1 天前", "3" to "3 天前"),
                        selected = settings.defaultAdvance.toString(),
                        onSelect = { value -> onSettings { it.copy(defaultAdvance = value.toIntOrNull() ?: 0) } },
                        colors = colors
                    )
                }
                DividerLine(colors)
                ValueRow(title = "免打扰开始", value = settings.dndStart, colors = colors) {
                    val time = parseTime(settings.dndStart)
                    TimePickerDialog(context, { _, h, m ->
                        onSettings { it.copy(dndStart = formatTime(h, m)) }
                    }, time.hour, time.minute, true).show()
                }
                DividerLine(colors)
                ValueRow(title = "免打扰结束", value = settings.dndEnd, colors = colors) {
                    val time = parseTime(settings.dndEnd)
                    TimePickerDialog(context, { _, h, m ->
                        onSettings { it.copy(dndEnd = formatTime(h, m)) }
                    }, time.hour, time.minute, true).show()
                }
                DividerLine(colors)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("提醒强度", fontSize = 14.sp, color = colors.text, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = listOf("notification" to "普通通知", "alarm" to "闹钟式"),
                        selected = settings.strength,
                        onSelect = { value -> onSettings { it.copy(strength = value) } },
                        colors = colors
                    )
                }
                DividerLine(colors)
                SwitchRow(
                    title = "逾期每天提醒",
                    desc = "逾期后每天同一时间再提醒一次",
                    checked = settings.dailyOverdue,
                    colors = colors
                ) { value -> onSettings { it.copy(dailyOverdue = value) } }
            }

            Spacer(Modifier.height(18.dp))
            GroupTitle("权限自检", colors)
            SectionCard(colors) {
                PermissionRow(
                    title = "通知权限",
                    desc = "接收提醒；关闭后不会弹通知",
                    ok = notificationOn,
                    colors = colors
                ) {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
                DividerLine(colors)
                PermissionRow(
                    title = "精确闹钟",
                    desc = "保证到点准时提醒",
                    ok = exactOn,
                    colors = colors
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
                DividerLine(colors)
                PermissionRow(
                    title = "电池优化",
                    desc = "允许后台被唤醒，国产 ROM 还需要开自启动",
                    ok = batteryOn,
                    colors = colors
                ) {
                    context.startActivity(
                        Intent(
                            AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
                DividerLine(colors)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent.copy(alpha = 0.13f))
                        .clickable {
                            val first = data.devices.firstOrNull()
                            when {
                                first == null ->
                                    Toast.makeText(context, "先添加一台设备", Toast.LENGTH_SHORT).show()
                                !Notifications.canNotify(context) ->
                                    Toast.makeText(context, "通知权限还没开", Toast.LENGTH_SHORT).show()
                                else -> Notifications.showDue(context, first)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("发送一条测试通知", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(18.dp))
            GroupTitle("数据", colors)
            SectionCard(colors) {
                ValueRow(title = "导出备份（JSON）", value = "", colors = colors) {
                    exportLauncher.launch("charge-reminder-backup.json")
                }
                DividerLine(colors)
                ValueRow(title = "导入备份", value = "", colors = colors) {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                }
                DividerLine(colors)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClear = true }
                        .padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("清空所有数据", color = colors.danger, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "数据只存在本机，不联网。卸载前记得导出备份。",
                fontSize = 11.5.sp,
                color = colors.text3,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(18.dp))
            GroupTitle("关于", colors)
            SectionCard(colors) {
                ValueRow(title = "版本", value = "1.0.0", colors = colors) {}
                DividerLine(colors)
                ValueRow(title = "隐私说明", value = "", colors = colors) {
                    info = "隐私说明" to "所有数据只保存在你的手机本地。不联网、不收集、不上传、没有账号。"
                }
                DividerLine(colors)
                ValueRow(title = "开源许可", value = "", colors = colors) {
                    info = "开源许可" to "本应用基于 Kotlin、Jetpack Compose、kotlinx.serialization 等开源项目构建。"
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "充电提醒 · 本地提醒，不收集任何数据",
                fontSize = 11.5.sp,
                color = colors.text3,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "由 YKL 构思设计 · DeepSeek 🐋 编程实现",
                    fontSize = 11.5.sp,
                    color = colors.text3
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Designed by YKL · Built with DeepSeek",
                    fontSize = 10.sp,
                    color = colors.text3.copy(alpha = 0.75f)
                )
            }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空所有数据") },
            text = { Text("会删除全部设备与充电记录，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    Repo.update { it.copy(devices = emptyList()) }
                }) { Text("清空", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("取消") }
            }
        )
    }

    info?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { info = null }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun GroupTitle(text: String, colors: AppColors) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.text3,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    colors: AppColors,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = colors.text, fontWeight = FontWeight.Medium)
            if (desc.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 11.5.sp, color = colors.text3)
            }
        }
        AppSwitch(checked, onChange)
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    colors: AppColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, color = colors.text)
        Spacer(Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, fontSize = 13.sp, color = colors.text3)
        } else {
            ChevronRight(colors)
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    ok: Boolean,
    colors: AppColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = colors.text, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 11.5.sp, color = colors.text3)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (ok) colors.accent.copy(alpha = 0.13f) else colors.amberBg)
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (ok) "已开启" else "去开启",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ok) colors.accent else colors.amber
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    colors: AppColors,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.5.sp, color = colors.text2, modifier = Modifier.width(34.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(valueText, fontSize = 12.sp, color = colors.text2, modifier = Modifier.width(36.dp))
    }
}
