package com.example.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.example.model.AppDestination
import com.example.ui.components.BlurredAmbientBackdrop
import com.example.ui.components.FloatingBottomNavBar
import com.example.ui.components.MiniAudioPlayerBar
import com.example.ui.components.OneNewsNavigationRail
import com.example.ui.components.PredictiveBackContainer
import com.example.ui.components.glassBorderBrush
import com.example.ui.components.glassFillColor
import com.example.ui.feed.NewsFeedPane
import com.example.ui.feed.SavedArticlesPane
import com.example.ui.reader.ArticleReaderPane
import com.example.ui.reader.FullScreenBookReader
import com.example.ui.reader.ReaderPaneEmptyState
import com.example.ui.reader.ReaderPaneShimmerSkeleton
import com.example.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

@Composable
fun OneNewsApp(
    uiState: OneNewsUiState,
    windowSizeClass: WindowSizeClass,
    foldingFeature: FoldingFeature?,
    viewModel: NewsViewModel,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
            )
            viewModel.clearSnackbar()
        }
    }

    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val isDualPaneActive = isWideScreen && uiState.themeSettings.enableDualPaneLayout
    val useTwoColumnGrid = !uiState.themeSettings.enableDualPaneLayout
    val isMiniPlayerVisible = uiState.audioState.spokenText.isNotBlank()
    val showFullScreenBook = uiState.isFullScreenBookOpen && uiState.selectedArticle != null
    val isCompactReaderVisible = !isWideScreen && uiState.isCompactReaderOpen && uiState.selectedArticle != null

    // Collapse expanded search bar on predictive back when at root Feed (otherwise let system handle back-to-home)
    PredictiveBackHandler(
        enabled = !showFullScreenBook &&
            !isCompactReaderVisible &&
            uiState.activeDestination == AppDestination.FEED &&
            uiState.isSearchExpanded,
    ) { progressFlow ->
        try {
            progressFlow.collect { }
            viewModel.toggleSearchExpanded(false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        BlurredAmbientBackdrop(
            imageResId = uiState.selectedArticle?.imageResId,
            imageUrl = uiState.selectedArticle?.imageUrl,
            intensity = 0.24f,
        )

        AnimatedContent(
            targetState = showFullScreenBook,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260)))
                        .togetherWith(fadeOut(tween(180)))
                } else {
                    fadeIn(tween(220))
                        .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.95f, animationSpec = tween(200)))
                }
            },
            label = "book_fullscreen_transition",
        ) { fullScreenActive ->
            if (fullScreenActive && uiState.selectedArticle != null) {
                val activeArticle = uiState.selectedArticle!!
                PredictiveBackContainer(
                    enabled = showFullScreenBook,
                    onBack = viewModel::closeFullScreenBookReader,
                    resetKey = activeArticle.id,
                ) {
                    FullScreenBookReader(
                        article = activeArticle,
                        windowSizeClass = windowSizeClass,
                        foldingFeature = foldingFeature,
                        isBookmarked = activeArticle.id in uiState.bookmarkedIds,
                        isExtractingFullText = activeArticle.id in uiState.extractingArticleIds,
                        initialPageIndex = uiState.readerPagePositions[activeArticle.id] ?: 0,
                        onSavePagePosition = viewModel::updateReaderPagePosition,
                        themeSettings = uiState.themeSettings,
                        audioState = uiState.audioState,
                        onClose = viewModel::closeFullScreenBookReader,
                        onPreviousArticle = viewModel::navigatePreviousArticle,
                        onNextArticle = viewModel::navigateNextArticle,
                        onToggleBookmark = viewModel::toggleBookmark,
                        onPlayArticleAudio = viewModel::playArticleAudio,
                        onFontScaleChange = viewModel::setReaderFontScale,
                        onShowMessage = viewModel::showSnackbar,
                    )
                }
            } else if (isWideScreen) {
                // Unfolded / Wide Display: Floating Glass Navigation Rail + Dual-Pane Master-Detail or Full-Width 2-Column Grid
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 10.dp, top = 6.dp, bottom = 10.dp, end = 10.dp),
                ) {
                    OneNewsNavigationRail(
                        activeDestination = uiState.activeDestination,
                        isBookModeEnabled = uiState.themeSettings.fullScreenBookReader || !uiState.themeSettings.enableDualPaneLayout,
                        isAmoledEnabled = uiState.themeSettings.useAmoledBlack,
                        onSelectDestination = viewModel::selectDestination,
                        onQuickToggleBookMode = {
                            if (!uiState.themeSettings.enableDualPaneLayout) {
                                val activeId = uiState.selectedArticle?.id ?: uiState.filteredArticles.firstOrNull()?.id
                                if (activeId != null && uiState.activeDestination != AppDestination.SETTINGS) {
                                    viewModel.openArticle(activeId, openCompactReader = true)
                                }
                            } else {
                                val next = !uiState.themeSettings.fullScreenBookReader
                                viewModel.setFullScreenBookReader(next)
                                val activeId = uiState.selectedArticle?.id
                                if (next && activeId != null && uiState.activeDestination != AppDestination.SETTINGS) {
                                    viewModel.openArticle(activeId, openCompactReader = true)
                                }
                            }
                        },
                        onQuickPlayBrief = viewModel::playNowBriefAudio,
                        onQuickToggleAmoled = {
                            viewModel.setUseAmoledBlack(!uiState.themeSettings.useAmoledBlack)
                        },
                        applyWindowInsets = false,
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    val bottomPad = if (isMiniPlayerVisible) 86.dp else 16.dp

                    PredictiveBackContainer(
                        enabled = !showFullScreenBook && uiState.activeDestination != AppDestination.FEED,
                        onBack = { viewModel.selectDestination(AppDestination.FEED) },
                        minScale = 0.93f,
                        resetKey = uiState.activeDestination,
                        modifier = Modifier.weight(1f),
                    ) {
                        AnimatedContent(
                            targetState = uiState.activeDestination,
                            transitionSpec = {
                                fadeIn(tween(200)).togetherWith(fadeOut(tween(150)))
                            },
                            label = "expanded_destination",
                            modifier = Modifier.fillMaxSize(),
                        ) { destination ->
                        when (destination) {
                            AppDestination.SETTINGS -> {
                                val paneShape = RoundedCornerShape(30.dp)
                                Surface(
                                    shape = paneShape,
                                    color = glassFillColor(
                                        alpha = 0.34f,
                                        baseColor = MaterialTheme.colorScheme.surface,
                                    ),
                                    border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(paneShape),
                                ) {
                                    SettingsScreen(
                                        themeSettings = uiState.themeSettings,
                                        availableSources = uiState.availableSources,
                                        enabledSourceIds = uiState.enabledSourceIds,
                                        customCategories = uiState.customCategories,
                                        widthSizeClass = windowSizeClass.widthSizeClass,
                                        foldingFeature = foldingFeature,
                                        offlineCount = uiState.offlineSavedIds.size,
                                        bookmarkCount = uiState.bookmarkedIds.size,
                                        bottomContentPadding = bottomPad,
                                        onSetUseDynamicColor = viewModel::setUseDynamicColor,
                                        onSetUseAmoledBlack = viewModel::setUseAmoledBlack,
                                        onSetThemeMode = viewModel::setThemeMode,
                                        onSetReaderFontScale = viewModel::setReaderFontScale,
                                        onSetDefaultAudioSpeed = viewModel::setDefaultAudioSpeed,
                                        onSetUseSerifHeadlines = viewModel::setUseSerifHeadlines,
                                        onSetUseSystemFont = viewModel::setUseSystemFont,
                                        onSetEnableNowBrief = viewModel::setEnableNowBrief,
                                        onSetShowNowNudgeChips = viewModel::setShowNowNudgeChips,
                                        onSetSmartNowNudgeAutoHide = viewModel::setSmartNowNudgeAutoHide,
                                        onSetEnableDualPaneLayout = viewModel::setEnableDualPaneLayout,
                                        onSetFullScreenBookReader = viewModel::setFullScreenBookReader,
                                        onSetPaginatedReadingMode = viewModel::setPaginatedReadingMode,
                                        onSetPageTurnAnimation = viewModel::setPageTurnAnimation,
                                        onSetLocalNewsLocation = viewModel::setLocalNewsLocation,
                                        onSetLocalNewsRadiusMiles = viewModel::setLocalNewsRadiusMiles,
                                        onToggleBriefTopic = viewModel::toggleBriefTopic,
                                        onSetBriefSchedule = viewModel::setBriefSchedule,
                                        onToggleSourceEnabled = viewModel::toggleSourceEnabled,
                                        onPreviewFeed = viewModel::previewFeedForAdd,
                                        onAddManualSource = viewModel::addManualSource,
                                        onReassignSourceCategory = viewModel::reassignSourceCategory,
                                        onCreateCustomCategory = viewModel::createCustomCategory,
                                        onRemoveCustomSource = viewModel::removeCustomSource,
                                        onClearOfflineCache = viewModel::clearOfflineCache,
                                        applyStatusBarPadding = false,
                                    )
                                }
                            }

                            AppDestination.FEED, AppDestination.SAVED -> {
                                val paneShape = RoundedCornerShape(30.dp)
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Surface(
                                        shape = paneShape,
                                        color = glassFillColor(
                                            alpha = 0.34f,
                                            baseColor = MaterialTheme.colorScheme.surface,
                                        ),
                                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(paneShape),
                                    ) {
                                        if (destination == AppDestination.FEED) {
                                            NewsFeedPane(
                                                articles = uiState.filteredArticles,
                                                nowBriefArticles = uiState.nowBriefArticles,
                                                selectedCategory = uiState.selectedCategory,
                                                customCategories = uiState.customCategories,
                                                selectedCustomCategory = uiState.selectedCustomCategory,
                                                searchQuery = uiState.searchQuery,
                                                isSearchExpanded = uiState.isSearchExpanded,
                                                selectedArticleId = uiState.selectedArticleId,
                                                highlightSelection = isDualPaneActive,
                                                bookmarkedIds = uiState.bookmarkedIds,
                                                offlineSavedIds = uiState.offlineSavedIds,
                                                themeSettings = uiState.themeSettings,
                                                audioState = uiState.audioState,
                                                initialScrollIndex = uiState.feedFirstVisibleIndex,
                                                initialScrollOffset = uiState.feedFirstVisibleOffset,
                                                bottomContentPadding = bottomPad,
                                                onSaveScrollPosition = viewModel::updateFeedScrollPosition,
                                                onSelectCategory = viewModel::selectCategory,
                                                onSelectCustomCategory = viewModel::selectCustomCategory,
                                                onToggleSearch = { viewModel.toggleSearchExpanded() },
                                                onSearchQueryChange = viewModel::updateSearchQuery,
                                                onArticleClick = { id ->
                                                    viewModel.openArticle(id, openCompactReader = true)
                                                },
                                                onToggleBookmark = viewModel::toggleBookmark,
                                                onPlayArticleAudio = viewModel::playArticleAudio,
                                                onPlayBriefAudio = viewModel::playNowBriefAudio,
                                                onToggleBriefPlayPause = viewModel::toggleAudioPlayPause,
                                                onSaveAllBriefOffline = viewModel::saveAllNowBriefOffline,
                                                onToggleBriefTopic = viewModel::toggleBriefTopic,
                                                onSelectLocalLocation = viewModel::setLocalNewsLocation,
                                                isRefreshing = uiState.isRefreshingFeed,
                                                onRefresh = viewModel::refreshFeed,
                                                useTwoColumnGrid = useTwoColumnGrid,
                                                applyStatusBarPadding = false,
                                            )
                                        } else {
                                            SavedArticlesPane(
                                                articles = uiState.articles,
                                                bookmarkedIds = uiState.bookmarkedIds,
                                                offlineSavedIds = uiState.offlineSavedIds,
                                                selectedArticleId = uiState.selectedArticleId,
                                                highlightSelection = isDualPaneActive,
                                                themeSettings = uiState.themeSettings,
                                                audioState = uiState.audioState,
                                                bottomContentPadding = bottomPad,
                                                onArticleClick = { id ->
                                                    viewModel.openArticle(id, openCompactReader = true)
                                                },
                                                onToggleBookmark = viewModel::toggleBookmark,
                                                onPlayArticleAudio = viewModel::playArticleAudio,
                                                useTwoColumnGrid = useTwoColumnGrid,
                                                applyStatusBarPadding = false,
                                            )
                                        }
                                    }

                                    if (isDualPaneActive) {
                                        Spacer(modifier = Modifier.width(10.dp))

                                        Surface(
                                            shape = paneShape,
                                            color = glassFillColor(
                                                alpha = 0.42f,
                                                baseColor = MaterialTheme.colorScheme.surface,
                                            ),
                                            border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(paneShape),
                                        ) {
                                            val activeArticle = uiState.selectedArticle
                                            if (activeArticle != null) {
                                                ArticleReaderPane(
                                                    article = activeArticle,
                                                    pageFlipDirection = uiState.pageFlipDirection,
                                                    showBackButton = false,
                                                    isBookmarked = activeArticle.id in uiState.bookmarkedIds,
                                                    isOfflineSaved = activeArticle.id in uiState.offlineSavedIds,
                                                    isNudgeDismissed = activeArticle.id in uiState.dismissedNudgeIds,
                                                    themeSettings = uiState.themeSettings,
                                                    audioState = uiState.audioState,
                                                    foldingFeature = foldingFeature,
                                                    savedScrollPositions = uiState.readerScrollPositions,
                                                    onSaveScrollPosition = viewModel::updateReaderScrollPosition,
                                                    savedPagePositions = uiState.readerPagePositions,
                                                    onSavePagePosition = viewModel::updateReaderPagePosition,
                                                    onBack = viewModel::closeCompactReader,
                                                    onPreviousArticle = viewModel::navigatePreviousArticle,
                                                    onNextArticle = viewModel::navigateNextArticle,
                                                    onToggleBookmark = viewModel::toggleBookmark,
                                                    onToggleOfflineSave = viewModel::toggleOfflineSave,
                                                    onPlayArticleAudio = viewModel::playArticleAudio,
                                                    onSeekAudio = viewModel::seekAudio,
                                                    onCycleAudioSpeed = viewModel::cycleAudioSpeed,
                                                    onFontScaleChange = viewModel::setReaderFontScale,
                                                    onTogglePaginatedMode = viewModel::setPaginatedReadingMode,
                                                    onOpenBookMode = {
                                                        viewModel.setFullScreenBookReader(true)
                                                        viewModel.openArticle(activeArticle.id, openCompactReader = true)
                                                    },
                                                    onDismissNudge = viewModel::dismissNowNudge,
                                                    onRestoreNudge = viewModel::restoreNowNudge,
                                                    onShowMessage = viewModel::showSnackbar,
                                                    isExtractingFullText = activeArticle.id in uiState.extractingArticleIds,
                                                    applyWindowInsets = false,
                                                )
                                            } else if (uiState.isInitialFeedLoading || uiState.isRefreshingFeed) {
                                                ReaderPaneShimmerSkeleton()
                                            } else {
                                                ReaderPaneEmptyState(
                                                    title = if (destination == AppDestination.SAVED) {
                                                        "No saved stories yet"
                                                    } else {
                                                        "No stories in this view"
                                                    },
                                                    subtitle = if (destination == AppDestination.SAVED) {
                                                        "Bookmark or save any article offline to read it here."
                                                    } else {
                                                        "Switch categories, adjust your search, or pull to refresh."
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                if (isMiniPlayerVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .padding(start = 92.dp, bottom = 20.dp)
                            .width(380.dp),
                    ) {
                        MiniAudioPlayerBar(
                            audioState = uiState.audioState,
                            onTogglePlayPause = viewModel::toggleAudioPlayPause,
                            onCycleSpeed = viewModel::cycleAudioSpeed,
                            onClose = viewModel::stopAudio,
                        )
                    }
                }
            } else {
                // Cover Screen (Compact Width)
                val showReader = uiState.isCompactReaderOpen && uiState.selectedArticle != null

                AnimatedContent(
                    targetState = showReader,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 5 }) + fadeOut(tween(180)))
                        } else {
                            (slideInHorizontally(initialOffsetX = { -it / 5 }) + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut(tween(180)))
                        }
                    },
                    label = "compact_reader_transition",
                ) { readerVisible ->
                    if (readerVisible && uiState.selectedArticle != null) {
                        val activeArticle = uiState.selectedArticle!!
                        PredictiveBackContainer(
                            enabled = !showFullScreenBook && showReader,
                            onBack = viewModel::closeCompactReader,
                            resetKey = activeArticle.id,
                        ) {
                            ArticleReaderPane(
                                article = activeArticle,
                                pageFlipDirection = uiState.pageFlipDirection,
                                showBackButton = true,
                                isBookmarked = activeArticle.id in uiState.bookmarkedIds,
                                isOfflineSaved = activeArticle.id in uiState.offlineSavedIds,
                                isNudgeDismissed = activeArticle.id in uiState.dismissedNudgeIds,
                                themeSettings = uiState.themeSettings,
                                audioState = uiState.audioState,
                                foldingFeature = foldingFeature,
                                savedScrollPositions = uiState.readerScrollPositions,
                                onSaveScrollPosition = viewModel::updateReaderScrollPosition,
                                savedPagePositions = uiState.readerPagePositions,
                                onSavePagePosition = viewModel::updateReaderPagePosition,
                                onBack = viewModel::closeCompactReader,
                                onPreviousArticle = viewModel::navigatePreviousArticle,
                                onNextArticle = viewModel::navigateNextArticle,
                                onToggleBookmark = viewModel::toggleBookmark,
                                onToggleOfflineSave = viewModel::toggleOfflineSave,
                                onPlayArticleAudio = viewModel::playArticleAudio,
                                onSeekAudio = viewModel::seekAudio,
                                onCycleAudioSpeed = viewModel::cycleAudioSpeed,
                                onFontScaleChange = viewModel::setReaderFontScale,
                                onTogglePaginatedMode = viewModel::setPaginatedReadingMode,
                                onOpenBookMode = {
                                    viewModel.setFullScreenBookReader(true)
                                    viewModel.openArticle(activeArticle.id, openCompactReader = true)
                                },
                                onDismissNudge = viewModel::dismissNowNudge,
                                onRestoreNudge = viewModel::restoreNowNudge,
                                onShowMessage = viewModel::showSnackbar,
                                isExtractingFullText = activeArticle.id in uiState.extractingArticleIds,
                            )
                        }
                    } else {
                        val bottomPad = if (isMiniPlayerVisible) 160.dp else 94.dp
                        var isCompactScrolling by remember { mutableStateOf(false) }
                        var hideFloatingNavBar by remember { mutableStateOf(false) }

                        LaunchedEffect(uiState.activeDestination) {
                            isCompactScrolling = false
                        }
                        LaunchedEffect(isCompactScrolling) {
                            if (isCompactScrolling) {
                                hideFloatingNavBar = true
                            } else {
                                delay(480L)
                                hideFloatingNavBar = false
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            PredictiveBackContainer(
                                enabled = !showFullScreenBook && !showReader && uiState.activeDestination != AppDestination.FEED,
                                onBack = { viewModel.selectDestination(AppDestination.FEED) },
                                minScale = 0.93f,
                                resetKey = uiState.activeDestination,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                AnimatedContent(
                                    targetState = uiState.activeDestination,
                                    transitionSpec = {
                                        fadeIn(tween(180)).togetherWith(fadeOut(tween(150)))
                                    },
                                    label = "compact_destination",
                                ) { destination ->
                                when (destination) {
                                    AppDestination.FEED -> {
                                        NewsFeedPane(
                                            articles = uiState.filteredArticles,
                                            nowBriefArticles = uiState.nowBriefArticles,
                                            selectedCategory = uiState.selectedCategory,
                                            customCategories = uiState.customCategories,
                                            selectedCustomCategory = uiState.selectedCustomCategory,
                                            searchQuery = uiState.searchQuery,
                                            isSearchExpanded = uiState.isSearchExpanded,
                                            selectedArticleId = uiState.selectedArticleId,
                                            highlightSelection = false,
                                            bookmarkedIds = uiState.bookmarkedIds,
                                            offlineSavedIds = uiState.offlineSavedIds,
                                            themeSettings = uiState.themeSettings,
                                            audioState = uiState.audioState,
                                            initialScrollIndex = uiState.feedFirstVisibleIndex,
                                            initialScrollOffset = uiState.feedFirstVisibleOffset,
                                            bottomContentPadding = bottomPad,
                                            onSaveScrollPosition = viewModel::updateFeedScrollPosition,
                                            onSelectCategory = viewModel::selectCategory,
                                            onSelectCustomCategory = viewModel::selectCustomCategory,
                                            onToggleSearch = { viewModel.toggleSearchExpanded() },
                                            onSearchQueryChange = viewModel::updateSearchQuery,
                                            onArticleClick = { id ->
                                                viewModel.openArticle(id, openCompactReader = true)
                                            },
                                            onToggleBookmark = viewModel::toggleBookmark,
                                            onPlayArticleAudio = viewModel::playArticleAudio,
                                            onPlayBriefAudio = viewModel::playNowBriefAudio,
                                            onToggleBriefPlayPause = viewModel::toggleAudioPlayPause,
                                            onSaveAllBriefOffline = viewModel::saveAllNowBriefOffline,
                                            onToggleBriefTopic = viewModel::toggleBriefTopic,
                                            onSelectLocalLocation = viewModel::setLocalNewsLocation,
                                            isRefreshing = uiState.isRefreshingFeed,
                                            onRefresh = viewModel::refreshFeed,
                                            onScrollingChange = { isCompactScrolling = it },
                                            useTwoColumnGrid = useTwoColumnGrid,
                                        )
                                    }

                                    AppDestination.SAVED -> {
                                        SavedArticlesPane(
                                            articles = uiState.articles,
                                            bookmarkedIds = uiState.bookmarkedIds,
                                            offlineSavedIds = uiState.offlineSavedIds,
                                            selectedArticleId = uiState.selectedArticleId,
                                            highlightSelection = false,
                                            themeSettings = uiState.themeSettings,
                                            audioState = uiState.audioState,
                                            bottomContentPadding = bottomPad,
                                            onArticleClick = { id ->
                                                viewModel.openArticle(id, openCompactReader = true)
                                            },
                                            onToggleBookmark = viewModel::toggleBookmark,
                                            onPlayArticleAudio = viewModel::playArticleAudio,
                                            onScrollingChange = { isCompactScrolling = it },
                                            useTwoColumnGrid = useTwoColumnGrid,
                                        )
                                    }

                                    AppDestination.SETTINGS -> {
                                        SettingsScreen(
                                            themeSettings = uiState.themeSettings,
                                            availableSources = uiState.availableSources,
                                            enabledSourceIds = uiState.enabledSourceIds,
                                            customCategories = uiState.customCategories,
                                            widthSizeClass = windowSizeClass.widthSizeClass,
                                            foldingFeature = foldingFeature,
                                            offlineCount = uiState.offlineSavedIds.size,
                                            bookmarkCount = uiState.bookmarkedIds.size,
                                            bottomContentPadding = bottomPad,
                                            onSetUseDynamicColor = viewModel::setUseDynamicColor,
                                            onSetUseAmoledBlack = viewModel::setUseAmoledBlack,
                                            onSetThemeMode = viewModel::setThemeMode,
                                            onSetReaderFontScale = viewModel::setReaderFontScale,
                                            onSetDefaultAudioSpeed = viewModel::setDefaultAudioSpeed,
                                            onSetUseSerifHeadlines = viewModel::setUseSerifHeadlines,
                                            onSetUseSystemFont = viewModel::setUseSystemFont,
                                            onSetEnableNowBrief = viewModel::setEnableNowBrief,
                                            onSetShowNowNudgeChips = viewModel::setShowNowNudgeChips,
                                            onSetSmartNowNudgeAutoHide = viewModel::setSmartNowNudgeAutoHide,
                                            onSetEnableDualPaneLayout = viewModel::setEnableDualPaneLayout,
                                            onSetFullScreenBookReader = viewModel::setFullScreenBookReader,
                                            onSetPaginatedReadingMode = viewModel::setPaginatedReadingMode,
                                            onSetPageTurnAnimation = viewModel::setPageTurnAnimation,
                                            onSetLocalNewsLocation = viewModel::setLocalNewsLocation,
                                            onSetLocalNewsRadiusMiles = viewModel::setLocalNewsRadiusMiles,
                                            onToggleBriefTopic = viewModel::toggleBriefTopic,
                                            onSetBriefSchedule = viewModel::setBriefSchedule,
                                            onToggleSourceEnabled = viewModel::toggleSourceEnabled,
                                            onPreviewFeed = viewModel::previewFeedForAdd,
                                            onAddManualSource = viewModel::addManualSource,
                                            onReassignSourceCategory = viewModel::reassignSourceCategory,
                                            onCreateCustomCategory = viewModel::createCustomCategory,
                                            onRemoveCustomSource = viewModel::removeCustomSource,
                                            onClearOfflineCache = viewModel::clearOfflineCache,
                                            onScrollingChange = { isCompactScrolling = it },
                                        )
                                    }
                                }
                            }
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                MiniAudioPlayerBar(
                                    audioState = uiState.audioState,
                                    onTogglePlayPause = viewModel::toggleAudioPlayPause,
                                    onCycleSpeed = viewModel::cycleAudioSpeed,
                                    onClose = viewModel::stopAudio,
                                )
                                AnimatedVisibility(
                                    visible = !hideFloatingNavBar,
                                    enter = expandVertically(
                                        expandFrom = Alignment.Bottom,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    ) + slideInVertically(
                                        initialOffsetY = { it },
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    ) + fadeIn(tween(180)),
                                    exit = shrinkVertically(
                                        shrinkTowards = Alignment.Bottom,
                                        animationSpec = tween(180),
                                    ) + slideOutVertically(
                                        targetOffsetY = { it },
                                        animationSpec = tween(180),
                                    ) + fadeOut(tween(150)),
                                ) {
                                    FloatingBottomNavBar(
                                        activeDestination = uiState.activeDestination,
                                        onSelectDestination = viewModel::selectDestination,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Snackbar feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 86.dp, start = 16.dp, end = 16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(22.dp),
                containerColor = glassFillColor(
                    alpha = 0.86f,
                    baseColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
