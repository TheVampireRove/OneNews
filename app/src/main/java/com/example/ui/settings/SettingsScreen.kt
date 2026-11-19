package com.example.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DevicesFold
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import com.example.data.FeedCategoryDetector
import com.example.data.FeedCategoryOption
import com.example.data.FeedChannelPreview
import com.example.data.MockNewsRepository
import com.example.model.BriefSchedule
import com.example.model.NewsCategory
import com.example.model.NewsSource
import com.example.model.PageTurnAnimation
import com.example.model.ThemeMode
import com.example.model.ThemeSettings
import com.example.ui.components.CircularControlButton
import com.example.ui.components.CollapsibleOneUiHeader
import com.example.ui.components.GlassContainer
import com.example.ui.components.formatSpeed
import com.example.ui.components.glassBorderBrush
import com.example.ui.components.glassFillColor
import com.example.ui.components.modernAutoHideScrollbar
import com.example.ui.components.rememberOneNewsHaptics
import com.example.ui.theme.NewsreaderFontFamily
import com.example.ui.theme.OneUiCardRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    themeSettings: ThemeSettings,
    availableSources: List<NewsSource>,
    enabledSourceIds: Set<String>,
    customCategories: List<String> = emptyList(),
    widthSizeClass: WindowWidthSizeClass,
    foldingFeature: FoldingFeature?,
    offlineCount: Int,
    bookmarkCount: Int,
    bottomContentPadding: Dp,
    onSetUseDynamicColor: (Boolean) -> Unit,
    onSetUseAmoledBlack: (Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetReaderFontScale: (Float) -> Unit,
    onSetDefaultAudioSpeed: (Float) -> Unit,
    onSetUseSerifHeadlines: (Boolean) -> Unit,
    onSetUseSystemFont: (Boolean) -> Unit = {},
    onSetEnableNowBrief: (Boolean) -> Unit,
    onSetShowNowNudgeChips: (Boolean) -> Unit,
    onSetSmartNowNudgeAutoHide: (Boolean) -> Unit,
    onSetEnableDualPaneLayout: (Boolean) -> Unit = {},
    onSetFullScreenBookReader: (Boolean) -> Unit,
    onSetPaginatedReadingMode: (Boolean) -> Unit = {},
    onSetPageTurnAnimation: (PageTurnAnimation) -> Unit = {},
    onSetLocalNewsLocation: (String) -> Unit = {},
    onSetLocalNewsRadiusMiles: (Int) -> Unit = {},
    onToggleBriefTopic: (NewsCategory) -> Unit,
    onSetBriefSchedule: (BriefSchedule) -> Unit,
    onToggleSourceEnabled: (String) -> Unit,
    onPreviewFeed: suspend (String) -> FeedChannelPreview = { FeedChannelPreview(it) },
    onAddManualSource: (String, String, NewsCategory, String?) -> Unit,
    onReassignSourceCategory: (String, NewsCategory, String?) -> Unit = { _, _, _ -> },
    onCreateCustomCategory: (String) -> Unit = {},
    onRemoveCustomSource: (String) -> Unit,
    onClearOfflineCache: () -> Unit,
    onScrollingChange: (Boolean) -> Unit = {},
    applyStatusBarPadding: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val useTwoColumnLayout = widthSizeClass != WindowWidthSizeClass.Compact
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

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress || isHeaderScrolling }
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val expandedHeight = (maxHeight * 0.28f).coerceIn(136.dp, 204.dp)
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
                title = "Settings",
                subtitleDate = "OneNews Preferences",
                collapseFraction = collapseFraction,
                expandedHeight = expandedHeight,
                collapsedHeight = collapsedHeight,
                applyStatusBarPadding = applyStatusBarPadding,
                showSearchButton = false,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .modernAutoHideScrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (useTwoColumnLayout) {
                    // Adaptive two-column layout for Unfolded / Tablet displays
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AppearanceAndMonetCard(
                                themeSettings = themeSettings,
                                onSetThemeMode = onSetThemeMode,
                                onSetUseDynamicColor = onSetUseDynamicColor,
                                onSetUseAmoledBlack = onSetUseAmoledBlack,
                            )
                            LocalNewsAreaCard(
                                themeSettings = themeSettings,
                                onSetLocalNewsLocation = onSetLocalNewsLocation,
                                onSetLocalNewsRadiusMiles = onSetLocalNewsRadiusMiles,
                            )
                            SourcesAndFeedsCard(
                                availableSources = availableSources,
                                enabledSourceIds = enabledSourceIds,
                                customCategories = customCategories,
                                onToggleSourceEnabled = onToggleSourceEnabled,
                                onPreviewFeed = onPreviewFeed,
                                onAddManualSource = onAddManualSource,
                                onReassignSourceCategory = onReassignSourceCategory,
                                onCreateCustomCategory = onCreateCustomCategory,
                                onRemoveCustomSource = onRemoveCustomSource,
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ReadingAndAudioCard(
                                themeSettings = themeSettings,
                                onSetReaderFontScale = onSetReaderFontScale,
                                onSetDefaultAudioSpeed = onSetDefaultAudioSpeed,
                                onSetUseSerifHeadlines = onSetUseSerifHeadlines,
                                onSetUseSystemFont = onSetUseSystemFont,
                                onSetShowNowNudgeChips = onSetShowNowNudgeChips,
                                onSetSmartNowNudgeAutoHide = onSetSmartNowNudgeAutoHide,
                                onSetEnableDualPaneLayout = onSetEnableDualPaneLayout,
                                onSetFullScreenBookReader = onSetFullScreenBookReader,
                                onSetPaginatedReadingMode = onSetPaginatedReadingMode,
                                onSetPageTurnAnimation = onSetPageTurnAnimation,
                            )
                            NowBriefPersonalizationCard(
                                themeSettings = themeSettings,
                                onSetEnableNowBrief = onSetEnableNowBrief,
                                onToggleBriefTopic = onToggleBriefTopic,
                                onSetBriefSchedule = onSetBriefSchedule,
                            )
                            FoldableAndStorageCard(
                                widthSizeClass = widthSizeClass,
                                foldingFeature = foldingFeature,
                                enableDualPaneLayout = themeSettings.enableDualPaneLayout,
                                offlineCount = offlineCount,
                                bookmarkCount = bookmarkCount,
                                onClearOfflineCache = onClearOfflineCache,
                            )
                        }
                    }
                } else {
                    // Single-column adaptive centered layout for Compact Cover screen
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .align(Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppearanceAndMonetCard(
                            themeSettings = themeSettings,
                            onSetThemeMode = onSetThemeMode,
                            onSetUseDynamicColor = onSetUseDynamicColor,
                            onSetUseAmoledBlack = onSetUseAmoledBlack,
                        )
                        LocalNewsAreaCard(
                            themeSettings = themeSettings,
                            onSetLocalNewsLocation = onSetLocalNewsLocation,
                            onSetLocalNewsRadiusMiles = onSetLocalNewsRadiusMiles,
                        )
                        SourcesAndFeedsCard(
                            availableSources = availableSources,
                            enabledSourceIds = enabledSourceIds,
                            customCategories = customCategories,
                            onToggleSourceEnabled = onToggleSourceEnabled,
                            onPreviewFeed = onPreviewFeed,
                            onAddManualSource = onAddManualSource,
                            onReassignSourceCategory = onReassignSourceCategory,
                            onCreateCustomCategory = onCreateCustomCategory,
                            onRemoveCustomSource = onRemoveCustomSource,
                        )
                        ReadingAndAudioCard(
                            themeSettings = themeSettings,
                            onSetReaderFontScale = onSetReaderFontScale,
                            onSetDefaultAudioSpeed = onSetDefaultAudioSpeed,
                            onSetUseSerifHeadlines = onSetUseSerifHeadlines,
                            onSetUseSystemFont = onSetUseSystemFont,
                            onSetShowNowNudgeChips = onSetShowNowNudgeChips,
                            onSetSmartNowNudgeAutoHide = onSetSmartNowNudgeAutoHide,
                            onSetEnableDualPaneLayout = onSetEnableDualPaneLayout,
                            onSetFullScreenBookReader = onSetFullScreenBookReader,
                            onSetPaginatedReadingMode = onSetPaginatedReadingMode,
                            onSetPageTurnAnimation = onSetPageTurnAnimation,
                        )
                        NowBriefPersonalizationCard(
                            themeSettings = themeSettings,
                            onSetEnableNowBrief = onSetEnableNowBrief,
                            onToggleBriefTopic = onToggleBriefTopic,
                            onSetBriefSchedule = onSetBriefSchedule,
                        )
                        FoldableAndStorageCard(
                            widthSizeClass = widthSizeClass,
                            foldingFeature = foldingFeature,
                            enableDualPaneLayout = themeSettings.enableDualPaneLayout,
                            offlineCount = offlineCount,
                            bookmarkCount = bookmarkCount,
                            onClearOfflineCache = onClearOfflineCache,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(bottomContentPadding + 24.dp))
            }
        }
    }
}

