package com.example.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.model.PageTurnAnimation
import com.example.ui.components.glassBorderBrush
import com.example.ui.theme.OneUiCardRadius
import kotlin.math.abs

/**
 * Resolves the opaque book page surface color directly from the active MaterialTheme ColorScheme.
 * Respects wallpaper Dynamic Color (Monet) palettes in both light and dark modes, tonal dark,
 * and AMOLED pure black (which Theme.kt maps onto surfaceContainer / surfaceContainerHigh).
 */
fun bookPageSurfaceColor(colorScheme: ColorScheme): Color {
    val isDark = colorScheme.background.luminance() < 0.5f
    return if (isDark) colorScheme.surfaceContainer else colorScheme.surfaceContainerLow
}

fun bookPageBackFaceColor(colorScheme: ColorScheme): Color {
    val isDark = colorScheme.background.luminance() < 0.5f
    return if (isDark) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerHighest
}

@Composable
internal fun SinglePageBookContent(
    currentPage: BookPageContent?,
    nextOrPrevPage: BookPageContent?,
    isTurning: Boolean,
    turnDirection: Int,
    turnProgress: Float,
    anchorYFraction: Float,
    animationMode: PageTurnAnimation,
    renderPage: @Composable (BookPageContent, Boolean) -> Unit,
) {
    val active = currentPage ?: return
    val p = turnProgress.coerceIn(0f, 1f)

    if (!isTurning || p <= 0.001f || animationMode == PageTurnAnimation.INSTANT || nextOrPrevPage == null) {
        renderPage(active, false)
        return
    }

    when (animationMode) {
        PageTurnAnimation.SIMPLE_SLIDE -> {
            val foregroundPage = if (turnDirection > 0) active else nextOrPrevPage
            val backgroundPage = if (turnDirection > 0) nextOrPrevPage else active
            val shiftFactor = if (turnDirection > 0) p else (1f - p)
            val pageBg = bookPageSurfaceColor(MaterialTheme.colorScheme)

            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                Surface(
                    color = pageBg,
                    shape = RoundedCornerShape(OneUiCardRadius),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                        .clipToBounds()
                        .graphicsLayer {
                            val scale = 0.97f + 0.03f * shiftFactor
                            scaleX = scale
                            scaleY = scale
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = (0.30f * (1f - shiftFactor)).coerceIn(0f, 0.30f)))
                        },
                ) {
                    renderPage(backgroundPage, false)
                }

                Surface(
                    color = pageBg,
                    shape = RoundedCornerShape(OneUiCardRadius),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .clipToBounds()
                        .graphicsLayer {
                            translationX = -size.width * shiftFactor
                        },
                ) {
                    renderPage(foregroundPage, false)
                }
            }
        }

        PageTurnAnimation.KINDLE_CURL -> {
            if (turnDirection > 0) {
                BookKindleCurlSheet(
                    turningContent = { renderPage(active, false) },
                    underlyingContent = { renderPage(nextOrPrevPage, false) },
                    peelProgress = p,
                    peelFromRight = true,
                    anchorYFraction = anchorYFraction,
                )
            } else {
                BookKindleCurlSheet(
                    turningContent = { renderPage(nextOrPrevPage, false) },
                    underlyingContent = { renderPage(active, false) },
                    peelProgress = (1f - p).coerceIn(0f, 1f),
                    peelFromRight = true,
                    anchorYFraction = anchorYFraction,
                )
            }
        }

        PageTurnAnimation.INSTANT -> renderPage(active, false)
    }
}

