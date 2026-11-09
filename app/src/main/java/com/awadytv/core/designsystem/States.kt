package com.awadytv.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

@Composable
fun GlassSpinner(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spinAngle")
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        drawArc(AwadyColors.TextPrimary.copy(alpha = 0.15f), 0f, 360f, false, style = stroke)
        drawArc(AwadyColors.TextPrimary, angle, 100f, false, style = stroke)
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier, message: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(shape = RoundedCornerShape(AwadyDimens.RadiusLarge)) {
            Column(Modifier.padding(horizontal = 36.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassSpinner()
                if (message != null) Text(message, style = AwadyType.Body, color = AwadyColors.TextSecondary)
            }
        }
    }
}

@Composable
fun EmptyView(title: String, message: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Inbox, action: (@Composable () -> Unit)? = null) {
    MessagePanel(icon, title, message, modifier, action)
}

/** Error state with a retry button that takes focus, so the D-pad is never left without a target. */
@Composable
fun ErrorView(title: String, message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier, retryLabel: String = "Try again") {
    val requester = remember { FocusRequester() }
    MessagePanel(Icons.Rounded.ErrorOutline, title, message, modifier) {
        if (onRetry != null) {
            GlassButton(text = retryLabel, onClick = onRetry, icon = Icons.Rounded.Refresh, focusRequester = requester)
            LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
        }
    }
}

@Composable
private fun MessagePanel(icon: ImageVector, title: String, message: String, modifier: Modifier, action: (@Composable () -> Unit)?) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassSurface(shape = RoundedCornerShape(AwadyDimens.RadiusXL), modifier = Modifier.widthIn(max = 560.dp)) {
            Column(
                Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, tint = AwadyColors.TextSecondary, modifier = Modifier.size(40.dp))
                Text(title, style = AwadyType.Headline, color = AwadyColors.TextPrimary, textAlign = TextAlign.Center)
                Text(message, style = AwadyType.Body, color = AwadyColors.TextSecondary, textAlign = TextAlign.Center)
                if (action != null) {
                    Box(Modifier.padding(top = 8.dp)) { action() }
                }
            }
        }
    }
}
