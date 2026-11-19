package com.example.ui.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.window.layout.FoldingFeature
import com.example.model.AudioPlaybackState
import com.example.model.EventMetadata
import com.example.model.LocationMetadata
import com.example.model.NewsArticle
import com.example.model.PageTurnAnimation
import com.example.model.ThemeSettings
import com.example.ui.components.ArticleBodyShimmerSkeleton
import com.example.ui.components.BlurredAmbientBackdrop
import com.example.ui.components.GlassCircleButton
import com.example.ui.components.InAppReaderWebViewDialog
import com.example.ui.components.OneNewsArticleImage
import com.example.ui.components.OpenOriginalReaderPill
import com.example.ui.components.glassBorderBrush
import com.example.ui.components.glassFillColor
import com.example.ui.components.rememberOneNewsHaptics
import com.example.ui.theme.NewsreaderFontFamily
import com.example.ui.theme.OneUiCardRadius
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal sealed interface BookPageContent {
    val pageNumber: Int
    val totalPages: Int
    val isLastPage: Boolean

    data class CoverPage(
        val article: NewsArticle,
        val heroImageHeightDp: Dp,
        val openingParagraphs: List<String>,
        override val pageNumber: Int,
        override val totalPages: Int,
        override val isLastPage: Boolean,
    ) : BookPageContent

    data class BodyPage(
        val article: NewsArticle,
        val paragraphs: List<String>,
        override val pageNumber: Int,
        override val totalPages: Int,
        override val isLastPage: Boolean,
    ) : BookPageContent
}

