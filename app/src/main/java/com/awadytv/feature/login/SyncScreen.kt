package com.awadytv.feature.login

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.GlassButton
import com.awadytv.core.designsystem.GlassSpinner
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.GlassVariant
import com.awadytv.core.designsystem.ProgressLine
import com.awadytv.data.repository.CatalogRepository
import com.awadytv.data.repository.SyncProgress
import com.awadytv.navigation.SyncRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val profileId = savedStateHandle.toRoute<SyncRoute>().profileId
    private val _progress = MutableStateFlow(SyncProgress(0, CatalogRepository.TOTAL, "Connecting"))
    val progress: StateFlow<SyncProgress> = _progress
    private var job: Job? = null

    init { start() }

    fun start() {
        job?.cancel()
        _progress.value = SyncProgress(0, CatalogRepository.TOTAL, "Connecting")
        job = viewModelScope.launch { catalog.sync(profileId).collect { _progress.value = it } }
    }
}

private val steps = listOf("Live TV categories", "Live channels", "Movie categories", "Movies", "Series categories", "Series")

@Composable
fun SyncScreen(onDone: () -> Unit, onBackToLogin: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    LaunchedEffect(progress.done) { if (progress.done) onDone() }
    val retryFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        GradientMesh(Modifier.fillMaxSize())
        GlassSurface(
            modifier = Modifier.align(Alignment.Center).width(560.dp).padding(vertical = AwadyDimens.SafeVertical),
            shape = RoundedCornerShape(AwadyDimens.RadiusXL),
            elevation = 32.dp,
        ) {
            Column(Modifier.padding(horizontal = 40.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val error = progress.error
                Text(if (error == null) "Setting up your library" else error.title, style = AwadyType.Title, color = AwadyColors.TextPrimary)
                if (error != null) {
                    Text(error.message, style = AwadyType.Body, color = AwadyColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GlassButton("Try again", onClick = viewModel::start, icon = Icons.Rounded.Refresh, variant = GlassVariant.Tinted, focusRequester = retryFocus)
                        GlassButton("Back to sign in", onClick = onBackToLogin)
                    }
                    LaunchedEffect(error) { runCatching { retryFocus.requestFocus() } }
                } else {
                    Text("Downloading the catalog once; afterwards it refreshes quietly in the background.", style = AwadyType.Caption, color = AwadyColors.TextSecondary)
                    ProgressLine(progress.step.toFloat() / progress.total, Modifier.fillMaxWidth().padding(vertical = 8.dp), height = 6.dp)
                    steps.forEachIndexed { index, label ->
                        val stepNumber = index + 1
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                stepNumber < progress.step || progress.done -> Icon(Icons.Rounded.CheckCircle, null, tint = AwadyColors.Success, modifier = Modifier.size(22.dp))
                                stepNumber == progress.step -> GlassSpinner(size = 22.dp)
                                else -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = AwadyColors.TextTertiary, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            val countKey = when (label) { "Live channels" -> "Channels"; "Movies" -> "Movies"; "Series" -> "Series"; else -> null }
                            val count = countKey?.let { progress.counts[it] }
                            Text(
                                label + (count?.let { "  ·  $it" } ?: ""),
                                style = AwadyType.Label,
                                color = if (stepNumber <= progress.step) AwadyColors.TextPrimary else AwadyColors.TextTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}
