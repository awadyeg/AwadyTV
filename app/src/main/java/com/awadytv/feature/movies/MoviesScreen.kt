package com.awadytv.feature.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awadytv.core.security.ParentalGate
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.MoviesRepository
import com.awadytv.data.repository.ProfileRepository
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
class MoviesViewModel @Inject constructor(
    profiles: ProfileRepository,
    library: LibraryRepository,
    parental: ParentalGate,
    private val movies: MoviesRepository,
) : CatalogBrowseViewModel(profiles, library, parental, ContentKind.MOVIE, MoviesRepository.RECENT, "Recently added") {
    init { start() }

    override fun categoriesFlow(profileId: Long): Flow<List<Category>> = movies.categories(profileId)

    override fun itemsFlow(profileId: Long, categoryId: String): Flow<List<PosterItem>> =
        movies.movies(profileId, categoryId).map { list ->
            list.map { PosterItem(ContentKind.MOVIE, it.streamId, it.name, it.icon, it.backdrop, ratingBadge(it.rating)) }
        }
}

@Composable
fun MoviesScreen(navigator: AppNavigator, viewModel: MoviesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogBrowseScreen(
        title = "Movies",
        state = state,
        onSelectCategory = viewModel::select,
        onOpen = { navigator.openMovie(it.id) },
        onSubmitPin = viewModel::submitPin,
        onDismissPin = viewModel::dismissPin,
        emptyTitle = "No movies here",
    )
}