@Composable
internal fun TwoColumnBookSpreadContent(
    pages: List<BookPageContent>,
    currentIndex: Int,
    targetIndex: Int,
    isTurning: Boolean,
    turnDirection: Int,
    turnProgress: Float,
    anchorYFraction: Float,
    hingeGap: Dp,
    animationMode: PageTurnAnimation,
    renderPage: @Composable (BookPageContent, Boolean) -> Unit,
) {
    val curLeft = pages.getOrNull(currentIndex)
    val curRight = pages.getOrNull(currentIndex + 1)
    val nextLeft = pages.getOrNull(targetIndex)
    val nextRight = pages.getOrNull(targetIndex + 1)
    val p = turnProgress.coerceIn(0f, 1f)

    if (!isTurning || p <= 0.001f || animationMode == PageTurnAnimation.INSTANT || targetIndex == currentIndex) {
        StaticTwoColumnSpread(
            leftPage = curLeft,
            rightPage = curRight,
            hingeGap = hingeGap,
            renderPage = renderPage,
        )
        return
    }

    if (animationMode == PageTurnAnimation.SIMPLE_SLIDE) {
        val fgLeft = if (turnDirection > 0) curLeft else nextLeft
        val fgRight = if (turnDirection > 0) curRight else nextRight
        val bgLeft = if (turnDirection > 0) nextLeft else curLeft
        val bgRight = if (turnDirection > 0) nextRight else curRight
        val shiftFactor = if (turnDirection > 0) p else (1f - p)

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .graphicsLayer {
                        val scale = 0.97f + 0.03f * shiftFactor
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = (0.28f * (1f - shiftFactor)).coerceIn(0f, 0.28f)))
                    },
            ) {
                StaticTwoColumnSpread(
                    leftPage = bgLeft,
                    rightPage = bgRight,
                    hingeGap = hingeGap,
                    renderPage = renderPage,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .graphicsLayer {
                        translationX = -size.width * shiftFactor
                    },
            ) {
                StaticTwoColumnSpread(
                    leftPage = fgLeft,
                    rightPage = fgRight,
                    hingeGap = hingeGap,
                    renderPage = renderPage,
                )
            }
        }
        return
    }

    // KINDLE_CURL across Two-Column Open-Book Spread:
    // Phase 1 (0.00..0.55): Outer edge curls inward toward the spine with real paper back-face.
    // Phase 2 (0.55..1.00): Turning leaf crosses the spine and unrolls onto the opposite side.
    val splitPhase = 0.55f

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // LEFT COLUMN
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            if (turnDirection > 0) {
                if (p < splitPhase) {
                    curLeft?.let { renderPage(it, true) }
                } else {
                    val unrollP = ((p - splitPhase) / (1f - splitPhase)).coerceIn(0f, 1f)
                    BookSpineUnrollLeaf(
                        underlyingContent = { curLeft?.let { renderPage(it, true) } },
                        incomingContent = { nextLeft?.let { renderPage(it, true) } },
                        unrollProgress = unrollP,
                        fromSpineOnRight = true,
                    )
                }
            } else {
                if (p < splitPhase && curLeft != null) {
                    val peelP = (p / splitPhase).coerceIn(0f, 1f)
                    BookKindleCurlSheet(
                        turningContent = { renderPage(curLeft, true) },
                        underlyingContent = { nextLeft?.let { renderPage(it, true) } },
                        peelProgress = peelP,
                        peelFromRight = false,
                        anchorYFraction = anchorYFraction,
                    )
                } else {
                    nextLeft?.let { renderPage(it, true) }
                }
            }
        }

        BookCenterSpineDivider(hingeGap = hingeGap)

        // RIGHT COLUMN
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            if (turnDirection > 0) {
                if (p < splitPhase && curRight != null) {
                    val peelP = (p / splitPhase).coerceIn(0f, 1f)
                    BookKindleCurlSheet(
                        turningContent = { renderPage(curRight, false) },
                        underlyingContent = {
                            if (nextRight != null) {
                                renderPage(nextRight, false)
                            } else {
                                EndOfArticleColophonCard()
                            }
                        },
                        peelProgress = peelP,
                        peelFromRight = true,
                        anchorYFraction = anchorYFraction,
                    )
                } else if (nextRight != null) {
                    renderPage(nextRight, false)
                } else {
                    EndOfArticleColophonCard()
                }
            } else {
                if (p < splitPhase) {
                    if (curRight != null) {
                        renderPage(curRight, false)
                    } else {
                        EndOfArticleColophonCard()
                    }
                } else {
                    val unrollP = ((p - splitPhase) / (1f - splitPhase)).coerceIn(0f, 1f)
                    BookSpineUnrollLeaf(
                        underlyingContent = {
                            if (curRight != null) {
                                renderPage(curRight, false)
                            } else {
                                EndOfArticleColophonCard()
                            }
                        },
                        incomingContent = {
                            if (nextRight != null) {
                                renderPage(nextRight, false)
                            } else {
                                EndOfArticleColophonCard()
                            }
                        },
                        unrollProgress = unrollP,
                        fromSpineOnRight = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun StaticTwoColumnSpread(
    leftPage: BookPageContent?,
    rightPage: BookPageContent?,
    hingeGap: Dp,
    renderPage: @Composable (BookPageContent, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            leftPage?.let { renderPage(it, true) }
        }

        BookCenterSpineDivider(hingeGap = hingeGap)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            if (rightPage != null) {
                renderPage(rightPage, false)
            } else {
                EndOfArticleColophonCard()
            }
        }
    }
}

@Composable
private fun BookCenterSpineDivider(hingeGap: Dp) {
    Box(
        modifier = Modifier
            .width(hingeGap)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight(0.82f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            Color.Transparent,
                        )
                    )
                ),
        )
    }
}

