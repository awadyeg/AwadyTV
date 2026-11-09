package com.awadytv.data.recommendations

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional second stage that re-orders the local engine's top candidates. Returns null to keep the
 * local order (the app must work fully without it).
 */
interface ReRanker {
    suspend fun rerank(liked: List<RecItem>, candidates: List<ScoredItem>): List<ScoredItem>?
}

/** No re-ranking (kept for tests and as a reference implementation). */
@Singleton
class LocalOnlyReRanker @Inject constructor() : ReRanker {
    override suspend fun rerank(liked: List<RecItem>, candidates: List<ScoredItem>): List<ScoredItem>? = null
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReRankerModule {
    @Binds
    /** Claude re-ranker; it returns null (local order) unless the user enabled it and saved a key. */
    abstract fun bindReRanker(impl: ClaudeRecommender): ReRanker
}