@Composable
private fun OneUiSettingsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        GlassContainer(
            shape = RoundedCornerShape(OneUiCardRadius),
            fillAlpha = 0.60f,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun SourcesAndFeedsCard(
    availableSources: List<NewsSource>,
    enabledSourceIds: Set<String>,
    customCategories: List<String>,
    onToggleSourceEnabled: (String) -> Unit,
    onPreviewFeed: suspend (String) -> FeedChannelPreview,
    onAddManualSource: (String, String, NewsCategory, String?) -> Unit,
    onReassignSourceCategory: (String, NewsCategory, String?) -> Unit,
    onCreateCustomCategory: (String) -> Unit,
    onRemoveCustomSource: (String) -> Unit,
) {
    val haptics = rememberOneNewsHaptics()
    // null = "All Feeds", otherwise filters strictly to the chosen built-in or custom category
    var selectedFilterOption by remember { mutableStateOf<FeedCategoryOption?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var sourceBeingReassigned by remember { mutableStateOf<NewsSource?>(null) }

    val filterOptions = remember(customCategories) {
        FeedCategoryDetector.buildCategoryOptions(customCategories)
    }

    val filteredSources = remember(availableSources, selectedFilterOption) {
        val activeFilter = selectedFilterOption
        val matched = if (activeFilter == null) {
            availableSources
        } else if (!activeFilter.customName.isNullOrBlank()) {
            availableSources.filter {
                it.cleanCustomCategory.equals(activeFilter.customName, ignoreCase = true)
            }
        } else {
            availableSources.filter {
                it.cleanCustomCategory == null && it.category == activeFilter.builtIn
            }
        }
        matched.sortedByDescending { it.isCustom }
    }

    if (showAddDialog) {
        AddFeedDialog(
            customCategories = customCategories,
            onPreviewFeed = onPreviewFeed,
            onCreateCustomCategory = onCreateCustomCategory,
            onSaveFeed = { url, title, category, customCat ->
                onAddManualSource(title, url, category, customCat)
                selectedFilterOption = if (!customCat.isNullOrBlank()) {
                    FeedCategoryOption(NewsCategory.ALL, customCat)
                } else {
                    FeedCategoryOption(category)
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }

    sourceBeingReassigned?.let { targetSource ->
        ReassignCategoryDialog(
            source = targetSource,
            customCategories = customCategories,
            onCreateCustomCategory = onCreateCustomCategory,
            onReassign = { id, cat, customCat ->
                onReassignSourceCategory(id, cat, customCat)
            },
            onDismiss = { sourceBeingReassigned = null },
        )
    }

    OneUiSettingsCard(
        title = "Manage Feeds & Sources",
        icon = Icons.Rounded.RssFeed,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Curated & Custom Feeds",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${enabledSourceIds.size} of ${availableSources.size} active • tap a badge to change category",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                onClick = {
                    haptics.tap()
                    showAddDialog = true
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_add_manual_source_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Add Feed",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        // Category Filter Strip for Manage Feeds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val allSelected = selectedFilterOption == null
            Surface(
                onClick = {
                    haptics.toggle()
                    selectedFilterOption = null
                },
                shape = CircleShape,
                color = if (allSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    glassFillColor(
                        alpha = 0.56f,
                        baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                },
                contentColor = if (allSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(1.dp, glassBorderBrush(highlighted = allSelected)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("source_genre_tab_all_feeds"),
            ) {
                Text(
                    text = "All Feeds",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }

            filterOptions.forEach { option ->
                val selected = selectedFilterOption?.key == option.key
                Surface(
                    onClick = {
                        haptics.toggle()
                        selectedFilterOption = option
                    },
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        glassFillColor(
                            alpha = 0.56f,
                            baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("source_genre_tab_${option.key.replace(':', '_').replace(' ', '_')}"),
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }

        // Sources List for Selected Category
        if (filteredSources.isEmpty()) {
            Text(
                text = "No feeds in this category yet. Tap Add Feed or reassign an existing feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredSources.forEachIndexed { index, source ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                    val isEnabled = source.id in enabledSourceIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.tap()
                                sourceBeingReassigned = source
                            }
                            .padding(vertical = 2.dp)
                            .testTag("source_row_${source.id}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    onClick = {
                                        haptics.tap()
                                        sourceBeingReassigned = source
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    ),
                                    modifier = Modifier.testTag("source_category_badge_${source.id}"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = source.assignedCategoryBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Change feed category",
                                            modifier = Modifier.size(11.dp),
                                        )
                                    }
                                }

                                Text(
                                    text = source.description.ifBlank { source.feedUrl },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (source.isCustom) {
                                CircularControlButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove custom source",
                                    onClick = { onRemoveCustomSource(source.id) },
                                    size = 38.dp,
                                    iconSize = 18.dp,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = {
                                    haptics.toggle()
                                    onToggleSourceEnabled(source.id)
                                },
                                modifier = Modifier.testTag("switch_source_${source.id}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceAndMonetCard(
    themeSettings: ThemeSettings,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetUseDynamicColor: (Boolean) -> Unit,
    onSetUseAmoledBlack: (Boolean) -> Unit,
) {
    val haptics = rememberOneNewsHaptics()
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    OneUiSettingsCard(
        title = "Theme & Monet Colors",
        icon = Icons.Rounded.Palette,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Theme Mode",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = themeSettings.themeMode == mode
                    val modeIcon = when (mode) {
                        ThemeMode.SYSTEM -> Icons.Rounded.SettingsSuggest
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                    }
                    Surface(
                        onClick = {
                            haptics.toggle()
                            onSetThemeMode(mode)
                        },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            glassFillColor(
                                alpha = 0.56f,
                                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("theme_mode_${mode.name.lowercase()}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(modeIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        SettingsSwitchRow(
            title = "Wallpaper Dynamic Colors (Monet)",
            subtitle = if (supportsMonet) {
                if (themeSettings.useDynamicColor) {
                    "Tinting highlights with your Android wallpaper palette"
                } else {
                    "Using Classic One UI cobalt blue accent"
                }
            } else {
                "Android 12+ required • Using Classic One UI Accent"
            },
            checked = themeSettings.useDynamicColor,
            enabled = supportsMonet,
            onCheckedChange = {
                haptics.toggle()
                onSetUseDynamicColor(it)
            },
            testTag = "switch_dynamic_color",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        SettingsSwitchRow(
            title = "AMOLED Pure Black",
            subtitle = if (themeSettings.useAmoledBlack) {
                "True #000000 canvas & #17171A elevated dark cards"
            } else {
                "Standard Material 3 tonal dark surfaces"
            },
            checked = themeSettings.useAmoledBlack,
            enabled = themeSettings.themeMode != ThemeMode.LIGHT,
            onCheckedChange = {
                haptics.toggle()
                onSetUseAmoledBlack(it)
            },
            testTag = "switch_amoled_black",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("live_color_swatch_bar"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Active Palette Tokens",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        glassFillColor(
                            alpha = 0.56f,
                            baseColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    )
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SwatchTokenChip("Primary", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SwatchTokenChip("Secondary", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                SwatchTokenChip("Tertiary", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                SwatchTokenChip("Canvas", MaterialTheme.colorScheme.background, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SwatchTokenChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(30.dp),
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalNewsAreaCard(
    themeSettings: ThemeSettings,
    onSetLocalNewsLocation: (String) -> Unit,
    onSetLocalNewsRadiusMiles: (Int) -> Unit,
) {
    val haptics = rememberOneNewsHaptics()
    val presets = remember { MockNewsRepository.getLocalPresets() }
    var customLocation by remember { mutableStateOf("") }

    OneUiSettingsCard(
        title = "Local News & Area",
        icon = Icons.Rounded.LocationOn,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Active Neighborhood / City",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Pulling local transit, community events, and city stories for ${themeSettings.localNewsLocation} (${themeSettings.localNewsRadiusMiles} mi radius)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                val selected = preset.displayName.equals(themeSettings.localNewsLocation, ignoreCase = true)
                Surface(
                    onClick = {
                        haptics.toggle()
                        onSetLocalNewsLocation(preset.displayName)
                    },
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        glassFillColor(
                            alpha = 0.56f,
                            baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("local_preset_${preset.id}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(14.dp))
                        }
                        Text(preset.cityShort, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = customLocation,
                onValueChange = { customLocation = it },
                label = { Text("Enter City or Postal / ZIP Code") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom_location_input"),
            )
            Surface(
                onClick = {
                    if (customLocation.isNotBlank()) {
                        haptics.confirm()
                        onSetLocalNewsLocation(customLocation.trim())
                        customLocation = ""
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("apply_custom_location_button"),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Set", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Local Coverage Radius",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(10, 25, 50).forEach { miles ->
                    val selected = themeSettings.localNewsRadiusMiles == miles
                    Surface(
                        onClick = {
                            haptics.toggle()
                            onSetLocalNewsRadiusMiles(miles)
                        },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            glassFillColor(
                                alpha = 0.56f,
                                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$miles mi", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingAndAudioCard(
    themeSettings: ThemeSettings,
    onSetReaderFontScale: (Float) -> Unit,
    onSetDefaultAudioSpeed: (Float) -> Unit,
    onSetUseSerifHeadlines: (Boolean) -> Unit,
    onSetUseSystemFont: (Boolean) -> Unit,
    onSetShowNowNudgeChips: (Boolean) -> Unit,
    onSetSmartNowNudgeAutoHide: (Boolean) -> Unit,
    onSetEnableDualPaneLayout: (Boolean) -> Unit,
    onSetFullScreenBookReader: (Boolean) -> Unit,
    onSetPaginatedReadingMode: (Boolean) -> Unit,
    onSetPageTurnAnimation: (PageTurnAnimation) -> Unit,
) {
    val haptics = rememberOneNewsHaptics()

    OneUiSettingsCard(
        title = "Reading, Book Mode & Audio",
        icon = Icons.Rounded.FormatSize,
    ) {
        SettingsSwitchRow(
            title = "Dual-Pane Layout",
            subtitle = if (themeSettings.enableDualPaneLayout) {
                "Show article feed and reader side by side on wide screens"
            } else {
                "Off: 2-column article grid opens stories in Full-Screen Book Reader"
            },
            checked = themeSettings.enableDualPaneLayout,
            onCheckedChange = {
                haptics.toggle()
                onSetEnableDualPaneLayout(it)
            },
            testTag = "switch_dual_pane_layout",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        SettingsSwitchRow(
            title = "Paginated Book Mode",
            subtitle = "Read articles as discrete pages inside the reader with tap/drag page turning",
            checked = themeSettings.isPaginatedReadingMode,
            onCheckedChange = {
                haptics.toggle()
                onSetPaginatedReadingMode(it)
            },
            testTag = "switch_paginated_reading_mode",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Page Turn Animation",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PageTurnAnimation.entries.forEach { anim ->
                    val selected = themeSettings.pageTurnAnimation == anim
                    Surface(
                        onClick = {
                            haptics.toggle()
                            onSetPageTurnAnimation(anim)
                        },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            glassFillColor(
                                alpha = 0.56f,
                                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("page_turn_anim_${anim.name.lowercase()}"),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = anim.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        SettingsSwitchRow(
            title = "Full-Screen Book Reader",
            subtitle = if (themeSettings.enableDualPaneLayout) {
                "Launch stories in full-screen two-column spread on foldables"
            } else {
                "Always active while Dual-Pane Layout is turned off"
            },
            checked = themeSettings.fullScreenBookReader || !themeSettings.enableDualPaneLayout,
            enabled = themeSettings.enableDualPaneLayout,
            onCheckedChange = {
                haptics.toggle()
                onSetFullScreenBookReader(it)
            },
            testTag = "switch_full_screen_book_reader",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Reader Text Scale",
                    style = MaterialTheme.typography.titleSmall,
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
                    onSetReaderFontScale(it)
                },
                valueRange = 0.85f..1.4f,
            )
            Text(
                text = "Preview: Foldable OLED typography stays crisp at every angle.",
                style = (if (themeSettings.useSerifHeadlines && !themeSettings.useSystemFont) {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = NewsreaderFontFamily)
                } else {
                    MaterialTheme.typography.bodyMedium
                }).copy(fontSize = (14f * themeSettings.readerFontScale).sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        SettingsSwitchRow(
            title = "Use System Font",
            subtitle = "Use your device's default system typeface across feeds and articles",
            checked = themeSettings.useSystemFont,
            onCheckedChange = {
                haptics.toggle()
                onSetUseSystemFont(it)
            },
            testTag = "switch_use_system_font",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        SettingsSwitchRow(
            title = "Editorial Serif Headlines",
            subtitle = if (themeSettings.useSystemFont) {
                "Disabled while Use System Font is active"
            } else {
                "Use Newsreader serif typography for article headlines"
            },
            checked = themeSettings.useSerifHeadlines && !themeSettings.useSystemFont,
            enabled = !themeSettings.useSystemFont,
            onCheckedChange = {
                haptics.toggle()
                onSetUseSerifHeadlines(it)
            },
            testTag = "switch_serif_headlines",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        SettingsSwitchRow(
            title = "Calendar & Maps Action Chips",
            subtitle = "Show 'Add event to Calendar' and 'View location in Maps' nudges inside articles",
            checked = themeSettings.showNowNudgeChips,
            onCheckedChange = {
                haptics.toggle()
                onSetShowNowNudgeChips(it)
            },
            testTag = "switch_show_now_nudge_chips",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        SettingsSwitchRow(
            title = "Smart Auto-Hide Action Chips",
            subtitle = "Hide Calendar & Maps chips while actively scrolling, reappear when paused",
            checked = themeSettings.smartNowNudgeAutoHide,
            enabled = themeSettings.showNowNudgeChips,
            onCheckedChange = {
                haptics.toggle()
                onSetSmartNowNudgeAutoHide(it)
            },
            testTag = "switch_nudge_auto_hide",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Default Audio Speed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0.8f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                    val selected = kotlin.math.abs(themeSettings.audioPlaybackSpeed - speed) < 0.05f
                    Surface(
                        onClick = {
                            haptics.toggle()
                            onSetDefaultAudioSpeed(speed)
                        },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            glassFillColor(
                                alpha = 0.56f,
                                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${formatSpeed(speed)}x",
                                style = MaterialTheme.typography.labelLarge,
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
private fun NowBriefPersonalizationCard(
    themeSettings: ThemeSettings,
    onSetEnableNowBrief: (Boolean) -> Unit,
    onToggleBriefTopic: (NewsCategory) -> Unit,
    onSetBriefSchedule: (BriefSchedule) -> Unit,
) {
    val haptics = rememberOneNewsHaptics()

    OneUiSettingsCard(
        title = "Editor's Brief Personalization",
        icon = Icons.Rounded.AutoAwesome,
    ) {
        SettingsSwitchRow(
            title = "Enable Editor's Brief",
            subtitle = "Show the daily summary hero card and 2-minute audio brief at the top of Feed",
            checked = themeSettings.enableNowBrief,
            onCheckedChange = {
                haptics.toggle()
                onSetEnableNowBrief(it)
            },
            testTag = "switch_enable_now_brief",
        )

        AnimatedVisibility(visible = themeSettings.enableNowBrief) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Text(
                    text = "Preferred Brief Topics",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NewsCategory.entries
                        .filter { it != NewsCategory.ALL }
                        .forEach { topic ->
                            val selected = topic in themeSettings.briefTopics
                            Surface(
                                onClick = {
                                    haptics.toggle()
                                    onToggleBriefTopic(topic)
                                },
                                shape = CircleShape,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    glassFillColor(
                                        alpha = 0.56f,
                                        baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    )
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (selected) {
                                        Icon(Icons.Rounded.Check, null, Modifier.size(15.dp))
                                    }
                                    Text(topic.label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Text(
                    text = "Brief Schedule",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefSchedule.entries.forEach { schedule ->
                        val selected = themeSettings.briefSchedule == schedule
                        Surface(
                            onClick = {
                                haptics.toggle()
                                onSetBriefSchedule(schedule)
                            },
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                glassFillColor(
                                    alpha = 0.56f,
                                    baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                            },
                            border = BorderStroke(1.dp, glassBorderBrush(highlighted = selected)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = schedule.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
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

@Composable
private fun FoldableAndStorageCard(
    widthSizeClass: WindowWidthSizeClass,
    foldingFeature: FoldingFeature?,
    enableDualPaneLayout: Boolean,
    offlineCount: Int,
    bookmarkCount: Int,
    onClearOfflineCache: () -> Unit,
) {
    val widthLabel = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> "Compact (Cover Display)"
        WindowWidthSizeClass.Medium -> if (enableDualPaneLayout) "Medium (Two-Pane Adaptive)" else "Medium (2-Column Grid)"
        else -> if (enableDualPaneLayout) "Expanded (Dual-Pane Display)" else "Expanded (2-Column Grid)"
    }
    val postureLabel = when {
        foldingFeature == null -> "Single Plane / Flat"
        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
            foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> "Flex Mode (Tabletop)"
        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> "Half-Opened (Book Posture)"
        else -> "Unfolded Flat"
    }

    OneUiSettingsCard(
        title = "Adaptive Display & Storage",
        icon = Icons.Rounded.DevicesFold,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Form Factor",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$widthLabel • $postureLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.SmartButton,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = "Saved & Offline Stories",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$offlineCount offline • $bookmarkCount bookmarked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CircularControlButton(
                icon = Icons.Rounded.DeleteSweep,
                contentDescription = "Clear offline cache",
                onClick = onClearOfflineCache,
                size = 44.dp,
                testTag = "clear_offline_cache_button",
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
        )
    }
}