/**
 * Interactive Kindle-style paper curl sheet using Canvas clipPath, diagonal fold tilt,
 * opaque theme/Monet-matched mirrored back-face flap, crease lines, and cast drop shadow.
 */
@Composable
private fun BookKindleCurlSheet(
    turningContent: @Composable () -> Unit,
    underlyingContent: @Composable () -> Unit,
    peelProgress: Float,
    peelFromRight: Boolean,
    anchorYFraction: Float,
) {
    val p = peelProgress.coerceIn(0f, 1f)
    val tiltBias = ((anchorYFraction - 0.5f) * 2f).coerceIn(-0.85f, 0.85f)
    val tiltSpreadNorm = (0.14f * (1f - abs(p - 0.5f) * 1.4f)).coerceAtLeast(0.02f) * tiltBias

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val opaquePageColor = bookPageSurfaceColor(MaterialTheme.colorScheme)
    val backFaceColor = bookPageBackFaceColor(MaterialTheme.colorScheme)
    val creaseHighlightColor = MaterialTheme.colorScheme.outlineVariant
    val cornerShape = RoundedCornerShape(OneUiCardRadius)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        // 1. Stationary underlying page (zIndex 0) + cast shadow beneath curl crease
        Surface(
            color = opaquePageColor,
            shape = cornerShape,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val h = size.height
                    val centerFoldX = if (peelFromRight) w * (1f - p) else w * p
                    val topFoldX = if (peelFromRight) {
                        (centerFoldX + w * tiltSpreadNorm).coerceIn(-w * 0.25f, w * 1.1f)
                    } else {
                        (centerFoldX - w * tiltSpreadNorm).coerceIn(-w * 0.1f, w * 1.25f)
                    }
                    val bottomFoldX = if (peelFromRight) {
                        (centerFoldX - w * tiltSpreadNorm).coerceIn(-w * 0.25f, w * 1.1f)
                    } else {
                        (centerFoldX + w * tiltSpreadNorm).coerceIn(-w * 0.1f, w * 1.25f)
                    }
                    val midFoldX = (topFoldX + bottomFoldX) * 0.5f
                    val shadowWidth = (w * 0.22f * (1f - p * 0.45f)).coerceAtLeast(24.dp.toPx())
                    val shadowAlpha = (if (isDark) 0.62f else 0.34f) * (1f - p * 0.4f)

                    if (peelFromRight) {
                        val sStart = midFoldX.coerceIn(0f, w)
                        val sEnd = (midFoldX + shadowWidth).coerceIn(0f, w)
                        if (sEnd > sStart) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = shadowAlpha),
                                        Color.Black.copy(alpha = shadowAlpha * 0.4f),
                                        Color.Transparent,
                                    ),
                                    startX = sStart,
                                    endX = sEnd,
                                ),
                                topLeft = Offset(sStart, 0f),
                                size = Size(sEnd - sStart, h),
                            )
                        }
                    } else {
                        val sEnd = midFoldX.coerceIn(0f, w)
                        val sStart = (midFoldX - shadowWidth).coerceIn(0f, w)
                        if (sEnd > sStart) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = shadowAlpha * 0.4f),
                                        Color.Black.copy(alpha = shadowAlpha),
                                    ),
                                    startX = sStart,
                                    endX = sEnd,
                                ),
                                topLeft = Offset(sStart, 0f),
                                size = Size(sEnd - sStart, h),
                            )
                        }
                    }
                },
        ) {
            underlyingContent()
        }

        // 2. Unpeeled portion of foreground turning page (zIndex 2)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .clipToBounds()
                .drawWithContent {
                    val w = size.width
                    val h = size.height
                    val centerFoldX = if (peelFromRight) w * (1f - p) else w * p
                    val topFoldX = if (peelFromRight) {
                        (centerFoldX + w * tiltSpreadNorm).coerceIn(-w * 0.2f, w * 1.1f)
                    } else {
                        (centerFoldX - w * tiltSpreadNorm).coerceIn(-w * 0.1f, w * 1.2f)
                    }
                    val bottomFoldX = if (peelFromRight) {
                        (centerFoldX - w * tiltSpreadNorm).coerceIn(-w * 0.2f, w * 1.1f)
                    } else {
                        (centerFoldX + w * tiltSpreadNorm).coerceIn(-w * 0.1f, w * 1.2f)
                    }

                    val frontPath = Path().apply {
                        if (peelFromRight) {
                            moveTo(0f, 0f)
                            lineTo(topFoldX.coerceAtLeast(0f), 0f)
                            lineTo(bottomFoldX.coerceAtLeast(0f), h)
                            lineTo(0f, h)
                        } else {
                            moveTo(topFoldX.coerceAtMost(w), 0f)
                            lineTo(w, 0f)
                            lineTo(w, h)
                            lineTo(bottomFoldX.coerceAtMost(w), h)
                        }
                        close()
                    }
                    clipPath(frontPath) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            turningContent()
        }

        // 3. Opaque theme-matched curled paper back-face flap (zIndex 3)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
                .clipToBounds()
                .drawWithContent {
                    val w = size.width
                    val h = size.height
                    val centerFoldX = if (peelFromRight) w * (1f - p) else w * p
                    val topFoldX = if (peelFromRight) centerFoldX + w * tiltSpreadNorm else centerFoldX - w * tiltSpreadNorm
                    val bottomFoldX = if (peelFromRight) centerFoldX - w * tiltSpreadNorm else centerFoldX + w * tiltSpreadNorm

                    val flapWidth = (w * p * 0.62f).coerceIn(0f, w * 0.56f)
                    val flapTopOuterX = if (peelFromRight) {
                        (topFoldX - flapWidth).coerceAtLeast(-w * 0.15f)
                    } else {
                        (topFoldX + flapWidth).coerceAtMost(w * 1.15f)
                    }
                    val flapBottomOuterX = if (peelFromRight) {
                        (bottomFoldX - flapWidth).coerceAtLeast(-w * 0.15f)
                    } else {
                        (bottomFoldX + flapWidth).coerceAtMost(w * 1.15f)
                    }

                    val backFlapPath = Path().apply {
                        if (peelFromRight) {
                            moveTo(flapTopOuterX, 0f)
                            lineTo(topFoldX, 0f)
                            lineTo(bottomFoldX, h)
                            lineTo(flapBottomOuterX, h)
                        } else {
                            moveTo(topFoldX, 0f)
                            lineTo(flapTopOuterX, 0f)
                            lineTo(flapBottomOuterX, h)
                            lineTo(bottomFoldX, h)
                        }
                        close()
                    }

                    clipPath(backFlapPath) {
                        this@drawWithContent.drawContent()
                        val minX = minOf(flapTopOuterX, flapBottomOuterX, topFoldX, bottomFoldX)
                        val maxX = maxOf(flapTopOuterX, flapBottomOuterX, topFoldX, bottomFoldX)
                        if (maxX > minX) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = if (isDark) 0.22f else 0.10f),
                                        Color.White.copy(alpha = if (isDark) 0.06f else 0.18f),
                                        Color.Black.copy(alpha = if (isDark) 0.14f else 0.06f),
                                    ),
                                    startX = minX,
                                    endX = maxX,
                                )
                            )
                        }
                    }

                    drawLine(
                        color = creaseHighlightColor,
                        start = Offset(topFoldX, 0f),
                        end = Offset(bottomFoldX, h),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = creaseHighlightColor.copy(alpha = 0.7f),
                        start = Offset(flapTopOuterX, 0f),
                        end = Offset(flapBottomOuterX, h),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        ) {
            Surface(
                color = backFaceColor,
                shape = cornerShape,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = -1f
                            rotationZ = (if (peelFromRight) -3.5f else 3.5f) * tiltBias * p
                            alpha = 0.14f
                        },
                ) {
                    turningContent()
                }
            }
        }
    }
}

