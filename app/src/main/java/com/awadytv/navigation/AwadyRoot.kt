package com.awadytv.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
    val performance = remember { PerformanceProfile.detect(context) }
    val glass = remember { GlassBackdrop() }
    val backdrop = remember { BackdropController() }
    val tracker = remember { FocusDirectionTracker() }
    AwadyTheme(performance = performance, backdrop = glass, focusDirection = tracker) {
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
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
                MainShell()
            }
        }
    }
}
