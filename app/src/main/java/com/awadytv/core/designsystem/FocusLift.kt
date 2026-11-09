package com.awadytv.core.designsystem

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val RestingBorder = Color(0x1AFFFFFF)

/**
 * Focus treatment shared by every focusable card: a short 120ms scale-up and a solid white border.
 * Only scale is animated, and it is applied in the draw phase (graphicsLayer lambda), so focus moves
 * neither recompose nor relayout the card. [parallax], [specular] and [dimWithSiblings] are accepted
 * for source compatibility and ignored: tilt, sweep, shadows and sibling dimming were too expensive
 * on TV GPUs.
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
    @Suppress("UNUSED_PARAMETER") parallax: Boolean = false,
    @Suppress("UNUSED_PARAMETER") specular: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dimWithSiblings: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val currentOnFocus by rememberUpdatedState(onFocus)
    val scale = animateFloatAsState(if (focused) focusScale else 1f, tween(AwadyMotion.FOCUS_MS), label = "liftScale")

    LaunchedEffect(focused) {
        if (focused) currentOnFocus?.invoke()
    }

    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                // RenderNode outline clip: round rects take HWUI's fast path, unlike a clipPath per draw.
                this.shape = shape
                clip = true
            }
            .border(if (focused) 3.dp else 1.dp, if (focused) AwadyColors.FocusBorder else RestingBorder, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        content(focused)
    }
}
