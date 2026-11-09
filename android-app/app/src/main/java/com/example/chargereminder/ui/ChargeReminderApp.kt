package com.example.chargereminder.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.chargereminder.data.Repo
import com.example.chargereminder.data.newDevice
import com.example.chargereminder.data.parseTime

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data class Editor(val id: String?) : Screen
    data object Settings : Screen
}

@Composable
fun ChargeReminderApp() {
    val context = LocalContext.current
    val data = Repo.data
    val dark = isDarkTheme(data.settings)
    val colors = colorsFor(data.settings)

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var chargeFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = when (val current = screen) {
            is Screen.Editor -> if (current.id != null) Screen.Detail(current.id) else Screen.Home
            is Screen.Detail -> Screen.Home
            Screen.Settings -> Screen.Home
            Screen.Home -> Screen.Home
        }
    }

    BackHandler(enabled = chargeFor != null) {
        chargeFor = null
    }

    MaterialTheme(colorScheme = schemeFor(colors, dark)) {
        Box(Modifier.fillMaxSize()) {
            AppBackground(data.settings, colors, dark) {
                when (val current = screen) {
                    Screen.Home -> HomeScreen(
                        data = data,
                        colors = colors,
                        onOpenSettings = { screen = Screen.Settings },
                        onAdd = { screen = Screen.Editor(null) },
                        onOpenDetail = { id -> screen = Screen.Detail(id) },
                        onToggle = { id, enabled -> Repo.setEnabled(id, enabled) },
                        onCharge = { id -> chargeFor = id }
                    )

                    is Screen.Detail -> {
                        val device = data.devices.firstOrNull { it.id == current.id }
                        if (device == null) {
                            LaunchedEffect(current.id) { screen = Screen.Home }
                        } else {
                            DetailScreen(
                                device = device,
                                colors = colors,
                                onBack = { screen = Screen.Home },
                                onEdit = { screen = Screen.Editor(device.id) },
                                onCharge = { chargeFor = device.id },
                                onDelete = { deleteFor = device.id }
                            )
                        }
                    }

                    is Screen.Editor -> {
                        val initial = remember(current.id) {
                            if (current.id == null) {
                                val time = parseTime(data.settings.defaultRemindTime)
                                newDevice().copy(
                                    intervalValue = data.settings.defaultIntervalValue,
                                    unit = data.settings.defaultIntervalUnit,
                                    remindHour = time.hour,
                                    remindMinute = time.minute,
                                    advanceDays = data.settings.defaultAdvance
                                )
                            } else {
                                data.devices.firstOrNull { it.id == current.id } ?: newDevice()
                            }
                        }
                        EditorScreen(
                            initial = initial,
                            isNew = current.id == null,
                            colors = colors,
                            onBack = {
                                screen = if (current.id != null) Screen.Detail(current.id) else Screen.Home
                            },
                            onSave = { device ->
                                if (current.id == null) Repo.add(device) else Repo.replace(device)
                                screen = if (current.id == null) Screen.Home else Screen.Detail(device.id)
                            }
                        )
                    }

                    Screen.Settings -> SettingsScreen(
                        data = data,
                        colors = colors,
                        onBack = { screen = Screen.Home },
                        onSettings = { transform -> Repo.updateSettings(transform) }
                    )
                }
            }

            chargeFor?.let { id ->
                val device = data.devices.firstOrNull { it.id == id }
                if (device != null) {
                    ChargeSheet(
                        device = device,
                        colors = colors,
                        onDismiss = { chargeFor = null },
                        onConfirm = { pct ->
                            val message = Repo.logCharge(id, pct)
                            chargeFor = null
                            Toast.makeText(
                                context,
                                message ?: "已记录，下次提醒已重新安排",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }

            deleteFor?.let { id ->
                val device = data.devices.firstOrNull { it.id == id }
                if (device != null) {
                    AlertDialog(
                        onDismissRequest = { deleteFor = null },
                        title = { Text("删除「${device.name}」") },
                        text = { Text("设备信息和充电记录都会被删除，无法恢复。") },
                        confirmButton = {
                            TextButton(onClick = {
                                Repo.remove(id)
                                deleteFor = null
                                screen = Screen.Home
                            }) {
                                Text("删除", color = colors.danger)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteFor = null }) { Text("取消") }
                        }
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
