package com.example.chargereminder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargereminder.data.Device
import com.example.chargereminder.data.Settings
import com.example.chargereminder.data.Status
import com.example.chargereminder.util.Images

/* ---------------- 背景 ---------------- */

@Composable
fun AppBackground(
    settings: Settings,
    colors: AppColors,
    dark: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(settings.bgType, settings.bgImage) {
        if (settings.bgType == "image") {
            Images.load(context, settings.bgImage, 1600)?.asImageBitmap()
        } else {
            null
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            val alpha = (settings.dim / 100f + if (dark) 0.12f else 0f).coerceIn(0f, 0.86f)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to colors.bgTop,
                            0.55f to colors.bgTop,
                            1f to colors.bgBottom
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(colors.accent.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(180f, 0f),
                            radius = 900f
                        )
                    )
            )
        }
        content()
    }
}

@Composable
fun PageColumn(
    colors: AppColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.bgTop, colors.bgBottom)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp),
        content = content
    )
}

/* ---------------- 基础控件 ---------------- */

@Composable
fun IconChip(
    icon: ImageVector,
    onClick: () -> Unit,
    colors: AppColors,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = colors.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TopBar(
    title: String,
    colors: AppColors,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(Icons.Filled.ArrowBack, onBack, colors, "返回")
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

@Composable
fun ProgressRing(
    diameter: Dp,
    progress: Float,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2f
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            if (progress > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

@Composable
fun DeviceIcon(device: Device, size: Dp, colors: AppColors) {
    val context = LocalContext.current
    val bitmap = remember(device.photo) {
        Images.load(context, device.photo, 160)?.asImageBitmap()
    }
    val tint = parseHexColor(device.color) ?: colors.accent

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = device.emoji,
                fontSize = with(LocalDensity.current) { (size * 0.52f).toSp() }
            )
        }
    }
}

fun parseHexColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
}

@Composable
fun StatusChip(status: Status, colors: AppColors, large: Boolean = false) {
    val fg = when (status.tone) {
        "over" -> colors.danger
        "today", "soon" -> colors.amber
        "off" -> colors.text3
        else -> colors.accent
    }
    val bg = when (status.tone) {
        "over" -> colors.dangerBg
        "today", "soon" -> colors.amberBg
        "off" -> colors.chip
        else -> colors.accent.copy(alpha = 0.13f)
    }
    Text(
        text = status.text,
        color = fg,
        fontSize = if (large) 13.sp else 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(if (large) 999.dp else 7.dp))
            .background(bg)
            .padding(
                horizontal = if (large) 12.dp else 7.dp,
                vertical = if (large) 5.dp else 3.dp
            )
    )
}

@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier.scale(0.78f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    fontSize: TextUnit = 12.5.sp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun SectionCard(
    colors: AppColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 2.dp),
        content = content
    )
}

@Composable
fun FieldLabel(text: String, colors: AppColors) {
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.text2,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun DividerLine(colors: AppColors) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
}

@Composable
fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    colors: AppColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(colors.chip)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (key, label) ->
            val isOn = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOn) colors.card else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    color = if (isOn) colors.text else colors.text2,
                    fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChevronRight(colors: AppColors) {
    Icon(
        Icons.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = colors.text3,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
fun EmptyState(colors: AppColors, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🔋", fontSize = 56.sp)
        Text("还没有设备", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
        Text(
            "添加第一台设备，到日子我会提醒你",
            fontSize = 13.sp,
            color = colors.text2
        )
        Spacer(Modifier.height(6.dp))
        PillButton(
            text = "添加设备",
            onClick = onAdd,
            background = colors.accent,
            foreground = colors.onAccent,
            height = 44.dp,
            fontSize = 14.sp
        )
    }
}
