package com.mclauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import com.mclauncher.ui.theme.CardShape
import com.mclauncher.ui.theme.LocalBackdrop
import com.mclauncher.ui.theme.LocalBackdropSize
import com.mclauncher.ui.theme.LocalTokens
import com.mclauncher.ui.theme.PillShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.RowScope

/* ------------------------------ 毛玻璃容器 ------------------------------ */

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    tint: Color = LocalTokens.current.glass,
    borderColor: Color = LocalTokens.current.border,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalTokens.current
    val backdrop: ImageBitmap? = LocalBackdrop.current
    val windowPx = LocalBackdropSize.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { offset = it.positionInWindow() }
            .clip(shape)
            .drawBehind {
                if (backdrop != null && windowPx.width > 0 && offset.x.isFinite() && offset.y.isFinite()) {
                    // 把整张模糊壁纸按窗口尺寸铺开，再用当前面板的位置做负偏移，
                    // 这样每块玻璃都能“透过”它身后那部分背景，形成真正的毛玻璃。
                    drawImage(
                        image = backdrop,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(backdrop.width, backdrop.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            -offset.x.toInt(),
                            -offset.y.toInt()
                        ),
                        dstSize = windowPx,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                    )
                }
                drawRect(tint)
                // 顶部高光 + 底部轻微阴影，模拟玻璃厚度
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (tokens.dark) 0.10f else 0.35f),
                        0.25f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (tokens.dark) 0.10f else 0.03f)
                    )
                )
            }
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content
    )
}

/* ------------------------------ 按钮 ------------------------------ */

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    onClick: () -> Unit
) {
    val tokens = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered && enabled) 1.025f else 1f, spring(stiffness = 400f), label = "btn-scale")
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(tokens.accent.copy(alpha = alpha), tokens.accent2.copy(alpha = alpha))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .hoverable(interaction, enabled = enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val tokens = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColor(if (selected) tokens.accent.copy(alpha = 0.22f) else if (hovered) tokens.field else Color.Transparent)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .hoverable(interaction, enabled = enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) tokens.accent else tokens.subtext, modifier = Modifier.size(16.dp))
        Text(
            text,
            color = if (selected) tokens.accent else tokens.text.copy(alpha = 0.86f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun animateColor(target: Color): State<Color> {
    return androidx.compose.animation.animateColorAsState(target, tween(160), label = "color")
}

/* ------------------------------ 输入框 ------------------------------ */

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    onSubmit: (() -> Unit)? = null,
    password: Boolean = false,
    height: Dp = 44.dp
) {
    val tokens = LocalTokens.current
    val focused = remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(13.dp))
            .background(tokens.field)
            .border(
                1.dp,
                if (focused.value) tokens.accent.copy(alpha = 0.7f) else tokens.border,
                RoundedCornerShape(13.dp)
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = tokens.subtext, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = tokens.subtext.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = tokens.text, fontSize = 13.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
                visualTransformation = if (password)
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused.value = it.isFocused }
            )
        }
    }
}

/* ------------------------------ 筛选片 ------------------------------ */

