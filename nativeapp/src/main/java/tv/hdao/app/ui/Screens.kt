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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.hdao.app.data.FeaturedCatalog
import tv.hdao.app.data.HdaoRepository
import tv.hdao.app.data.mergeVodPages
import tv.hdao.app.data.Vod
import tv.hdao.app.data.VodDetail
import tv.hdao.app.data.WatchEntry
import tv.hdao.app.data.WatchProgress

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

@Composable
fun HomeScreen(
    repository: HdaoRepository,
    watchProgress: WatchProgress,
    contentFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
    onVodClick: (Vod) -> Unit,
    onPlay: (Vod) -> Unit,
    onContinue: (WatchEntry) -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<FeaturedCatalog>>(LoadState.Loading) }
    LaunchedEffect(retry) {
        state = LoadState.Loading
        state = try {
            LoadState.Ready(repository.featured(force = retry > 0))
        } catch (error: Exception) {
            LoadState.Failed(error.message ?: "网络连接失败")
        }
    }
    when (val current = state) {
        LoadState.Loading -> LoadingView(
            message = "正在准备片库…",
            modifier = Modifier.focusRequester(contentFocusRequester)
                .focusProperties { up = navigationFocusRequester }
                .focusable(),
        )
        is LoadState.Failed -> ErrorView(
            message = current.message,
            retry = { retry++ },
            buttonModifier = Modifier.focusRequester(contentFocusRequester)
                .focusProperties { up = navigationFocusRequester },
        )
        is LoadState.Ready -> HomeCatalog(
            catalog = current.value,
            repository = repository,
            continueEntries = watchProgress.recent(),
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
        (heroVods + catalog.rows.flatMap { it.items })
            .distinctBy { it.vodId }
            .take(10)
    }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(bottom = 42.dp),
    ) {
        item(key = "home-hero") {
            HomePosterCarousel(
                heroVods = heroVods,
                posterVods = featuredPosters,
                repository = repository,
                onVodClick = onVodClick,
                onPlay = onPlay,
                firstActionFocusRequester = contentFocusRequester,
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
            )
        }
    }
}