/**
 * Second half of a two-column open-book spread page turn:
 * unfolds the turned leaf smoothly outward from the center spine across the destination column.
 */
@Composable
private fun BookSpineUnrollLeaf(
    underlyingContent: @Composable () -> Unit,
    incomingContent: @Composable () -> Unit,
    unrollProgress: Float,
    fromSpineOnRight: Boolean,
) {
    val p = unrollProgress.coerceIn(0f, 1f)
    val density = LocalDensity.current.density
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black.copy(alpha = (0.18f * (1f - p)).coerceIn(0f, 0.18f)))
                },
        ) {
            underlyingContent()
        }

        val angle = if (fromSpineOnRight) (1f - p) * 28f else -(1f - p) * 28f
        val originX = if (fromSpineOnRight) 1f else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .clipToBounds()
                .graphicsLayer {
                    cameraDistance = 18f * density
                    transformOrigin = TransformOrigin(originX, 0.5f)
                    rotationY = angle
                }
                .drawWithContent {
                    val w = size.width
                    val h = size.height
                    val clipPathRegion = Path().apply {
                        if (fromSpineOnRight) {
                            val leftEdge = w * (1f - p)
                            moveTo(leftEdge, 0f)
                            lineTo(w, 0f)
                            lineTo(w, h)
                            lineTo(leftEdge, h)
                        } else {
                            val rightEdge = w * p
                            moveTo(0f, 0f)
                            lineTo(rightEdge, 0f)
                            lineTo(rightEdge, h)
                            lineTo(0f, h)
                        }
                        close()
                    }
                    clipPath(clipPathRegion) {
                        this@drawWithContent.drawContent()
                        val shadowAlpha = (if (isDark) 0.28f else 0.14f) * (1f - p)
                        if (shadowAlpha > 0.01f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = if (fromSpineOnRight) {
                                        listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent)
                                    } else {
                                        listOf(Color.Transparent, Color.Black.copy(alpha = shadowAlpha))
                                    },
                                )
                            )
                        }
                    }
                },
        ) {
            incomingContent()
        }
    }
}

@Composable
internal fun EndOfArticleColophonCard() {
    val pageColor = bookPageSurfaceColor(MaterialTheme.colorScheme)
    Surface(
        shape = RoundedCornerShape(OneUiCardRadius),
        color = pageColor,
        border = BorderStroke(1.dp, glassBorderBrush(highlighted = false)),
        modifier = Modifier
            .fillMaxSize()
            .testTag("book_end_colophon_card"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "End of Article",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Flip forward for the next dispatch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
