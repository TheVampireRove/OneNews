package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Interactive Samsung One UI-inspired quick scrollbar overlay for LazyColumn feeds.
 * Supports dragging the thumb or tapping along the right edge track to scrub rapidly
 * through stories, displaying a frosted floating indicator bubble while scrubbing.
 */
@Composable
fun LazyListQuickScrollbar(
    listState: LazyListState,
    bottomPadding: Dp = 16.dp,
    topPadding: Dp = 10.dp,
    endPadding: Dp = 4.dp,
    scrubLabelForIndex: ((Int) -> String?)? = null,
    onDraggingChange: (Boolean) -> Unit = {},
    testTag: String = "feed_quick_scrollbar",
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val isScrollable by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total <= 1 || visible.isEmpty() || !(listState.canScrollForward || listState.canScrollBackward)) {
                false
            } else {
                val viewportHeight = info.viewportSize.height.toFloat().coerceAtLeast(1f)
                val avgItemSize = visible.sumOf { it.size }.toFloat() / visible.size
                val estimatedTotal = avgItemSize * total
                estimatedTotal > viewportHeight * 1.25f
            }
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var isRecentlyActive by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var cachedAvgItemSizePx by remember { mutableFloatStateOf(320f) }
    var lastHapticItemIndex by remember { mutableIntStateOf(-1) }
    var activeScrubIndex by remember { mutableIntStateOf(0) }

    val latestOnDraggingChange by rememberUpdatedState(onDraggingChange)
    LaunchedEffect(isDragging) {
        latestOnDraggingChange(isDragging)
    }

    LaunchedEffect(listState, isDragging) {
        snapshotFlow { listState.isScrollInProgress || isDragging }.collectLatest { active ->
            if (active) {
                isRecentlyActive = true
            } else {
                delay(1200L)
                isRecentlyActive = false
            }
        }
    }

    val scrollMetrics by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            val viewportHeight = layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
            if (totalItems <= 0 || visibleItems.isEmpty()) {
                Triple(0f, 0.2f, 0)
            } else {
                val avgItemSize = (visibleItems.sumOf { it.size }.toFloat() / visibleItems.size)
                    .coerceAtLeast(1f)
                cachedAvgItemSizePx = avgItemSize
                val estimatedTotal = (avgItemSize * totalItems).coerceAtLeast(viewportHeight)
                val fraction = when {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 0 -> 0f
                    !listState.canScrollForward && listState.canScrollBackward -> 1f
                    else -> {
                        val firstItem = visibleItems.first()
                        val scrolled = firstItem.index * avgItemSize - firstItem.offset
                        val maxScroll = (estimatedTotal - viewportHeight).coerceAtLeast(1f)
                        (scrolled / maxScroll).coerceIn(0f, 1f)
                    }
                }
                val thumbRatio = (viewportHeight / estimatedTotal).coerceIn(0.08f, 0.44f)
                Triple(fraction, thumbRatio, visibleItems.first().index)
            }
        }
    }

    val currentFraction = if (isDragging) dragFraction else scrollMetrics.first
    val thumbRatio = scrollMetrics.second
    val totalItemsCount = listState.layoutInfo.totalItemsCount
    val listViewportHeightPx = listState.layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)

    val minThumbPx = with(density) { 48.dp.toPx() }
    val thumbHeightPx = if (trackHeightPx > 0f) {
        (trackHeightPx * thumbRatio).coerceIn(minThumbPx, (trackHeightPx * 0.44f).coerceAtLeast(minThumbPx))
    } else {
        minThumbPx
    }
    val availableTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val thumbTopPx = (currentFraction * availableTravelPx).coerceIn(0f, availableTravelPx)

    val thumbWidth by animateDpAsState(
        targetValue = when {
            isDragging -> 11.dp
            isRecentlyActive -> 6.dp
            else -> 4.dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "quick_scrollbar_width",
    )

    val thumbAlpha by animateFloatAsState(
        targetValue = when {
            !isScrollable -> 0f
            isDragging -> 1f
            isRecentlyActive -> 0.88f
            else -> 0.36f
        },
        animationSpec = tween(durationMillis = if (isDragging || isRecentlyActive) 140 else 360),
        label = "quick_scrollbar_alpha",
    )

    val trackAlpha by animateFloatAsState(
        targetValue = when {
            !isScrollable -> 0f
            isDragging -> 0.34f
            isRecentlyActive -> 0.16f
            else -> 0.08f
        },
        animationSpec = tween(durationMillis = 180),
        label = "quick_scrollbar_track_alpha",
    )

    val latestTotalItems by rememberUpdatedState(totalItemsCount)
    val latestTrackHeight by rememberUpdatedState(trackHeightPx)
    val latestThumbHeight by rememberUpdatedState(thumbHeightPx)
    val latestAvgItemSize by rememberUpdatedState(cachedAvgItemSizePx)
    val latestViewportHeight by rememberUpdatedState(listViewportHeightPx)

    val scrubToPointerY: (Float) -> Unit = remember(listState, haptics, coroutineScope) {
        { pointerY ->
            val trackH = latestTrackHeight
            val thumbH = latestThumbHeight
            val total = latestTotalItems
            if (trackH > 0f && total > 0) {
                val travel = (trackH - thumbH).coerceAtLeast(1f)
                val clampedTop = (pointerY - thumbH / 2f).coerceIn(0f, travel)
                val fraction = (clampedTop / travel).coerceIn(0f, 1f)
                dragFraction = fraction

                val maxIndex = (total - 1).coerceAtLeast(0)
                val avgItemH = latestAvgItemSize.coerceAtLeast(1f)
                val estTotalH = avgItemH * total
                val maxScrollPx = (estTotalH - latestViewportHeight).coerceAtLeast(avgItemH)
                val targetPixel = fraction * maxScrollPx
                val targetIndex = (targetPixel / avgItemH).toInt().coerceIn(0, maxIndex)
                val targetOffset = if (fraction >= 0.995f) {
                    avgItemH.toInt()
                } else {
                    (targetPixel - targetIndex * avgItemH).toInt().coerceAtLeast(0)
                }

                if (targetIndex != lastHapticItemIndex) {
                    if (lastHapticItemIndex != -1) {
                        haptics.scrubTick()
                    }
                    lastHapticItemIndex = targetIndex
                }
                activeScrubIndex = targetIndex
                coroutineScope.launch {
                    listState.scrollToItem(targetIndex, targetOffset)
                }
            }
        }
    }

    if (!isScrollable) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding),
    ) {
        // Floating glass preview bubble anchored cleanly beside the thumb while scrubbing
        val displayIndex = if (isDragging) activeScrubIndex else scrollMetrics.third
        val bubbleText = scrubLabelForIndex?.invoke(displayIndex)
            ?: "${(displayIndex + 1).coerceAtMost(totalItemsCount)} / $totalItemsCount"
        val thumbCenterY = (thumbTopPx + thumbHeightPx / 2f).roundToInt()

        AnimatedVisibility(
            visible = isDragging && bubbleText.isNotBlank(),
            enter = fadeIn(tween(110)) + scaleIn(initialScale = 0.88f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 34.dp)
                .offset {
                    val halfBubbleApproxPx = with(density) { 20.dp.roundToPx() }
                    val maxOffset = (trackHeightPx - halfBubbleApproxPx * 2).roundToInt().coerceAtLeast(0)
                    IntOffset(
                        x = 0,
                        y = (thumbCenterY - halfBubbleApproxPx).coerceIn(0, maxOffset),
                    )
                },
        ) {
            GlassContainer(
                shape = CircleShape,
                fillAlpha = if (isDark) 0.90f else 0.96f,
                baseColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                highlighted = true,
                shadowElevation = 8.dp,
                modifier = Modifier.testTag("quick_scrollbar_bubble"),
            ) {
                Text(
                    text = bubbleText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 180.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Right-edge interactive fast-scroll track & thumb gutter (clear of card action buttons)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(28.dp)
                .padding(end = endPadding)
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .semantics {
                    contentDescription = "Quick scrollbar"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = currentFraction.coerceIn(0f, 1f),
                        range = 0f..1f,
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        isDragging = true
                        haptics.tap()
                        lastHapticItemIndex = -1
                        scrubToPointerY(offset.y)
                        isDragging = false
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            haptics.tap()
                            lastHapticItemIndex = -1
                            scrubToPointerY(offset.y)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            scrubToPointerY(change.position.y)
                        },
                        onDragEnd = {
                            isDragging = false
                            lastHapticItemIndex = -1
                        },
                        onDragCancel = {
                            isDragging = false
                            lastHapticItemIndex = -1
                        },
                    )
                }
                .testTag(testTag),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackW = 2.5.dp.toPx()
                val trackLeft = size.width - trackW - 2.dp.toPx()
                drawRoundRect(
                    color = trackColor.copy(alpha = trackAlpha),
                    topLeft = Offset(trackLeft, 0f),
                    size = Size(trackW, size.height),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f),
                )

                val thumbW = thumbWidth.toPx()
                val thumbLeft = size.width - thumbW - 1.dp.toPx()
                val radius = thumbW / 2f
                drawRoundRect(
                    color = primaryColor.copy(alpha = thumbAlpha),
                    topLeft = Offset(thumbLeft, thumbTopPx),
                    size = Size(thumbW, thumbHeightPx),
                    cornerRadius = CornerRadius(radius, radius),
                )

                if (isDragging) {
                    // Subtle inner specular grip highlight while scrubbing
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.45f),
                        topLeft = Offset(thumbLeft + 1.5.dp.toPx(), thumbTopPx + 8.dp.toPx()),
                        size = Size((thumbW - 3.dp.toPx()).coerceAtLeast(1.5.dp.toPx()), (thumbHeightPx - 16.dp.toPx()).coerceAtLeast(4.dp.toPx())),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
        }
    }
}

