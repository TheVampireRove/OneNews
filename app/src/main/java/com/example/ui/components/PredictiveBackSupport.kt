package com.example.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Material 3 predictive back deceleration curve (0, 0, 0, 1).
 */
val PredictiveBackDecelerateEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

data class PredictiveBackTransform(
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val cornerRadiusDp: Float = 0f,
    val scrimAlpha: Float = 0f,
)

object PredictiveBackMotionSpecs {
    fun computeSurfaceTransform(
        rawProgress: Float,
        swipeEdge: Int,
        verticalTouchDeltaPx: Float,
        containerWidthPx: Float,
        containerHeightPx: Float,
        marginPx: Float,
        minScale: Float = 0.90f,
        maxCornerRadiusDp: Float = 28f,
    ): PredictiveBackTransform {
        val clamped = rawProgress.coerceIn(0f, 1f)
        if (clamped <= 0.0001f) {
            return PredictiveBackTransform()
        }

        val eased = PredictiveBackDecelerateEasing.transform(clamped)
        val scale = 1f - (1f - minScale) * eased

        // Android predictive back design spec: max X shift = (screenWidth / 20) - 8dp
        val maxShiftX = ((containerWidthPx / 20f) - marginPx).coerceAtLeast(0f)
        val directionX = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        val translationX = directionX * maxShiftX * eased

        // Bounded Y parallax shift: keeps an 8dp gap from top/bottom screen edges
        val maxShiftY = ((containerHeightPx / 20f) - marginPx).coerceAtLeast(0f)
        val normalizedY = if (containerHeightPx > 1f) {
            (verticalTouchDeltaPx / (containerHeightPx * 0.45f)).coerceIn(-1f, 1f)
        } else {
            0f
        }
        val translationY = PredictiveBackDecelerateEasing.transform(kotlin.math.abs(normalizedY)) *
            kotlin.math.sign(normalizedY) * maxShiftY * eased

        val cornerRadiusDp = maxCornerRadiusDp * eased
        val scrimAlpha = (0.38f * (1f - eased * 0.45f)).coerceIn(0f, 0.45f)

        return PredictiveBackTransform(
            scale = scale,
            translationX = translationX,
            translationY = translationY,
            cornerRadiusDp = cornerRadiusDp,
            scrimAlpha = scrimAlpha,
        )
    }
}

/**
 * Wraps an in-app screen/overlay with real-time Android Predictive Back gesture animations.
 * When disabled (e.g., at the root Feed screen), the system handles back events natively
 * so Android's predictive back-to-home animation remains intact.
 */
@Composable
fun PredictiveBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    minScale: Float = 0.90f,
    cornerRadiusTarget: Dp = 28.dp,
    resetKey: Any? = enabled,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.toPx() }

    val progressAnim = remember { Animatable(0f) }
    val verticalDeltaAnim = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }
    var isGestureActive by remember { mutableStateOf(false) }

    LaunchedEffect(resetKey, enabled) {
        if (!isGestureActive) {
            progressAnim.snapTo(0f)
            verticalDeltaAnim.snapTo(0f)
        }
    }

    PredictiveBackHandler(enabled = enabled) { progressFlow: Flow<BackEventCompat> ->
        var initialTouchY: Float? = null
        isGestureActive = true
        try {
            progressFlow.collect { backEvent ->
                if (initialTouchY == null) {
                    initialTouchY = backEvent.touchY
                }
                swipeEdge = backEvent.swipeEdge
                progressAnim.snapTo(backEvent.progress.coerceIn(0f, 1f))
                verticalDeltaAnim.snapTo(backEvent.touchY - (initialTouchY ?: backEvent.touchY))
            }
            isGestureActive = false
            onBack()
        } catch (e: CancellationException) {
            isGestureActive = false
            scope.launch {
                progressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            scope.launch {
                verticalDeltaAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            throw e
        }
    }

    val transform = PredictiveBackMotionSpecs.computeSurfaceTransform(
        rawProgress = progressAnim.value,
        swipeEdge = swipeEdge,
        verticalTouchDeltaPx = verticalDeltaAnim.value,
        containerWidthPx = containerWidthPx.toFloat(),
        containerHeightPx = containerHeightPx.toFloat(),
        marginPx = marginPx,
        minScale = minScale,
        maxCornerRadiusDp = cornerRadiusTarget.value,
    )

    val isTransforming = transform.scale < 0.999f || transform.cornerRadiusDp > 0.1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                containerWidthPx = size.width
                containerHeightPx = size.height
            },
    ) {
        if (transform.scrimAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = transform.scrimAlpha)),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isTransforming) {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.translationX
                        translationY = transform.translationY
                    }
                }
                .let { mod ->
                    if (isTransforming) {
                        mod.clip(RoundedCornerShape(transform.cornerRadiusDp.dp))
                    } else {
                        mod
                    }
                },
        ) {
            content()
        }
    }
}
