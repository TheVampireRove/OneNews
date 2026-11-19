package com.example.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.EventMetadata
import com.example.model.LocationMetadata
import com.example.model.NewsArticle
import com.example.model.PageTurnAnimation
import com.example.model.ThemeSettings
import com.example.ui.components.ArticleBodyShimmerSkeleton
import com.example.ui.components.GlassContainer
import com.example.ui.components.OneNewsArticleImage
import com.example.ui.components.OpenOriginalReaderPill
import com.example.ui.components.glassBorderBrush
import com.example.ui.components.rememberOneNewsHaptics
import com.example.ui.theme.AmoledCardElevated
import com.example.ui.theme.NewsreaderFontFamily
import com.example.ui.theme.OneUiCardRadius
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun KindlePaginatedReader(
    article: NewsArticle,
    themeSettings: ThemeSettings,
    toolbarsVisible: Boolean,
    isExtractingFullText: Boolean = false,
    initialPageIndex: Int = 0,
    onSavePagePosition: (String, Int) -> Unit = { _, _ -> },
    onToggleToolbars: () -> Unit,
    onPreviousArticle: () -> Unit,
    onNextArticle: () -> Unit,
    onAddCalendarEvent: (EventMetadata) -> Unit,
    onOpenMaps: (LocationMetadata) -> Unit,
    onOpenOriginalReaderView: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    val headlineStyle = (if (themeSettings.useSerifHeadlines && !themeSettings.useSystemFont) {
        MaterialTheme.typography.headlineMedium.copy(fontFamily = NewsreaderFontFamily)
    } else {
        MaterialTheme.typography.headlineMedium
    }).copy(
        fontSize = (23f * themeSettings.readerFontScale.coerceIn(0.9f, 1.25f)).sp,
        lineHeight = (30f * themeSettings.readerFontScale.coerceIn(0.9f, 1.25f)).sp,
    )
    val subtitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = (15f * themeSettings.readerFontScale.coerceIn(0.9f, 1.25f)).sp,
        lineHeight = (22f * themeSettings.readerFontScale.coerceIn(0.9f, 1.25f)).sp,
    )
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = (16f * themeSettings.readerFontScale).sp,
        lineHeight = (25.5f * themeSettings.readerFontScale).sp,
    )
    val metaLabelStyle = MaterialTheme.typography.labelMedium

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("kindle_paginated_reader"),
    ) {
        val paneWidthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(180)
        val paneHeightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(240)
        val indicatorLabelHeightPx = textMeasurer.measure("Page 99 of 99", metaLabelStyle).size.height
        val bottomIndicatorSpacePx = with(density) {
            (if (toolbarsVisible) 93.dp else 33.dp).toPx()
        }.roundToInt() + indicatorLabelHeightPx
        val bottomIndicatorSpaceDp = with(density) { bottomIndicatorSpacePx.toDp() }

        val pages = remember(
            article.id,
            article.paragraphs,
            article.imageUrl,
            article.imageResId,
            article.fullTextExtractionFailed,
            isExtractingFullText,
            paneWidthPx,
            paneHeightPx,
            bottomIndicatorSpacePx,
            themeSettings.readerFontScale,
            themeSettings.useSerifHeadlines,
            themeSettings.useSystemFont,
            themeSettings.showNowNudgeChips,
            toolbarsVisible,
        ) {
            paginateKindleArticle(
                article = article,
                paneWidthDp = maxWidth,
                paneHeightDp = (maxHeight - bottomIndicatorSpaceDp).coerceAtLeast(180.dp),
                density = density,
                textMeasurer = textMeasurer,
                headlineStyle = headlineStyle,
                subtitleStyle = subtitleStyle,
                bodyStyle = bodyStyle,
                metaLabelStyle = metaLabelStyle,
                includeNudgeFooter = themeSettings.showNowNudgeChips &&
                    (article.eventMetadata != null || article.locationMetadata != null),
                includeOriginalReaderPill = !isExtractingFullText &&
                    article.fullTextExtractionFailed &&
                    !article.articleUrl.isNullOrBlank(),
                includeEndOfArticleCard = !isExtractingFullText,
            )
        }

        var currentPage by remember(article.id) {
            mutableIntStateOf(initialPageIndex.coerceAtLeast(0))
        }
        currentPage = currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

        var isTurning by remember(article.id) { mutableStateOf(false) }
        var turnDirection by remember(article.id) { mutableIntStateOf(1) }
        var dragAnchorY by remember(article.id) { mutableFloatStateOf(0.55f) }
        val turnProgress = remember(article.id) { Animatable(0f) }

        LaunchedEffect(article.id) {
            isTurning = false
            turnProgress.snapTo(0f)
        }

        LaunchedEffect(article.id, currentPage) {
            onSavePagePosition(article.id, currentPage)
        }

        fun animateTurn(direction: Int, startProgress: Float = 0f) {
            val targetIdx = currentPage + direction
            if (targetIdx < 0) {
                haptics.pageFlip()
                scope.launch { turnProgress.snapTo(0f) }
                isTurning = false
                onPreviousArticle()
                return
            }
            if (targetIdx >= pages.size) {
                haptics.pageFlip()
                scope.launch { turnProgress.snapTo(0f) }
                isTurning = false
                onNextArticle()
                return
            }

            haptics.pageFlip()
            if (themeSettings.pageTurnAnimation == PageTurnAnimation.INSTANT) {
                scope.launch {
                    turnProgress.snapTo(0f)
                    isTurning = false
                    currentPage = targetIdx
                }
                return
            }

            turnDirection = direction
            isTurning = true
            scope.launch {
                if (startProgress <= 0f) {
                    turnProgress.snapTo(0.02f)
                }
                turnProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (startProgress > 0.2f) 280 else 420,
                        easing = FastOutSlowInEasing,
                    ),
                )
                currentPage = targetIdx
                isTurning = false
                turnProgress.snapTo(0f)
            }
        }

        fun springBackCurl() {
            scope.launch {
                turnProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
                isTurning = false
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Interactive Page Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(currentPage, pages.size, themeSettings.pageTurnAnimation) {
                        detectTapGestures { offset ->
                            if (isTurning) return@detectTapGestures
                            val widthF = size.width.toFloat().coerceAtLeast(1f)
                            val xNorm = offset.x / widthF
                            dragAnchorY = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0.15f, 0.85f)
                            when {
                                xNorm > 0.75f -> animateTurn(1)
                                xNorm < 0.25f -> animateTurn(-1)
                                else -> {
                                    haptics.tap()
                                    onToggleToolbars()
                                }
                            }
                        }
                    }
                    .pointerInput(currentPage, pages.size, themeSettings.pageTurnAnimation) {
                        var activeDragDirection = 0
                        var accumulatedDx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { startOffset ->
                                activeDragDirection = 0
                                accumulatedDx = 0f
                                dragAnchorY = (startOffset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0.15f, 0.85f)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDx += dragAmount
                                val widthF = size.width.toFloat().coerceAtLeast(1f)
                                if (activeDragDirection == 0 && abs(accumulatedDx) > 8f) {
                                    val candidateDir = if (accumulatedDx < 0f) 1 else -1
                                    activeDragDirection = candidateDir
                                    turnDirection = candidateDir
                                    isTurning = true
                                }
                                if (activeDragDirection != 0) {
                                    dragAnchorY = (change.position.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0.12f, 0.88f)
                                    val rawProgress = if (activeDragDirection > 0) {
                                        (-accumulatedDx / (widthF * 0.85f)).coerceIn(0f, 1f)
                                    } else {
                                        (accumulatedDx / (widthF * 0.85f)).coerceIn(0f, 1f)
                                    }
                                    if (themeSettings.pageTurnAnimation != PageTurnAnimation.INSTANT) {
                                        scope.launch { turnProgress.snapTo(rawProgress) }
                                    }
                                }
                            },
                            onDragCancel = {
                                activeDragDirection = 0
                                springBackCurl()
                            },
                            onDragEnd = {
                                val widthF = size.width.toFloat().coerceAtLeast(1f)
                                val dragRatio = if (activeDragDirection > 0) {
                                    (-accumulatedDx / widthF).coerceIn(0f, 1f)
                                } else if (activeDragDirection < 0) {
                                    (accumulatedDx / widthF).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                val currentProg = if (themeSettings.pageTurnAnimation == PageTurnAnimation.INSTANT) {
                                    dragRatio
                                } else {
                                    maxOf(turnProgress.value, dragRatio)
                                }
                                if (activeDragDirection != 0 && currentProg >= 0.35f) {
                                    animateTurn(activeDragDirection, startProgress = turnProgress.value)
                                } else {
                                    springBackCurl()
                                }
                                activeDragDirection = 0
                            },
                        )
                    },
            ) {
                val activePage = pages[currentPage]
                val progress = turnProgress.value.coerceIn(0f, 1f)

                val targetIndex = (currentPage + turnDirection).coerceIn(0, pages.lastIndex)
                val targetPage = pages[targetIndex]

                val renderPageContent: @Composable (MeasuredPaginatedPage) -> Unit = { page ->
                    KindleScreenPageContent(
                        article = article,
                        page = page,
                        headlineStyle = headlineStyle,
                        subtitleStyle = subtitleStyle,
                        bodyStyle = bodyStyle,
                        isExtractingFullText = isExtractingFullText,
                        showNudges = themeSettings.showNowNudgeChips &&
                            (article.eventMetadata != null || article.locationMetadata != null),
                        onAddCalendarEvent = onAddCalendarEvent,
                        onOpenMaps = onOpenMaps,
                        onOpenOriginalReaderView = onOpenOriginalReaderView,
                    )
                }

                if (!isTurning || progress <= 0.001f || themeSettings.pageTurnAnimation == PageTurnAnimation.INSTANT) {
                    renderPageContent(activePage)
                } else {
                    when (themeSettings.pageTurnAnimation) {
                        PageTurnAnimation.SIMPLE_SLIDE -> {
                            SimpleSlidePageTransition(
                                foregroundPage = if (turnDirection > 0) activePage else targetPage,
                                backgroundPage = if (turnDirection > 0) targetPage else activePage,
                                direction = turnDirection,
                                progress = progress,
                                renderPage = renderPageContent,
                            )
                        }

                        PageTurnAnimation.KINDLE_CURL -> {
                            if (turnDirection > 0) {
                                KindleCurlOverlayTransition(
                                    turningPage = activePage,
                                    underlyingPage = targetPage,
                                    peelProgress = progress,
                                    anchorYFraction = dragAnchorY,
                                    useAmoledBackFace = themeSettings.useAmoledBlack,
                                    renderPage = renderPageContent,
                                )
                            } else {
                                KindleCurlOverlayTransition(
                                    turningPage = targetPage,
                                    underlyingPage = activePage,
                                    peelProgress = (1f - progress).coerceIn(0f, 1f),
                                    anchorYFraction = dragAnchorY,
                                    useAmoledBackFace = themeSettings.useAmoledBlack,
                                    renderPage = renderPageContent,
                                )
                            }
                        }

                        PageTurnAnimation.INSTANT -> renderPageContent(activePage)
                    }
                }
            }

            KindlePageIndicatorFooter(
                currentPage = currentPage + 1,
                totalPages = pages.size,
                hasBottomToolbarAbove = toolbarsVisible,
            )
        }
    }
}

