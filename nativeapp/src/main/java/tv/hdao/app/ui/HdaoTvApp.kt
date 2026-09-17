package tv.hdao.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import tv.hdao.app.data.HdaoRepository
import tv.hdao.app.data.WatchProgress

private sealed interface Screen {
    data object Home : Screen
    data class Category(val key: String, val title: String) : Screen
    data object Search : Screen
    data class Detail(val vodId: Int) : Screen
    data class Player(val vodId: Int, val episodeIndex: Int) : Screen
}

@Composable
fun HdaoTvApp() {
    val context = LocalContext.current
    val repository = remember { HdaoRepository() }
    val progress = remember { WatchProgress(context.applicationContext) }
    val contentFocusRequester = remember { FocusRequester() }
    val navigationFocusRequester = remember { FocusRequester() }
    val backStack = remember { mutableStateListOf<Screen>() }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    fun navigate(next: Screen) {
        backStack.add(screen)
        screen = next
    }

    fun openRoot(next: Screen) {
        backStack.clear()
        screen = next
    }

    fun goBack() {
        screen = if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else Screen.Home
    }

    BackHandler(enabled = screen != Screen.Home, onBack = ::goBack)

    Box(Modifier.fillMaxSize().background(Ink)) {
        when (val current = screen) {
            Screen.Home -> HomeScreen(
                repository = repository,
                watchProgress = progress,
                contentFocusRequester = contentFocusRequester,
                navigationFocusRequester = navigationFocusRequester,
                onVodClick = { navigate(Screen.Detail(it.vodId)) },
                onPlay = { navigate(Screen.Player(it.vodId, 0)) },
                onContinue = { navigate(Screen.Player(it.vodId, it.episodeIndex)) },
            )
            is Screen.Category -> CategoryScreen(
                category = current.key,
                title = current.title,
                repository = repository,
                contentFocusRequester = contentFocusRequester,
                navigationFocusRequester = navigationFocusRequester,
                onVodClick = { navigate(Screen.Detail(it.vodId)) },
            )
            Screen.Search -> SearchScreen(
                repository = repository,
                contentFocusRequester = contentFocusRequester,
                navigationFocusRequester = navigationFocusRequester,
                onVodClick = { navigate(Screen.Detail(it.vodId)) },
            )
            is Screen.Detail -> DetailScreen(
                vodId = current.vodId,
                repository = repository,
                onEpisodeClick = { index -> navigate(Screen.Player(current.vodId, index)) },
                onVodClick = { navigate(Screen.Detail(it.vodId)) },
            )
            is Screen.Player -> PlayerScreen(
                vodId = current.vodId,
                initialEpisodeIndex = current.episodeIndex,
                repository = repository,
                watchProgress = progress,
                onBack = ::goBack,
            )
        }

        if (screen is Screen.Home || screen is Screen.Category || screen is Screen.Search) {
            TopNavigation(
                selected = when (val current = screen) {
                    Screen.Home -> "home"
                    Screen.Search -> "search"
                    is Screen.Category -> current.key
                    else -> ""
                },
                onSelect = { key, title ->
                    when (key) {
                        "home" -> openRoot(Screen.Home)
                        "search" -> openRoot(Screen.Search)
                        else -> openRoot(Screen.Category(key, title))
                    }
                },
                contentFocusRequester = contentFocusRequester,
                selectedFocusRequester = navigationFocusRequester,
                modifier = Modifier.align(Alignment.TopStart).zIndex(20f),
            )
        }

        UpdateCoordinator()
    }
}