@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        selected -> tokens.accent.copy(alpha = 0.24f)
        hovered -> tokens.field
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(PillShape)
            .background(bg)
            .border(
                1.dp,
                if (selected) tokens.accent.copy(alpha = 0.55f) else tokens.border.copy(alpha = 0.6f),
                PillShape
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) tokens.accent else tokens.text.copy(alpha = 0.8f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/* ------------------------------ 弹窗（mac 风格） ------------------------------ */

@Composable
fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    width: Dp = 780.dp,
    maxHeight: Dp = 640.dp,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalTokens.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var origin by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) }
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned {
            val pos = it.positionInWindow()
            origin = androidx.compose.ui.unit.IntOffset(pos.x.toInt(), pos.y.toInt())
        }
    ) {
        val availW = this.maxWidth
        val availH = this.maxHeight
        val panelWidth = androidx.compose.ui.unit.max(280.dp, androidx.compose.ui.unit.min(width, availW - 24.dp))
        val panelHeight = androidx.compose.ui.unit.min(maxHeight, availH - 24.dp)
        // 父容器是内容区（212/44 偏移）时往左上补一半；是整窗时补 0
        val panelShiftX = with(density) { (-origin.x / 2f).toDp() }
        val panelShiftY = with(density) { (-origin.y / 2f).toDp() }
        // 顶部留一条给标题栏拖窗口：遮罩不吞这一块的指针事件
        val titleBarPx = with(density) { 44.dp.toPx() }

        AnimatedVisibility(visible = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val minY = event.changes.minOfOrNull { it.position.y } ?: 0f
                                if (minY > titleBarPx) event.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .drawBehind {
                        drawRect(
                            color = tokens.scrim,
                            topLeft = androidx.compose.ui.geometry.Offset(-origin.x.toFloat(), -origin.y.toFloat()),
                            size = androidx.compose.ui.geometry.Size(
                                size.width + origin.x.toFloat(),
                                size.height + origin.y.toFloat()
                            )
                        )
                    }
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center).offset(x = panelShiftX, y = panelShiftY),
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(120))
        ) {
            GlassPanel(
                modifier = if (fillHeight) Modifier.width(panelWidth).height(panelHeight)
                else Modifier.width(panelWidth).heightIn(max = panelHeight),
                shape = RoundedCornerShape(22.dp),
                tint = if (tokens.dark) Color(0xE60C1016) else Color(0xEDF6F8FC)
            ) {
                Column(Modifier.fillMaxWidth().then(if (fillHeight) Modifier.height(panelHeight) else Modifier)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 18.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                            if (subtitle != null) {
                                Spacer(Modifier.height(3.dp))
                                Text(subtitle, fontSize = 12.sp, color = tokens.subtext)
                            }
                        }
                        CircleIconButton(Icons.Filled.Close, onClick = onDismiss, size = 30.dp)
                    }
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false)) { content() }
                    Box(
                        Modifier.fillMaxWidth().height(20.dp).background(
                            Brush.verticalGradient(listOf(Color.Transparent, tokens.accent.copy(alpha = 0.20f)))
                        )
                    )
                    Box(
                        Modifier.fillMaxWidth().height(2.dp).background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, tokens.accent, tokens.accent2, Color.Transparent)
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val tokens = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hovered) tokens.field else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint ?: tokens.subtext, modifier = Modifier.size(size * 0.52f))
    }
}

/* ------------------------------ 其它小部件 ------------------------------ */

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val tokens = LocalTokens.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tokens.subtext, letterSpacing = 0.5.sp)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier, alpha: Float = 1f) {
    val tokens = LocalTokens.current
    Box(modifier.fillMaxWidth().height(1.dp).background(tokens.divider.copy(alpha = alpha)))
}

@Composable
fun Tag(text: String, color: Color = LocalTokens.current.accent, small: Boolean = true) {
    Box(
        Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 2.dp else 4.dp)
    ) {
        Text(text, fontSize = if (small) 10.sp else 11.sp, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val tokens = LocalTokens.current
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(220), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(PillShape)
            .background(tokens.field)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(PillShape)
                .background(Brush.horizontalGradient(listOf(tokens.accent, tokens.accent2)))
        )
    }
}

@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = index * 130),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(tokens.accent.copy(alpha = alpha))
            )
        }
    }
}

/** 让文字在窄容器里优雅省略 */
@Composable
fun EllipsisText(text: String, modifier: Modifier = Modifier, color: Color = LocalTokens.current.text, size: Int = 13) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/* ------------------------------ 窗口按钮 ------------------------------ */

