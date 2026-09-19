package tv.hdao.app.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import tv.hdao.app.data.Episode
import tv.hdao.app.data.HdaoRepository
import tv.hdao.app.data.NetworkClients
import tv.hdao.app.data.MEDIA_REFERER
import tv.hdao.app.data.MEDIA_USER_AGENT
import tv.hdao.app.data.VodDetail
import tv.hdao.app.data.WatchEntry
import tv.hdao.app.data.WatchProgress
import tv.hdao.app.data.playbackUrl
import java.util.Locale

@Composable
fun PlayerScreen(
    vodId: Int,
    initialEpisodeIndex: Int,
    repository: HdaoRepository,
    watchProgress: WatchProgress,
    onBack: () -> Unit,
) {
    var detail by remember(vodId) { mutableStateOf<VodDetail?>(null) }
    var error by remember(vodId) { mutableStateOf<String?>(null) }
    var retry by remember(vodId) { mutableIntStateOf(0) }
    LaunchedEffect(vodId, retry) {
        try { detail = repository.detail(vodId) }
        catch (exception: Exception) { error = exception.message ?: "播放信息加载失败" }
    }
    when {
        error != null -> ErrorView(error!!) { error = null; retry++ }
        detail == null -> LoadingView("正在进入播放器…")
        detail!!.episodes.isEmpty() -> ErrorView("该内容暂无可播放剧集", retry = onBack)
        else -> NativePlayer(detail!!, initialEpisodeIndex, watchProgress, onBack)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NativePlayer(
    detail: VodDetail,
    initialEpisodeIndex: Int,
    watchProgress: WatchProgress,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val episodes = detail.episodes
    var episodeIndex by remember { mutableIntStateOf(initialEpisodeIndex.coerceIn(episodes.indices)) }
    val episode = episodes[episodeIndex]
    val player = remember {
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkClients.httpClient)
            .setUserAgent(MEDIA_USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to MEDIA_REFERER))
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply { playWhenReady = true }
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var controlsTick by remember { mutableIntStateOf(1) }
    var controlsVisible by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    fun showControls() {
        controlsTick++
        controlsVisible = true
    }

    fun saveProgress(
        targetEpisode: Episode,
        targetIndex: Int,
        targetPosition: Long,
        targetDuration: Long,
    ) {
        watchProgress.save(
            WatchEntry(
                vodId = detail.item.vodId,
                episodeId = targetEpisode.id,
                episodeIndex = targetIndex,
                title = detail.item.title,
                episodeName = targetEpisode.name,
                imageUrl = detail.item.backdropUrl ?: detail.item.coverUrl,
                positionMs = targetPosition.coerceAtLeast(0L),
                durationMs = targetDuration.coerceAtLeast(0L),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun playEpisode(nextIndex: Int) {
        saveProgress(episode, episodeIndex, player.currentPosition, player.duration)
        episodeIndex = nextIndex.coerceIn(episodes.indices)
    }

    val activeEpisode by rememberUpdatedState(episode)
    val activeEpisodeIndex by rememberUpdatedState(episodeIndex)
    val activeDuration by rememberUpdatedState(duration)
    val switchEpisode by rememberUpdatedState<(Int) -> Unit>({ playEpisode(it) })

    LaunchedEffect(episode.id) {
        playbackError = null
        buffering = true
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(playbackUrl(episode.originalUrl))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        )
        player.prepare()
        val saved = watchProgress.get(detail.item.vodId, episode.id)
        if (saved > 10_000L) player.seekTo(saved)
        player.playWhenReady = true
        showControls()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && activeEpisodeIndex < episodes.lastIndex) {
                    switchEpisode(activeEpisodeIndex + 1)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress(activeEpisode, activeEpisodeIndex, player.currentPosition, activeDuration)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, episode.id) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            if (position > 0L) saveProgress(episode, episodeIndex, position, duration)
            delay(5_000L)
        }
    }

    // Keyed on isPlaying as well as controlsTick. The previous version waited a
    // fixed 4.5s from the last interaction and only hid the controls if playback
    // had started by then, so any source that needed longer to start playing
    // (anime episodes are the reported case) left the progress bar on screen
    // forever. Now the countdown starts when playback actually starts, and it
    // never hides while the player is paused or buffering.
    LaunchedEffect(controlsTick, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        delay(4_500L)
        controlsVisible = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BackHandler(onBack = onBack)

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        showControls(); true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); showControls(); true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        player.seekTo(player.currentPosition + 10_000L); showControls(); true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (episodeIndex < episodes.lastIndex) playEpisode(episodeIndex + 1)
                        showControls(); true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (episodeIndex > 0) playEpisode(episodeIndex - 1)
                        showControls(); true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; this.player = player } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Gold)

        if (controlsVisible) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEF000000))))
                    .padding(horizontal = 58.dp, vertical = 30.dp),
            ) {
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.width(32.dp).height(32.dp),
                        )
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text(detail.item.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("${episode.name}  ·  左右快退/快进  ·  上下切换集数", color = Color(0xFFCED2D9), fontSize = 14.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(15.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF50545D))) {
                        val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(Gold))
                    }
                }
            }
        }

        playbackError?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("播放失败", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(it, color = Color(0xFFBFC3CC), modifier = Modifier.padding(top = 8.dp))
                Text("按返回键退出后重试", color = Gold, modifier = Modifier.padding(top = 15.dp))
            }
        }

    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    else String.format(Locale.US, "%02d:%02d", minutes, remainder)
}
