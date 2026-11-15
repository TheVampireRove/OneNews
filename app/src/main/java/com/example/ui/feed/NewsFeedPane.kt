package com.example.ui.feed

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.MockNewsRepository
import com.example.model.AudioPlaybackState
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.ThemeSettings
import com.example.ui.components.ArticleFeedCard
import com.example.ui.components.CategoryFilterStrip
import com.example.ui.components.CollapsibleOneUiHeader
import com.example.ui.components.FeedBackToTopButton
import com.example.ui.components.GlassContainer
import com.example.ui.components.LazyListQuickScrollbar
import com.example.ui.components.NowBriefHeroCard
import com.example.ui.components.formatTodayDate
import com.example.ui.components.glassBorderBrush
import com.example.ui.components.glassFillColor
import com.example.ui.components.rememberOneNewsHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedPane(
    articles: List<NewsArticle>,
    nowBriefArticles: List<NewsArticle>,
    selectedCategory: NewsCategory,
    customCategories: List<String> = emptyList(),
    selectedCustomCategory: String? = null,
    searchQuery: String,
    isSearchExpanded: Boolean,
    selectedArticleId: String?,
    highlightSelection: Boolean,
    bookmarkedIds: Set<String>,
    offlineSavedIds: Set<String>,
    themeSettings: ThemeSettings,
    audioState: AudioPlaybackState,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    bottomContentPadding: Dp,
    onSaveScrollPosition: (Int, Int) -> Unit,
    onSelectCategory: (NewsCategory) -> Unit,
    onSelectCustomCategory: (String) -> Unit = {},
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onArticleClick: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onPlayArticleAudio: (NewsArticle) -> Unit,
    onPlayBriefAudio: () -> Unit,
    onToggleBriefPlayPause: () -> Unit,
    onSaveAllBriefOffline: () -> Unit,
    onToggleBriefTopic: (NewsCategory) -> Unit,
    onSelectLocalLocation: (String) -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onScrollingChange: (Boolean) -> Unit = {},
    applyStatusBarPadding: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
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

    // Persist exact scroll position in ViewModel for Cover <-> Unfolded continuity
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                onSaveScrollPosition(idx, off)
            }
    }

    val todayFormatted = remember { formatTodayDate() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Viewing Area: ~35% of screen height on compact portrait, bounded cleanly on tall/wide screens
        val expandedHeaderHeight = (maxHeight * 0.34f).coerceIn(156.dp, 248.dp)
        val collapsedHeaderHeight = 64.dp
        val maxCollapsePx = with(density) { (expandedHeaderHeight - collapsedHeaderHeight).toPx() }
            .coerceAtLeast(1f)

        var headerOffsetPx by remember { mutableFloatStateOf(0f) }
        val collapseFraction = (-headerOffsetPx / maxCollapsePx).coerceIn(0f, 1f)

        val nestedScrollConnection = remember(maxCollapsePx, listState) {
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
                    // Expand collapsed One UI header at top before inner PullToRefreshBox starts pulling
                    if (delta > 0f && headerOffsetPx < 0f && !listState.canScrollBackward) {
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
                title = "News",
                subtitleDate = todayFormatted,
                collapseFraction = collapseFraction,
                expandedHeight = expandedHeaderHeight,
                collapsedHeight = collapsedHeaderHeight,
                applyStatusBarPadding = applyStatusBarPadding,
                showSearchButton = true,
                isSearchExpanded = isSearchExpanded,
                searchQuery = searchQuery,
                onToggleSearch = onToggleSearch,
                onSearchQueryChange = onSearchQueryChange,
            )

            CategoryFilterStrip(
                selectedCategory = selectedCategory,
                onSelectCategory = onSelectCategory,
                customCategories = customCategories,
                selectedCustomCategory = selectedCustomCategory,
                onSelectCustomCategory = onSelectCustomCategory,
            )

            AnimatedVisibility(visible = selectedCustomCategory == null && selectedCategory == NewsCategory.LOCAL) {
                LocalAreaInlineBar(
                    currentLocation = themeSettings.localNewsLocation,
                    radiusMiles = themeSettings.localNewsRadiusMiles,
                    onSelectLocation = onSelectLocalLocation,
                )
            }

            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    haptics.confirm()
                    onRefresh()
                },
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("feed_pull_to_refresh"),
            ) {
                if (articles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp),
                            )
                            Text(
                                text = "No matching stories",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Try switching categories, clearing your search filter, or pull down to refresh.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    val hasNowBriefItem = themeSettings.enableNowBrief &&
                        searchQuery.isBlank() &&
                        selectedCustomCategory == null &&
                        selectedCategory == NewsCategory.ALL

                    val showScrollToTop by remember(listState, articles) {
                        derivedStateOf {
                            articles.isNotEmpty() &&
                                (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 260)
                        }
                    }

                    val scrubLabelForIndex: (Int) -> String? = remember(articles, hasNowBriefItem) {
                        { itemIdx ->
                            if (hasNowBriefItem && itemIdx == 0) {
                                "Editor's Brief"
                            } else {
                                val articleIdx = (itemIdx - if (hasNowBriefItem) 1 else 0)
                                    .coerceIn(0, (articles.size - 1).coerceAtLeast(0))
                                val article = articles.getOrNull(articleIdx)
                                if (article != null) {
                                    "${articleIdx + 1} / ${articles.size} • ${article.displayCategoryLabel}"
                                } else {
                                    null
                                }
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
                                bottom = bottomContentPadding + 36.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("news_feed_list"),
                        ) {
                            if (hasNowBriefItem) {
                                item(key = "now_brief_hero") {
                                    NowBriefHeroCard(
                                        briefArticles = nowBriefArticles,
                                        selectedTopics = themeSettings.briefTopics,
                                        offlineSavedIds = offlineSavedIds,
                                        audioState = audioState,
                                        onOpenArticle = onArticleClick,
                                        onPlayBriefAudio = onPlayBriefAudio,
                                        onTogglePlayPause = onToggleBriefPlayPause,
                                        onSaveAllOffline = onSaveAllBriefOffline,
                                        onToggleTopic = onToggleBriefTopic,
                                    )
                                }
                            }

                            items(
                                items = articles,
                                key = { it.id },
                            ) { article ->
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
                            testTag = "feed_quick_scrollbar",
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
                            testTag = "feed_scroll_to_top_button",
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
}

@Composable
private fun LocalAreaInlineBar(
    currentLocation: String,
    radiusMiles: Int,
    onSelectLocation: (String) -> Unit,
) {
    val haptics = rememberOneNewsHaptics()
    var showLocationMenu by remember { mutableStateOf(false) }
    var customAreaInput by remember { mutableStateOf("") }
    val presets = remember { MockNewsRepository.getLocalPresets() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        GlassContainer(
            shape = CircleShape,
            fillAlpha = 0.64f,
            highlighted = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                onClick = {
                    haptics.tap()
                    showLocationMenu = true
                },
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("feed_local_location_pill"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "Active local area",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Area: $currentLocation • $radiusMiles mi",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Change local area",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showLocationMenu,
            onDismissRequest = { showLocationMenu = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = glassFillColor(
                alpha = 0.92f,
                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Text(
                text = "Local News Area",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            presets.forEach { preset ->
                val selected = preset.displayName.equals(currentLocation, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = preset.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        haptics.confirm()
                        onSelectLocation(preset.displayName)
                        showLocationMenu = false
                    },
                )
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = customAreaInput,
                    onValueChange = { customAreaInput = it },
                    label = { Text("Custom City or ZIP") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.width(220.dp),
                )
                Surface(
                    onClick = {
                        if (customAreaInput.isNotBlank()) {
                            haptics.confirm()
                            onSelectLocation(customAreaInput.trim())
                            customAreaInput = ""
                            showLocationMenu = false
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    border = BorderStroke(1.dp, glassBorderBrush(highlighted = true)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Pull Local News", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
