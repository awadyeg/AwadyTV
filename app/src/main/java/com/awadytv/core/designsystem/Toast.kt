package com.awadytv.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@Stable
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var serial by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        message = text
        serial++
    }

    internal fun clear() {
        message = null
    }
}

@Composable
fun rememberToastState() = remember { ToastState() }

/** Non-focusable glass capsule for transient confirmations ("Added to likes"). */
@Composable
fun GlassToastHost(state: ToastState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.serial) {
        if (state.message != null) {
            delay(2_200)
            state.clear()
        }
    }
    var last by remember { mutableStateOf("") }
    state.message?.let { last = it }
    AnimatedVisibility(
        visible = state.message != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier,
    ) {
        GlassSurface(shape = RoundedCornerShape(percent = 50), elevation = 20.dp) {
            Text(last, style = AwadyType.Label, color = AwadyColors.TextPrimary, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
        }
    }
}
