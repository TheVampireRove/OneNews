package com.example.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.OneUiCardRadius
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun glassBorderBrush(
    highlighted: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (highlighted) {
        Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = 0.85f),
                Color.White.copy(alpha = if (isDark) 0.36f else 0.7f),
                accent.copy(alpha = 0.45f),
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.24f else 0.85f),
                accent.copy(alpha = if (isDark) 0.16f else 0.22f),
                Color.White.copy(alpha = if (isDark) 0.06f else 0.35f),
            )
        )
    }
}

@Composable
fun glassFillColor(
    alpha: Float = 0.64f,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainer,
): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolvedAlpha = if (isDark) alpha else (alpha + 0.18f).coerceAtMost(0.9f)
    return baseColor.copy(alpha = resolvedAlpha)
}

/**
 * Real blurred atmospheric background canvas driven by active article imagery and Monet accents.
 * Uses RenderEffect blur on Android 12+ and layered radial color washes across all API levels.
 */
@Composable
fun BlurredAmbientBackdrop(
    @DrawableRes imageResId: Int?,
    imageUrl: String? = null,
    intensity: Float = 0.28f,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val baseBg = MaterialTheme.colorScheme.background
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg),
    ) {
        val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(72.dp)
        } else {
            Modifier
        }
        AnimatedContent(
            targetState = imageUrl to imageResId,
            transitionSpec = { fadeIn(tween(550)).togetherWith(fadeOut(tween(450))) },
            label = "ambient_backdrop_image",
        ) { (remoteUrl, resId) ->
            if (!remoteUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(remoteUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (isDark) intensity else (intensity * 0.45f),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurMod),
                )
            } else if (resId != null) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (isDark) intensity else (intensity * 0.45f),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurMod),
                )
            }
        }

        // Ambient Monet radial glow & deep vignette for crisp legibility
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = if (isDark) 0.16f else 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.16f),
                    radius = size.maxDimension * 0.62f,
                ),
                radius = size.maxDimension * 0.62f,
                center = Offset(size.width * 0.82f, size.height * 0.16f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiary.copy(alpha = if (isDark) 0.11f else 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.85f),
                    radius = size.maxDimension * 0.55f,
                ),
                radius = size.maxDimension * 0.55f,
                center = Offset(size.width * 0.18f, size.height * 0.85f),
            )
            // Subtle dark glass vignette to preserve AMOLED contrast
            if (isDark) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            baseBg.copy(alpha = 0.28f),
                            baseBg.copy(alpha = 0.48f),
                            baseBg.copy(alpha = 0.72f),
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(OneUiCardRadius),
    fillAlpha: Float = 0.64f,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    highlighted: Boolean = false,
    borderWidth: Dp = 1.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        shape = shape,
        color = glassFillColor(alpha = fillAlpha, baseColor = baseColor),
        border = BorderStroke(borderWidth, glassBorderBrush(highlighted = highlighted)),
        shadowElevation = shadowElevation,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.055f else 0.25f),
                        Color.Transparent,
                    )
                )
            ),
            content = content,
        )
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 46.dp,
    iconSize: Dp = 20.dp,
    testTag: String? = null,
) {
    val haptics = rememberOneNewsHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val bgColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.62f else 0.82f)
        },
        label = "glass_circle_bg",
    )
    val fgColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "glass_circle_fg",
    )

    Surface(
        onClick = {
            haptics.tap()
            onClick()
        },
        shape = CircleShape,
        color = bgColor,
        contentColor = fgColor,
        border = BorderStroke(
            width = 1.dp,
            brush = glassBorderBrush(highlighted = active),
        ),
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

/**
 * Compact frosted glass segmented pill switcher matching FloatingBottomNavBar's glass styling
 * and snug uniform tray inset around the action pills.
 */
@Composable
fun <T> GlassSegmentedPill(
    options: List<T>,
    selectedOption: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val frostedBase = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (isDark) 0.91f else 0.95f,
    )

    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, glassBorderBrush(highlighted = true)),
        shadowElevation = 18.dp,
        modifier = modifier
            .clip(CircleShape)
            .testTag("reader_mode_tray"),
    ) {
        Box {
            // Blurred frosted glass substrate matching FloatingBottomNavBar
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

            // Top specular glass sheen matching FloatingBottomNavBar
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
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { option ->
                    val selected = option == selectedOption
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) {
                            primaryColor.copy(alpha = if (isDark) 0.38f else 0.24f)
                        } else {
                            Color.Transparent
                        },
                        label = "segmented_pill_bg",
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (selected) {
                            if (isDark) Color.White else primaryColor
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
                        },
                        label = "segmented_pill_fg",
                    )

                    Surface(
                        shape = CircleShape,
                        color = pillColor,
                        contentColor = textColor,
                        border = if (selected) {
                            BorderStroke(1.dp, primaryColor.copy(alpha = 0.72f))
                        } else {
                            null
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .semantics {
                                this.selected = selected
                            }
                            .clickable(role = Role.Tab) {
                                haptics.toggle()
                                onSelect(option)
                            }
                            .testTag("segmented_${labelFor(option).lowercase()}"),
                    ) {
                        Text(
                            text = labelFor(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive wavy progress scrubber matching the organic wave bar in the reference screenshot.
 */
@Composable
fun WavyAudioScrubber(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberOneNewsHaptics()
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val thumbHalo = Color.White

    val infiniteTransition = rememberInfiniteTransition(label = "wavy_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_shift",
    )

    val activePhase = if (isPlaying) phase else 0f
    val clampedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    haptics.scrubTick()
                    onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            val totalW = size.width
            val splitX = totalW * clampedProgress

            // Inactive straight track on the right
            if (splitX < totalW) {
                drawLine(
                    color = trackColor,
                    start = Offset(splitX, midY),
                    end = Offset(totalW, midY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Active organic wave on the left
            if (splitX > 2f) {
                val wavePath = Path()
                val amplitude = if (isPlaying) 4.5.dp.toPx() else 2.5.dp.toPx()
                val wavelength = 24.dp.toPx()
                val step = 2.dp.toPx()
                var x = 0f
                var first = true
                while (x <= splitX) {
                    val y = midY + sin((x / wavelength) * 2f * PI + activePhase).toFloat() * amplitude
                    if (first) {
                        wavePath.moveTo(x, y)
                        first = false
                    } else {
                        wavePath.lineTo(x, y)
                    }
                    x += step
                }
                drawPath(
                    path = wavePath,
                    color = primary,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Luminous circular scrub thumb
            val thumbY = midY
            drawCircle(
                color = primary.copy(alpha = 0.35f),
                radius = 10.dp.toPx(),
                center = Offset(splitX, thumbY),
            )
            drawCircle(
                color = thumbHalo,
                radius = 6.5.dp.toPx(),
                center = Offset(splitX, thumbY),
            )
            drawCircle(
                color = primary,
                radius = 3.5.dp.toPx(),
                center = Offset(splitX, thumbY),
            )
        }
    }
}
