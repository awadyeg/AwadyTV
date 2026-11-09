package com.awadytv.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.awadytv.core.designsystem.AppBackdrop
import com.awadytv.core.designsystem.AwadyTheme
import com.awadytv.core.designsystem.BackdropController
import com.awadytv.core.designsystem.FocusDirectionTracker
import com.awadytv.core.designsystem.GlassBackdrop
import com.awadytv.core.designsystem.LocalBackdrop
import com.awadytv.core.designsystem.MoveDirection
import com.awadytv.core.designsystem.PerformanceProfile

@Composable
fun AwadyRoot() {
    val context = LocalContext.current
    val rootViewModel: RootViewModel = hiltViewModel()
    val settings by rootViewModel.settings.collectAsStateWithLifecycle()
    val performance = remember(settings.forceLowEnd) { PerformanceProfile.detect(context, settings.forceLowEnd) }
    val glass = remember { GlassBackdrop() }
    val backdrop = remember { BackdropController() }
    val tracker = remember { FocusDirectionTracker() }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    AwadyTheme(performance = performance, backdrop = glass, focusDirection = tracker) {
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            // Compose uses Back to move focus out of the focused item first, which looked like
                            // a "deselect" and needed a second press. Send Back straight to the BackHandlers.
                            Key.Back -> {
                                if (event.type == KeyEventType.KeyUp && !event.nativeKeyEvent.isCanceled) backDispatcher?.onBackPressed()
                                return@onPreviewKeyEvent true
                            }
                            // Holding OK auto-repeats; after a long press opens a new screen those repeats would
                            // press (and on release click) whatever gets focus there, e.g. the Play button.
                            // Long presses are timed from the first press, so repeats are never needed.
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                            else -> Unit
                        }
                        if (event.type == KeyEventType.KeyDown) {
                            tracker.last = when (event.key) {
                                Key.DirectionLeft -> MoveDirection.Left
                                Key.DirectionRight -> MoveDirection.Right
                                Key.DirectionUp -> MoveDirection.Up
                                Key.DirectionDown -> MoveDirection.Down
                                else -> tracker.last
                            }
                        }
                        false
                    },
            ) {
                AppBackdrop(controller = backdrop, glass = glass)
                AppNavHost()
            }
        }
    }
}