@Composable
private fun SimpleSlidePageTransition(
    foregroundPage: MeasuredPaginatedPage,
    backgroundPage: MeasuredPaginatedPage,
    direction: Int,
    progress: Float,
    renderPage: @Composable (MeasuredPaginatedPage) -> Unit,
) {
    val shiftFactor = if (direction > 0) progress else (1f - progress)
    val pageBackground = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        // Stationary underlying page (100% opaque, zIndex = 0)
        Surface(
            color = pageBackground,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .clipToBounds()
                .graphicsLayer {
                    val scale = 0.97f + 0.03f * shiftFactor
                    scaleX = scale
                    scaleY = scale
                }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black.copy(alpha = (0.35f * (1f - shiftFactor)).coerceIn(0f, 0.35f)))
                },
        ) {
            renderPage(backgroundPage)
        }

        // Sliding foreground sheet (100% opaque, higher zIndex = 2)
        Surface(
            color = pageBackground,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .clipToBounds()
                .graphicsLayer {
                    translationX = -size.width * shiftFactor
                },
        ) {
            renderPage(foregroundPage)
        }
    }
}

/**
 * Interactive Kindle-style paper curl built solely with Compose Canvas, drawWithContent clipPath,
 * and graphicsLayer transforms:
 * - 100% opaque front page surface matching MaterialTheme.colorScheme.background (zero bleed-through)
 * - 100% opaque curled back-face using #17171A / surfaceContainerHigh in dark mode or surfaceContainerLow in light/Monet mode
 * - Crisp 1.dp outlineVariant crease border and soft drop shadow cast onto stationary page underneath
 */
