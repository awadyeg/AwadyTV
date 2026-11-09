package com.awadytv.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Screens call [show] with the artwork of the focused item. The app background used to blur that
 * artwork full-screen and tint itself with its Palette colour; that cost more than a frame on TV
 * GPUs, so the background is now a flat canvas and these calls only record the URL.
 */
@Stable
class BackdropController {
    var artworkUrl by mutableStateOf<String?>(null)
        private set

    fun show(url: String?) {
        if (!url.isNullOrBlank()) artworkUrl = url
    }

    fun clear() {
        artworkUrl = null
    }
}

val LocalBackdrop = staticCompositionLocalOf { BackdropController() }

/**
 * App background. Draws nothing: the window background (Theme.AwadyTV) is already the canvas colour, and
 * a second full-screen fill costs a whole frame of fill-rate on TV GPUs. Does not observe [BackdropController].
 */
@Composable
fun AppBackdrop(
    @Suppress("UNUSED_PARAMETER") controller: BackdropController,
    @Suppress("UNUSED_PARAMETER") glass: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize())
}
