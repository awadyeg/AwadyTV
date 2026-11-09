package com.awadytv.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

private val PillShape = RoundedCornerShape(percent = 50)

/** tvOS glass button: translucent pill at rest, near-solid white with dark label when focused. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: GlassVariant = GlassVariant.Regular,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) AwadyMotion.BUTTON_FOCUS_SCALE else 1f, tween(AwadyMotion.FOCUS_MS), label = "btnScale")
    val restFill = when (variant) {
        GlassVariant.Tinted -> AwadyColors.Accent.copy(alpha = 0.6f)
        GlassVariant.Clear -> AwadyColors.GlassClear
        GlassVariant.Regular -> AwadyColors.GlassRegular
    }
    val fill by animateColorAsState(if (focused) AwadyColors.GlassFocused else restFill, tween(150), label = "btnFill")
    val content = if (focused) AwadyColors.TextOnFocus else AwadyColors.TextPrimary
    Row(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
                shape = PillShape
                clip = true
            }
            .background(fill)
            .drawWithContent {
                drawContent()
                drawSpecularStroke(PillShape, strength = if (focused) 0.3f else 1f)
            }
            .clickable(interactionSource = interaction, indication = null) { if (enabled) onClick() }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(10.dp))
        }
        if (text.isNotEmpty()) Text(text, style = AwadyType.Label, color = content, maxLines = 1)
    }
}

/** Round glass icon button (player transport, like toggle). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeTint: Color = AwadyColors.Like,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(AwadyMotion.FOCUS_MS), label = "iconBtnScale")
    val fill by animateColorAsState(if (focused) AwadyColors.GlassFocused else AwadyColors.GlassRegular, tween(150), label = "iconBtnFill")
    val tint = when {
        active -> activeTint
        focused -> AwadyColors.TextOnFocus
        else -> AwadyColors.TextPrimary
    }
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = CircleShape
                clip = true
            }
            .background(fill)
            .drawWithContent {
                drawContent()
                drawSpecularStroke(CircleShape)
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** Glass pill chip (season picker, filters). Selected chips are a brighter glass. */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    if (focused && onFocus != null) androidx.compose.runtime.LaunchedEffect(Unit) { onFocus() }
    val scale by animateFloatAsState(if (focused) AwadyMotion.BUTTON_FOCUS_SCALE else 1f, tween(AwadyMotion.FOCUS_MS), label = "chipScale")
    val fill = when {
        focused -> AwadyColors.GlassFocused
        selected -> Color(0x47FFFFFF)
        else -> AwadyColors.GlassClear
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(PillShape)
            .background(fill)
            .border(1.dp, if (selected && !focused) Color(0x66FFFFFF) else Color(0x1FFFFFFF), PillShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text, style = AwadyType.Label, color = if (focused) AwadyColors.TextOnFocus else AwadyColors.TextPrimary, maxLines = 1)
    }
}

/**
 * Glass text field. D-pad Up/Down always leave the field (no focus trap); Select opens the IME.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(AwadyDimens.RadiusSmall + 2.dp)
    val borderColor by animateColorAsState(if (focused) AwadyColors.FocusBorder else Color(0x26FFFFFF), tween(150), label = "fieldBorder")
    val fill by animateColorAsState(if (focused) Color(0x33FFFFFF) else Color(0x1AFFFFFF), tween(150), label = "fieldFill")

    androidx.compose.foundation.layout.Column(modifier) {
        Text(label.uppercase(), style = AwadyType.Caption, color = AwadyColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(fill)
                .border(if (focused) 2.dp else 1.dp, borderColor, shape)
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = AwadyColors.TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = AwadyType.Body.copy(color = AwadyColors.TextPrimary),
                    cursorBrush = SolidColor(AwadyColors.TextPrimary),
                    interactionSource = interaction,
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(
                        onAny = {
                            if (onImeAction != null) onImeAction() else focusManager.moveFocus(FocusDirection.Down)
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    keyboard?.show()
                                    true
                                }
                                else -> false
                            }
                        },
                )
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = AwadyType.Body, color = AwadyColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}
