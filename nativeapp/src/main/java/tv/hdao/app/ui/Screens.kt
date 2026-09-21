package tv.hdao.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.hdao.app.data.FavoriteEntry
import tv.hdao.app.data.Favorites
import tv.hdao.app.data.FeaturedCatalog
import tv.hdao.app.data.HdaoRepository
import tv.hdao.app.data.SearchHistory
import tv.hdao.app.data.mergeVodPages
import tv.hdao.app.data.toVod
import tv.hdao.app.data.Vod
import tv.hdao.app.data.VodDetail
import tv.hdao.app.data.WatchEntry
import tv.hdao.app.data.WatchProgress

internal sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

/**
 * Catalogue, scroll positions and the last opened poster of the home screen.
 *
 * Owned by the navigation host for the same reason as [CategoryScreenState]:
 * the home screen leaves composition while a detail page is open, so anything
 * kept in remember() is thrown away. Without this, going back re-ran the fetch
 * (flashing "正在准备片库…") and rebuilt the layout from the top.
 */
@Stable
class HomeScreenState {
    var retry by mutableIntStateOf(0)
    // Internal, not public: LoadState is internal to this file's package.
    internal var catalog by mutableStateOf<LoadState<FeaturedCatalog>?>(null)
    val listState = LazyListState()
    val railState = LazyListState()
    val continueState = LazyListState()
    val favoritesState = LazyListState()
    var lastOpenedVodId by mutableStateOf<Int?>(null)
    /** Saved titles, reloaded on every entry so a new favourite shows up at once. */
    var favoriteItems by mutableStateOf(emptyList<Vod>())

    private var loadedRetry = -1

    /**
     * True when the catalogue has never loaded, when the last attempt failed, or
     * when a retry was requested.
     *
     * A failed state counts as needing a load on purpose: the screen re-enters
     * composition whenever the user comes back to it, so a transient failure
     * recovers by navigating away and back instead of staying stuck until the
     * user finds the retry button.
     */
    val needsLoad: Boolean
        get() = catalog == null || catalog is LoadState.Failed || loadedRetry != retry

    fun markLoaded() {
        loadedRetry = retry
    }
}

@Composable
fun HomeScreen(
    repository: HdaoRepository,
    watchProgress: WatchProgress,
    favorites: Favorites,
    state: HomeScreenState,
    contentFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
    onVodClick: (Vod) -> Unit,
    onPlay: (Vod) -> Unit,
    onContinue: (WatchEntry) -> Unit,
) {
    LaunchedEffect(state.retry) {
        // Returning from a detail page must not reload the catalogue.
        if (!state.needsLoad) return@LaunchedEffect
        state.catalog = LoadState.Loading
        state.catalog = try {
            LoadState.Ready(repository.featured(force = state.retry > 0))
        } catch (first: Exception) {
            // One silent retry before showing the error: most failures here are a
            // transient network blip, and the user should not have to press retry.
            delay(1_200L)
            try {
                LoadState.Ready(repository.featured(force = true))
            } catch (second: Exception) {
                LoadState.Failed(second.message ?: "网络连接失败")
            }
        }
        state.markLoaded()
    }
    LaunchedEffect(Unit) {
        state.favoriteItems = favorites.all().map { it.toVod() }
    }
    when (val current = state.catalog ?: LoadState.Loading) {
        LoadState.Loading -> LoadingView(
            message = "正在准备片库…",
            modifier = Modifier.focusRequester(contentFocusRequester)
                .focusProperties { up = navigationFocusRequester }
                .focusable(),
        )
        is LoadState.Failed -> ErrorView(
            message = current.message,
            retry = { state.retry++ },
            buttonModifier = Modifier.focusRequester(contentFocusRequester)
                .focusProperties { up = navigationFocusRequester },
        )
        is LoadState.Ready -> HomeCatalog(
            catalog = current.value,
            repository = repository,
            continueEntries = watchProgress.recent(),
            favoriteItems = state.favoriteItems,
            state = state,
            onVodClick = onVodClick,
            onPlay = onPlay,
            onContinue = onContinue,
            contentFocusRequester = contentFocusRequester,
            navigationFocusRequester = navigationFocusRequester,
        )
    }
}

