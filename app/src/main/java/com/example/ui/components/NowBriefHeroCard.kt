package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AudioPlaybackState
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.ui.theme.OneUiCardRadius
import java.time.LocalTime

@Composable
fun NowBriefHeroCard(
    briefArticles: List<NewsArticle>,
    selectedTopics: Set<NewsCategory>,
    offlineSavedIds: Set<String>,
    audioState: AudioPlaybackState,
    onOpenArticle: (String) -> Unit,
    onPlayBriefAudio: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSaveAllOffline: () -> Unit,
    onToggleTopic: (NewsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    var showTopicMenu by remember { mutableStateOf(false) }

    val hour = remember {
        try {
            LocalTime.now().hour
        } catch (_: Exception) {
            9
        }
    }
    val greetingTitle = when {
        hour < 12 -> "Morning Editor's Brief"
        hour < 17 -> "Afternoon Editor's Brief"
        else -> "Evening Editor's Brief"
    }

    val allSavedOffline = briefArticles.isNotEmpty() && briefArticles.all { it.id in offlineSavedIds }
    val isBriefPlaying = audioState.isNowBriefQueue && audioState.isPlaying

    GlassContainer(
        shape = RoundedCornerShape(OneUiCardRadius),
        fillAlpha = 0.62f,
        highlighted = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("now_brief_hero_card"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "EDITOR'S BRIEF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = greetingTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = selectedTopics.joinToString(" • ") { it.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Box {
                        GlassCircleButton(
                            icon = Icons.Rounded.Tune,
                            contentDescription = "Customize Editor's Brief topics",
                            onClick = { showTopicMenu = true },
                            size = 44.dp,
                            iconSize = 20.dp,
                            testTag = "now_brief_customize_button",
                        )

                        DropdownMenu(
                            expanded = showTopicMenu,
                            onDismissRequest = { showTopicMenu = false },
                            shape = RoundedCornerShape(22.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        ) {
                            Text(
                                text = "Brief Topics",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            NewsCategory.entries
                                .filter { it != NewsCategory.ALL }
                                .forEach { category ->
                                    val checked = category in selectedTopics
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = category.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        trailingIcon = {
                                            if (checked) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        onClick = {
                                            haptics.toggle()
                                            onToggleTopic(category)
                                        },
                                    )
                                }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    briefArticles.forEachIndexed { index, article ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    haptics.tap()
                                    onOpenArticle(article.id)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                .testTag("now_brief_headline_${article.id}"),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(22.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = article.headline,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${article.displayCategoryLabel} • ${article.source}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = {
                            haptics.confirm()
                            if (audioState.isNowBriefQueue) onTogglePlayPause() else onPlayBriefAudio()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("now_brief_play_audio_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                modifier = Modifier.size(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isBriefPlaying) Icons.Rounded.Pause else Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Text(
                                text = if (isBriefPlaying) "Pause Brief" else "2-Min Brief",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptics.confirm()
                            onSaveAllOffline()
                        },
                        shape = CircleShape,
                        color = if (allSavedOffline) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.68f)
                        },
                        contentColor = if (allSavedOffline) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        border = BorderStroke(1.dp, glassBorderBrush(highlighted = allSavedOffline)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("now_brief_save_offline_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (allSavedOffline) {
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (allSavedOffline) Icons.Rounded.Check else Icons.Rounded.CloudDownload,
                                        contentDescription = "Save All Offline",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Text(
                                text = if (allSavedOffline) "Saved Offline" else "Save All",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
