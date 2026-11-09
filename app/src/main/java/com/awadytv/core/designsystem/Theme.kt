package com.awadytv.core.designsystem

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * How glass is rendered on this device.
 * [runtimeBlur] true: API 31+ and not a low-RAM device, so the backdrop layer behind glass is blurred with RenderEffect.
 * Otherwise the fallback path: the backdrop is pre-blurred once at load time (1/8 size) and no runtime blur is used.
 */
@Immutable
data class PerformanceProfile(
    val runtimeBlur: Boolean,
    val lowEnd: Boolean,
) {
    /** Cap items per shelf on low-end boxes to keep scrolling at 60 fps. */
    val shelfItemCap: Int get() = if (lowEnd) 24 else 48

    companion object {
        fun detect(context: Context, forceLowEnd: Boolean = false): PerformanceProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val lowRam = am?.isLowRamDevice ?: true
            val memClassSmall = (am?.memoryClass ?: 0) in 1..191
            val lowEnd = forceLowEnd || lowRam || memClassSmall
            val blur = !forceLowEnd && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !lowRam
            return PerformanceProfile(runtimeBlur = blur, lowEnd = lowEnd)
        }
    }
}

/** Direction the D-pad last moved focus; drives the parallax tilt of focused cards. */
enum class MoveDirection { None, Left, Right, Up, Down }

@Stable
class FocusDirectionTracker {
    // Deliberately not snapshot state: read only when a card gains focus, so it never triggers recomposition.
    var last: MoveDirection = MoveDirection.None
}

val LocalPerformance = staticCompositionLocalOf { PerformanceProfile(runtimeBlur = false, lowEnd = true) }
val LocalFocusDirection = staticCompositionLocalOf { FocusDirectionTracker() }
val LocalGlassBackdrop = staticCompositionLocalOf { GlassBackdrop() }

/** True while the enclosing shelf (row) contains focus; unfocused siblings dim to 85 %. */
val LocalShelfFocused = compositionLocalOf { false }

@Composable
fun AwadyTheme(
    performance: PerformanceProfile,
    backdrop: GlassBackdrop,
    focusDirection: FocusDirectionTracker,
    content: @Composable () -> Unit,
) {
    val colors = remember {
        darkColorScheme(
            primary = AwadyColors.Accent,
            onPrimary = AwadyColors.TextPrimary,
            background = AwadyColors.Canvas,
            onBackground = AwadyColors.TextPrimary,
            surface = AwadyColors.CanvasRaised,
            onSurface = AwadyColors.TextPrimary,
            error = AwadyColors.Danger,
        )
    }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalPerformance provides performance,
            LocalGlassBackdrop provides backdrop,
            LocalFocusDirection provides focusDirection,
            LocalContentColor provides AwadyColors.TextPrimary,
            content = content,
        )
    }
}