@Composable
private fun HomeCatalog(
    catalog: FeaturedCatalog,
    repository: HdaoRepository,
    continueEntries: List<WatchEntry>,
    favoriteItems: List<Vod>,
    state: HomeScreenState,
    onVodClick: (Vod) -> Unit,
    onPlay: (Vod) -> Unit,
    onContinue: (WatchEntry) -> Unit,
    contentFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
) {
    val fallback = catalog.rows.firstOrNull()?.items.orEmpty()
    val heroVods = remember(catalog) {
        catalog.hero.distinctBy { it.vodId }.ifEmpty { fallback }.take(6)
    }
    val featuredPosters = remember(catalog) {
        val allItems = (heroVods + catalog.rows.flatMap { it.items }).distinctBy { it.vodId }
        val tmdbPosters = allItems.filter { it.coverUrl?.contains("image.tmdb.org") == true }
        (heroVods + tmdbPosters + allItems)
            .distinctBy { it.vodId }
            .take(10)
    }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    LazyColumn(
        state = state.listState,
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(bottom = 42.dp),
    ) {
        item(key = "home-hero") {
            HomePosterCarousel(
                heroVods = heroVods,
                posterVods = featuredPosters,
                repository = repository,
                railState = state.railState,
                lastOpenedVodId = state.lastOpenedVodId,
                onVodClick = onVodClick,
                onRailVodClick = { vod ->
                    state.lastOpenedVodId = vod.vodId
                    onVodClick(vod)
                },
                onPlay = onPlay,
                initialPosterFocusRequester = contentFocusRequester,
                navigationFocusRequester = navigationFocusRequester,
            )
        }
        item(key = "recent-watching") {
            ContinueWatchingRow(
                entries = continueEntries,
                onClick = onContinue,
                navigationFocusRequester = null,
                firstItemFocusRequester = continueWatchingFocusRequester,
                contentStart = 42.dp,
                listState = state.continueState,
            )
        }
        if (favoriteItems.isNotEmpty()) {
            item(key = "favorites") {
                LandscapeVodRow(
                    title = "我的收藏",
                    items = favoriteItems,
                    onVodClick = onVodClick,
                    navigationFocusRequester = navigationFocusRequester,
                    contentStart = 42.dp,
                    listState = state.favoritesState,
                )
            }
        }
    }
}

