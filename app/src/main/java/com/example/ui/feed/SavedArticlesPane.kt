package com.example.ui.feed

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AudioPlaybackState
import com.example.model.NewsArticle
import com.example.model.ThemeSettings
import com.example.ui.components.ArticleFeedCard
import com.example.ui.components.CollapsibleOneUiHeader
import com.example.ui.components.FeedBackToTopButton
import com.example.ui.components.LazyListQuickScrollbar
import com.example.ui.components.rememberAutoHideScrollToTopVisible
import com.example.ui.components.rememberOneNewsHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class SavedFilter(val label: String) {
    ALL("All Saved"),
    BOOKMARKS("Bookmarked"),
    OFFLINE("Offline"),
}

@Composable
fun SavedArticlesPane(
    articles: List<NewsArticle>,
    bookmarkedIds: Set<String>,
    offlineSavedIds: Set<String>,
    selectedArticleId: String?,
    highlightSelection: Boolean,
    themeSettings: ThemeSettings,
    audioState: AudioPlaybackState,
    bottomContentPadding: Dp,
    onArticleClick: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onPlayArticleAudio: (NewsArticle) -> Unit,
    onScrollingChange: (Boolean) -> Unit = {},
    applyStatusBarPadding: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val coroutineScope = rememberCoroutineScope()
    var activeFilter by remember { mutableStateOf(SavedFilter.ALL) }
    val listState = rememberLazyListState()
    var isScrollbarDragging by remember { mutableStateOf(false) }
    var headerScrollPulse by remember { mutableIntStateOf(0) }
    var isHeaderScrolling by remember { mutableStateOf(false) }
    val latestOnScrollingChange by rememberUpdatedState(onScrollingChange)

    LaunchedEffect(headerScrollPulse) {
        if (headerScrollPulse > 0) {
            isHeaderScrolling = true
            delay(320L)
            isHeaderScrolling = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress || isScrollbarDragging || isHeaderScrolling }
            .distinctUntilChanged()
            .collect { scrolling ->
                latestOnScrollingChange(scrolling)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            latestOnScrollingChange(false)
        }
    }

    val displayed = remember(articles, bookmarkedIds, offlineSavedIds, activeFilter) {
        when (activeFilter) {
            SavedFilter.ALL -> articles.filter { it.id in bookmarkedIds || it.id in offlineSavedIds }
            SavedFilter.BOOKMARKS -> articles.filter { it.id in bookmarkedIds }
            SavedFilter.OFFLINE -> articles.filter { it.id in offlineSavedIds }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val expandedHeight = (maxHeight * 0.28f).coerceIn(136.dp, 200.dp)
        val collapsedHeight = 64.dp
        val maxCollapsePx = with(density) { (expandedHeight - collapsedHeight).toPx() }.coerceAtLeast(1f)
        var headerOffsetPx by remember { mutableFloatStateOf(0f) }
        val collapseFraction = (-headerOffsetPx / maxCollapsePx).coerceIn(0f, 1f)

        val nestedScrollConnection = remember(maxCollapsePx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    if (source == NestedScrollSource.UserInput && abs(delta) > 0.5f) {
                        headerScrollPulse++
                    }
                    if (delta < 0f && headerOffsetPx > -maxCollapsePx) {
                        val old = headerOffsetPx
                        headerOffsetPx = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                        return Offset(0f, headerOffsetPx - old)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val delta = available.y
                    if (delta > 0f && headerOffsetPx < 0f) {
                        val old = headerOffsetPx
                        headerOffsetPx = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                        return Offset(0f, headerOffsetPx - old)
                    }
                    return Offset.Zero
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        ) {
            CollapsibleOneUiHeader(
                title = "Saved",
                subtitleDate = "${displayed.size} stories ready",
                collapseFraction = collapseFraction,
                expandedHeight = expandedHeight,
                collapsedHeight = collapsedHeight,
                applyStatusBarPadding = applyStatusBarPadding,
                showSearchButton = false,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SavedFilter.entries.forEach { filter ->
                    val selected = filter == activeFilter
                    Surface(
                        onClick = {
                            haptics.toggle()
                            activeFilter = filter
                        },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            if (displayed.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                        Text(
                            text = "No saved stories yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Bookmark articles or tap Save All in Editor's Brief.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val showScrollToTop = rememberAutoHideScrollToTopVisible(
                    listState = listState,
                    hasItems = displayed.isNotEmpty(),
                    isExternalScrolling = isScrollbarDragging || isHeaderScrolling,
                )
                val scrubLabelForIndex: (Int) -> String? = remember(displayed) {
                    { itemIdx ->
                        val article = displayed.getOrNull(itemIdx)
                        if (article != null) {
                            "${itemIdx + 1} / ${displayed.size} • ${article.displayCategoryLabel}"
                        } else {
                            null
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = bottomContentPadding + 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("saved_articles_list"),
                    ) {
                        items(displayed, key = { it.id }) { article ->
                            val isPlayingThis = !audioState.isNowBriefQueue &&
                                audioState.activeArticleId == article.id &&
                                audioState.isPlaying
                            ArticleFeedCard(
                                article = article,
                                isSelected = highlightSelection && article.id == selectedArticleId,
                                isBookmarked = article.id in bookmarkedIds,
                                isOfflineSaved = article.id in offlineSavedIds,
                                isAudioPlaying = isPlayingThis,
                                useSerifHeadlines = themeSettings.useSerifHeadlines,
                                onClick = { onArticleClick(article.id) },
                                onToggleBookmark = { onToggleBookmark(article.id) },
                                onPlayAudio = { onPlayArticleAudio(article) },
                            )
                        }
                    }

                    LazyListQuickScrollbar(
                        listState = listState,
                        bottomPadding = bottomContentPadding + 10.dp,
                        scrubLabelForIndex = scrubLabelForIndex,
                        onDraggingChange = { isScrollbarDragging = it },
                        testTag = "saved_quick_scrollbar",
                        modifier = Modifier.navigationBarsPadding(),
                    )

                    FeedBackToTopButton(
                        visible = showScrollToTop,
                        onClick = {
                            coroutineScope.launch {
                                launch { listState.animateScrollToItem(0) }
                                if (headerOffsetPx < 0f) {
                                    animate(
                                        initialValue = headerOffsetPx,
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    ) { value, _ ->
                                        headerOffsetPx = value
                                    }
                                }
                            }
                        },
                        testTag = "saved_scroll_to_top_button",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = bottomContentPadding + 14.dp),
                    )
                }
            }
        }
    }
}
