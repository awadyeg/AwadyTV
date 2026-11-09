package com.awadytv.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.GlassButton
import com.awadytv.core.designsystem.GlassDialog
import com.awadytv.core.designsystem.GlassListRow
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.GlassTextField
import com.awadytv.core.designsystem.GlassToastHost
import com.awadytv.core.designsystem.GlassVariant
import com.awadytv.core.designsystem.PinDialog
import com.awadytv.core.designsystem.RailItem
import com.awadytv.core.designsystem.rememberToastState
import com.awadytv.core.datastore.DecoderMode
import com.awadytv.core.datastore.PreferredFormat
import com.awadytv.core.util.TimeFormat
import com.awadytv.navigation.AppNavigator
import kotlinx.coroutines.delay

private enum class Section(val title: String) {
    Profiles("Profiles"), Playback("Playback"), Guide("TV guide"), Parental("Parental controls"),
    Ai("AI recommendations"), Performance("Performance"), Storage("Storage"), About("About"),
}

private sealed interface SettingsDialog {
    data object NewPin : SettingsDialog
    data class ConfirmPin(val first: String) : SettingsDialog
    data object RemovePin : SettingsDialog
    data object ClaudeKey : SettingsDialog
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(navigator: AppNavigator, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    var section by rememberSaveable { mutableStateOf(Section.Profiles) }
    var dwell by remember { mutableStateOf<Section?>(null) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LaunchedEffect(dwell) {
        val s = dwell ?: return@LaunchedEffect
        delay(250)
        section = s
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> toast.show(event.text)
                is SettingsEvent.Resync -> navigator.resync(event.profileId)
                SettingsEvent.SignedOut -> navigator.signedOut()
                SettingsEvent.Restart -> navigator.restartHome()
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = AwadyDimens.SafeHorizontal, end = AwadyDimens.SafeHorizontal, top = AwadyDimens.ContentTop, bottom = AwadyDimens.SafeVertical),
    ) {
        GlassSurface(Modifier.width(AwadyDimens.SideRailWidth - 40.dp).fillMaxHeight(), shape = RoundedCornerShape(AwadyDimens.RadiusXL)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("SETTINGS", style = AwadyType.ShelfHeader, color = AwadyColors.TextSecondary, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
                LazyColumn(Modifier.fillMaxSize().focusRestorer(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(4.dp)) {
                    items(Section.entries, key = { it.name }) { s ->
                        RailItem(title = s.title, selected = s == section, onClick = { section = s }, onFocus = { dwell = s })
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(section.title, style = AwadyType.Title, color = AwadyColors.TextPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            LazyColumn(
                Modifier.fillMaxSize().focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                when (section) {
                    Section.Profiles -> {
                        val active = state.active
                        if (active != null) {
                            item {
                                InfoPanel(
                                    active.name,
                                    listOfNotNull(
                                        active.serverUrl.substringAfter("://"),
                                        active.status?.let { "Status: $it" },
                                        active.expDate?.takeIf { it > 0 }?.let { "Expires ${TimeFormat.date(it)}" },
                                        active.maxConnections?.let { "Max connections: $it" },
                                        active.lastSyncAt?.let { "Catalog synced ${TimeFormat.date(it / 1000)}" },
                                    ).joinToString("  ·  "),
                                )
                            }
                            item { SettingRow("Refresh catalog now", "Downloads categories, channels, movies and series again", onClick = viewModel::refreshCatalog) }
                        }
                        items(state.profiles.filter { it.id != active?.id }, key = { it.id }) { p ->
                            SettingRow("Switch to ${p.name}", p.serverUrl.substringAfter("://"), value = "Switch", onClick = { viewModel.switchProfile(p) })
                        }
                        item { SettingRow("Add profile", "Sign in to another Xtream Codes account", onClick = navigator::addProfile) }
                        items(state.profiles, key = { "rm${it.id}" }) { p ->
                            SettingRow("Remove ${p.name}", "Deletes its saved password, favourites, likes and history from this TV", onClick = { viewModel.removeProfile(p) })
                        }
                        if (active != null) item { SettingRow("Sign out", "Keeps saved profiles; returns to the sign-in screen", onClick = viewModel::signOut) }
                    }
                    Section.Playback -> {
                        item {
                            SettingRow(
                                "Decoder", "Software decoding helps with streams this TV's hardware decoder rejects",
                                value = if (state.settings.decoder == DecoderMode.HARDWARE) "Hardware" else "Software", onClick = viewModel::cycleDecoder,
                            )
                        }
                        item {
                            SettingRow(
                                "Live stream format", "Auto uses HLS when your account allows it",
                                value = when (state.settings.preferredFormat) { PreferredFormat.AUTO -> "Auto"; PreferredFormat.M3U8 -> "HLS (m3u8)"; PreferredFormat.TS -> "MPEG-TS (ts)" },
                                onClick = viewModel::cycleFormat,
                            )
                        }
                        item { SettingRow("Buffer", "Larger buffers ride out slow providers but start slower", value = "${state.settings.bufferSeconds} s", onClick = viewModel::cycleBuffer) }
                        item {
                            SettingRow(
                                "Subtitle size", null,
                                value = when { state.settings.subtitleScale < 0.9f -> "Small"; state.settings.subtitleScale < 1.2f -> "Medium"; state.settings.subtitleScale < 1.5f -> "Large"; else -> "Extra large" },
                                onClick = viewModel::cycleSubtitleSize,
                            )
                        }
                        item { ToggleRow("Subtitle background", "Dark box behind subtitles for legibility", state.settings.subtitleBackground, viewModel::toggleSubtitleBackground) }
                        item { ToggleRow("Autoplay next episode", "Starts the next episode after a 10 second countdown", state.settings.autoplayNextEpisode, viewModel::toggleAutoplay) }
                    }
                    Section.Guide -> {
                        item { SettingRow("Refresh interval", "How often the full guide is downloaded in the background", value = "Every ${state.settings.epgRefreshHours} h", onClick = viewModel::cycleEpgHours) }
                        item { SettingRow("Refresh guide now", "Downloads the XMLTV guide from your provider", onClick = viewModel::refreshEpgNow) }
                        item { SettingRow("Clear guide cache", null, onClick = viewModel::clearGuide) }
                    }
                    Section.Parental -> {
                        if (state.pinSet) {
                            item { SettingRow("Lock adult categories now", "They stay unlocked for this session after the PIN is entered", onClick = viewModel::lockNow) }
                            item { SettingRow("Change PIN", null, onClick = { dialog = SettingsDialog.NewPin }) }
                            item { SettingRow("Remove PIN", "Adult categories will be visible without a PIN", onClick = { dialog = SettingsDialog.RemovePin }) }
                        } else {
                            item { SettingRow("Set parental PIN", "Locks adult categories (XXX, 18+, Adults…) behind a 4-digit PIN", onClick = { dialog = SettingsDialog.NewPin }) }
                        }
                    }
                    Section.Ai -> {
                        item {
                            ToggleRow(
                                "AI recommendations",
                                "On-device picks from the movies and series you like. Nothing leaves this TV.",
                                state.settings.aiRecommendations,
                                viewModel::toggleAi,
                            )
                        }
                        item { SettingRow("Recompute now", "Recommendations also refresh every 6 hours and after each like", onClick = viewModel::recomputeRecommendations) }
                        item {
                            ToggleRow(
                                "Use Claude for smarter picks",
                                "Optional. Sends only liked titles (title, year, genre) and candidate titles to Anthropic with your own API key. No credentials, plots or server URL.",
                                state.settings.claudeEnabled,
                                viewModel::toggleClaude,
                            )
                        }
                        item {
                            SettingRow(
                                "Claude API key",
                                if (state.claudeKeySet) "Stored encrypted on this TV" else "Not set. The app works fully without it.",
                                value = if (state.claudeKeySet) "Change" else "Add",
                                onClick = { dialog = SettingsDialog.ClaudeKey },
                            )
                        }
                    }
                    Section.Performance -> {
                        item {
                            ToggleRow(
                                "Low-end mode",
                                "Turns off runtime blur and parallax and shortens shelves. Automatic on low-RAM TV boxes and Android versions before 12.",
                                state.settings.forceLowEnd,
                                viewModel::toggleLowEnd,
                            )
                        }
                    }
                    Section.Storage -> {
                        item { SettingRow("Clear image cache", "Posters and logos are downloaded again as needed", onClick = viewModel::clearImageCache) }
                        item { SettingRow("Clear guide cache", null, onClick = viewModel::clearGuide) }
                        if (!state.secretsPersistent) {
                            item { InfoPanel("Secure storage unavailable", "This TV's keystore is not working, so passwords are kept in memory only and you will need to sign in again after a restart.") }
                        }
                    }
                    Section.About -> {
                        item { InfoPanel("AwadyTV ${state.version}", "An Xtream Codes player for Android TV with a Liquid Glass interface.") }
                        item { InfoPanel("Privacy", "No telemetry, no analytics. Your Xtream password and optional API key are stored only in encrypted storage on this TV.") }
                        item { InfoPanel("Licences", "Inter typeface © The Inter Project Authors, SIL Open Font License 1.1. Media3, Jetpack Compose, Coil, OkHttp and Retrofit under the Apache License 2.0.") }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GlassToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = AwadyDimens.SafeVertical + 8.dp))
    }

    when (val d = dialog) {
        SettingsDialog.NewPin -> PinDialog(title = "New PIN", message = "Choose a 4-digit PIN", onDismiss = { dialog = null }, onSubmit = { pin ->
            dialog = SettingsDialog.ConfirmPin(pin)
            true
        })
        is SettingsDialog.ConfirmPin -> PinDialog(title = "Confirm PIN", message = "Enter the same PIN again", onDismiss = { dialog = null }, onSubmit = { pin ->
            if (pin == d.first) {
                viewModel.setPin(pin)
                dialog = null
                true
            } else {
                false
            }
        })
        SettingsDialog.RemovePin -> PinDialog(title = "Remove PIN", message = "Enter your current PIN", onDismiss = { dialog = null }, onSubmit = { pin ->
            viewModel.removePin(pin).also { if (it) dialog = null }
        })
        SettingsDialog.ClaudeKey -> ApiKeyDialog(onDismiss = { dialog = null }, onSave = { key ->
            viewModel.saveClaudeKey(key)
            dialog = null
        })
        null -> Unit
    }
}

@Composable
private fun SettingRow(title: String, description: String?, value: String? = null, onClick: () -> Unit) {
    GlassListRow(onClick = onClick, modifier = Modifier.fillMaxWidth()) { focused ->
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, style = AwadyType.Label, color = if (focused) AwadyColors.TextOnFocus else AwadyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description != null) {
                Text(description, style = AwadyType.Caption, color = if (focused) AwadyColors.TextOnFocus.copy(alpha = 0.7f) else AwadyColors.TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(16.dp))
            Text(value, style = AwadyType.Label, color = if (focused) AwadyColors.TextOnFocus else AwadyColors.TextSecondary)
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String?, checked: Boolean, onToggle: () -> Unit) {
    GlassListRow(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { focused ->
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, style = AwadyType.Label, color = if (focused) AwadyColors.TextOnFocus else AwadyColors.TextPrimary)
            if (description != null) {
                Text(description, style = AwadyType.Caption, color = if (focused) AwadyColors.TextOnFocus.copy(alpha = 0.7f) else AwadyColors.TextTertiary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(16.dp))
        GlassSwitch(checked, focused)
    }
}

@Composable
private fun GlassSwitch(checked: Boolean, focused: Boolean) {
    val track by animateColorAsState(
        when {
            checked -> AwadyColors.Success
            focused -> Color(0x33000000)
            else -> Color(0x33FFFFFF)
        },
        label = "switchTrack",
    )
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switchThumb")
    Box(Modifier.size(width = 50.dp, height = 30.dp).clip(RoundedCornerShape(50)).background(track), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset { IntOffset(offset.roundToPx(), 0) }.size(26.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun InfoPanel(title: String, body: String) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(AwadyDimens.RadiusMedium), elevation = 0.dp, variant = GlassVariant.Clear) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = AwadyType.Headline, color = AwadyColors.TextPrimary)
            Text(body, style = AwadyType.Caption, color = AwadyColors.TextSecondary)
        }
    }
}

@Composable
private fun ApiKeyDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    GlassDialog(title = "Claude API key", onDismiss = onDismiss) {
        Text("Stored only in encrypted storage on this TV and never logged. Leave empty and save to remove it.", style = AwadyType.Caption, color = AwadyColors.TextSecondary)
        Spacer(Modifier.height(16.dp))
        GlassTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = "API key",
            placeholder = "sk-ant-…",
            isPassword = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { onSave(key) },
            leadingIcon = Icons.Rounded.Key,
            focusRequester = focus,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassButton("Save", onClick = { onSave(key) }, variant = GlassVariant.Tinted)
            GlassButton("Cancel", onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
