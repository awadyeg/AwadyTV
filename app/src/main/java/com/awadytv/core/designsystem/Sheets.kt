package com.awadytv.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/** Side glass sheet sliding in from the start edge (live channel list, track picker). */
@Composable
fun GlassSideSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 420.dp,
    alignEnd: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { if (alignEnd) it / 3 else -it / 3 },
        exit = fadeOut() + slideOutHorizontally { if (alignEnd) it / 3 else -it / 3 },
        modifier = modifier.fillMaxSize(),
    ) {
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize()) {
            GlassSurface(
                modifier = Modifier
                    .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(vertical = AwadyDimens.SafeVertical, horizontal = 24.dp)
                    .width(width)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(AwadyDimens.RadiusXL),
                fillOverride = Color(0x40141420),
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp), content = content)
            }
        }
    }
}

/** Centred modal glass card with a dimmed scrim. */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(modifier = modifier.widthIn(min = 420.dp, max = 640.dp), shape = RoundedCornerShape(AwadyDimens.RadiusXL), fillOverride = Color(0x33202030)) {
            Column(Modifier.padding(32.dp)) {
                Text(title, style = AwadyType.Title, color = AwadyColors.TextPrimary, modifier = Modifier.padding(bottom = 16.dp))
                content()
            }
        }
    }
}

/** Screen container applying the overscan-safe area. */
@Composable
fun SafeArea(modifier: Modifier = Modifier, top: Dp = AwadyDimens.SafeVertical, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .padding(start = AwadyDimens.SafeHorizontal, end = AwadyDimens.SafeHorizontal, top = top, bottom = AwadyDimens.SafeVertical),
        content = content,
    )
}
