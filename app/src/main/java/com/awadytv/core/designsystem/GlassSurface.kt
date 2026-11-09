package com.awadytv.core.designsystem

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random

enum class GlassVariant {
    /** Tab bar, side rails, overlays. */
    Regular,

    /** Hero text scrims: barely-there glass. */
    Clear,

    /** Accent-tinted buttons. */
    Tinted,
}

/**
 * The shared backdrop that glass refracts. [image] is the current screen artwork, already small
 * (and pre-blurred on the fallback path); glass surfaces draw the slice of it that lies behind them.
 */
@Stable
class GlassBackdrop {
    var image by mutableStateOf<ImageBitmap?>(null)
    var rootSize by mutableStateOf(IntSize.Zero)
}

/**
 * Liquid Glass surface: blurred backdrop slice + white 8-14 % fill + 3 % noise + 1dp specular inner
 * stroke (white 45 % top-left to 5 % bottom-right) + soft black 35 % shadow.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AwadyDimens.RadiusMedium),
    variant: GlassVariant = GlassVariant.Regular,
    tint: Color = AwadyColors.Accent,
    elevation: Dp = 12.dp,
    fillOverride: Color? = null,
    refractBackdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = fillOverride ?: when (variant) {
        GlassVariant.Regular -> AwadyColors.GlassRegular
        GlassVariant.Clear -> AwadyColors.GlassClear
        GlassVariant.Tinted -> tint.copy(alpha = 0.55f)
    }
    Box(
        modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = AwadyColors.Shadow, spotColor = AwadyColors.Shadow) else Modifier)
            .clip(shape)
            .drawWithContent {
                drawContent()
                drawSpecularStroke(shape)
            },
    ) {
        if (refractBackdrop && variant != GlassVariant.Clear) {
            GlassBackdropLayer(Modifier.matchParentSize())
        }
        Box(
            Modifier
                .matchParentSize()
                .background(fill)
                .glassNoise(),
        )
        content()
    }
}

/** Specular inner stroke drawn on top of glass content. */
fun DrawScope.drawSpecularStroke(shape: Shape, width: Float = 1.dp.toPx(), strength: Float = 1f) {
    val outline: Outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = outline,
        brush = Brush.linearGradient(
            colors = listOf(
                AwadyColors.StrokeHighlight.copy(alpha = AwadyColors.StrokeHighlight.alpha * strength),
                AwadyColors.StrokeFade,
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        style = Stroke(width = width * 2f), // half of it is clipped away -> visually an inner stroke
    )
}

@Composable
private fun GlassBackdropLayer(modifier: Modifier) {
    val backdrop = LocalGlassBackdrop.current
    val performance = LocalPerformance.current
    val image = backdrop.image ?: return
    val position = remember { PositionHolder() }
    val blur = performance.runtimeBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Canvas(
        modifier
            .onGloballyPositioned { position.offset = it.positionInRoot() }
            .then(
                if (blur) {
                    Modifier.graphicsLayer {
                        renderEffect = BlurEffect(28f, 28f, TileMode.Clamp)
                        clip = true
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val root = backdrop.rootSize
        if (root.width == 0 || root.height == 0) return@Canvas
        val o = position.offset
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(-o.x.roundToInt(), -o.y.roundToInt()),
            dstSize = root,
            alpha = 0.95f,
        )
        // Darken so white text on glass keeps contrast regardless of artwork.
        drawRect(Color.Black.copy(alpha = 0.35f))
    }
}

@Stable
private class PositionHolder {
    var offset by mutableStateOf(Offset.Zero)
}

private val noiseBitmap: ImageBitmap by lazy {
    val size = 96
    val random = Random(7)
    val pixels = IntArray(size * size) {
        val v = random.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** 3 % monochrome noise so large glass panels do not band. */
fun Modifier.glassNoise(alpha: Float = 0.03f): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = ShaderBrush(ImageShader(noiseBitmap, TileMode.Repeated, TileMode.Repeated)),
        alpha = alpha,
    )
}
