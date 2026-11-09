package com.awadytv.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

private val FocusedBorderBrush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0x99FFFFFF)))
private val RestingBorderBrush = Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x08FFFFFF)))

/**
 * tvOS "lift" focus treatment shared by every focusable card:
 * - scales to 1.08 with spring (damping 0.7, stiffness 400), raises its shadow, gains a 2dp white glass border;
 * - siblings in the same shelf dim to 85 % ([LocalShelfFocused]);
 * - parallax tilt of +-4 degrees toward the D-pad direction that moved focus, 200ms, then settles;
 * - a specular sweep crosses the card when it gains focus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusLift(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AwadyDimens.RadiusCard),
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    focusScale: Float = AwadyMotion.FOCUS_SCALE,
    parallax: Boolean = true,
    specular: Boolean = true,
    dimWithSiblings: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val performance = LocalPerformance.current
    val tracker = LocalFocusDirection.current
    val currentOnFocus by rememberUpdatedState(onFocus)
    val scale by animateFloatAsState(if (focused) focusScale else 1f, AwadyMotion.liftSpring(), label = "liftScale")
    val shelfFocused = LocalShelfFocused.current
    val alpha by animateFloatAsState(
        if (dimWithSiblings && shelfFocused && !focused) AwadyMotion.DIMMED_ALPHA else 1f,
        tween(200),
        label = "liftDim",
    )
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val sweep = remember { Animatable(-1f) }

    LaunchedEffect(focused) {
        if (focused) {
            currentOnFocus?.invoke()
            if (parallax && !performance.lowEnd) {
                val t = AwadyMotion.TILT_DEGREES
                val (targetX, targetY) = when (tracker.last) {
                    MoveDirection.Left -> 0f to -t
                    MoveDirection.Right -> 0f to t
                    MoveDirection.Up -> t to 0f
                    MoveDirection.Down -> -t to 0f
                    MoveDirection.None -> 0f to 0f
                }
                launch {
                    tiltX.animateTo(targetX, tween(AwadyMotion.TILT_MS))
                    tiltX.animateTo(0f, tween(AwadyMotion.SETTLE_MS))
                }
                launch {
                    tiltY.animateTo(targetY, tween(AwadyMotion.TILT_MS))
                    tiltY.animateTo(0f, tween(AwadyMotion.SETTLE_MS))
                }
            }
            if (specular) {
                sweep.snapTo(-0.5f)
                sweep.animateTo(1.5f, tween(AwadyMotion.SPECULAR_MS))
            }
        } else {
            tiltX.snapTo(0f)
            tiltY.snapTo(0f)
            sweep.snapTo(-1f)
        }
    }

    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = tiltX.value
                rotationY = tiltY.value
                cameraDistance = 14f * density
                this.alpha = alpha
                shadowElevation = if (focused) 28.dp.toPx() else 2.dp.toPx()
                this.shape = shape
                clip = true
            }
            .border(if (focused) 2.dp else 1.dp, if (focused) FocusedBorderBrush else RestingBorderBrush, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .drawWithContent {
                drawContent()
                val p = sweep.value
                if (p > -0.5f && p < 1.5f) {
                    val band = size.width * 0.5f
                    val x = p * (size.width + band) - band
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            start = Offset(x, 0f),
                            end = Offset(x + band, size.height),
                        ),
                    )
                }
            },
    ) {
        content(focused)
    }
}
