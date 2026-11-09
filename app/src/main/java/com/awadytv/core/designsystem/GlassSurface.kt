package com.awadytv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

enum class GlassVariant {
    /** Tab bar, side rails, overlays. */
    Regular,

    /** Hero text scrims: barely-there surface. */
    Clear,

    /** Accent-tinted buttons. */
    Tinted,
}

/**
 * Kept for API compatibility. The app no longer refracts a backdrop: real-time blur made low-end TV
 * GPUs (e.g. MediaTek MT5896) render at 2-8 fps.
 */
@Stable
class GlassBackdrop {
    var image by mutableStateOf<ImageBitmap?>(null)
    var rootSize by mutableStateOf(IntSize.Zero)
}

/**
 * Flat surface: one fill and a 1dp hairline. No blur, no shadow, no noise, no extra layers, so it
 * costs a single draw call whatever the device. [elevation] and [refractBackdrop] are accepted and
 * ignored so existing call sites keep compiling.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AwadyDimens.RadiusMedium),
    variant: GlassVariant = GlassVariant.Regular,
    tint: Color = AwadyColors.Accent,
    @Suppress("UNUSED_PARAMETER") elevation: Dp = 0.dp,
    fillOverride: Color? = null,
    @Suppress("UNUSED_PARAMETER") refractBackdrop: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = fillOverride ?: when (variant) {
        GlassVariant.Regular -> AwadyColors.Surface
        GlassVariant.Clear -> AwadyColors.SurfaceClear
        GlassVariant.Tinted -> tint
    }
    Box(
        modifier
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, AwadyColors.Hairline, shape),
        content = content,
    )
}

/** Thin top-left highlight; now a plain hairline so it stays one cheap draw. */
fun DrawScope.drawSpecularStroke(shape: Shape, width: Float = 1.dp.toPx(), strength: Float = 1f) {
    val outline: Outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline = outline, color = AwadyColors.Hairline.copy(alpha = AwadyColors.Hairline.alpha * strength), style = Stroke(width = width * 2f))
}
