package com.awadytv.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/**
 * Focusable list row for rails and lists. Focused rows invert to white glass (tvOS); the selected row
 * keeps a brighter glass fill so the current choice stays visible when focus moves away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val currentOnFocus by rememberUpdatedState(onFocus)
    LaunchedEffect(focused) { if (focused) currentOnFocus?.invoke() }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(AwadyMotion.FOCUS_MS), label = "rowScale")
    val fill by animateColorAsState(
        when {
            focused -> AwadyColors.GlassFocused
            selected -> Color(0x33FFFFFF)
            else -> Color.Transparent
        },
        tween(150),
        label = "rowFill",
    )
    val shape = RoundedCornerShape(AwadyDimens.RadiusCard)
    Row(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.shape = shape
                clip = true
            }
            .background(fill)
            .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(focused)
    }
}

/** Simple category rail entry. */
@Composable
fun RailItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    locked: Boolean = false,
    onFocus: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
    moving: Boolean = false,
) {
    GlassListRow(onClick = onClick, selected = selected || moving, onFocus = onFocus, onLongClick = onLongClick, focusRequester = focusRequester, modifier = modifier.fillMaxWidth()) { focused ->
        val color = if (focused) AwadyColors.TextOnFocus else if (selected) AwadyColors.TextPrimary else AwadyColors.TextSecondary
        if (moving) {
            Icon(Icons.Rounded.SwapVert, contentDescription = "Moving", tint = if (focused) AwadyColors.Accent else AwadyColors.AccentSoft, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = AwadyType.Label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (locked) Icon(Icons.Rounded.Lock, contentDescription = "Locked", tint = color, modifier = Modifier.size(18.dp))
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), style = AwadyType.Caption, color = color.copy(alpha = 0.7f))
        }
    }
}

/** D-pad friendly 4-digit PIN entry (number pad of glass keys). */
@Composable
fun PinDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Boolean,
    message: String = "Enter your 4-digit PIN",
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    GlassDialog(title = title, onDismiss = onDismiss) {
        Text(if (error) "Wrong PIN, try again" else message, style = AwadyType.Body, color = if (error) AwadyColors.Danger else AwadyColors.TextSecondary)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) AwadyColors.TextPrimary else Color(0x33FFFFFF)),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "" -> Spacer(Modifier.size(64.dp))
                            "⌫" -> GlassIconButton(
                                icon = Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = "Delete",
                                onClick = { pin = pin.dropLast(1); error = false },
                                modifier = Modifier.size(64.dp),
                            )
                            else -> GlassButton(
                                text = key,
                                onClick = {
                                    if (pin.length < 4) pin += key
                                    error = false
                                    if (pin.length == 4) {
                                        if (!onSubmit(pin)) {
                                            error = true
                                            pin = ""
                                        }
                                    }
                                },
                                modifier = Modifier.width(64.dp),
                                focusRequester = if (key == "1") first else null,
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
