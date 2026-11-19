package com.example.ui.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import com.example.model.AudioPlaybackState
import com.example.model.EventMetadata
import com.example.model.LocationMetadata
import com.example.model.NewsArticle
import com.example.model.ThemeSettings
import com.example.ui.components.ArticleBodyShimmerSkeleton
import com.example.ui.components.BlurredAmbientBackdrop
import com.example.ui.components.CircularControlButton
import com.example.ui.components.GlassCircleButton
import com.example.ui.components.GlassContainer
import com.example.ui.components.GlassSegmentedPill
import com.example.ui.components.InAppReaderWebViewDialog
import com.example.ui.components.OneNewsArticleImage
import com.example.ui.components.OpenOriginalReaderPill
import com.example.ui.components.WavyAudioScrubber
import com.example.ui.components.formatDuration
import com.example.ui.components.formatSpeed
import com.example.ui.components.glassBorderBrush
import com.example.ui.components.modernAutoHideScrollbar
import com.example.ui.components.rememberOneNewsHaptics
import com.example.ui.theme.NewsreaderFontFamily
import com.example.ui.theme.OneUiCardRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private enum class ReaderViewMode(val label: String) {
    ARTICLE("Article"),
    BRIEF("Brief"),
    AUDIO("Audio"),
}

@Composable
fun ArticleReaderPane(
    article: NewsArticle,
    pageFlipDirection: Int,
    showBackButton: Boolean,
    isBookmarked: Boolean,
    isOfflineSaved: Boolean,
    isNudgeDismissed: Boolean,
    themeSettings: ThemeSettings,
    audioState: AudioPlaybackState,
    foldingFeature: FoldingFeature?,
    savedScrollPositions: Map<String, Int>,
    onSaveScrollPosition: (String, Int) -> Unit,
    savedPagePositions: Map<String, Int> = emptyMap(),
    onSavePagePosition: (String, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onPreviousArticle: () -> Unit,
    onNextArticle: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    onToggleOfflineSave: (String) -> Unit,
    onPlayArticleAudio: (NewsArticle) -> Unit,
    onSeekAudio: (Float) -> Unit,
    onCycleAudioSpeed: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onTogglePaginatedMode: (Boolean) -> Unit = {},
    onOpenBookMode: (() -> Unit)? = null,
    onDismissNudge: (String) -> Unit,
    onRestoreNudge: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    isExtractingFullText: Boolean = false,
    applyWindowInsets: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberOneNewsHaptics()
    val density = LocalDensity.current

    var containerTopWindowPx by remember { mutableStateOf(0f) }
    var readerWebViewUrl by remember { mutableStateOf<String?>(null) }

    val isFlexTabletop = foldingFeature != null &&
        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
        foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL

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
            .onGloballyPositioned { coords ->
                containerTopWindowPx = coords.boundsInWindow().top
            },
    ) {
        val totalHeightDp = maxHeight

        // Real blurred atmospheric backdrop derived from active story imagery
        BlurredAmbientBackdrop(
            imageResId = article.imageResId,
            imageUrl = article.imageUrl,
            intensity = 0.34f,
        )

        AnimatedContent(
            targetState = article,
            contentKey = { it.id },
            transitionSpec = {
                val enterOffset: (Int) -> Int = { fullWidth ->
                    if (pageFlipDirection >= 0) (fullWidth * 0.28f).roundToInt() else -(fullWidth * 0.28f).roundToInt()
                }
                val exitOffset: (Int) -> Int = { fullWidth ->
                    if (pageFlipDirection >= 0) -(fullWidth * 0.18f).roundToInt() else (fullWidth * 0.18f).roundToInt()
                }
                (slideInHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.88f),
                    initialOffsetX = enterOffset,
                ) + fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(210),
                            targetOffsetX = exitOffset,
                        ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.97f, animationSpec = tween(200))
                    )
            },
            label = "article_page_flip",
        ) { activeArticle ->
            val scrollState = rememberScrollState(
                initial = savedScrollPositions[activeArticle.id] ?: 0
            )

            LaunchedEffect(activeArticle.id, scrollState) {
                snapshotFlow { scrollState.value }
                    .distinctUntilChanged()
                    .collect { pos ->
                        onSaveScrollPosition(activeArticle.id, pos)
                    }
            }

            if (isFlexTabletop && foldingFeature != null) {
                val hingeBounds = foldingFeature.bounds
                val relativeHingeTopPx = (hingeBounds.top - containerTopWindowPx).coerceAtLeast(0f)
                val relativeHingeBottomPx = (hingeBounds.bottom - containerTopWindowPx)
                    .coerceAtLeast(relativeHingeTopPx)

                val topHalfHeightDp: Dp = with(density) {
                    (relativeHingeTopPx.toDp() - 16.dp).coerceIn(140.dp, totalHeightDp * 0.56f)
                }
                val hingeBufferDp: Dp = with(density) {
                    ((relativeHingeBottomPx - relativeHingeTopPx).toDp() + 32.dp).coerceAtLeast(28.dp)
                }

                FlexTabletopReaderLayout(
                    article = activeArticle,
                    scrollState = scrollState,
                    topHalfHeight = topHalfHeightDp,
                    hingeBufferHeight = hingeBufferDp,
                    applyWindowInsets = applyWindowInsets,
                    showBackButton = showBackButton,
                    isBookmarked = isBookmarked,
                    isExtractingFullText = isExtractingFullText,
                    themeSettings = themeSettings,
                    audioState = audioState,
                    onBack = onBack,
                    onPreviousArticle = {
                        haptics.pageFlip()
                        onPreviousArticle()
                    },
                    onNextArticle = {
                        haptics.pageFlip()
                        onNextArticle()
                    },
                    onToggleBookmark = { onToggleBookmark(activeArticle.id) },
                    onPlayArticleAudio = { onPlayArticleAudio(activeArticle) },
                    onSeekAudio = onSeekAudio,
                    onCycleAudioSpeed = onCycleAudioSpeed,
                    onFontScaleChange = onFontScaleChange,
                    onAddCalendarEvent = { event ->
                        launchCalendarEventIntent(context, event, onShowMessage)
                    },
                    onOpenMaps = { loc ->
                        launchMapsIntent(context, loc, onShowMessage)
                    },
                    onOpenOriginalReaderView = { url -> readerWebViewUrl = url },
                    onShareSystem = {
                        launchShareIntent(context, activeArticle)
                    },
                )
            } else {
                StandardReaderLayout(
                    article = activeArticle,
                    scrollState = scrollState,
                    initialPageIndex = savedPagePositions[activeArticle.id] ?: 0,
                    onSavePagePosition = onSavePagePosition,
                    applyWindowInsets = applyWindowInsets,
                    showBackButton = showBackButton,
                    isBookmarked = isBookmarked,
                    isOfflineSaved = isOfflineSaved,
                    isNudgeDismissed = isNudgeDismissed,
                    isExtractingFullText = isExtractingFullText,
                    themeSettings = themeSettings,
                    audioState = audioState,
                    onBack = onBack,
                    onPreviousArticle = {
                        haptics.pageFlip()
                        onPreviousArticle()
                    },
                    onNextArticle = {
                        haptics.pageFlip()
                        onNextArticle()
                    },
                    onToggleBookmark = { onToggleBookmark(activeArticle.id) },
                    onToggleOfflineSave = { onToggleOfflineSave(activeArticle.id) },
                    onPlayArticleAudio = { onPlayArticleAudio(activeArticle) },
                    onSeekAudio = onSeekAudio,
                    onCycleAudioSpeed = onCycleAudioSpeed,
                    onFontScaleChange = onFontScaleChange,
                    onTogglePaginatedMode = onTogglePaginatedMode,
                    onOpenBookMode = onOpenBookMode,
                    onDismissNudge = { onDismissNudge(activeArticle.id) },
                    onRestoreNudge = { onRestoreNudge(activeArticle.id) },
                    onAddCalendarEvent = { event ->
                        launchCalendarEventIntent(context, event, onShowMessage)
                    },
                    onOpenMaps = { loc ->
                        launchMapsIntent(context, loc, onShowMessage)
                    },
                    onOpenOriginalReaderView = { url -> readerWebViewUrl = url },
                    onShareSystem = {
                        launchShareIntent(context, activeArticle)
                    },
                    onCopyArticleLink = {
                        copyArticleToClipboard(context, activeArticle)
                        onShowMessage("Headline & summary copied")
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandardReaderLayout(
    article: NewsArticle,
    scrollState: ScrollState,
    initialPageIndex: Int,
    onSavePagePosition: (String, Int) -> Unit,
    applyWindowInsets: Boolean,
    showBackButton: Boolean,
    isBookmarked: Boolean,
    isOfflineSaved: Boolean,
    isNudgeDismissed: Boolean,
    isExtractingFullText: Boolean,
    themeSettings: ThemeSettings,
    audioState: AudioPlaybackState,
    onBack: () -> Unit,
    onPreviousArticle: () -> Unit,
    onNextArticle: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleOfflineSave: () -> Unit,
    onPlayArticleAudio: () -> Unit,
    onSeekAudio: (Float) -> Unit,
    onCycleAudioSpeed: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onTogglePaginatedMode: (Boolean) -> Unit,
    onOpenBookMode: (() -> Unit)?,
    onDismissNudge: () -> Unit,
    onRestoreNudge: () -> Unit,
    onAddCalendarEvent: (EventMetadata) -> Unit,
    onOpenMaps: (LocationMetadata) -> Unit,
    onOpenOriginalReaderView: (String) -> Unit,
    onShareSystem: () -> Unit,
    onCopyArticleLink: () -> Unit,
) {
    val haptics = rememberOneNewsHaptics()
    var viewMode by remember { mutableStateOf(ReaderViewMode.ARTICLE) }
    var toolbarsVisible by remember { mutableStateOf(true) }
    var showFontMenu by remember { mutableStateOf(false) }
    var showBookmarkMenu by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }

    LaunchedEffect(viewMode, themeSettings.isPaginatedReadingMode) {
        if (viewMode != ReaderViewMode.ARTICLE || !themeSettings.isPaginatedReadingMode) {
            toolbarsVisible = true
        }
    }

    val hasContextualMetadata = article.eventMetadata != null || article.locationMetadata != null
    val isAtTopOrBottom by remember(scrollState) {
        derivedStateOf {
            scrollState.value < 60 || scrollState.value >= (scrollState.maxValue - 140).coerceAtLeast(0)
        }
    }

    var settledIdle by remember { mutableStateOf(true) }
    LaunchedEffect(scrollState, themeSettings.smartNowNudgeAutoHide) {
        if (!themeSettings.smartNowNudgeAutoHide) {
            settledIdle = true
            return@LaunchedEffect
        }
        snapshotFlow { scrollState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                settledIdle = false
            } else {
                delay(550L)
                settledIdle = true
            }
        }
    }

    val showNowNudgeChips = themeSettings.showNowNudgeChips &&
        hasContextualMetadata &&
        !isNudgeDismissed &&
        !themeSettings.isPaginatedReadingMode &&
        (!themeSettings.smartNowNudgeAutoHide || settledIdle || isAtTopOrBottom)

    val isPlayingThisArticle = !audioState.isNowBriefQueue &&
        audioState.activeArticleId == article.id &&
        audioState.isPlaying
    val isAudioActiveForThis = !audioState.isNowBriefQueue &&
        audioState.activeArticleId == article.id &&
        audioState.spokenText.isNotBlank()

    val showChrome = toolbarsVisible || !themeSettings.isPaginatedReadingMode || viewMode != ReaderViewMode.ARTICLE

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Frosted Glass Control Bar (hides on center tap in Paginated Book Mode)
            AnimatedVisibility(visible = showChrome) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (applyWindowInsets) it.statusBarsPadding() else it }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showBackButton) {
                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to feed",
                            onClick = onBack,
                            size = 44.dp,
                            testTag = "reader_back_button",
                        )
                    } else {
                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Previous article",
                            onClick = onPreviousArticle,
                            size = 44.dp,
                            iconSize = 19.dp,
                            testTag = "reader_prev_article",
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlassSegmentedPill(
                            options = ReaderViewMode.entries,
                            selectedOption = viewMode,
                            labelFor = { it.label },
                            onSelect = { viewMode = it },
                        )
                    }

                    GlassCircleButton(
                        icon = if (themeSettings.isPaginatedReadingMode) {
                            Icons.Rounded.MenuBook
                        } else {
                            Icons.Rounded.ViewDay
                        },
                        contentDescription = if (themeSettings.isPaginatedReadingMode) {
                            "Switch to Vertical Scroll"
                        } else {
                            "Switch to Paginated Book Mode"
                        },
                        onClick = {
                            val next = !themeSettings.isPaginatedReadingMode
                            onTogglePaginatedMode(next)
                            if (viewMode != ReaderViewMode.ARTICLE) {
                                viewMode = ReaderViewMode.ARTICLE
                            }
                        },
                        active = themeSettings.isPaginatedReadingMode,
                        size = 44.dp,
                        iconSize = 19.dp,
                        testTag = "reader_reading_mode_toggle",
                    )
                }
            }

            // Mode Content: Article (Vertical Scroll OR Kindle Paginated) | Brief | Audio
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(180))) },
                label = "reader_view_mode",
                modifier = Modifier.weight(1f),
            ) { mode ->
                when (mode) {
                    ReaderViewMode.ARTICLE -> {
                        if (themeSettings.isPaginatedReadingMode) {
                            KindlePaginatedReader(
                                article = article,
                                themeSettings = themeSettings,
                                toolbarsVisible = toolbarsVisible,
                                isExtractingFullText = isExtractingFullText,
                                initialPageIndex = initialPageIndex,
                                onSavePagePosition = onSavePagePosition,
                                onToggleToolbars = { toolbarsVisible = !toolbarsVisible },
                                onPreviousArticle = onPreviousArticle,
                                onNextArticle = onNextArticle,
                                onAddCalendarEvent = onAddCalendarEvent,
                                onOpenMaps = onOpenMaps,
                                onOpenOriginalReaderView = onOpenOriginalReaderView,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .let {
                                        if (!showChrome && applyWindowInsets) it.statusBarsPadding() else it
                                    },
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .modernAutoHideScrollbar(scrollState)
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 680.dp)
                                    .align(Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                // Source + metadata glass pill
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
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
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

                                if (!article.imageUrl.isNullOrBlank() || article.imageResId != null) {
                                    Surface(
                                        shape = RoundedCornerShape(OneUiCardRadius),
                                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
                                        shadowElevation = 10.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        OneNewsArticleImage(
                                            imageUrl = article.imageUrl,
                                            fallbackResId = article.imageResId,
                                            contentDescription = article.headline,
                                            shape = RoundedCornerShape(OneUiCardRadius),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(216.dp),
                                        )
                                    }
                                }

                                Text(
                                    text = article.headline,
                                    style = (if (themeSettings.useSerifHeadlines && !themeSettings.useSystemFont) {
                                        MaterialTheme.typography.headlineLarge.copy(fontFamily = NewsreaderFontFamily)
                                    } else {
                                        MaterialTheme.typography.headlineLarge
                                    }).copy(
                                        fontSize = (26f * themeSettings.readerFontScale.coerceIn(0.9f, 1.25f)).sp,
                                        lineHeight = (33f * themeSettings.readerFontScale.coerceIn(0.9f, 1.25f)).sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )

                                Text(
                                    text = article.subtitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = (16f * themeSettings.readerFontScale).sp,
                                        lineHeight = (23f * themeSettings.readerFontScale).sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                if (article.hasAudioBrief) {
                                    GlassContainer(
                                        shape = RoundedCornerShape(OneUiCardRadius),
                                        fillAlpha = 0.62f,
                                        highlighted = isPlayingThisArticle,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                CircularControlButton(
                                                    icon = if (isPlayingThisArticle) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                    contentDescription = if (isPlayingThisArticle) "Pause article brief" else "Listen to article brief",
                                                    onClick = onPlayArticleAudio,
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    borderColor = Color.White.copy(alpha = 0.35f),
                                                    size = 46.dp,
                                                    testTag = "reader_audio_play_button",
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (isPlayingThisArticle) "Playing Audio Brief" else "Listen to Audio Brief",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    Text(
                                                        text = "${formatDuration(article.audioDurationSeconds)} narrated summary",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                Surface(
                                                    onClick = {
                                                        haptics.toggle()
                                                        onCycleAudioSpeed()
                                                    },
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                                    border = BorderStroke(1.dp, glassBorderBrush()),
                                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                                ) {
                                                    Text(
                                                        text = "${formatSpeed(audioState.playbackSpeed)}x",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    )
                                                }
                                            }

                                            if (isAudioActiveForThis) {
                                                WavyAudioScrubber(
                                                    progress = audioState.progress,
                                                    isPlaying = isPlayingThisArticle,
                                                    onSeek = onSeekAudio,
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )

                                article.paragraphs.forEach { paragraph ->
                                    Text(
                                        text = paragraph,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = (16f * themeSettings.readerFontScale).sp,
                                            lineHeight = (26f * themeSettings.readerFontScale).sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                                    )
                                }

                                if (isExtractingFullText) {
                                    ArticleBodyShimmerSkeleton(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                } else if (article.fullTextExtractionFailed && !article.articleUrl.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OpenOriginalReaderPill(
                                        onClick = { onOpenOriginalReaderView(article.articleUrl) },
                                    )
                                }

                                Spacer(modifier = Modifier.height(148.dp))
                            }
                        }
                        }
                    }

                    ReaderViewMode.BRIEF -> {
                        // Glass Executive Summary & Key Takeaways View
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            GlassContainer(
                                shape = RoundedCornerShape(OneUiCardRadius),
                                fillAlpha = 0.64f,
                                highlighted = true,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = "STORY BRIEF",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        text = article.headline,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = article.audioSummary.ifBlank { article.subtitle },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Text(
                                        text = "Key Context",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    article.paragraphs.take(2).forEachIndexed { idx, para ->
                                        Text(
                                            text = "${idx + 1}. $para",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ReaderViewMode.AUDIO -> {
                        // Frosted Glass Now-Listening Audio Deck inspired directly by the screenshot's right pane
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            if (!article.imageUrl.isNullOrBlank() || article.imageResId != null) {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    border = BorderStroke(1.5.dp, glassBorderBrush(highlighted = true)),
                                    shadowElevation = 16.dp,
                                    modifier = Modifier
                                        .fillMaxWidth(0.84f)
                                        .height(240.dp),
                                ) {
                                    OneNewsArticleImage(
                                        imageUrl = article.imageUrl,
                                        fallbackResId = article.imageResId,
                                        contentDescription = article.headline,
                                        shape = RoundedCornerShape(28.dp),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = article.headline,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${article.source} — ${article.displayCategoryLabel}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                GlassCircleButton(
                                    icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    onClick = onToggleBookmark,
                                    active = isBookmarked,
                                    size = 46.dp,
                                )
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                val totalSecs = article.audioDurationSeconds.coerceAtLeast(60)
                                val activeProg = if (isAudioActiveForThis) audioState.progress else 0f
                                val elapsedSecs = (totalSecs * activeProg).roundToInt()

                                WavyAudioScrubber(
                                    progress = activeProg,
                                    isPlaying = isPlayingThisArticle,
                                    onSeek = {
                                        if (!isAudioActiveForThis) onPlayArticleAudio()
                                        onSeekAudio(it)
                                    },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = formatDuration(elapsedSecs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = formatDuration(totalSecs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // 5-button circular glass transport bar matching the screenshot
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GlassCircleButton(
                                    icon = Icons.Rounded.Speed,
                                    contentDescription = "Speed ${formatSpeed(audioState.playbackSpeed)}x",
                                    onClick = onCycleAudioSpeed,
                                    size = 48.dp,
                                )
                                GlassCircleButton(
                                    icon = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Previous article",
                                    onClick = onPreviousArticle,
                                    size = 52.dp,
                                )
                                CircularControlButton(
                                    icon = if (isPlayingThisArticle) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlayingThisArticle) "Pause" else "Play",
                                    onClick = onPlayArticleAudio,
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    borderColor = Color.White.copy(alpha = 0.4f),
                                    size = 66.dp,
                                    iconSize = 30.dp,
                                )
                                GlassCircleButton(
                                    icon = Icons.Rounded.SkipNext,
                                    contentDescription = "Next article",
                                    onClick = onNextArticle,
                                    size = 52.dp,
                                )
                                GlassCircleButton(
                                    icon = Icons.Rounded.Share,
                                    contentDescription = "Share",
                                    onClick = onShareSystem,
                                    size = 48.dp,
                                )
                            }

                            Spacer(modifier = Modifier.height(74.dp))
                        }
                    }
                }
            }
        }

        // Bottom Thumb-Zone: Smart Now Nudge Glass Pill + Floating Glass Utility Dock
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .let { if (applyWindowInsets) it.windowInsetsPadding(WindowInsets.navigationBars) else it }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            AnimatedVisibility(
                visible = showNowNudgeChips,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(240)),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(180)),
            ) {
                GlassContainer(
                    shape = RoundedCornerShape(24.dp),
                    fillAlpha = 0.78f,
                    highlighted = true,
                    shadowElevation = 8.dp,
                    modifier = Modifier.testTag("now_nudge_container"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false),
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
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("nudge_add_calendar_button"),
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
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("nudge_view_maps_button"),
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

                        GlassCircleButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Hide contextual nudge",
                            onClick = onDismissNudge,
                            size = 34.dp,
                            iconSize = 16.dp,
                            testTag = "nudge_dismiss_button",
                        )
                    }
                }
            }

            if (themeSettings.showNowNudgeChips && hasContextualMetadata && isNudgeDismissed) {
                Surface(
                    onClick = {
                        haptics.tap()
                        onRestoreNudge()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, glassBorderBrush()),
                ) {
                    Text(
                        text = "Show event & map actions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Bottom Floating Glass Toolbar
            if (viewMode != ReaderViewMode.AUDIO) {
                GlassContainer(
                    shape = CircleShape,
                    fillAlpha = 0.88f,
                    shadowElevation = 12.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Previous article",
                            onClick = onPreviousArticle,
                            size = 44.dp,
                            iconSize = 18.dp,
                        )

                        Box {
                            GlassCircleButton(
                                icon = Icons.Rounded.FormatSize,
                                contentDescription = "Reading mode & text size",
                                onClick = { showFontMenu = true },
                                active = showFontMenu,
                                size = 46.dp,
                                testTag = "reader_font_button",
                            )

                            DropdownMenu(
                                expanded = showFontMenu,
                                onDismissRequest = { showFontMenu = false },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(250.dp)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "Reading Mode",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        val isPaginated = themeSettings.isPaginatedReadingMode
                                        Surface(
                                            onClick = {
                                                haptics.toggle()
                                                onTogglePaginatedMode(false)
                                            },
                                            shape = CircleShape,
                                            color = if (!isPaginated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = if (!isPaginated) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f).testTag("reading_mode_vertical_scroll"),
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("Vertical Scroll", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                            }
                                        }
                                        Surface(
                                            onClick = {
                                                haptics.toggle()
                                                onTogglePaginatedMode(true)
                                                viewMode = ReaderViewMode.ARTICLE
                                            },
                                            shape = CircleShape,
                                            color = if (isPaginated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = if (isPaginated) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f).testTag("reading_mode_paginated_book"),
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("Book Mode", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                            }
                                        }
                                    }

                                    if (onOpenBookMode != null) {
                                        Surface(
                                            onClick = {
                                                haptics.tap()
                                                showFontMenu = false
                                                onOpenBookMode()
                                            },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            border = BorderStroke(1.dp, glassBorderBrush()),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                            ) {
                                                Icon(Icons.Rounded.OpenInFull, null, Modifier.size(15.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Full-Screen Spread", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Text Size",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "${(themeSettings.readerFontScale * 100).roundToInt()}%",
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
                                        valueRange = 0.85f..1.4f,
                                        modifier = Modifier.testTag("anchored_font_slider"),
                                    )
                                }
                            }
                        }

                        Box {
                            GlassCircleButton(
                                icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark options",
                                onClick = { showBookmarkMenu = true },
                                active = isBookmarked,
                                size = 46.dp,
                                testTag = "reader_bookmark_button",
                            )

                            DropdownMenu(
                                expanded = showBookmarkMenu,
                                onDismissRequest = { showBookmarkMenu = false },
                                shape = RoundedCornerShape(22.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isBookmarked) "Remove Bookmark" else "Bookmark Article")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Rounded.Check else Icons.Rounded.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = {
                                        haptics.toggle()
                                        onToggleBookmark()
                                        showBookmarkMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isOfflineSaved) "Remove Offline Copy" else "Save for Offline Reading")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isOfflineSaved) Icons.Rounded.CloudDone else Icons.Rounded.CloudDownload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                        )
                                    },
                                    onClick = {
                                        haptics.toggle()
                                        onToggleOfflineSave()
                                        showBookmarkMenu = false
                                    },
                                )
                            }
                        }

                        Box {
                            GlassCircleButton(
                                icon = Icons.Rounded.Share,
                                contentDescription = "Share article",
                                onClick = { showShareMenu = true },
                                size = 46.dp,
                                testTag = "reader_share_button",
                            )

                            DropdownMenu(
                                expanded = showShareMenu,
                                onDismissRequest = { showShareMenu = false },
                                shape = RoundedCornerShape(22.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share Headline & Link") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = {
                                        haptics.tap()
                                        showShareMenu = false
                                        onShareSystem()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy Summary") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        haptics.confirm()
                                        showShareMenu = false
                                        onCopyArticleLink()
                                    },
                                )
                            }
                        }

                        if (article.hasAudioBrief) {
                            GlassCircleButton(
                                icon = if (isPlayingThisArticle) Icons.Rounded.Pause else Icons.Rounded.GraphicEq,
                                contentDescription = if (isPlayingThisArticle) "Pause audio brief" else "Play audio brief",
                                onClick = onPlayArticleAudio,
                                active = isPlayingThisArticle,
                                size = 46.dp,
                                testTag = "reader_toolbar_audio_button",
                            )
                        }

                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Next article",
                            onClick = onNextArticle,
                            size = 44.dp,
                            iconSize = 18.dp,
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
private fun FlexTabletopReaderLayout(
    article: NewsArticle,
    scrollState: ScrollState,
    topHalfHeight: Dp,
    hingeBufferHeight: Dp,
    applyWindowInsets: Boolean,
    showBackButton: Boolean,
    isBookmarked: Boolean,
    isExtractingFullText: Boolean,
    themeSettings: ThemeSettings,
    audioState: AudioPlaybackState,
    onBack: () -> Unit,
    onPreviousArticle: () -> Unit,
    onNextArticle: () -> Unit,
    onToggleBookmark: () -> Unit,
    onPlayArticleAudio: () -> Unit,
    onSeekAudio: (Float) -> Unit,
    onCycleAudioSpeed: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onAddCalendarEvent: (EventMetadata) -> Unit,
    onOpenMaps: (LocationMetadata) -> Unit,
    onOpenOriginalReaderView: (String) -> Unit,
    onShareSystem: () -> Unit,
) {
    val haptics = rememberOneNewsHaptics()
    val isPlayingThisArticle = !audioState.isNowBriefQueue &&
        audioState.activeArticleId == article.id &&
        audioState.isPlaying
    val isAudioActiveForThis = !audioState.isNowBriefQueue &&
        audioState.activeArticleId == article.id &&
        audioState.spokenText.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("flex_tabletop_layout"),
    ) {
        // TOP HALF: Article text, headline, imagery strictly above the hinge crease
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topHalfHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (applyWindowInsets) it.statusBarsPadding() else it }
                    .modernAutoHideScrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!article.imageUrl.isNullOrBlank() || article.imageResId != null) {
                    OneNewsArticleImage(
                        imageUrl = article.imageUrl,
                        fallbackResId = article.imageResId,
                        contentDescription = article.headline,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    )
                }

                Text(
                    text = "${article.displayCategoryLabel.uppercase()} • ${article.source} • ${article.timestamp}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = article.headline,
                    style = (if (themeSettings.useSerifHeadlines && !themeSettings.useSystemFont) {
                        MaterialTheme.typography.headlineMedium.copy(fontFamily = NewsreaderFontFamily)
                    } else {
                        MaterialTheme.typography.headlineMedium
                    }).copy(
                        fontSize = (22f * themeSettings.readerFontScale.coerceIn(0.9f, 1.25f)).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    text = article.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                article.paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (16f * themeSettings.readerFontScale).sp,
                            lineHeight = (25f * themeSettings.readerFontScale).sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                    )
                }

                if (isExtractingFullText) {
                    ArticleBodyShimmerSkeleton(
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else if (article.fullTextExtractionFailed && !article.articleUrl.isNullOrBlank()) {
                    OpenOriginalReaderPill(
                        onClick = { onOpenOriginalReaderView(article.articleUrl) },
                    )
                }
            }
        }

        // CENTER HINGE CREASE BUFFER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hingeBufferHeight),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, glassBorderBrush()),
            ) {
                Text(
                    text = "FLEX TABLETOP CONTROLS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        // BOTTOM HALF: Frosted Glass Flex Control Deck
        GlassContainer(
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            fillAlpha = 0.72f,
            highlighted = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularControlButton(
                            icon = if (isPlayingThisArticle) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlayingThisArticle) "Pause TTS" else "Play TTS",
                            onClick = onPlayArticleAudio,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            borderColor = Color.White.copy(alpha = 0.35f),
                            size = 52.dp,
                            testTag = "flex_audio_play_pause",
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAudioActiveForThis) "Audio Brief Active" else "Read Aloud / Audio Brief",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${formatDuration(article.audioDurationSeconds.coerceAtLeast(90))} • ${formatSpeed(audioState.playbackSpeed)}x speed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        GlassCircleButton(
                            icon = Icons.Rounded.Speed,
                            contentDescription = "Cycle playback speed",
                            onClick = onCycleAudioSpeed,
                            size = 46.dp,
                            testTag = "flex_speed_button",
                        )
                    }

                    WavyAudioScrubber(
                        progress = if (isAudioActiveForThis) audioState.progress else 0f,
                        isPlaying = isPlayingThisArticle,
                        onSeek = {
                            if (!isAudioActiveForThis) onPlayArticleAudio()
                            onSeekAudio(it)
                        },
                        modifier = Modifier.testTag("flex_audio_scrubber"),
                    )
                }

                if (themeSettings.showNowNudgeChips && (article.eventMetadata != null || article.locationMetadata != null)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showBackButton) {
                            GlassCircleButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to feed",
                                onClick = onBack,
                                size = 48.dp,
                            )
                        }
                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Previous article",
                            onClick = onPreviousArticle,
                            size = 48.dp,
                            testTag = "flex_prev_article",
                        )
                        GlassCircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Next article",
                            onClick = onNextArticle,
                            size = 48.dp,
                            testTag = "flex_next_article",
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = {
                                haptics.toggle()
                                onFontScaleChange(themeSettings.readerFontScale - 0.1f)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, glassBorderBrush()),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("A-", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Surface(
                            onClick = {
                                haptics.toggle()
                                onFontScaleChange(themeSettings.readerFontScale + 0.1f)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, glassBorderBrush()),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("A+", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        GlassCircleButton(
                            icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            onClick = onToggleBookmark,
                            active = isBookmarked,
                            size = 48.dp,
                        )
                        GlassCircleButton(
                            icon = Icons.Rounded.Share,
                            contentDescription = "Share",
                            onClick = onShareSystem,
                            size = 48.dp,
                        )
                    }
                }
            }
        }
    }
}

private fun launchCalendarEventIntent(
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

private fun launchMapsIntent(
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

private fun launchShareIntent(context: Context, article: NewsArticle) {
    val shareText = "${article.headline}\n\n${article.subtitle}\n— ${article.source} via OneNews"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.headline)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share article"))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun copyArticleToClipboard(context: Context, article: NewsArticle) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("OneNews Article", "${article.headline} — ${article.subtitle}")
    clipboard?.setPrimaryClip(clip)
}
