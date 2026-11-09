package com.awadytv.feature.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awadytv.core.security.ParentalGate
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.data.repository.SeriesRepository
import com.awadytv.domain.Category
import com.awadytv.domain.ContentKind
import com.awadytv.feature.common.CatalogBrowseScreen
import com.awadytv.feature.common.CatalogBrowseViewModel
import com.awadytv.domain.PosterItem
import com.awadytv.feature.common.ratingBadge
import com.awadytv.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SeriesBrowseViewModel @Inject constructor(
    profiles: ProfileRepository,
    library: LibraryRepository,
    parental: ParentalGate,
    private val series: SeriesRepository,
) : CatalogBrowseViewModel(profiles, library, parental, ContentKind.SERIES, SeriesRepository.RECENT, "Recently updated") {
    init { start() }

    override fun categoriesFlow(profileId: Long): Flow<List<Category>> = series.categories(profileId)

    override fun itemsFlow(profileId: Long, categoryId: String): Flow<List<PosterItem>> =
        series.series(profileId, categoryId).map { list ->
            list.map { PosterItem(ContentKind.SERIES, it.seriesId, it.name, it.cover, it.backdrop, ratingBadge(it.rating)) }
        }
}

@Composable
fun SeriesScreen(navigator: AppNavigator, viewModel: SeriesBrowseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogBrowseScreen(
        title = "Series",
        state = state,
        onSelectCategory = viewModel::select,
        onOpen = { navigator.openSeries(it.id) },
        onSubmitPin = viewModel::submitPin,
        onDismissPin = viewModel::dismissPin,
        emptyTitle = "No series here",
        onLike = viewModel::toggleLike,
        messages = viewModel.messages,
    )
}