@Composable
private fun KindleCurlOverlayTransition(
    turningPage: MeasuredPaginatedPage,
    underlyingPage: MeasuredPaginatedPage,
    peelProgress: Float,
    anchorYFraction: Float,
    useAmoledBackFace: Boolean,
    renderPage: @Composable (MeasuredPaginatedPage) -> Unit,
) {
    val p = peelProgress.coerceIn(0f, 1f)
    val tiltBias = ((anchorYFraction - 0.5f) * 2f).coerceIn(-0.85f, 0.85f)
    val tiltSpreadNorm = (0.14f * (1f - abs(p - 0.5f) * 1.4f)).coerceAtLeast(0.02f) * tiltBias

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val opaquePageSurface = MaterialTheme.colorScheme.background
    val backFaceSurfaceColor = when {
        isDark && useAmoledBackFace -> AmoledCardElevated
        isDark -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val creaseHighlightColor = MaterialTheme.colorScheme.outlineVariant

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        // 1. Stationary underlying page (zIndex = 0f, completely opaque) + soft shadow cast beneath fold
        Surface(
            color = opaquePageSurface,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val h = size.height
                    val centerFoldX = w * (1f - p)
                    val topFoldX = (centerFoldX + w * tiltSpreadNorm).coerceIn(-w * 0.25f, w * 1.1f)
                    val bottomFoldX = (centerFoldX - w * tiltSpreadNorm).coerceIn(-w * 0.25f, w * 1.1f)
                    val midFoldX = (topFoldX + bottomFoldX) * 0.5f

                    val shadowWidth = (w * 0.20f * (1f - p * 0.45f)).coerceAtLeast(26.dp.toPx())
                    val shadowStart = midFoldX.coerceIn(0f, w)
                    val shadowEnd = (midFoldX + shadowWidth).coerceIn(0f, w)
                    if (shadowEnd > shadowStart) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f * (1f - p * 0.4f)),
                                    Color.Black.copy(alpha = 0.26f * (1f - p * 0.4f)),
                                    Color.Transparent,
                                ),
                                startX = shadowStart,
                                endX = shadowEnd,
                            ),
                            topLeft = Offset(shadowStart, 0f),
                            size = Size(shadowEnd - shadowStart, h),
                        )
                    }
                },
        ) {
            renderPage(underlyingPage)
        }

        // 2. Opaque foreground turning page clipped strictly to unpeeled region (zIndex = 2f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .clipToBounds()
                .drawWithContent {
                    val w = size.width
                    val h = size.height
                    val centerFoldX = w * (1f - p)
                    val topFoldX = (centerFoldX + w * tiltSpreadNorm).coerceIn(-w * 0.2f, w * 1.1f)
                    val bottomFoldX = (centerFoldX - w * tiltSpreadNorm).coerceIn(-w * 0.2f, w * 1.1f)

                    val frontPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(topFoldX.coerceAtLeast(0f), 0f)
                        lineTo(bottomFoldX.coerceAtLeast(0f), h)
                        lineTo(0f, h)
                        close()
                    }
                    clipPath(frontPath) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            Surface(
                color = opaquePageSurface,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                renderPage(turningPage)
            }
        }

        // 3. Solid, completely opaque theme-matched page back-face (alpha = 1.0f, zIndex = 3f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
                .clipToBounds()
                .drawWithContent {
                    val w = size.width
                    val h = size.height
                    val centerFoldX = w * (1f - p)
                    val topFoldX = centerFoldX + w * tiltSpreadNorm
                    val bottomFoldX = centerFoldX - w * tiltSpreadNorm

                    val flapWidth = (w * p * 0.62f).coerceIn(0f, w * 0.56f)
                    val flapTopOuterX = (topFoldX - flapWidth).coerceAtLeast(-w * 0.15f)
                    val flapBottomOuterX = (bottomFoldX - flapWidth).coerceAtLeast(-w * 0.15f)

                    val backFlapPath = Path().apply {
                        moveTo(flapTopOuterX, 0f)
                        lineTo(topFoldX, 0f)
                        lineTo(bottomFoldX, h)
                        lineTo(flapBottomOuterX, h)
                        close()
                    }

                    clipPath(backFlapPath) {
                        this@drawWithContent.drawContent()

                        val minX = minOf(flapTopOuterX, flapBottomOuterX)
                        val maxX = maxOf(topFoldX, bottomFoldX)
                        if (maxX > minX) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = if (isDark) 0.22f else 0.10f),
                                        Color.White.copy(alpha = if (isDark) 0.06f else 0.18f),
                                        Color.Black.copy(alpha = if (isDark) 0.14f else 0.06f),
                                    ),
                                    startX = minX,
                                    endX = maxX,
                                ),
                            )
                        }
                    }

                    // Crisp 1.dp outlineVariant border highlight along the folded paper crease & outer lip
                    drawLine(
                        color = creaseHighlightColor,
                        start = Offset(topFoldX, 0f),
                        end = Offset(bottomFoldX, h),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = creaseHighlightColor.copy(alpha = 0.7f),
                        start = Offset(flapTopOuterX, 0f),
                        end = Offset(flapBottomOuterX, h),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        ) {
            // Solid opaque back-face container (alpha = 1.0f) prevents any underlying page bleed-through
            Surface(
                color = backFaceSurfaceColor,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = -1f
                            rotationZ = tiltBias * -3.5f * p
                            alpha = 0.14f
                        },
                ) {
                    renderPage(turningPage)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindleScreenPageContent(
    article: NewsArticle,
    page: MeasuredPaginatedPage,
    headlineStyle: TextStyle,
    subtitleStyle: TextStyle,
    bodyStyle: TextStyle,
    isExtractingFullText: Boolean,
    showNudges: Boolean,
    onAddCalendarEvent: (EventMetadata) -> Unit,
    onOpenMaps: (LocationMetadata) -> Unit,
    onOpenOriginalReaderView: (String) -> Unit,
) {
    val haptics = rememberOneNewsHaptics()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page.isCoverPage) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = article.source,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = "${article.displayCategoryLabel} • ${article.timestamp} • ${article.readingTimeMinutes} min read",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val hasLeadImage = !article.imageUrl.isNullOrBlank() || article.imageResId != null
                if (hasLeadImage && page.heroImageHeightDp > 40.dp) {
                    Surface(
                        shape = RoundedCornerShape(OneUiCardRadius),
                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OneNewsArticleImage(
                            imageUrl = article.imageUrl,
                            fallbackResId = article.imageResId,
                            contentDescription = article.headline,
                            shape = RoundedCornerShape(OneUiCardRadius),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(page.heroImageHeightDp),
                        )
                    }
                }

                Text(
                    text = article.headline,
                    style = headlineStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    text = article.subtitle,
                    style = subtitleStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (page.paragraphs.isNotEmpty() || isExtractingFullText) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }

            if (page.isCoverPage && isExtractingFullText) {
                ArticleBodyShimmerSkeleton()
            } else {
                page.paragraphs.forEach { para ->
                    Text(
                        text = para,
                        style = bodyStyle,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                    )
                }
            }

            if (page.isLastPage && !isExtractingFullText && article.fullTextExtractionFailed && !article.articleUrl.isNullOrBlank()) {
                OpenOriginalReaderPill(
                    onClick = { onOpenOriginalReaderView(article.articleUrl) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (page.isLastPage && showNudges) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    article.eventMetadata?.let { event ->
                        Surface(
                            onClick = {
                                haptics.confirm()
                                onAddCalendarEvent(event)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp))
                                Text("Add event to Calendar", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }
                    article.locationMetadata?.let { location ->
                        Surface(
                            onClick = {
                                haptics.confirm()
                                onOpenMaps(location)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.LocationOn, null, Modifier.size(16.dp))
                                Text("View location in Maps", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (page.isLastPage && !isExtractingFullText) {
                EndOfArticleFooterCard(sourceName = article.source)
            }
        }
    }
}

@Composable
internal fun EndOfArticleFooterCard(
    sourceName: String,
    modifier: Modifier = Modifier,
) {
    GlassContainer(
        shape = RoundedCornerShape(22.dp),
        fillAlpha = 0.56f,
        modifier = modifier
            .fillMaxWidth()
            .testTag("end_of_article_footer"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "End of Article",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$sourceName • Swipe forward for next story",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KindlePageIndicatorFooter(
    currentPage: Int,
    totalPages: Int,
    hasBottomToolbarAbove: Boolean,
) {
    val progress by animateFloatAsState(
        targetValue = (currentPage.toFloat() / totalPages.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(220),
        label = "kindle_page_progress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = if (hasBottomToolbarAbove) 74.dp else 14.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlassContainer(
            shape = CircleShape,
            fillAlpha = 0.66f,
            modifier = Modifier.testTag("kindle_page_indicator_pill"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Page $currentPage of $totalPages",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .height(3.dp)
                .clip(CircleShape),
        )
    }
}

/**
 * Computes exact pixel heights for the Kindle cover header, footer reserves, and viewport,
 * then delegates to PaginatedTextEngine so 100% of article text flows across N pages without clipping.
 */
private fun paginateKindleArticle(
    article: NewsArticle,
    paneWidthDp: Dp,
    paneHeightDp: Dp,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: TextMeasurer,
    headlineStyle: TextStyle,
    subtitleStyle: TextStyle,
    bodyStyle: TextStyle,
    metaLabelStyle: TextStyle,
    includeNudgeFooter: Boolean,
    includeOriginalReaderPill: Boolean,
    includeEndOfArticleCard: Boolean,
): List<MeasuredPaginatedPage> {
    val horizontalPadPx = with(density) { 40.dp.toPx() }.roundToInt()
    val verticalPadPx = with(density) { 26.dp.toPx() }.roundToInt() // 20.dp padding + 6.dp rounding buffer
    val paraGapPx = with(density) { 12.dp.toPx() }.roundToInt()

    val innerWidthPx = (with(density) { paneWidthDp.toPx() }.roundToInt() - horizontalPadPx).coerceAtLeast(140)
    val pageHeightPx = (with(density) { paneHeightDp.toPx() }.roundToInt() - verticalPadPx).coerceAtLeast(180)
    val textConstraints = Constraints(maxWidth = innerWidthPx)

    val metaRowHeightPx = maxOf(
        with(density) { 26.dp.toPx() }.roundToInt(),
        textMeasurer.measure(article.source, metaLabelStyle).size.height + with(density) { 10.dp.toPx() }.roundToInt(),
    )
    val headlineHeightPx = textMeasurer.measure(article.headline, headlineStyle, constraints = textConstraints).size.height
    val subtitleHeightPx = textMeasurer.measure(article.subtitle, subtitleStyle, constraints = textConstraints).size.height

    val hasLeadImage = !article.imageUrl.isNullOrBlank() || article.imageResId != null
    val heroDp = if (hasLeadImage) {
        when {
            paneHeightDp < 360.dp -> 96.dp
            paneHeightDp < 500.dp -> 140.dp
            else -> 182.dp
        }
    } else {
        0.dp
    }
    val hasVisibleHero = hasLeadImage && heroDp > 40.dp
    val heroBlockPx = if (hasVisibleHero) with(density) { heroDp.toPx() }.roundToInt() + paraGapPx else 0
    val dividerBlockPx = if (article.paragraphs.isNotEmpty()) {
        with(density) { 2.dp.toPx() }.roundToInt() + paraGapPx
    } else {
        0
    }

    // Items before first body paragraph: MetaRow -> (Hero) -> Headline -> Subtitle -> (Divider)
    val coverHeaderPx = metaRowHeightPx +
        heroBlockPx +
        paraGapPx + headlineHeightPx +
        paraGapPx + subtitleHeightPx +
        dividerBlockPx

    val originalPillReservePx = if (includeOriginalReaderPill) {
        with(density) { 62.dp.toPx() }.roundToInt()
    } else {
        0
    }
    val nudgeReservePx = if (includeNudgeFooter) {
        with(density) { 66.dp.toPx() }.roundToInt()
    } else {
        0
    }
    val endCardReservePx = if (includeEndOfArticleCard) {
        with(density) { 78.dp.toPx() }.roundToInt()
    } else {
        0
    }
    val lastPageFooterReservePx = originalPillReservePx + nudgeReservePx + endCardReservePx

    return paginateArticleWithTextMeasurer(
        paragraphs = article.paragraphs,
        pageWidthPx = innerWidthPx,
        pageHeightPx = pageHeightPx,
        coverHeaderHeightPx = coverHeaderPx,
        paraGapPx = paraGapPx,
        lastPageFooterReservePx = lastPageFooterReservePx,
        textMeasurer = textMeasurer,
        bodyStyle = bodyStyle,
        heroImageHeightDp = if (hasVisibleHero) heroDp else 0.dp,
        allowBodyOnCoverPage = true,
        coverHeaderIncludesTrailingGap = false,
    )
}
