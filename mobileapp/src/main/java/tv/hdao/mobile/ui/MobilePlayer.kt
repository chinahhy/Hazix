package tv.hdao.mobile.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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

@Composable
fun MobilePlayerScreen(
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
        try {
            detail = repository.detail(vodId)
            error = null
        } catch (exception: Exception) {
            error = exception.message ?: "播放信息加载失败"
        }
    }
    when {
        error != null -> MobilePlayerMessage(error!!, "重新加载") { retry++ }
        detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MobileGold)
        }
        detail!!.episodes.isEmpty() -> MobilePlayerMessage("该内容暂无可播放剧集", "返回", onBack)
        else -> MobileNativePlayer(detail!!, initialEpisodeIndex, watchProgress, onBack)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MobileNativePlayer(
    detail: VodDetail,
    initialEpisodeIndex: Int,
    watchProgress: WatchProgress,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val episodes = detail.episodes
    var episodeIndex by remember { mutableIntStateOf(initialEpisodeIndex.coerceIn(episodes.indices)) }
    val episode = episodes[episodeIndex]
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(
                    OkHttpDataSource.Factory(NetworkClients.httpClient)
                        .setUserAgent(MEDIA_USER_AGENT)
                        .setDefaultRequestProperties(mapOf("Referer" to MEDIA_REFERER))
                )
            )
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply { playWhenReady = true }
    }
    var buffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    fun saveProgress(targetEpisode: Episode, targetIndex: Int, position: Long, duration: Long) {
        watchProgress.save(
            WatchEntry(
                vodId = detail.item.vodId,
                episodeId = targetEpisode.id,
                episodeIndex = targetIndex,
                title = detail.item.title,
                episodeName = targetEpisode.name,
                imageUrl = detail.item.backdropUrl ?: detail.item.coverUrl,
                positionMs = position.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(0L),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun switchEpisode(next: Int) {
        saveProgress(episode, episodeIndex, player.currentPosition, player.duration)
        episodeIndex = next.coerceIn(episodes.indices)
    }

    val activeEpisode by rememberUpdatedState(episode)
    val activeIndex by rememberUpdatedState(episodeIndex)
    val switchTo by rememberUpdatedState<(Int) -> Unit>({ switchEpisode(it) })

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

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
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && activeIndex < episodes.lastIndex) switchTo(activeIndex + 1)
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress(activeEpisode, activeIndex, player.currentPosition, player.duration)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(episode.id) {
        while (true) {
            if (player.currentPosition > 0L) {
                saveProgress(episode, episodeIndex, player.currentPosition, player.duration)
            }
            delay(5_000L)
        }
    }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = true; this.player = player } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).background(Color(0x66000000), RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
            }
            Column(Modifier.align(Alignment.Center)) {
                Text(detail.item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(episode.name, color = Color(0xFFD4D7DD), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        if (episodes.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (episodeIndex > 0) {
                    Button(onClick = { switchEpisode(episodeIndex - 1) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xB3151922))) {
                        Icon(Icons.Rounded.SkipPrevious, null)
                        Spacer(Modifier.width(4.dp))
                        Text("上一集")
                    }
                }
                if (episodeIndex < episodes.lastIndex) {
                    Button(onClick = { switchEpisode(episodeIndex + 1) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xB3151922))) {
                        Text("下一集")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.SkipNext, null)
                    }
                }
            }
        }
        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = MobileGold)
        playbackError?.let {
            Text("播放失败：$it", Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(18.dp), color = Color.White)
        }
    }
}

@Composable
private fun MobilePlayerMessage(message: String, action: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White)
            Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
        }
    }
}