@Composable
fun WindowControls(
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        TrafficDot(Color(0xFFFFBD2E), onMinimize) { color ->
            drawLine(color, Offset(size.width * 0.28f, size.height / 2), Offset(size.width * 0.72f, size.height / 2), 1.6f, StrokeCap.Round)
        }
        TrafficDot(Color(0xFF28C840), onMaximize) { color ->
            drawRect(color, topLeft = Offset(size.width * 0.3f, size.height * 0.3f), size = Size(size.width * 0.4f, size.height * 0.4f))
        }
        TrafficDot(Color(0xFFFF5F57), onClose) { color ->
            drawLine(color, Offset(size.width * 0.3f, size.height * 0.3f), Offset(size.width * 0.7f, size.height * 0.7f), 1.6f, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.7f, size.height * 0.3f), Offset(size.width * 0.3f, size.height * 0.7f), 1.6f, StrokeCap.Round)
        }
    }
}

@Composable
private fun TrafficDot(
    color: Color,
    onClick: () -> Unit,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (hovered) 1f else 0.85f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .drawBehind {
                if (hovered) glyph(Color(0x66000000))
            }
    )
}

/* ------------------------------ 滚动条 ------------------------------ */

private fun scrollbarStyle(tokens: com.mclauncher.ui.theme.ThemeTokens): androidx.compose.foundation.ScrollbarStyle =
    androidx.compose.foundation.ScrollbarStyle(
        minimalHeight = 28.dp,
        thickness = 6.dp,
        shape = RoundedCornerShape(50),
        hoverDurationMillis = 150,
        unhoverColor = tokens.subtext.copy(alpha = 0.25f),
        hoverColor = tokens.accent.copy(alpha = 0.8f)
    )

@Composable
fun ListScrollbar(state: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    androidx.compose.foundation.VerticalScrollbar(
        adapter = androidx.compose.foundation.rememberScrollbarAdapter(state),
        modifier = modifier.fillMaxHeight().width(10.dp).padding(vertical = 4.dp),
        style = scrollbarStyle(tokens)
    )
}

@Composable
fun GridScrollbar(state: androidx.compose.foundation.lazy.grid.LazyGridState, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    androidx.compose.foundation.VerticalScrollbar(
        adapter = androidx.compose.foundation.rememberScrollbarAdapter(state),
        modifier = modifier.fillMaxHeight().width(10.dp).padding(vertical = 4.dp),
        style = scrollbarStyle(tokens)
    )
}

@Composable
fun ColumnScrollbar(state: androidx.compose.foundation.ScrollState, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    androidx.compose.foundation.VerticalScrollbar(
        adapter = androidx.compose.foundation.rememberScrollbarAdapter(state),
        modifier = modifier.fillMaxHeight().width(10.dp).padding(vertical = 4.dp),
        style = scrollbarStyle(tokens)
    )
}
/** 会转的刷新按钮：有任务在跑时持续旋转，给用户"正在干活"的反馈 */
@Composable
fun RefreshButton(spinning: Boolean, onClick: () -> Unit, size: Dp = 34.dp) {
    val tokens = LocalTokens.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "refresh")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1100, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "angle"
    )
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hovered) tokens.field else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            androidx.compose.material.icons.Icons.Filled.Refresh,
            null,
            tint = if (spinning) tokens.accent else tokens.subtext,
            modifier = Modifier.size(size * 0.5f).rotate(if (spinning) angle else 0f)
        )
    }
}
/**
 * 横向滚动的行：内容放不下时可以左右拖，底部有横向滚动条。
 * 设置里的各种长条（模式、主题、预设、配色……）都用它包。
 */
@Composable
fun HScroll(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        if (scroll.maxValue > 0) {
            androidx.compose.foundation.HorizontalScrollbar(
                adapter = androidx.compose.foundation.rememberScrollbarAdapter(scroll),
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
        }
    }
}
/** 右下角的失败提示 */
@Composable
fun ErrorToast(text: String, onDismiss: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier
            .widthIn(max = 380.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (tokens.dark) Color(0xF2231A1C) else Color(0xFFFDF2F2))
            .border(1.dp, tokens.danger.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable { onDismiss() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.Warning,
            null, tint = tokens.danger, modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = tokens.text, fontSize = 11.sp, maxLines = 3)
    }
}