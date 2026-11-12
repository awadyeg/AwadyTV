package com.awadytv.feature.player

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/**
 * PlayerView-free video output: a SurfaceView in an AspectRatioFrameLayout plus a SubtitleView fed from
 * player cues. All controls are Compose glass overlays drawn on top.
 */
@OptIn(UnstableApi::class)
class VideoSurfaceLayout(context: Context) : FrameLayout(context), Player.Listener {
    private val aspect = AspectRatioFrameLayout(context).apply { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT }
    private val surface = SurfaceView(context)
    private val subtitles = SubtitleView(context)
    private var player: Player? = null

    init {
        setBackgroundColor(Color.BLACK)
        aspect.addView(surface, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(aspect, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER))
        addView(subtitles, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun bind(newPlayer: Player) {
        if (player === newPlayer) return
        unbind()
        player = newPlayer
        newPlayer.setVideoSurfaceView(surface)
        newPlayer.addListener(this)
        onVideoSizeChanged(newPlayer.videoSize)
        updateKeepScreenOn()
    }

    fun unbind() {
        player?.let {
            it.removeListener(this)
            it.clearVideoSurfaceView(surface)
        }
        player = null
        keepScreenOn = false
    }

    /**
     * Keeps the TV awake while video is playing or about to (buffering with play requested). Without it the
     * TV's sleep / screensaver timer kept running mid-film and closed the app. PlayerView does this itself;
     * this custom surface has to. Paused or stopped video lets the TV sleep as usual.
     */
    private fun updateKeepScreenOn() {
        val p = player
        keepScreenOn = p != null && p.playWhenReady &&
            (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) updateKeepScreenOn()
    }

    fun styleSubtitles(scale: Float, background: Boolean) {
        subtitles.setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                if (background) 0x99000000.toInt() else Color.TRANSPARENT,
                Color.TRANSPARENT,
                if (background) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                Color.BLACK,
                null,
            ),
        )
        subtitles.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 26f * scale)
        subtitles.setBottomPaddingFraction(0.08f)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
            aspect.setAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
        }
    }

    override fun onCues(cueGroup: CueGroup) {
        subtitles.setCues(cueGroup.cues)
    }
}

@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier, subtitleScale: Float = 1f, subtitleBackground: Boolean = true) {
    AndroidView(
        factory = { ctx -> VideoSurfaceLayout(ctx) },
        update = { view ->
            view.bind(player)
            view.styleSubtitles(subtitleScale, subtitleBackground)
        },
        onRelease = { it.unbind() },
        modifier = modifier,
    )
}
