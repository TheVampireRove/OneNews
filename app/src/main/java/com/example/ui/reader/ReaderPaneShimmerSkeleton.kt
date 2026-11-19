package com.example.ui.reader

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.glassBorderBrush
import com.example.ui.components.glassFillColor
import com.example.ui.theme.OneUiCardRadius

@Composable
fun ReaderPaneShimmerSkeleton(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "reader_shimmer_transition")
    val shimmerOffset by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "reader_shimmer_offset",
    )

    val baseTone = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
    val highlightTone = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
    val accentSweep = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseTone, highlightTone, accentSweep, highlightTone, baseTone),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 380f, 180f),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading newest story" }
            .testTag("reader_pane_shimmer_skeleton")
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top segmented tray + action button shimmer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(210.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush),
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Source pill + metadata row shimmer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .width(104.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush),
                )
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .width(148.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush),
                )
            }

            // One UI hero image card skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(216.dp)
                    .clip(RoundedCornerShape(OneUiCardRadius))
                    .background(shimmerBrush),
            )

            // Editorial headline skeleton (2 lines)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(shimmerBrush),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(shimmerBrush),
                )
            }

            // Subtitle skeleton
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerBrush),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.64f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerBrush),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Article body prose skeleton
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val lineWidths = listOf(1f, 0.95f, 0.88f, 0.76f, 0.98f, 0.91f, 0.62f)
                lineWidths.forEachIndexed { index, fraction ->
                    if (index == 4) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(shimmerBrush),
                    )
                }
            }
        }

        // Bottom floating toolbar pill skeleton
        Box(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .height(54.dp)
                .width(280.dp)
                .clip(CircleShape)
                .background(shimmerBrush),
        )
    }
}

@Composable
fun ReaderPaneEmptyState(
    title: String = "Select a story to read",
    subtitle: String = "The newest story from your active feed appears here automatically.",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("reader_pane_empty_state")
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(OneUiCardRadius),
            color = glassFillColor(alpha = 0.48f),
            border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