@Composable
private fun HomePosterCarousel(
    heroVods: List<Vod>,
    posterVods: List<Vod>,
    repository: HdaoRepository,
    onVodClick: (Vod) -> Unit,
    onPlay: (Vod) -> Unit,
    firstActionFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
) {
    if (heroVods.isEmpty()) return
    var selectedVod by remember(heroVods) { mutableStateOf(heroVods.first()) }
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
        delay(1_800L)
        previewUrl = runCatching {
            repository.detail(selectedVod.vodId).episodes.firstOrNull()?.originalUrl
        }.getOrNull()
    }

    Box(Modifier.fillMaxWidth().height(560.dp)) {
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
                firstActionFocusRequester = firstActionFocusRequester,
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
                modifier = Modifier.onFocusChanged { postersHaveFocus = it.hasFocus }.focusGroup(),
                contentPadding = PaddingValues(start = 42.dp, end = 28.dp, top = 5.dp, bottom = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItemsIndexed(posterVods, key = { index, vod -> "home-poster:${vod.vodId}:$index" }) { _, vod ->
                    PosterCard(
                        vod = vod,
                        onClick = { onVodClick(vod) },
                        onFocused = { selectedVod = vod },
                        cardWidth = 112.dp,
                        cardHeight = 158.dp,
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
    firstActionFocusRequester: FocusRequester,
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
                modifier = Modifier.focusRequester(firstActionFocusRequester)
                    .focusProperties { up = navigationFocusRequester },
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

@Composable
fun CategoryScreen(
    category: String,
    title: String,
    repository: HdaoRepository,
    contentFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
    onVodClick: (Vod) -> Unit,
) {
    val gridState = remember(category) { LazyGridState() }
    var retry by remember(category) { mutableIntStateOf(0) }
    var requestedPage by remember(category) { mutableIntStateOf(1) }
    var loadedPage by remember(category) { mutableIntStateOf(0) }
    var totalPages by remember(category) { mutableIntStateOf(1) }
    var items by remember(category) { mutableStateOf(emptyList<Vod>()) }
    var loading by remember(category) { mutableStateOf(true) }
    var loadError by remember(category) { mutableStateOf<String?>(null) }

    LaunchedEffect(category, requestedPage, retry) {
        loading = true
        loadError = null
        try {
            val result = repository.category(category, requestedPage)
            items = if (requestedPage == 1) {
                result.items
            } else {
                mergeVodPages(items, result.items)
            }
            loadedPage = result.page.coerceAtLeast(requestedPage)
            totalPages = result.totalPages.coerceAtLeast(loadedPage)
        } catch (error: Exception) {
            loadError = error.message ?: "网络连接失败"
        } finally {
            loading = false
        }
    }
    // Category changes replace the grid and paging state objects. Recreate the
    // derived state too, otherwise it keeps observing the first category opened.
    val shouldLoadMore by remember(category, gridState) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            items.isNotEmpty() &&
                lastVisible >= items.lastIndex - 6 &&
                loadedPage < totalPages &&
                !loading &&
                loadError == null
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) requestedPage = loadedPage + 1
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
            items.isEmpty() && loading -> LoadingView()
            items.isEmpty() && loadError != null -> ErrorView(loadError ?: "网络连接失败") { retry++ }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(142.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 28.dp, top = 8.dp, bottom = 42.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(items, key = { index, vod -> "${vod.vodId}:$index" }) { index, vod ->
                    val modifier = if (index == 0) {
                        Modifier.focusRequester(contentFocusRequester)
                            .focusProperties { up = navigationFocusRequester }
                    } else {
                        Modifier
                    }
                    PosterCard(vod, onClick = { onVodClick(vod) }, modifier = modifier)
                }
                if (loadError != null) {
                    item(key = "load-more-error", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("更多内容暂时没有加载成功", color = Muted, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            TvButton("重试", onClick = { retry++ })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    repository: HdaoRepository,
    contentFocusRequester: FocusRequester,
    navigationFocusRequester: FocusRequester,
    onVodClick: (Vod) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var searchAttempt by remember { mutableIntStateOf(0) }
    var fieldFocused by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<LoadState<List<Vod>>?>(null) }
    LaunchedEffect(submitted, searchAttempt) {
        if (submitted.isBlank()) return@LaunchedEffect
        state = LoadState.Loading
        state = try { LoadState.Ready(repository.search(submitted)) }
        catch (error: Exception) { LoadState.Failed(error.message ?: "搜索失败") }
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
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    submitted = query.trim()
                    searchAttempt++
                }),
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
                        if (query.isEmpty()) Text("片名、演员或导演", color = Muted, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            TvButton("搜索", primary = true, onClick = {
                submitted = query.trim()
                searchAttempt++
            })
        }
        when (val current = state) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("使用电视键盘输入关键词", color = Muted, fontSize = 17.sp)
            }
            LoadState.Loading -> LoadingView("正在搜索…")
            is LoadState.Failed -> ErrorView(current.message) { searchAttempt++ }
            is LoadState.Ready -> {
                if (current.value.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有找到“$submitted”", color = Muted, fontSize = 18.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(142.dp),
                        contentPadding = PaddingValues(start = 14.dp, end = 28.dp, top = 28.dp, bottom = 42.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        itemsIndexed(current.value, key = { index, vod -> "${vod.vodId}:$index" }) { _, vod ->
                            PosterCard(vod, onClick = { onVodClick(vod) })
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
    onEpisodeClick: (Int) -> Unit,
    onVodClick: (Vod) -> Unit,
) {
    var retry by remember(vodId) { mutableIntStateOf(0) }
    var state by remember(vodId) { mutableStateOf<LoadState<VodDetail>>(LoadState.Loading) }
    LaunchedEffect(vodId, retry) {
        state = LoadState.Loading
        state = try { LoadState.Ready(repository.detail(vodId, retry > 0)) }
        catch (error: Exception) { LoadState.Failed(error.message ?: "详情加载失败") }
    }
    when (val current = state) {
        LoadState.Loading -> LoadingView("正在加载影片详情…")
        is LoadState.Failed -> ErrorView(current.message) { retry++ }
        is LoadState.Ready -> DetailContent(current.value, onEpisodeClick, onVodClick)
    }
}

@Composable
private fun DetailContent(detail: VodDetail, onEpisodeClick: (Int) -> Unit, onVodClick: (Vod) -> Unit) {
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
