package com.awadytv.data.repository

import android.content.Context
import com.awadytv.core.database.AppDatabase
import com.awadytv.core.database.ProfileDao
import com.awadytv.core.database.ProfileEntity
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.core.network.ErrorMapping
import com.awadytv.core.network.XtreamCredentials
import com.awadytv.core.network.XtreamUrls
import com.awadytv.core.security.SecretStore
import com.awadytv.core.util.AppError
import com.awadytv.core.util.AppResult
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.xtream.AuthResponseDto
import com.awadytv.data.xtream.XtreamClient
import com.awadytv.domain.Profile
import com.awadytv.domain.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ProfileDao,
    private val db: AppDatabase,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
    private val client: XtreamClient,
) {
    val profiles: Flow<List<Profile>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProfile: Flow<Profile?> = settings.activeProfileId.flatMapLatest { id ->
        if (id == null) flowOf(null) else dao.observe(id).map { it?.toDomain() }
    }.distinctUntilChanged()

    /** The active session (profile + decrypted credentials), re-emitted when the active profile changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val session: Flow<Session?> = settings.activeProfileId.flatMapLatest { id ->
        if (id == null) flowOf(null) else dao.observe(id).map { entity -> entity?.let { sessionOf(it) } }
    }.distinctUntilChanged { a, b -> a?.profileId == b?.profileId && a?.profile?.lastSyncAt == b?.profile?.lastSyncAt }

    suspend fun currentSession(): Session? {
        val id = settings.activeProfileId.first() ?: return null
        return dao.get(id)?.let { sessionOf(it) }
    }

    suspend fun sessionFor(profileId: Long): Session? = dao.get(profileId)?.let { sessionOf(it) }

    private fun sessionOf(entity: ProfileEntity): Session? {
        val password = secrets.get(secrets.passwordKey(entity.passwordRef)) ?: return null
        return Session(entity.toDomain(), XtreamCredentials(entity.serverUrl, entity.username, password))
    }

    /**
     * Xtream "API login": normalise the URL, authenticate, validate account status, then persist the profile
     * (password into encrypted storage) and make it active.
     */
    suspend fun login(serverInput: String, username: String, password: String, name: String? = null): AppResult<Long> {
        val server = XtreamUrls.normalizeServer(serverInput) ?: return AppResult.Failure(AppError.InvalidUrl)
        if (username.isBlank() || password.isEmpty()) return AppResult.Failure(AppError.WrongCredentials)
        val credentials = XtreamCredentials(server, username.trim(), password)
        val auth = try {
            client.authenticate(credentials)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return AppResult.Failure(ErrorMapping.map(e, context))
        }
        validate(auth)?.let { return AppResult.Failure(it) }

        val now = System.currentTimeMillis()
        val user = auth.userInfo!!
        val existing = dao.find(server, credentials.username)
        val ref = existing?.passwordRef ?: UUID.randomUUID().toString()
        secrets.put(secrets.passwordKey(ref), password)
        val entity = ProfileEntity(
            id = existing?.id ?: 0,
            name = name?.takeIf { it.isNotBlank() } ?: existing?.name ?: defaultName(server, credentials.username),
            serverUrl = server,
            username = credentials.username,
            passwordRef = ref,
            createdAt = existing?.createdAt ?: now,
            lastUsedAt = now,
            expDate = user.expDate,
            status = user.status,
            maxConnections = user.maxConnections,
            allowedFormats = user.allowedOutputFormats.joinToString(","),
            lastSyncAt = existing?.lastSyncAt,
        )
        val id = if (existing == null) dao.insert(entity) else entity.id.also { dao.upsert(entity) }
        settings.setActiveProfile(id)
        return AppResult.Success(id)
    }

    suspend fun switchTo(profileId: Long) {
        dao.touch(profileId, System.currentTimeMillis())
        settings.setActiveProfile(profileId)
    }

    suspend fun delete(profileId: Long) {
        val entity = dao.get(profileId) ?: return
        secrets.put(secrets.passwordKey(entity.passwordRef), null)
        db.channels().clear(profileId)
        db.movies().clear(profileId)
        db.series().clear(profileId)
        db.episodes().clear(profileId)
        db.epg().clear(profileId)
        db.favourites().clear(profileId)
        db.likes().clear(profileId)
        db.progress().clear(profileId)
        db.recommendations().clear(profileId)
        db.categoryOpens().clear(profileId)
        listOf("live", "movie", "series").forEach { db.categories().clear(profileId, it) }
        dao.delete(profileId)
        if (settings.activeProfileId.first() == profileId) {
            settings.setActiveProfile(dao.all().firstOrNull()?.id)
        }
    }

    suspend fun markSynced(profileId: Long) = dao.setLastSync(profileId, System.currentTimeMillis())

    private fun defaultName(server: String, username: String): String {
        val host = server.substringAfter("://").substringBefore('/').substringBefore(':')
        return "$username @ $host"
    }

    companion object {
        fun validate(auth: AuthResponseDto, nowMs: Long = System.currentTimeMillis()): AppError? {
            val user = auth.userInfo ?: return AppError.WrongCredentials
            if (user.auth != null && user.auth == 0) return AppError.WrongCredentials
            when (user.status?.trim()?.lowercase()) {
                null, "", "active" -> Unit
                "expired" -> return AppError.Expired(user.expDate?.let { TimeFormat.date(it) })
                "banned" -> return AppError.Banned
                "disabled" -> return AppError.Disabled
                else -> Unit
            }
            val exp = user.expDate
            if (exp != null && exp > 0 && exp * 1000 < nowMs) return AppError.Expired(TimeFormat.date(exp))
            if (user.auth == null && user.status == null) return AppError.WrongCredentials
            return null
        }
    }
}

fun ProfileEntity.toDomain() = Profile(
    id = id,
    name = name,
    serverUrl = serverUrl,
    username = username,
    expDate = expDate,
    status = status,
    maxConnections = maxConnections,
    allowedFormats = allowedFormats.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    lastSyncAt = lastSyncAt,
    lastUsedAt = lastUsedAt,
)