@Composable
private fun HomePosterCarousel(
    heroVods: List<Vod>,
    posterVods: List<Vod>,
    repository: HdaoRepository,
    railState: LazyListState,
    lastOpenedVodId: Int?,
    onVodClick: (Vod) -> Unit,
    onRailVodClick: (Vod) -> Unit,
    onPlay: (Vod) -> Unit,
    initialPosterFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
) {
    if (heroVods.isEmpty()) return
    var selectedVod by remember(heroVods) { mutableStateOf(heroVods.first()) }
    val railRestoreRequester = remember { FocusRequester() }

    // Open directly on the first programme card. On the way back from detail,
    // restore the card that was opened instead. This also makes the selected
    // card drive the backdrop and preview immediately, without an extra press.
    LaunchedEffect(posterVods, lastOpenedVodId) {
        if (posterVods.isEmpty()) return@LaunchedEffect
        val index = lastOpenedVodId
            ?.let { target -> posterVods.indexOfFirst { it.vodId == target }.takeIf { it >= 0 } }
            ?: 0
        railState.scrollToItem(index)
        val requester = if (lastOpenedVodId == null || index == 0) {
            initialPosterFocusRequester
        } else {
            railRestoreRequester
        }
        repeat(5) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
    }
    var actionsHaveFocus by remember { mutableStateOf(false) }
    var postersHaveFocus by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    val heroRegionHasFocus = actionsHaveFocus || postersHaveFocus

    LaunchedEffect(heroVods, heroRegionHasFocus) {
        if (heroVods.size < 2 || heroRegionHasFocus) return@LaunchedEffect
        while (true) {
            delay(8_000L)
            val currentIndex = heroVods.indexOfFirst { it.vodId == selectedVod.vodId }
            selectedVod = heroVods[(currentIndex + 1).mod(heroVods.size)]
        }
    }

    LaunchedEffect(selectedVod.vodId, heroRegionHasFocus) {
        if (!heroRegionHasFocus) return@LaunchedEffect
        previewUrl = null
        delay(850L)
        previewUrl = runCatching {
            repository.detail(selectedVod.vodId).episodes.firstOrNull()?.originalUrl
        }.getOrNull()
    }

    // Calibrated against the 1920x1080 / 240 dpi TCL tcl_m7642: 64dp is 96px.
    // Keep the details fixed and move the bottom-anchored shelf far enough down
    // that the focused poster no longer crowds the synopsis and action buttons.
    Box(Modifier.fillMaxWidth().height(664.dp)) {
        Crossfade(
            targetState = selectedVod,
            animationSpec = tween(420),
            label = "homeBackdrop",
            modifier = Modifier.matchParentSize(),
        ) { vod ->
            Box(Modifier.fillMaxSize().background(Ink)) {
                AsyncImage(
                    model = vod.backdropUrl ?: vod.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
            }
        }
        HomePreviewPlayer(
            previewUrl = previewUrl,
            isActive = heroRegionHasFocus,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to Ink,
                    0.24f to Color(0xF0080A0E),
                    0.56f to Color(0x96080A0E),
                    0.78f to Color(0x22080A0E),
                    1f to Color.Transparent,
                )
            )
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0x4D06090D),
                    0.52f to Color.Transparent,
                    0.72f to Color(0xB3090B10),
                    1f to Ink,
                )
            )
        )

        Crossfade(
            targetState = selectedVod,
            animationSpec = tween(220),
            label = "homeDetails",
            modifier = Modifier.align(Alignment.TopStart).padding(start = 42.dp, top = 82.dp).width(470.dp),
        ) { vod ->
            HomeFeaturedDetails(
                vod = vod,
                onPlay = { onPlay(vod) },
                onMoreInfo = { onVodClick(vod) },
                navigationFocusRequester = navigationFocusRequester,
                onActionsFocusChanged = { actionsHaveFocus = it },
            )
        }

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Text(
                "最近热播",
                modifier = Modifier.padding(start = 42.dp, bottom = 3.dp),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            LazyRow(
                state = railState,
                modifier = Modifier.onFocusChanged { postersHaveFocus = it.hasFocus }.focusGroup(),
                contentPadding = PaddingValues(start = 42.dp, end = 28.dp, top = 5.dp, bottom = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItemsIndexed(posterVods, key = { index, vod -> "home-poster:${vod.vodId}:$index" }) { index, vod ->
                    val focusModifier = when {
                        index == 0 -> Modifier.focusRequester(initialPosterFocusRequester)
                        vod.vodId == lastOpenedVodId -> Modifier.focusRequester(railRestoreRequester)
                        else -> Modifier
                    }
                    PosterCard(
                        vod = vod,
                        onClick = { onRailVodClick(vod) },
                        onFocused = { selectedVod = vod },
                        modifier = focusModifier,
                        cardWidth = 132.dp,
                        cardHeight = 186.dp,
                        focusedScale = 1.10f,
                        focusBorderColor = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFeaturedDetails(
    vod: Vod,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
    navigationFocusRequester: FocusRequester,
    onActionsFocusChanged: (Boolean) -> Unit,
) {
    Column {
        Text(
            vod.title,
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        val metadata = listOfNotNull(vod.year, vod.area, vod.mediaType, vod.score?.let { "★ $it" })
            .filter { it.isNotBlank() }
            .joinToString("  ·  ")
        if (metadata.isNotEmpty()) {
            Text(
                metadata,
                color = Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }
        vod.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                description,
                color = Color(0xFFE0E2E6),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(13.dp))
        }
        Row(
            modifier = Modifier.onFocusChanged { onActionsFocusChanged(it.hasFocus) }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TvButton(
                text = "播放",
                primary = true,
                icon = PlayIcon,
                onClick = onPlay,
                modifier = Modifier.focusProperties { up = navigationFocusRequester },
            )
            TvButton(
                text = "更多信息",
                icon = InfoIcon,
                onClick = onMoreInfo,
                modifier = Modifier.focusProperties { up = navigationFocusRequester },
            )
        }
    }
}

/**
 * Loaded pages, scroll position and the last opened poster of one category.
 *
 * This state is owned by the navigation host, not by [CategoryScreen] itself,
 * because the screen leaves composition while a detail page is open. Keeping it
 * here is what lets the category screen come back with its grid intact, the
 * cursor back on the poster the user opened, and no page-one reload.
 */
@Stable
class CategoryScreenState {
    val gridState = LazyGridState()
    var items by mutableStateOf(emptyList<Vod>())
    var requestedPage by mutableIntStateOf(1)
    var loadedPage by mutableIntStateOf(0)
    var totalPages by mutableIntStateOf(1)
    var loading by mutableStateOf(true)
    var loadError by mutableStateOf<String?>(null)
    var retry by mutableIntStateOf(0)
    var lastOpenedVodId by mutableStateOf<Int?>(null)
}

@Composable
fun CategoryScreen(
    category: String,
    title: String,
    repository: HdaoRepository,
    state: CategoryScreenState,
    contentFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
    onVodClick: (Vod) -> Unit,
) {
    val gridState = state.gridState
    val restoredFocusRequester = remember { FocusRequester() }

    // Runs on every entry to this screen, including the return from a detail
    // page: scroll the poster that was opened back into view and put the cursor
    // on it, instead of dropping the cursor on the top navigation bar.
    LaunchedEffect(Unit) {
        val target = state.lastOpenedVodId ?: return@LaunchedEffect
        val index = state.items.indexOfFirst { it.vodId == target }
        if (index < 0) return@LaunchedEffect
        state.gridState.scrollToItem(index)
        if (index == 0) {
            runCatching { contentFocusRequester.requestFocus() }
            return@LaunchedEffect
        }
        // The card has to be composed before its FocusRequester can be used.
        repeat(5) {
            withFrameNanos { }
            if (runCatching { restoredFocusRequester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(category, state.requestedPage, state.retry) {
        // Returning from a detail page must not reload and replace the grid.
        if (state.loadedPage >= state.requestedPage && state.loadError == null) {
            return@LaunchedEffect
        }
        state.loading = true
        state.loadError = null
        try {
            val result = repository.category(category, state.requestedPage)
            state.items = if (state.requestedPage == 1) {
                result.items
            } else {
                mergeVodPages(state.items, result.items)
            }
            state.loadedPage = result.page.coerceAtLeast(state.requestedPage)
            state.totalPages = result.totalPages.coerceAtLeast(state.loadedPage)
        } catch (error: Exception) {
            state.loadError = error.message ?: "网络连接失败"
        } finally {
            state.loading = false
        }
    }
    val shouldLoadMore by remember(state) {
        derivedStateOf {
            val lastVisible = state.gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.items.isNotEmpty() &&
                lastVisible >= state.items.lastIndex - 6 &&
                state.loadedPage < state.totalPages &&
                !state.loading &&
                state.loadError == null
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) state.requestedPage = state.loadedPage + 1
    }

    Column(Modifier.fillMaxSize().padding(start = 28.dp, top = 58.dp)) {
        Text(
            title,
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
        )
        when {
            state.items.isEmpty() && state.loading -> LoadingView()
            state.items.isEmpty() && state.loadError != null ->
                ErrorView(state.loadError ?: "网络连接失败") { state.retry++ }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(142.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 28.dp, top = 8.dp, bottom = 42.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(state.items, key = { index, vod -> "${vod.vodId}:$index" }) { index, vod ->
                    // Item 0 always carries the shared requester so the top
                    // navigation can still jump into the grid; the poster the
                    // user last opened gets its own requester for the return.
                    val modifier = when {
                        index == 0 -> Modifier.focusRequester(contentFocusRequester)
                            .focusProperties { up = navigationFocusRequester }
                        vod.vodId == state.lastOpenedVodId ->
                            Modifier.focusRequester(restoredFocusRequester)
                        else -> Modifier
                    }
                    PosterCard(vod, onClick = { onVodClick(vod) }, modifier = modifier)
                }
                if (state.loadError != null) {
                    item(key = "load-more-error", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("更多内容暂时没有加载成功", color = Muted, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            TvButton("重试", onClick = { state.retry++ })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Query, results and scroll position of the search screen.
 *
 * Owned by the navigation host. While this lived in remember() inside the
 * screen, opening a result and pressing back discarded the query and the whole
 * result list, so the user had to search all over again.
 */
@Stable
class SearchScreenState {
    var query by mutableStateOf("")
    var submitted by mutableStateOf("")
    var searchAttempt by mutableIntStateOf(0)
    internal var results by mutableStateOf<LoadState<List<Vod>>?>(null)
    val gridState = LazyGridState()
    var lastOpenedVodId by mutableStateOf<Int?>(null)
    /** Recent search terms, shown while nothing has been searched yet. */
    var terms by mutableStateOf(emptyList<String>())

    private var loadedQuery: String? = null
    private var loadedAttempt = 0

    /** True when the current query has not been fetched yet. */
    val needsSearch: Boolean
        get() = submitted.isNotBlank() && (loadedQuery != submitted || loadedAttempt != searchAttempt)

    fun markSearched() {
        loadedQuery = submitted
        loadedAttempt = searchAttempt
    }
}

@Composable
fun SearchScreen(
    repository: HdaoRepository,
    state: SearchScreenState,
    history: SearchHistory,
    contentFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
    onVodClick: (Vod) -> Unit,
) {
    var fieldFocused by remember { mutableStateOf(false) }
    val restoredFocusRequester = remember { FocusRequester() }

    fun submit(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        state.submitted = trimmed
        state.searchAttempt++
        state.terms = history.record(trimmed)
    }

    LaunchedEffect(state.submitted, state.searchAttempt) {
        // A return from a detail page keeps the previous results; only a new
        // query or an explicit retry runs another search.
        if (!state.needsSearch) return@LaunchedEffect
        state.results = LoadState.Loading
        state.results = try {
            LoadState.Ready(repository.search(state.submitted))
        } catch (error: Exception) {
            LoadState.Failed(error.message ?: "搜索失败")
        }
        state.markSearched()
    }

    // Runs on every entry: put the cursor back on the poster that was opened.
    LaunchedEffect(Unit) {
        state.terms = history.all()
        val target = state.lastOpenedVodId ?: return@LaunchedEffect
        val results = (state.results as? LoadState.Ready)?.value ?: return@LaunchedEffect
        val index = results.indexOfFirst { it.vodId == target }
        if (index < 0) return@LaunchedEffect
        state.gridState.scrollToItem(index)
        repeat(5) {
            withFrameNanos { }
            if (runCatching { restoredFocusRequester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(start = 28.dp, top = 58.dp)) {
        Text(
            "搜索",
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
        )
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = state.query,
                onValueChange = {
                    state.query = it
                    // Clearing the field goes back to the recent searches, so the
                    // history stays reachable after a search instead of only on the
                    // first visit of a session.
                    if (it.isBlank()) state.results = null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(state.query) }),
                modifier = Modifier.focusRequester(contentFocusRequester)
                    .focusProperties { up = navigationFocusRequester }
                    .onFocusChanged { fieldFocused = it.isFocused },
                decorationBox = { inner ->
                    Box(
                        Modifier.width(510.dp).height(48.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Panel)
                            .border(
                                if (fieldFocused) 2.dp else 1.dp,
                                if (fieldFocused) Gold else Color(0xFF3A404D),
                                RoundedCornerShape(7.dp),
                            )
                            .padding(horizontal = 15.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (state.query.isEmpty()) Text("片名、演员或导演", color = Muted, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            TvButton("搜索", primary = true, onClick = { submit(state.query) })
        }
        when (val current = state.results) {
            null -> if (state.terms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("使用电视键盘输入关键词", color = Muted, fontSize = 17.sp)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(start = 14.dp, top = 20.dp)) {
                    Text("最近搜索", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.terms) { term ->
                            TvButton(text = term, onClick = { submit(term) })
                        }
                    }
                }
            }
            LoadState.Loading -> LoadingView("正在搜索…")
            is LoadState.Failed -> ErrorView(current.message) { state.searchAttempt++ }
            is LoadState.Ready -> {
                if (current.value.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有找到“${state.submitted}”", color = Muted, fontSize = 18.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(142.dp),
                        state = state.gridState,
                        contentPadding = PaddingValues(start = 14.dp, end = 28.dp, top = 28.dp, bottom = 42.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        itemsIndexed(current.value, key = { index, vod -> "${vod.vodId}:$index" }) { _, vod ->
                            PosterCard(
                                vod = vod,
                                onClick = { onVodClick(vod) },
                                modifier = if (vod.vodId == state.lastOpenedVodId) {
                                    Modifier.focusRequester(restoredFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    vodId: Int,
    repository: HdaoRepository,
    favorites: Favorites,
    onEpisodeClick: (Int) -> Unit,
    onVodClick: (Vod) -> Unit,
) {
    var retry by remember(vodId) { mutableIntStateOf(0) }
    var state by remember(vodId) { mutableStateOf<LoadState<VodDetail>>(LoadState.Loading) }
    var favorite by remember(vodId) { mutableStateOf(favorites.isFavorite(vodId)) }
    LaunchedEffect(vodId, retry) {
        state = LoadState.Loading
        state = try { LoadState.Ready(repository.detail(vodId, retry > 0)) }
        catch (error: Exception) { LoadState.Failed(error.message ?: "详情加载失败") }
    }
    when (val current = state) {
        LoadState.Loading -> LoadingView("正在加载影片详情…")
        is LoadState.Failed -> ErrorView(current.message) { retry++ }
        is LoadState.Ready -> DetailContent(
            detail = current.value,
            isFavorite = favorite,
            onToggleFavorite = {
                val vod = current.value.item
                favorite = favorites.toggle(
                    FavoriteEntry(
                        vodId = vod.vodId,
                        title = vod.title,
                        imageUrl = vod.backdropUrl ?: vod.coverUrl,
                        year = vod.year,
                        score = vod.score,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            },
            onEpisodeClick = onEpisodeClick,
            onVodClick = onVodClick,
        )
    }
}

@Composable
private fun DetailContent(
    detail: VodDetail,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onEpisodeClick: (Int) -> Unit,
    onVodClick: (Vod) -> Unit,
) {
    val vod = detail.item
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(detail.item.vodId) {
        if (detail.episodes.isNotEmpty()) playFocusRequester.requestFocus()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 58.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().height(420.dp)
                    .background(Brush.horizontalGradient(listOf(Ink, Color(0xFF151922), Ink)))
            ) {
                vod.backdropUrl?.let { backdrop ->
                    AsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Ink,
                            0.56f to Color(0xE8090B10),
                            1f to Color(0x99090B10),
                        )
                    )
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Ink))))
                Column(Modifier.align(Alignment.CenterStart).width(720.dp).padding(start = 48.dp)) {
                    Text(vod.title, color = Color.White, fontSize = 39.sp, fontWeight = FontWeight.Black, maxLines = 2)
                    Text(
                        listOfNotNull(vod.year, vod.area, vod.genres, vod.score?.let { "评分 $it" }).joinToString("  ·  "),
                        Modifier.padding(top = 10.dp), color = Gold, fontSize = 14.sp,
                    )
                    Text(
                        vod.description ?: "暂无简介",
                        Modifier.padding(top = 14.dp),
                        color = Color(0xFFD1D5DC),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.episodes.isNotEmpty()) {
                        TvButton(
                            text = if (detail.episodes.size > 1) "播放第 1 集" else "立即播放",
                            primary = true,
                            icon = PlayIcon,
                            onClick = { onEpisodeClick(0) },
                            modifier = Modifier.padding(top = 20.dp).focusRequester(playFocusRequester),
                        )
                    }
                    TvButton(
                        text = if (isFavorite) "已收藏" else "收藏",
                        onClick = onToggleFavorite,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 54.dp)
                        .width(210.dp)
                        .height(300.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                        .background(Panel)
                ) {
                    AsyncImage(
                        model = vod.coverUrl,
                        contentDescription = vod.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        if (detail.episodes.isNotEmpty()) {
            item {
                Text(
                    "选集 · 共 ${detail.episodes.size} 集",
                    Modifier.padding(start = 48.dp, top = 8.dp, bottom = 12.dp),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(detail.episodes.size) { index ->
                        TvButton(detail.episodes[index].name, onClick = { onEpisodeClick(index) })
                    }
                }
            }
        }
        if (detail.related.isNotEmpty()) {
            item { LandscapeVodRow("相关推荐", detail.related, onVodClick) }
        }
    }
}
