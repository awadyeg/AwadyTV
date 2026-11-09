package com.awadytv.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.GlassButton
import com.awadytv.core.designsystem.GlassChip
import com.awadytv.core.designsystem.GlassDialog
import com.awadytv.core.designsystem.GlassIconButton
import com.awadytv.core.designsystem.GlassSpinner
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.GlassTextField
import com.awadytv.core.designsystem.GlassVariant
import com.awadytv.core.util.TimeFormat
import com.awadytv.domain.Profile

@Composable
fun LoginScreen(
    onSignedIn: (profileId: Long, needsSync: Boolean) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.SignedIn -> onSignedIn(event.profileId, event.needsSync)
            }
        }
    }
    val serverFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { serverFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        GradientMesh(Modifier.fillMaxSize())
        if (state.profiles.isNotEmpty()) {
            Box(Modifier.align(Alignment.TopEnd).padding(horizontal = AwadyDimens.SafeHorizontal, vertical = AwadyDimens.SafeVertical)) {
                GlassButton(text = "Switch profile", icon = Icons.Rounded.SwitchAccount, onClick = { viewModel.showProfiles(true) })
            }
        }
        GlassSurface(
            modifier = Modifier
                .align(Alignment.Center)
                .width(560.dp)
                .padding(vertical = AwadyDimens.SafeVertical),
            shape = RoundedCornerShape(AwadyDimens.RadiusXL),
            elevation = 32.dp,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 40.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassSurface(Modifier.size(48.dp), shape = CircleShape, variant = GlassVariant.Tinted, elevation = 0.dp) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("AwadyTV", style = AwadyType.Title, color = AwadyColors.TextPrimary)
                        Text("Sign in with Xtream Codes API", style = AwadyType.Caption, color = AwadyColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                GlassTextField(
                    value = state.server,
                    onValueChange = viewModel::onServer,
                    label = "Server URL",
                    placeholder = "http://provider.example:8080",
                    keyboardType = KeyboardType.Uri,
                    leadingIcon = Icons.Rounded.Dns,
                    focusRequester = serverFocus,
                )
                GlassTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsername,
                    label = "Username",
                    leadingIcon = Icons.Rounded.Person,
                )
                GlassTextField(
                    value = state.password,
                    onValueChange = viewModel::onPassword,
                    label = "Password",
                    isPassword = true,
                    passwordVisible = state.passwordVisible,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                    onImeAction = viewModel::signIn,
                    leadingIcon = Icons.Rounded.Lock,
                    trailing = {
                        GlassIconButton(
                            icon = if (state.passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (state.passwordVisible) "Hide password" else "Show password",
                            onClick = viewModel::togglePasswordVisible,
                            modifier = Modifier.size(40.dp),
                        )
                    },
                )
                state.error?.let { error ->
                    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text(error.title, style = AwadyType.Label, color = AwadyColors.Danger)
                        Text(error.message, style = AwadyType.Caption, color = AwadyColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (state.loading) {
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        GlassSpinner(size = 28.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Signing in…", style = AwadyType.Label, color = AwadyColors.TextSecondary)
                    }
                } else {
                    GlassButton(text = "Sign in", onClick = viewModel::signIn, variant = GlassVariant.Tinted, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (state.showProfiles) {
            ProfilePickerDialog(
                profiles = state.profiles,
                onPick = viewModel::useProfile,
                onDelete = viewModel::deleteProfile,
                onDismiss = { viewModel.showProfiles(false) },
            )
        }
    }
}

@Composable
fun ProfilePickerDialog(
    profiles: List<Profile>,
    onPick: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onDismiss: () -> Unit,
    activeId: Long? = null,
) {
    val first = remember { FocusRequester() }
    GlassDialog(title = "Profiles", onDismiss = onDismiss) {
        if (profiles.isEmpty()) {
            Text("No saved profiles yet.", style = AwadyType.Body, color = AwadyColors.TextSecondary)
        }
        LazyColumn(Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(profiles, key = { it.id }) { profile ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name + if (profile.id == activeId) "  •  active" else "", style = AwadyType.Label, color = AwadyColors.TextPrimary)
                        Text(
                            profile.serverUrl.substringAfter("://") + (profile.expDate?.let { "  ·  expires ${TimeFormat.date(it)}" } ?: ""),
                            style = AwadyType.Caption,
                            color = AwadyColors.TextTertiary,
                        )
                    }
                    GlassChip(text = "Use", selected = profile.id == activeId, onClick = { onPick(profile) })
                    GlassChip(text = "Remove", selected = false, onClick = { onDelete(profile) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassButton(text = "Close", onClick = onDismiss, focusRequester = first)
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

/** Bundled "artwork" for the login screen: a soft gradient mesh in the app's palette. */
@Composable
fun GradientMesh(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(AwadyColors.Canvas)
        val blobs = listOf(
            Triple(Offset(size.width * 0.15f, size.height * 0.2f), Color(0xFF1B3A9B), 0.55f),
            Triple(Offset(size.width * 0.85f, size.height * 0.25f), Color(0xFF5B1E8F), 0.5f),
            Triple(Offset(size.width * 0.6f, size.height * 0.95f), Color(0xFF0E5E6F), 0.55f),
            Triple(Offset(size.width * 0.05f, size.height * 0.95f), Color(0xFF6A1B4D), 0.4f),
        )
        blobs.forEach { (center, color, radius) ->
            drawRect(Brush.radialGradient(listOf(color.copy(alpha = 0.75f), Color.Transparent), center = center, radius = size.width * radius))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, AwadyColors.Canvas.copy(alpha = 0.5f))))
    }
}