/**
 * Floating frosted-glass One UI "Back to top" action pill that appears once the user
 * starts scrolling down through stories and smoothly returns both the list and collapsible header to top.
 */
@Composable
fun FeedBackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    testTag: String = "feed_scroll_to_top_button",
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
            scaleIn(
                initialScale = 0.86f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) +
            slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ),
        exit = fadeOut(tween(150)) +
            scaleOut(targetScale = 0.88f) +
            slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier,
    ) {
        Surface(
            onClick = {
                haptics.tap()
                onClick()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                alpha = if (isDark) 0.88f else 0.94f,
            ),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.1.dp, glassBorderBrush(highlighted = true)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Back to top",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Back to top",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Tracks whether a LazyColumn's "Back to top" button should be visible:
 * returns true only when scrolled away from the top AND actively scrolling (or within the
 * short post-scroll grace window), hiding automatically once scrolling stops and reappearing
 * immediately when the user scrolls again.
 */
@Composable
fun rememberAutoHideScrollToTopVisible(
    listState: LazyListState,
    hasItems: Boolean,
    isExternalScrolling: Boolean = false,
    idleHideDelayMillis: Long = 1200L,
): Boolean {
    val latestExternalScrolling by rememberUpdatedState(isExternalScrolling)
    val isScrolledPastTop by remember(listState, hasItems) {
        derivedStateOf {
            hasItems && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 260)
        }
    }
    var scrollActivityPulse by remember { mutableIntStateOf(0) }
    var isRecentlyScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        var firstEmission = true
        snapshotFlow {
            Triple(
                listState.isScrollInProgress || latestExternalScrolling,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
            .distinctUntilChanged()
            .collect { (active, _, _) ->
                if (firstEmission) {
                    firstEmission = false
                    if (active) {
                        scrollActivityPulse++
                    }
                } else {
                    scrollActivityPulse++
                }
            }
    }

    LaunchedEffect(scrollActivityPulse, listState) {
        if (scrollActivityPulse > 0) {
            isRecentlyScrolling = true
            while (listState.isScrollInProgress || latestExternalScrolling) {
                delay(120L)
            }
            delay(idleHideDelayMillis)
            isRecentlyScrolling = false
        }
    }

    return isScrolledPastTop && (isRecentlyScrolling || listState.isScrollInProgress || isExternalScrolling)
}
