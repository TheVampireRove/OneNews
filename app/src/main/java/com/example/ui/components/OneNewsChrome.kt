package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppDestination
import com.example.model.AudioPlaybackState
import com.example.model.NewsCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CircularControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.64f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    size: Dp = 48.dp,
    iconSize: Dp = 21.dp,
    testTag: String? = null,
) {
    val haptics = rememberOneNewsHaptics()
    val border = if (borderColor != null) {
        BorderStroke(1.dp, borderColor)
    } else {
        BorderStroke(1.dp, glassBorderBrush(highlighted = false))
    }
    Surface(
        onClick = {
            haptics.tap()
            onClick()
        },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
fun CollapsibleOneUiHeader(
    title: String,
    subtitleDate: String,
    collapseFraction: Float,
    expandedHeight: Dp,
    collapsedHeight: Dp = 64.dp,
    applyStatusBarPadding: Boolean = true,
    showSearchButton: Boolean = true,
    isSearchExpanded: Boolean = false,
    searchQuery: String = "",
    onToggleSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    trailingActions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val currentHeight = androidx.compose.ui.unit.lerp(expandedHeight, collapsedHeight, fraction)
    val viewingAlpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)
    val toolbarTitleAlpha = ((fraction - 0.45f) / 0.55f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.58f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.16f),
                        Color.Transparent,
                    )
                )
            )
            .let { if (applyStatusBarPadding) it.statusBarsPadding() else it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeight)
                .padding(horizontal = 20.dp)
        ) {
            // Viewing Area (top expanded state)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 64.dp)
                    .alpha(viewingAlpha),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = subtitleDate.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.1.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            }

            // Compact Collapsed Toolbar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedHeight)
                    .align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.alpha(toolbarTitleAlpha),
                    maxLines = 1,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    trailingActions()
                    if (showSearchButton) {
                        GlassCircleButton(
                            icon = if (isSearchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (isSearchExpanded) "Close search" else "Search news",
                            onClick = onToggleSearch,
                            active = isSearchExpanded,
                            size = 44.dp,
                            testTag = "header_search_button",
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        ) {
            GlassContainer(
                shape = CircleShape,
                fillAlpha = 0.72f,
                highlighted = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search headlines, sources, topics…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_input_field"),
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onSearchQueryChange("") }
                                .testTag("clear_search_button"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryFilterStrip(
    selectedCategory: NewsCategory,
    onSelectCategory: (NewsCategory) -> Unit,
    customCategories: List<String> = emptyList(),
    selectedCustomCategory: String? = null,
    onSelectCustomCategory: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NewsCategory.entries.forEach { category ->
            val selected = selectedCustomCategory == null && category == selectedCategory
            val bgColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.28f else 0.88f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.56f else 0.78f)
                },
                label = "category_pill_bg",
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    if (isDark) Color.White else MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "category_pill_fg",
            )
            Surface(
                onClick = {
                    haptics.toggle()
                    onSelectCategory(category)
                },
                shape = CircleShape,
                color = bgColor,
                contentColor = contentColor,
                border = BorderStroke(
                    width = 1.dp,
                    brush = glassBorderBrush(highlighted = selected),
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("category_chip_${category.name.lowercase()}"),
            ) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        customCategories.forEach { customName ->
            val selected = selectedCustomCategory.equals(customName, ignoreCase = true)
            val bgColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.28f else 0.88f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.56f else 0.78f)
                },
                label = "custom_category_pill_bg",
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    if (isDark) Color.White else MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "custom_category_pill_fg",
            )
            val sanitizedTag = customName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            Surface(
                onClick = {
                    haptics.toggle()
                    onSelectCustomCategory(customName)
                },
                shape = CircleShape,
                color = bgColor,
                contentColor = contentColor,
                border = BorderStroke(
                    width = 1.dp,
                    brush = glassBorderBrush(highlighted = selected),
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("category_chip_custom_$sanitizedTag"),
            ) {
                Text(
                    text = customName,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
fun FloatingBottomNavBar(
    activeDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val frostedBase = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (isDark) 0.91f else 0.95f,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = if (isDark) 0.62f else 0.55f),
                        MaterialTheme.colorScheme.background.copy(alpha = if (isDark) 0.88f else 0.85f),
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.2.dp, glassBorderBrush(highlighted = true)),
            shadowElevation = 18.dp,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(CircleShape)
                .testTag("floating_bottom_nav_bar"),
        ) {
            Box {
                // Blurred frosted glass substrate layer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(26.dp)
                        .background(frostedBase)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                                    tertiaryColor.copy(alpha = if (isDark) 0.14f else 0.08f),
                                    primaryColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                                )
                            )
                        ),
                )

                // Top specular glass sheen for crisp edge definition and label legibility
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.11f else 0.45f),
                                    frostedBase.copy(alpha = if (isDark) 0.72f else 0.78f),
                                    Color.Black.copy(alpha = if (isDark) 0.18f else 0.04f),
                                )
                            )
                        ),
                )

                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppDestination.entries.forEach { destination ->
                        val selected = destination == activeDestination
                        val icon = destinationIcon(destination, selected)
                        val pillColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.38f else 0.24f)
                            } else {
                                Color.Transparent
                            },
                            label = "bottom_nav_pill",
                        )
                        val fgColor by animateColorAsState(
                            targetValue = if (selected) {
                                if (isDark) Color.White else MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
                            },
                            label = "bottom_nav_fg",
                        )
                        Surface(
                            onClick = {
                                haptics.tap()
                                onSelectDestination(destination)
                            },
                            shape = CircleShape,
                            color = pillColor,
                            contentColor = fgColor,
                            border = if (selected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                            } else {
                                null
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bottom_nav_${destination.name.lowercase()}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = destination.label,
                                    modifier = Modifier.size(20.dp),
                                )
                                AnimatedVisibility(visible = selected) {
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
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

/**
 * Floating frosted-glass vertical Navigation Rail inspired by the reference OneMusic capsule.
 * Keeps icon-only buttons (no button titles) with top glowing brand emblem and bottom glass utilities.
 */
@Composable
fun OneNewsNavigationRail(
    activeDestination: AppDestination,
    isBookModeEnabled: Boolean,
    isAmoledEnabled: Boolean,
    onSelectDestination: (AppDestination) -> Unit,
    onQuickToggleBookMode: () -> Unit,
    onQuickPlayBrief: () -> Unit,
    onQuickToggleAmoled: () -> Unit,
    applyWindowInsets: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = modifier
            .fillMaxHeight()
            .let {
                if (applyWindowInsets) {
                    it.statusBarsPadding().windowInsetsPadding(WindowInsets.navigationBars)
                } else {
                    it
                }
            },
    ) {
        GlassContainer(
            shape = RoundedCornerShape(36.dp),
            fillAlpha = if (isDark) 0.58f else 0.78f,
            baseColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxHeight()
                .width(72.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Glowing circular OneNews top badge centered within rail border
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Newspaper,
                            contentDescription = "OneNews",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Icon-only primary navigation items (centered symmetrically within 72dp rail)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppDestination.entries.forEach { destination ->
                        val selected = destination == activeDestination
                        val buttonSize by animateDpAsState(
                            targetValue = if (selected) 50.dp else 48.dp,
                            label = "rail_pill_size",
                        )
                        Surface(
                            onClick = {
                                haptics.tap()
                                onSelectDestination(destination)
                            },
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.34f else 0.22f)
                            } else {
                                Color.Transparent
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            border = if (selected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                            } else {
                                null
                            },
                            modifier = Modifier
                                .size(buttonSize)
                                .testTag("rail_nav_${destination.name.lowercase()}"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = destinationIcon(destination, selected),
                                    contentDescription = destination.label,
                                    modifier = Modifier.size(23.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom circular frosted glass utility quick-toggles centered inside 72dp rail
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassCircleButton(
                        icon = Icons.Rounded.AutoStories,
                        contentDescription = "Toggle full-screen book reader",
                        onClick = onQuickToggleBookMode,
                        active = isBookModeEnabled,
                        size = 48.dp,
                        iconSize = 20.dp,
                        testTag = "rail_quick_book_mode",
                    )
                    GlassCircleButton(
                        icon = Icons.Rounded.GraphicEq,
                        contentDescription = "Play 2-Min Editor's Brief",
                        onClick = onQuickPlayBrief,
                        active = false,
                        size = 48.dp,
                        iconSize = 20.dp,
                        testTag = "rail_quick_audio_brief",
                    )
                    GlassCircleButton(
                        icon = Icons.Rounded.DarkMode,
                        contentDescription = "Toggle AMOLED Pure Black",
                        onClick = onQuickToggleAmoled,
                        active = isAmoledEnabled,
                        size = 48.dp,
                        iconSize = 20.dp,
                        testTag = "rail_quick_amoled",
                    )
                }
            }
        }
    }
}

@Composable
fun MiniAudioPlayerBar(
    audioState: AudioPlaybackState,
    onTogglePlayPause: () -> Unit,
    onCycleSpeed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val visible = audioState.spokenText.isNotBlank()
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        GlassContainer(
            shape = RoundedCornerShape(26.dp),
            fillAlpha = 0.76f,
            baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            highlighted = true,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularControlButton(
                        icon = if (audioState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (audioState.isPlaying) "Pause audio" else "Play audio",
                        onClick = onTogglePlayPause,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        borderColor = Color.White.copy(alpha = 0.35f),
                        size = 44.dp,
                        testTag = "mini_player_play_pause",
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = audioState.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = audioState.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    Surface(
                        onClick = {
                            haptics.toggle()
                            onCycleSpeed()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mini_player_speed"),
                    ) {
                        Text(
                            text = "${formatSpeed(audioState.playbackSpeed)}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }

                    GlassCircleButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close audio player",
                        onClick = onClose,
                        size = 40.dp,
                        iconSize = 18.dp,
                        testTag = "mini_player_close",
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { audioState.progress.coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                )
            }
        }
    }
}

private fun destinationIcon(destination: AppDestination, selected: Boolean): ImageVector {
    return when (destination) {
        AppDestination.FEED -> if (selected) Icons.Rounded.Newspaper else Icons.Outlined.Newspaper
        AppDestination.SAVED -> if (selected) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder
        AppDestination.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
    }
}

fun formatTodayDate(): String {
    return try {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    } catch (_: Exception) {
        "Saturday, Nov 14"
    }
}

fun formatSpeed(speed: Float): String {
    return when {
        kotlin.math.abs(speed - 0.8f) < 0.05f -> "0.8"
        kotlin.math.abs(speed - 1.25f) < 0.05f -> "1.25"
        kotlin.math.abs(speed - 1.5f) < 0.05f -> "1.5"
        else -> "1.0"
    }
}

fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}
