package tv.hdao.app.ui

import android.view.LayoutInflater
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import tv.hdao.app.R
import tv.hdao.app.data.NetworkClients
import tv.hdao.app.data.MEDIA_REFERER
import tv.hdao.app.data.MEDIA_USER_AGENT
import tv.hdao.app.data.playbackUrl
import tv.hdao.app.data.previewStartPositionMs

@OptIn(UnstableApi::class)
@Composable
internal fun HomePreviewPlayer(
    previewUrl: String?,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    val latestIsActive by rememberUpdatedState(isActive)
    var firstFrameRendered by remember(previewUrl) { mutableStateOf(false) }
    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        animationSpec = tween(600),
        label = "homePreviewAlpha",
    )

    DisposableEffect(context, lifecycleOwner, previewUrl) {
        if (previewUrl.isNullOrBlank()) {
            activePlayer = null
            onDispose { }
        } else {
            val dataSourceFactory = OkHttpDataSource.Factory(NetworkClients.httpClient)
                .setUserAgent(MEDIA_USER_AGENT)
                .setDefaultRequestProperties(mapOf("Referer" to MEDIA_REFERER))
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_500, 8_000, 1_000, 1_500)
                .build()
            val player = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
                )
                .build()
                .apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(playbackUrl(previewUrl))
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build()
                    )
                }
            var previewSeekApplied = false
            fun preparePreviewStart(): Boolean {
                if (previewSeekApplied) return true
                if (player.isCurrentMediaItemLive) {
                    previewSeekApplied = true
                    return true
                }
                val duration = player.duration
                if (duration <= 0L) return false
                val startPosition = previewStartPositionMs(duration)
                if (startPosition > 0L) player.seekTo(startPosition)
                previewSeekApplied = true
                return true
            }
            fun playWhenPreviewIsReady() {
                if (
                    player.playbackState == Player.STATE_READY &&
                    preparePreviewStart() &&
                    latestIsActive &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    player.play()
                }
            }
            val playerListener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_READY) return
                    playWhenPreviewIsReady()
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    playWhenPreviewIsReady()
                }

                override fun onRenderedFirstFrame() {
                    firstFrameRendered = true
                }
            }
            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                        playWhenPreviewIsReady()
                    }
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> player.pause()
                    else -> Unit
                }
            }

            player.addListener(playerListener)
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            activePlayer = player
            player.prepare()

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                player.removeListener(playerListener)
                player.release()
                if (activePlayer === player) activePlayer = null
            }
        }
    }

    DisposableEffect(activePlayer, isActive, lifecycleOwner) {
        val player = activePlayer
        if (player != null) {
            if (
                isActive &&
                player.playbackState == Player.STATE_READY &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                val durationKnown = player.isCurrentMediaItemLive || player.duration > 0L
                if (durationKnown) {
                    val startPosition = previewStartPositionMs(player.duration)
                    if (!player.isCurrentMediaItemLive && player.currentPosition < 1_000L && startPosition > 0L) {
                        player.seekTo(startPosition)
                    }
                    player.play()
                } else {
                    player.pause()
                }
            } else {
                player.pause()
            }
        }
        onDispose { }
    }

    AndroidView(
        factory = { viewContext ->
            LayoutInflater.from(viewContext).inflate(
                R.layout.home_preview_player,
                null,
                false,
            ) as PlayerView
        },
        update = { view ->
            view.player = activePlayer
            view.alpha = videoAlpha
            view.visibility = if (activePlayer == null) View.INVISIBLE else View.VISIBLE
        },
        modifier = modifier,
    )
}