@Composable
fun FullScreenBookReader(
    article: NewsArticle,
    windowSizeClass: WindowSizeClass,
    foldingFeature: FoldingFeature?,
    isBookmarked: Boolean,
    themeSettings: ThemeSettings,
    audioState: AudioPlaybackState,
    onClose: () -> Unit,
    onPreviousArticle: () -> Unit,
    onNextArticle: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    onPlayArticleAudio: (NewsArticle) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onShowMessage: (String) -> Unit,
    initialPageIndex: Int = 0,
    onSavePagePosition: (String, Int) -> Unit = { _, _ -> },
    isExtractingFullText: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberOneNewsHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val isTwoColumnSpread = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact ||
        (foldingFeature?.orientation == FoldingFeature.Orientation.VERTICAL)

    val isFlexTabletop = foldingFeature != null &&
        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
        foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL

    var showFontMenu by remember { mutableStateOf(false) }
    var readerWebViewUrl by remember { mutableStateOf<String?>(null) }

    val coverHeadlineStyle = (if (themeSettings.useSerifHeadlines && !themeSettings.useSystemFont) {
        MaterialTheme.typography.headlineMedium.copy(fontFamily = NewsreaderFontFamily)
    } else {
        MaterialTheme.typography.headlineMedium
    }).copy(
        fontSize = (22f * themeSettings.readerFontScale.coerceIn(0.9f, 1.2f)).sp,
        lineHeight = (28f * themeSettings.readerFontScale.coerceIn(0.9f, 1.2f)).sp,
    )
    val coverSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = (14f * themeSettings.readerFontScale.coerceIn(0.9f, 1.2f)).sp,
        lineHeight = (20f * themeSettings.readerFontScale.coerceIn(0.9f, 1.2f)).sp,
    )
    val bookBodyStyle = (if (!themeSettings.useSystemFont) {
        MaterialTheme.typography.bodyLarge.copy(fontFamily = NewsreaderFontFamily)
    } else {
        MaterialTheme.typography.bodyLarge
    }).copy(
        fontSize = (16f * themeSettings.readerFontScale).sp,
        lineHeight = (25f * themeSettings.readerFontScale).sp,
    )
    val metaLabelStyle = MaterialTheme.typography.labelMedium

    readerWebViewUrl?.let { targetUrl ->
        InAppReaderWebViewDialog(
            url = targetUrl,
            title = article.headline,
            sourceName = article.source,
            onDismiss = { readerWebViewUrl = null },
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_screen_book_reader"),
    ) {
        BlurredAmbientBackdrop(
            imageResId = article.imageResId,
            imageUrl = article.imageUrl,
            intensity = 0.30f,
        )

        val statusInsetPx = WindowInsets.statusBars.getTop(density)
        val navInsetPx = WindowInsets.navigationBars.getBottom(density)
        val hingePaddingDp = if (
            foldingFeature != null &&
            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL &&
            foldingFeature.isSeparating
        ) {
            with(density) { (foldingFeature.bounds.width().toDp() + 18.dp).coerceAtLeast(24.dp) }
        } else {
            18.dp
        }
        val outerHorizPadDp = if (isTwoColumnSpread) 20.dp else 14.dp
        val spreadContentWidthDp = if (isTwoColumnSpread && !isFlexTabletop) {
            ((maxWidth - outerHorizPadDp * 2 - hingePaddingDp) / 2f).coerceAtLeast(180.dp)
        } else {
            (maxWidth - outerHorizPadDp * 2).coerceAtLeast(180.dp)
        }
        val spreadContentHeightDp = with(density) {
            val totalPx = maxHeight.toPx() - statusInsetPx - navInsetPx
            (totalPx.toDp() - 132.dp).coerceAtLeast(220.dp)
        }

        // Compute dynamic TextMeasurer pages sized to exact book card dimensions
        val pages = remember(
            article.id,
            article.paragraphs,
            article.imageUrl,
            article.imageResId,
            article.fullTextExtractionFailed,
            isExtractingFullText,
            themeSettings.readerFontScale,
            themeSettings.useSerifHeadlines,
            themeSettings.useSystemFont,
            themeSettings.showNowNudgeChips,
            isTwoColumnSpread,
            isFlexTabletop,
            spreadContentWidthDp,
            spreadContentHeightDp,
        ) {
            paginateArticleForBook(
                article = article,
                cardWidthDp = spreadContentWidthDp,
                cardHeightDp = spreadContentHeightDp,
                density = density,
                textMeasurer = textMeasurer,
                headlineStyle = coverHeadlineStyle,
                subtitleStyle = coverSubtitleStyle,
                bodyStyle = bookBodyStyle,
                metaLabelStyle = metaLabelStyle,
                isFlexTabletop = isFlexTabletop,
                includeNudgeFooter = themeSettings.showNowNudgeChips &&
                    (article.eventMetadata != null || article.locationMetadata != null),
                includeOriginalReaderPill = !isExtractingFullText &&
                    article.fullTextExtractionFailed &&
                    !article.articleUrl.isNullOrBlank(),
                includeEndOfArticleCard = !isExtractingFullText,
            )
        }

        val stepSize = if (isTwoColumnSpread && !isFlexTabletop) 2 else 1
        var currentPageIndex by remember(article.id) {
            mutableIntStateOf(initialPageIndex.coerceAtLeast(0))
        }
        val maxPageStart = ((pages.size - 1) / stepSize) * stepSize
        currentPageIndex = ((currentPageIndex / stepSize) * stepSize)
            .coerceIn(0, maxPageStart.coerceAtLeast(0))

        // Interactive Kindle-style page-turning animation state (0f = flat resting, 1f = turn completed)
        var turnDirection by remember(article.id) { mutableIntStateOf(1) }
        var dragAnchorY by remember(article.id) { mutableFloatStateOf(0.55f) }
        val turnProgress = remember(article.id) { Animatable(0f) }
        var isTurning by remember(article.id) { mutableStateOf(false) }

        LaunchedEffect(article.id) {
            turnProgress.snapTo(0f)
            isTurning = false
        }

        LaunchedEffect(article.id, currentPageIndex) {
            onSavePagePosition(article.id, currentPageIndex)
        }

        fun animateTurn(direction: Int, startProgress: Float = 0f) {
            val targetIndex = currentPageIndex + (direction * stepSize)
            if (targetIndex < 0) {
                haptics.pageFlip()
                scope.launch { turnProgress.snapTo(0f) }
                isTurning = false
                onPreviousArticle()
                return
            }
            if (targetIndex >= pages.size) {
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
                    currentPageIndex = targetIndex
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
                        durationMillis = if (startProgress > 0.2f) 320 else 480,
                        easing = FastOutSlowInEasing,
                    ),
                )
                currentPageIndex = targetIndex
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

        val isPlayingThis = !audioState.isNowBriefQueue &&
            audioState.activeArticleId == article.id &&
            audioState.isPlaying

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = if (isTwoColumnSpread) 20.dp else 14.dp, vertical = 10.dp),
        ) {
            // Minimalist Book Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    GlassCircleButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Exit full-screen book view",
                        onClick = onClose,
                        size = 44.dp,
                        testTag = "book_close_button",
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = article.source,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val pageLabel = if (isTwoColumnSpread && !isFlexTabletop && currentPageIndex + 1 < pages.size) {
                            "Pages ${currentPageIndex + 1}–${currentPageIndex + 2} of ${pages.size}"
                        } else {
                            "Page ${currentPageIndex + 1} of ${pages.size}"
                        }
                        Text(
                            text = pageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("book_page_indicator"),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        GlassCircleButton(
                            icon = Icons.Rounded.FormatSize,
                            contentDescription = "Book text size",
                            onClick = { showFontMenu = true },
                            active = showFontMenu,
                            size = 44.dp,
                        )
                        DropdownMenu(
                            expanded = showFontMenu,
                            onDismissRequest = { showFontMenu = false },
                            shape = RoundedCornerShape(22.dp),
                            containerColor = glassFillColor(
                                alpha = 0.88f,
                                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(220.dp)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Page Font Size", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "${(themeSettings.readerFontScale * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Slider(
                                    value = themeSettings.readerFontScale,
                                    onValueChange = {
                                        haptics.scrubTick()
                                        onFontScaleChange(it)
                                    },
                                    valueRange = 0.85f..1.35f,
                                )
                            }
                        }
                    }

                    if (article.hasAudioBrief) {
                        GlassCircleButton(
                            icon = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.GraphicEq,
                            contentDescription = "Audio brief",
                            onClick = { onPlayArticleAudio(article) },
                            active = isPlayingThis,
                            size = 44.dp,
                        )
                    }

                    GlassCircleButton(
                        icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        onClick = { onToggleBookmark(article.id) },
                        active = isBookmarked,
                        size = 44.dp,
                    )

                    GlassCircleButton(
                        icon = Icons.Rounded.Share,
                        contentDescription = "Share",
                        onClick = { shareBookArticle(context, article) },
                        size = 44.dp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(currentPageIndex, pages.size, isTwoColumnSpread, themeSettings.pageTurnAnimation) {
                        detectTapGestures { offset ->
                            if (isTurning) return@detectTapGestures
                            val widthF = size.width.toFloat().coerceAtLeast(1f)
                            val xNorm = offset.x / widthF
                            dragAnchorY = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0.15f, 0.85f)
                            when {
                                xNorm > 0.78f -> animateTurn(1)
                                xNorm < 0.22f -> animateTurn(-1)
                            }
                        }
                    }
                    .pointerInput(currentPageIndex, pages.size, isTwoColumnSpread, themeSettings.pageTurnAnimation) {
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
                                        (-accumulatedDx / (widthF * 0.75f)).coerceIn(0f, 1f)
                                    } else {
                                        (accumulatedDx / (widthF * 0.75f)).coerceIn(0f, 1f)
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
                                if (activeDragDirection != 0 && currentProg >= 0.28f) {
                                    animateTurn(activeDragDirection, startProgress = turnProgress.value)
                                } else {
                                    springBackCurl()
                                }
                                activeDragDirection = 0
                            },
                        )
                    },
            ) {
                val progress = turnProgress.value.coerceIn(0f, 1f)
                val targetIndex = (currentPageIndex + turnDirection * stepSize)
                    .coerceIn(0, (pages.size - 1).coerceAtLeast(0))

                val renderBookPage: @Composable (BookPageContent, Boolean) -> Unit = { page, isLeftSide ->
                    BookPageCard(
                        page = page,
                        isLeftSide = isLeftSide,
                        themeSettings = themeSettings,
                        headlineStyle = coverHeadlineStyle,
                        subtitleStyle = coverSubtitleStyle,
                        bodyStyle = bookBodyStyle,
                        isExtractingFullText = isExtractingFullText,
                        onAddCalendarEvent = { launchBookCalendarIntent(context, it, onShowMessage) },
                        onOpenMaps = { launchBookMapsIntent(context, it, onShowMessage) },
                        onOpenOriginalReaderView = { url -> readerWebViewUrl = url },
                    )
                }

                if (isTwoColumnSpread && !isFlexTabletop) {
                    TwoColumnBookSpreadContent(
                        pages = pages,
                        currentIndex = currentPageIndex,
                        targetIndex = targetIndex,
                        isTurning = isTurning,
                        turnDirection = turnDirection,
                        turnProgress = progress,
                        anchorYFraction = dragAnchorY,
                        hingeGap = hingePaddingDp,
                        animationMode = themeSettings.pageTurnAnimation,
                        renderPage = renderBookPage,
                    )
                } else {
                    SinglePageBookContent(
                        currentPage = pages.getOrNull(currentPageIndex),
                        nextOrPrevPage = pages.getOrNull(targetIndex)
                            ?.takeIf { targetIndex != currentPageIndex },
                        isTurning = isTurning,
                        turnDirection = turnDirection,
                        turnProgress = progress,
                        anchorYFraction = dragAnchorY,
                        animationMode = themeSettings.pageTurnAnimation,
                        renderPage = renderBookPage,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Thumb-Zone Book Page-Turner Deck (consistent frosted glass style & automatic theme-adaptive text)
            val navButtonColor = glassFillColor(
                alpha = 0.64f,
                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            val navButtonContentColor = MaterialTheme.colorScheme.onSurface
            val navButtonBorder = BorderStroke(1.dp, glassBorderBrush(highlighted = false))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { if (!isTurning) animateTurn(-1) },
                    shape = CircleShape,
                    color = navButtonColor,
                    contentColor = navButtonContentColor,
                    border = navButtonBorder,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("book_prev_page_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = navButtonContentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (currentPageIndex == 0) "Prev Story" else "Prev Page",
                            style = MaterialTheme.typography.labelLarge,
                            color = navButtonContentColor,
                        )
                    }
                }

                // Visual page dot indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val totalSpreads = ((pages.size + stepSize - 1) / stepSize).coerceAtLeast(1)
                    val activeSpread = (currentPageIndex / stepSize).coerceIn(0, totalSpreads - 1)
                    repeat(totalSpreads) { dot ->
                        val active = dot == activeSpread
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(if (active) 22.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                        )
                    }
                }

                Surface(
                    onClick = { if (!isTurning) animateTurn(1) },
                    shape = CircleShape,
                    color = navButtonColor,
                    contentColor = navButtonContentColor,
                    border = navButtonBorder,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("book_next_page_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val isAtEnd = currentPageIndex + stepSize >= pages.size
                        Text(
                            text = if (isAtEnd) "Next Story" else "Flip Page",
                            style = MaterialTheme.typography.labelLarge,
                            color = navButtonContentColor,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = navButtonContentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookPageCard(
    page: BookPageContent,
    isLeftSide: Boolean,
    themeSettings: ThemeSettings,
    headlineStyle: TextStyle,
    subtitleStyle: TextStyle,
    bodyStyle: TextStyle,
    isExtractingFullText: Boolean,
    onAddCalendarEvent: (EventMetadata) -> Unit,
    onOpenMaps: (LocationMetadata) -> Unit,
    onOpenOriginalReaderView: (String) -> Unit,
) {
    val cornerShape = RoundedCornerShape(OneUiCardRadius)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val opaquePageColor = bookPageSurfaceColor(MaterialTheme.colorScheme)
    val spineShadeAlpha = if (isDark) 0.15f else 0.07f

    // 100% opaque theme-matched paper surface (honors Monet dynamic color, tonal dark, light, and AMOLED)
    Surface(
        shape = cornerShape,
        color = opaquePageColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .testTag("book_page_card_${page.pageNumber}"),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle book spine shading gradient along inner edge
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .fillMaxHeight()
                    .align(if (isLeftSide) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            colors = if (isLeftSide) {
                                listOf(Color.Transparent, Color.Black.copy(alpha = spineShadeAlpha))
                            } else {
                                listOf(Color.Black.copy(alpha = spineShadeAlpha), Color.Transparent)
                            }
                        )
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                when (page) {
                    is BookPageContent.CoverPage -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if ((!page.article.imageUrl.isNullOrBlank() || page.article.imageResId != null) &&
                                page.heroImageHeightDp > 40.dp
                            ) {
                                OneNewsArticleImage(
                                    imageUrl = page.article.imageUrl,
                                    fallbackResId = page.article.imageResId,
                                    contentDescription = page.article.headline,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(page.heroImageHeightDp),
                                )
                            }

                            Text(
                                text = "${page.article.displayCategoryLabel.uppercase()} • ${page.article.timestamp}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )

                            Text(
                                text = page.article.headline,
                                style = headlineStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Text(
                                text = page.article.subtitle,
                                style = subtitleStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (page.openingParagraphs.isNotEmpty() || isExtractingFullText) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                                if (isExtractingFullText) {
                                    ArticleBodyShimmerSkeleton()
                                } else {
                                    page.openingParagraphs.forEach { para ->
                                        Text(
                                            text = para,
                                            style = bodyStyle,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                        )
                                    }
                                }
                            }

                            if (page.isLastPage && !isExtractingFullText) {
                                BookLastPageFooterExtras(
                                    article = page.article,
                                    themeSettings = themeSettings,
                                    onAddCalendarEvent = onAddCalendarEvent,
                                    onOpenMaps = onOpenMaps,
                                    onOpenOriginalReaderView = onOpenOriginalReaderView,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${page.article.readingTimeMinutes} min read",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${page.pageNumber} / ${page.totalPages}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    is BookPageContent.BodyPage -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            page.paragraphs.forEach { para ->
                                Text(
                                    text = para,
                                    style = bodyStyle,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                )
                            }

                            if (page.isLastPage && !isExtractingFullText) {
                                BookLastPageFooterExtras(
                                    article = page.article,
                                    themeSettings = themeSettings,
                                    onAddCalendarEvent = onAddCalendarEvent,
                                    onOpenMaps = onOpenMaps,
                                    onOpenOriginalReaderView = onOpenOriginalReaderView,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = page.article.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${page.pageNumber} / ${page.totalPages}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookLastPageFooterExtras(
    article: NewsArticle,
    themeSettings: ThemeSettings,
    onAddCalendarEvent: (EventMetadata) -> Unit,
    onOpenMaps: (LocationMetadata) -> Unit,
    onOpenOriginalReaderView: (String) -> Unit,
) {
    val haptics = rememberOneNewsHaptics()

    if (article.fullTextExtractionFailed && !article.articleUrl.isNullOrBlank()) {
        OpenOriginalReaderPill(
            onClick = { onOpenOriginalReaderView(article.articleUrl) },
        )
    }

    if (themeSettings.showNowNudgeChips &&
        (article.eventMetadata != null || article.locationMetadata != null)
    ) {
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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp))
                        Text("Add event to Calendar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            article.locationMetadata?.let { loc ->
                Surface(
                    onClick = {
                        haptics.confirm()
                        onOpenMaps(loc)
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, Modifier.size(16.dp))
                        Text("View location in Maps", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    EndOfArticleFooterCard(sourceName = article.source)
}

private fun paginateArticleForBook(
    article: NewsArticle,
    cardWidthDp: Dp,
    cardHeightDp: Dp,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: TextMeasurer,
    headlineStyle: TextStyle,
    subtitleStyle: TextStyle,
    bodyStyle: TextStyle,
    metaLabelStyle: TextStyle,
    isFlexTabletop: Boolean,
    includeNudgeFooter: Boolean,
    includeOriginalReaderPill: Boolean,
    includeEndOfArticleCard: Boolean,
): List<BookPageContent> {
    val cardPadPx = with(density) { 44.dp.toPx() }.roundToInt() // 22.dp * 2
    val cardFooterRowPx = with(density) { 28.dp.toPx() }.roundToInt()
    val safetyPx = with(density) { 8.dp.toPx() }.roundToInt()
    val paraGapPx = with(density) { 12.dp.toPx() }.roundToInt()

    val innerWidthPx = (with(density) { cardWidthDp.toPx() }.roundToInt() - cardPadPx).coerceAtLeast(140)
    val pageHeightPx = (with(density) { cardHeightDp.toPx() }.roundToInt() - cardPadPx - cardFooterRowPx - safetyPx).coerceAtLeast(180)
    val textConstraints = Constraints(maxWidth = innerWidthPx)

    val hasLeadImage = !article.imageUrl.isNullOrBlank() || article.imageResId != null
    val heroDp = if (hasLeadImage) {
        when {
            isFlexTabletop || cardHeightDp < 380.dp -> 88.dp
            cardHeightDp < 500.dp -> 120.dp
            else -> 145.dp
        }
    } else {
        0.dp
    }
    val hasVisibleHero = hasLeadImage && heroDp > 40.dp
    val heroBlockPx = if (hasVisibleHero) with(density) { heroDp.toPx() }.roundToInt() + paraGapPx else 0
    val metaHeightPx = textMeasurer.measure("TECH • Today", metaLabelStyle, constraints = textConstraints).size.height
    val headlineHeightPx = textMeasurer.measure(article.headline, headlineStyle, constraints = textConstraints).size.height
    val subtitleHeightPx = textMeasurer.measure(article.subtitle, subtitleStyle, constraints = textConstraints).size.height
    val dividerBlockPx = if (article.paragraphs.isNotEmpty()) {
        with(density) { 2.dp.toPx() }.roundToInt() + paraGapPx
    } else {
        0
    }

    val coverHeaderPx = heroBlockPx +
        metaHeightPx + paraGapPx +
        headlineHeightPx + paraGapPx +
        subtitleHeightPx +
        dividerBlockPx

    val originalPillReservePx = if (includeOriginalReaderPill) with(density) { 62.dp.toPx() }.roundToInt() else 0
    val nudgeReservePx = if (includeNudgeFooter) with(density) { 66.dp.toPx() }.roundToInt() else 0
    val endCardReservePx = if (includeEndOfArticleCard) with(density) { 78.dp.toPx() }.roundToInt() else 0
    val lastPageFooterReservePx = originalPillReservePx + nudgeReservePx + endCardReservePx

    val measuredPages = paginateArticleWithTextMeasurer(
        paragraphs = article.paragraphs,
        pageWidthPx = innerWidthPx,
        pageHeightPx = pageHeightPx,
        coverHeaderHeightPx = coverHeaderPx,
        paraGapPx = paraGapPx,
        lastPageFooterReservePx = lastPageFooterReservePx,
        textMeasurer = textMeasurer,
        bodyStyle = bodyStyle,
        heroImageHeightDp = if (hasVisibleHero) heroDp else 0.dp,
        allowBodyOnCoverPage = !isFlexTabletop && cardHeightDp > 420.dp,
        coverHeaderIncludesTrailingGap = false,
    )

    val totalPages = measuredPages.size.coerceAtLeast(1)
    return measuredPages.mapIndexed { idx, mp ->
        if (mp.isCoverPage) {
            BookPageContent.CoverPage(
                article = article,
                heroImageHeightDp = mp.heroImageHeightDp,
                openingParagraphs = mp.paragraphs,
                pageNumber = idx + 1,
                totalPages = totalPages,
                isLastPage = mp.isLastPage,
            )
        } else {
            BookPageContent.BodyPage(
                article = article,
                paragraphs = mp.paragraphs,
                pageNumber = idx + 1,
                totalPages = totalPages,
                isLastPage = mp.isLastPage,
            )
        }
    }
}

private fun launchBookCalendarIntent(
    context: Context,
    event: EventMetadata,
    onFeedback: (String) -> Unit,
) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, event.title)
        putExtra(CalendarContract.Events.EVENT_LOCATION, event.locationLabel)
        putExtra(CalendarContract.Events.DESCRIPTION, event.description)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startTimeMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endTimeMillis)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onFeedback("Event prepared: ${event.title} (${event.dateTimeLabel})")
    }
}

private fun launchBookMapsIntent(
    context: Context,
    location: LocationMetadata,
    onFeedback: (String) -> Unit,
) {
    val geoUri = Uri.parse(
        "geo:${location.latitude},${location.longitude}?q=${Uri.encode(location.venueName + ", " + location.address)}"
    )
    val intent = Intent(Intent.ACTION_VIEW, geoUri)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onFeedback("Location: ${location.venueName} • ${location.address}")
    }
}

private fun shareBookArticle(context: Context, article: NewsArticle) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.headline)
        putExtra(Intent.EXTRA_TEXT, "${article.headline}\n\n${article.subtitle}\n— ${article.source} via OneNews")
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share article"))
    } catch (_: ActivityNotFoundException) {
    }
}
