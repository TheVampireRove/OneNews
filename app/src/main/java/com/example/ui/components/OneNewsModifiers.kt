package com.example.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class OneNewsHaptics(
    private val view: View,
    private val composeHaptics: HapticFeedback,
) {
    fun tap() {
        if (!view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun toggle() {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        if (!view.performHapticFeedback(flag)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun pageFlip() {
        if (!view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun scrubTick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        if (!view.performHapticFeedback(flag)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
fun rememberOneNewsHaptics(): OneNewsHaptics {
    val view = LocalView.current
    val composeHaptics = LocalHapticFeedback.current
    return remember(view, composeHaptics) { OneNewsHaptics(view, composeHaptics) }
}

/**
 * Modernized pill scrollbar for LazyColumn that stays hidden when inactive
 * and fades in smoothly only while scrolling is taking place.
 */
fun Modifier.modernAutoHideScrollbar(
    state: LazyListState,
    thumbColor: Color? = null,
    endPadding: Dp = 4.dp,
    verticalPadding: Dp = 12.dp,
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                visible = true
            } else {
                delay(850L)
                visible = false
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.72f else 0f,
        animationSpec = tween(durationMillis = if (visible) 130 else 360),
        label = "lazy_scrollbar_alpha",
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (state.isScrollInProgress) 4.5.dp else 3.dp,
        animationSpec = tween(durationMillis = 150),
        label = "lazy_scrollbar_width",
    )
    val resolvedColor = thumbColor ?: MaterialTheme.colorScheme.primary

    drawWithContent {
        drawContent()
        if (alpha <= 0.01f) return@drawWithContent

        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo
        if (totalItems <= 0 || visibleItems.isEmpty()) return@drawWithContent

        val avgItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size.coerceAtLeast(1)
        val estimatedTotalHeight = avgItemSize * totalItems
        val viewportHeight = size.height - verticalPadding.toPx() * 2f
        if (estimatedTotalHeight <= viewportHeight || viewportHeight <= 0f) return@drawWithContent

        val firstItem = visibleItems.first()
        val scrolledPixels = firstItem.index * avgItemSize - firstItem.offset
        val maxScrollPixels = (estimatedTotalHeight - viewportHeight).coerceAtLeast(1f)
        val scrollFraction = (scrolledPixels / maxScrollPixels).coerceIn(0f, 1f)

        val minThumbHeight = 32.dp.toPx()
        val thumbHeight = ((viewportHeight / estimatedTotalHeight) * viewportHeight)
            .coerceIn(minThumbHeight, viewportHeight * 0.48f)
        val availableTravel = (viewportHeight - thumbHeight).coerceAtLeast(0f)
        val thumbTop = verticalPadding.toPx() + scrollFraction * availableTravel

        val widthPx = thumbWidth.toPx()
        val left = size.width - endPadding.toPx() - widthPx

        drawRoundRect(
            color = resolvedColor.copy(alpha = alpha),
            topLeft = Offset(left, thumbTop),
            size = Size(widthPx, thumbHeight),
            cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f),
        )
    }
}

/**
 * Modernized pill scrollbar for vertical ScrollState that disappears completely
 * when inactive and no scrolling is taking place.
 */
fun Modifier.modernAutoHideScrollbar(
    state: ScrollState,
    thumbColor: Color? = null,
    endPadding: Dp = 4.dp,
    verticalPadding: Dp = 12.dp,
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                visible = true
            } else {
                delay(850L)
                visible = false
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.72f else 0f,
        animationSpec = tween(durationMillis = if (visible) 130 else 360),
        label = "scroll_scrollbar_alpha",
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (state.isScrollInProgress) 4.5.dp else 3.dp,
        animationSpec = tween(durationMillis = 150),
        label = "scroll_scrollbar_width",
    )
    val resolvedColor = thumbColor ?: MaterialTheme.colorScheme.primary

    drawWithContent {
        drawContent()
        val maxScroll = state.maxValue
        if (alpha <= 0.01f || maxScroll <= 0) return@drawWithContent

        val viewportHeight = size.height - verticalPadding.toPx() * 2f
        if (viewportHeight <= 0f) return@drawWithContent

        val totalContentHeight = viewportHeight + maxScroll
        val scrollFraction = (state.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)

        val minThumbHeight = 32.dp.toPx()
        val thumbHeight = ((viewportHeight / totalContentHeight) * viewportHeight)
            .coerceIn(minThumbHeight, viewportHeight * 0.48f)
        val availableTravel = (viewportHeight - thumbHeight).coerceAtLeast(0f)
        val thumbTop = verticalPadding.toPx() + scrollFraction * availableTravel

        val widthPx = thumbWidth.toPx()
        val left = size.width - endPadding.toPx() - widthPx

        drawRoundRect(
            color = resolvedColor.copy(alpha = alpha),
            topLeft = Offset(left, thumbTop),
            size = Size(widthPx, thumbHeight),
            cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f),
        )
    }
}
