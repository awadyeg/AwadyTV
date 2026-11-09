package com.awadytv.core.designsystem

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Drives the app-wide cinematic backdrop. Screens call [show] with the artwork of the focused item;
 * the dominant colour is extracted with Palette and animated over 600ms.
 */
@Stable
class BackdropController {
    var artworkUrl by mutableStateOf<String?>(null)
        private set
    var dominant by mutableStateOf(AwadyColors.Canvas)
        internal set

    fun show(url: String?) {
        if (!url.isNullOrBlank()) artworkUrl = url
    }

    fun clear() {
        artworkUrl = null
    }
}

val LocalBackdrop = staticCompositionLocalOf { BackdropController() }

@Composable
fun AppBackdrop(controller: BackdropController, glass: GlassBackdrop, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val performance = LocalPerformance.current
    val url = controller.artworkUrl
    var image by androidx.compose.runtime.remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url, performance.runtimeBlur) {
        if (url == null) {
            image = null
            glass.image = null
            controller.dominant = AwadyColors.Canvas
            return@LaunchedEffect
        }
        delay(220) // do not decode artwork for every card the user flies past
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(if (performance.runtimeBlur) 320 else 240, if (performance.runtimeBlur) 180 else 135)
            .allowHardware(false)
            .apply { if (!performance.runtimeBlur) transformations(DownsampleBlurTransformation()) }
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        val dominant = withContext(Dispatchers.Default) {
            val swatch = Palette.from(bitmap).maximumColorCount(12).generate()
            Color(swatch.getDarkVibrantColor(swatch.getDominantColor(0xFF0B0B0F.toInt())))
        }
        val imageBitmap = bitmap.asImageBitmap()
        image = imageBitmap
        glass.image = imageBitmap
        // Keep the canvas cinematic: only ever a tint of the artwork colour over near-black.
        controller.dominant = lerp(AwadyColors.Canvas, dominant, 0.45f)
    }

    val tint by animateColorAsState(controller.dominant, tween(AwadyMotion.BACKDROP_COLOR_MS), label = "backdropTint")

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { glass.rootSize = it }
            .background(AwadyColors.Canvas),
    ) {
        Crossfade(targetState = image, animationSpec = tween(600), label = "backdropImage") { img ->
            if (img != null) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (performance.runtimeBlur && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                renderEffect = BlurEffect(36f, 36f, TileMode.Clamp)
                            }
                            alpha = 0.55f
                        },
                ) {
                    drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.85f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.1f),
                    radius = size.width * 0.9f,
                ),
            )
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to AwadyColors.Canvas.copy(alpha = 0.55f),
                    1f to AwadyColors.Canvas.copy(alpha = 0.92f),
                ),
            )
        }
    }
}

/**
 * Fallback-path blur: downsample to 1/8 and run a 3-pass box blur on the CPU once at load time.
 * The result is cached by Coil, so no blur work happens while scrolling.
 */
class DownsampleBlurTransformation(private val radius: Int = 4, private val sampling: Int = 8) : Transformation() {
    override val cacheKey: String = "glass-blur-$radius-$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val w = (input.width / sampling).coerceAtLeast(8)
        val h = (input.height / sampling).coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(input, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(3) {
            boxBlurHorizontal(pixels, w, h, radius)
            boxBlurVertical(pixels, w, h, radius)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== input) small.recycle()
        out
    }

    private fun boxBlurHorizontal(px: IntArray, w: Int, h: Int, r: Int) {
        val row = IntArray(w)
        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    val c = px[base + xx]
                    a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                row[x] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
            System.arraycopy(row, 0, px, base, w)
        }
    }

    private fun boxBlurVertical(px: IntArray, w: Int, h: Int, r: Int) {
        val col = IntArray(h)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    val c = px[yy * w + x]
                    a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                col[y] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
            for (y in 0 until h) px[y * w + x] = col[y]
        }
    }
}
