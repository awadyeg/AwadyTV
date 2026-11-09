package com.awadytv.core.security

import com.awadytv.core.database.CategoryDao
import com.awadytv.core.database.CategoryKind
import com.awadytv.domain.AdultContent
import javax.inject.Inject
import javax.inject.Singleton

/** Category ids that are adult (per kind), used to hide adult titles from Home and Search while locked. */
@Singleton
class AdultFilter @Inject constructor(private val categories: CategoryDao, private val parental: ParentalGate) {
    suspend fun blockedCategoryIds(profileId: Long, kind: String): Set<String> {
        if (!parental.locked()) return emptySet()
        return categories.all(profileId, kind).filter { AdultContent.isAdult(it.name) }.map { it.id }.toSet()
    }

    suspend fun blockedLive(profileId: Long) = blockedCategoryIds(profileId, CategoryKind.LIVE)
    suspend fun blockedMovies(profileId: Long) = blockedCategoryIds(profileId, CategoryKind.MOVIE)
    suspend fun blockedSeries(profileId: Long) = blockedCategoryIds(profileId, CategoryKind.SERIES)
}
