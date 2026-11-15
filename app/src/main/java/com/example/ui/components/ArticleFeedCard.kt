package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.NewsArticle
import com.example.ui.theme.BreakingAlertRed
import com.example.ui.theme.BreakingAlertRedContainer
import com.example.ui.theme.NewsreaderFontFamily
import com.example.ui.theme.OneUiCardRadius

@Composable
fun ArticleFeedCard(
    article: NewsArticle,
    isSelected: Boolean,
    isBookmarked: Boolean,
    isOfflineSaved: Boolean,
    isAudioPlaying: Boolean,
    useSerifHeadlines: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onPlayAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(
        onClick = {
            haptics.tap()
            onClick()
        },
        shape = RoundedCornerShape(OneUiCardRadius),
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.76f else 0.90f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isDark) 0.58f else 0.80f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            brush = glassBorderBrush(highlighted = isSelected),
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("article_card_${article.id}"),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.045f else 0.25f),
                        Color.Transparent,
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val showHeroImage = !article.imageUrl.isNullOrBlank() ||
                    (article.imageResId != null && (article.isFeaturedForBrief || article.headline.length > 68))

                if (showHeroImage) {
                    OneNewsArticleImage(
                        imageUrl = article.imageUrl,
                        fallbackResId = article.imageResId,
                        contentDescription = article.headline,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.56f),
                            contentColor = Color.White,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                        ) {
                            Text(
                                text = article.displayCategoryLabel,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (!showHeroImage) {
                            SourceMonogramBadge(
                                sourceName = article.source,
                                size = 24.dp,
                            )
                        }
                        if (article.isBreaking) {
                            Surface(
                                shape = CircleShape,
                                color = BreakingAlertRedContainer.copy(alpha = 0.85f),
                                contentColor = BreakingAlertRed,
                                border = BorderStroke(1.dp, BreakingAlertRed.copy(alpha = 0.45f)),
                            ) {
                                Text(
                                    text = "BREAKING",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                )
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            ) {
                                Text(
                                    text = article.displayCategoryLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                )
                            }
                        }

                        Text(
                            text = "${article.source} • ${article.timestamp}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (isOfflineSaved) {
                        Icon(
                            imageVector = Icons.Rounded.OfflinePin,
                            contentDescription = "Available offline",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Text(
                    text = article.headline,
                    style = if (useSerifHeadlines) {
                        MaterialTheme.typography.titleLarge.copy(fontFamily = NewsreaderFontFamily)
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = article.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${article.readingTimeMinutes} min read",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (article.hasAudioBrief) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Audio brief available",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = formatDuration(article.audioDurationSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (article.eventMetadata != null) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = "Includes event details",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        if (article.locationMetadata != null) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = "Includes venue location",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (article.hasAudioBrief) {
                            GlassCircleButton(
                                icon = if (isAudioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isAudioPlaying) "Pause audio brief" else "Play audio brief",
                                onClick = onPlayAudio,
                                active = isAudioPlaying,
                                size = 42.dp,
                                iconSize = 20.dp,
                                testTag = "card_audio_button_${article.id}",
                            )
                        }

                        GlassCircleButton(
                            icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark article",
                            onClick = onToggleBookmark,
                            active = isBookmarked,
                            size = 42.dp,
                            iconSize = 19.dp,
                            testTag = "card_bookmark_button_${article.id}",
                        )
                    }
                }
            }
        }
    }
}
