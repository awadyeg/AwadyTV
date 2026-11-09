package com.awadytv.feature.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.awadytv.core.datastore.AppSettings
import com.awadytv.core.datastore.DecoderMode
import com.awadytv.core.network.USER_AGENT
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Builds ExoPlayer instances honouring the playback settings (decoder preference, buffer size). */
@OptIn(UnstableApi::class)
@Singleton
class PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
) {
    // Streams get their own read timeout; the shared client's interceptors never log stream URLs.
    private val streamClient = okHttpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()

    fun create(settings: AppSettings): ExoPlayer {
        val httpFactory = OkHttpDataSource.Factory(streamClient).setUserAgent(USER_AGENT)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .apply { if (settings.decoder == DecoderMode.SOFTWARE) setMediaCodecSelector(softwareFirst) }
        val bufferMs = settings.bufferSeconds.coerceIn(10, 120) * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                (bufferMs / 2).coerceAtLeast(DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS),
                bufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setPreferredTextLanguage(null).setSelectUndeterminedTextLanguage(true))
        }
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    /** MediaItem with an explicit MIME type so HLS vs MPEG-TS vs progressive is never guessed wrong. */
    fun mediaItem(url: String, mediaId: String, live: Boolean): MediaItem {
        val path = url.substringBefore('?').lowercase()
        val mime = when {
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            else -> null
        }
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(mediaId)
            .apply { if (mime != null) setMimeType(mime) }
            .apply { if (live) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(8_000).build()) }
            .build()
    }

    private val softwareFirst = MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
        MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling).sortedByDescending { it.softwareOnly }
    }
}
