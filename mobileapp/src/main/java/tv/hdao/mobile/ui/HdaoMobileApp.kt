package tv.hdao.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.hdao.app.data.FeaturedCatalog
import tv.hdao.app.data.HdaoRepository
import tv.hdao.app.data.Vod
import tv.hdao.app.data.VodDetail
import tv.hdao.app.data.WatchEntry
import tv.hdao.app.data.WatchProgress
import tv.hdao.app.data.mergeVodPages

private sealed interface MobileScreen {
    data object Home : MobileScreen
    data object Search : MobileScreen
    data class Category(val key: String, val title: String) : MobileScreen
    data class Detail(val vodId: Int) : MobileScreen
    data class Player(val vodId: Int, val episodeIndex: Int) : MobileScreen
}

private sealed interface MobileLoad<out T> {
    data object Loading : MobileLoad<Nothing>
    data class Ready<T>(val value: T) : MobileLoad<T>
    data class Failed(val message: String) : MobileLoad<Nothing>
}

@Composable
fun HdaoMobileApp() {
    val context = LocalContext.current
    val repository = remember { HdaoRepository() }
    val progress = remember { WatchProgress(context.applicationContext) }
    val backStack = remember { mutableStateListOf<MobileScreen>() }
    var screen by remember { mutableStateOf<MobileScreen>(MobileScreen.Home) }

    fun navigate(next: MobileScreen) {
        backStack.add(screen)
        screen = next
    }

    fun openRoot(next: MobileScreen) {
        backStack.clear()
        screen = next
    }

    fun goBack() {
        screen = if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else MobileScreen.Home
    }

    BackHandler(enabled = screen != MobileScreen.Home, onBack = ::goBack)
    val showBottomBar = screen is MobileScreen.Home || screen is MobileScreen.Search || screen is MobileScreen.Category

    Box(Modifier.fillMaxSize().background(MobileInk)) {
        when (val current = screen) {
            MobileScreen.Home -> MobileHomeScreen(
                repository = repository,
                watchProgress = progress,
                onVodClick = { navigate(MobileScreen.Detail(it.vodId)) },
                onPlayVod = { navigate(MobileScreen.Player(it.vodId, 0)) },
                onContinue = { navigate(MobileScreen.Player(it.vodId, it.episodeIndex)) },
            )
            MobileScreen.Search -> MobileSearchScreen(repository) {
                navigate(MobileScreen.Detail(it.vodId))
            }
            is MobileScreen.Category -> MobileCategoryScreen(current.key, current.title, repository) {
                navigate(MobileScreen.Detail(it.vodId))
            }
            is MobileScreen.Detail -> MobileDetailScreen(
                vodId = current.vodId,
                repository = repository,
                onBack = ::goBack,
                onPlay = { index -> navigate(MobileScreen.Player(current.vodId, index)) },
                onVodClick = { navigate(MobileScreen.Detail(it.vodId)) },
            )
            is MobileScreen.Player -> MobilePlayerScreen(
                vodId = current.vodId,
                initialEpisodeIndex = current.episodeIndex,
                repository = repository,
                watchProgress = progress,
                onBack = ::goBack,
            )
        }

        if (showBottomBar) {
            MobileBottomBar(
                selected = when (val current = screen) {
                    MobileScreen.Home -> "home"
                    MobileScreen.Search -> "search"
                    is MobileScreen.Category -> current.key
                    else -> ""
                },
                onSelect = { key ->
                    when (key) {
                        "home" -> openRoot(MobileScreen.Home)
                        "search" -> openRoot(MobileScreen.Search)
                        "movie" -> openRoot(MobileScreen.Category("movie", "电影"))
                        "tv" -> openRoot(MobileScreen.Category("tv", "剧集"))
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun MobileBottomBar(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple("home", "首页", Icons.Rounded.Home),
        Triple("search", "搜索", Icons.Rounded.Search),
        Triple("movie", "电影", Icons.Rounded.Movie),
        Triple("tv", "剧集", Icons.Rounded.Tv),
    )
    NavigationBar(modifier, containerColor = Color(0xF20C0F14)) {
        items.forEach { (key, label, icon) ->
            NavigationBarItem(
                selected = selected == key,
                onClick = { onSelect(key) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun MobileHomeScreen(
    repository: HdaoRepository,
    watchProgress: WatchProgress,
    onVodClick: (Vod) -> Unit,
    onPlayVod: (Vod) -> Unit,
    onContinue: (WatchEntry) -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<MobileLoad<FeaturedCatalog>>(MobileLoad.Loading) }
    LaunchedEffect(retry) {
        state = try { MobileLoad.Ready(repository.featured(force = retry > 0)) }
        catch (error: Exception) { MobileLoad.Failed(error.message ?: "网络连接失败") }
    }
    when (val current = state) {
        MobileLoad.Loading -> MobileLoading("正在准备片库…")
        is MobileLoad.Failed -> MobileError(current.message) { retry++ }
        is MobileLoad.Ready -> {
            val catalog = current.value
            val heroFallback = catalog.rows.firstNotNullOf { it.items.firstOrNull() }
            val heroes = catalog.hero.distinctBy { it.vodId }.ifEmpty { listOf(heroFallback) }
            val continueEntries = watchProgress.recent()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item { MobileHeroCarousel(heroes, onPlayVod, onVodClick) }
                catalog.rows.take(2).forEach { row ->
                    item(key = row.category) { MobileVodRow(row.title, row.items.take(20), onVodClick) }
                }
                if (continueEntries.isNotEmpty()) {
                    item { MobileContinueRow(continueEntries, onContinue) }
                }
                catalog.rows.drop(2).forEach { row ->
                    item(key = row.category) { MobileVodRow(row.title, row.items.take(20), onVodClick) }
                }
            }
        }
    }
}

@Composable
private fun MobileHeroCarousel(
    vods: List<Vod>,
    onPlay: (Vod) -> Unit,
    onDetails: (Vod) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { vods.size })
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage, vods.size) {
        if (vods.size <= 1) return@LaunchedEffect
        delay(6_000L)
        if (!pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage((currentPage + 1) % vods.size)
        }
    }
    Box(Modifier.fillMaxWidth().height(420.dp)) {
        HorizontalPager(
            state = pagerState,
            key = { vods[it].vodId },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val vod = vods[page]
            MobileHero(vod, { onPlay(vod) }, { onDetails(vod) })
        }
        if (vods.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                vods.indices.forEach { index ->
                    Box(
                        Modifier
                            .width(if (index == currentPage) 18.dp else 6.dp)
                            .height(4.dp)
                            .background(
                                if (index == currentPage) MobileGold else Color(0x99FFFFFF),
                                RoundedCornerShape(50),
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileHero(vod: Vod, onPlay: () -> Unit, onDetails: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(420.dp)) {
        AsyncImage(
            model = vod.backdropUrl ?: vod.coverUrl,
            contentDescription = vod.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x22000000), MobileInk))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xD9090B10), Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 22.dp)) {
            Text(vod.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 2)
            Text(
                listOfNotNull(vod.score?.let { "★ $it" }, vod.year, vod.area).joinToString("  ·  "),
                modifier = Modifier.padding(top = 6.dp),
                color = MobileGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                vod.description ?: "发现更多精彩内容",
                modifier = Modifier.padding(top = 8.dp),
                color = Color(0xFFD3D6DC),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MobileActionButton("播放", Icons.Rounded.PlayArrow, true, onPlay)
                MobileActionButton("详情", Icons.Rounded.Info, false, onDetails)
            }
        }
    }
}

@Composable
private fun MobileActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MobileGold else Color(0xDD252931),
            contentColor = if (primary) MobileInk else Color.White,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MobileVodRow(title: String, vods: List<Vod>, onVodClick: (Vod) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(vods, key = { it.vodId }) { vod -> MobilePosterCard(vod) { onVodClick(vod) } }
        }
    }
}

@Composable
private fun MobilePosterCard(vod: Vod, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(10.dp)).background(MobilePanel)) {
            AsyncImage(vod.coverUrl, vod.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            vod.score?.let { MobileScoreBadge(it, Modifier.align(Alignment.TopStart)) }
            vod.remarks?.let {
                Text(
                    it,
                    Modifier.align(Alignment.BottomEnd).background(Color(0xD9090B10)).padding(horizontal = 6.dp, vertical = 4.dp),
                    color = MobileGold,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
        Text(vod.title, Modifier.padding(top = 7.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MobileScoreBadge(score: String, modifier: Modifier = Modifier) {
    Text(
        text = "★ $score",
        modifier = modifier.padding(7.dp).background(Color(0xE6090B10), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
        color = MobileGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun MobileContinueRow(entries: List<WatchEntry>, onContinue: (WatchEntry) -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text("继续观看", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.vodId }) { entry ->
                Column(Modifier.width(220.dp).clickable { onContinue(entry) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(MobilePanel)) {
                        AsyncImage(entry.imageUrl, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                            Text(entry.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(entry.episodeName, color = MobileMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0xFF4D5159))) {
                            Box(Modifier.fillMaxWidth(entry.fraction).height(3.dp).background(MobileGold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileCategoryScreen(category: String, title: String, repository: HdaoRepository, onVodClick: (Vod) -> Unit) {
    val tvCategories = listOf(
        "tv" to "剧集",
        "variety" to "综艺",
        "documentary" to "纪录片",
        "anime" to "动漫",
        "short-drama" to "短剧",
    )
    var activeCategory by remember(category) { mutableStateOf(category) }
    val gridState = remember(activeCategory) { LazyGridState() }
    var retry by remember(activeCategory) { mutableIntStateOf(0) }
    var requestedPage by remember(activeCategory) { mutableIntStateOf(1) }
    var loadedPage by remember(activeCategory) { mutableIntStateOf(0) }
    var totalPages by remember(activeCategory) { mutableIntStateOf(1) }
    var vods by remember(activeCategory) { mutableStateOf(emptyList<Vod>()) }
    var loading by remember(activeCategory) { mutableStateOf(true) }
    var loadError by remember(activeCategory) { mutableStateOf<String?>(null) }
    LaunchedEffect(activeCategory, requestedPage, retry) {
        loading = true
        loadError = null
        try {
            val result = repository.category(activeCategory, requestedPage)
            vods = if (requestedPage == 1) result.items else mergeVodPages(vods, result.items)
            loadedPage = result.page.coerceAtLeast(requestedPage)
            totalPages = result.totalPages.coerceAtLeast(loadedPage)
        } catch (error: Exception) {
            loadError = error.message ?: "加载失败"
        } finally {
            loading = false
        }
    }
    val shouldLoadMore by remember(activeCategory, gridState) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            vods.isNotEmpty() &&
                lastVisible >= vods.lastIndex - 6 &&
                loadedPage < totalPages &&
                !loading &&
                loadError == null
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) requestedPage = loadedPage + 1
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val activeTitle = tvCategories.firstOrNull { it.first == activeCategory }?.second ?: title
        Text(activeTitle, Modifier.padding(horizontal = 20.dp, vertical = 18.dp), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        if (category == "tv") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tvCategories, key = { it.first }) { (key, label) ->
                    val selected = activeCategory == key
                    Button(
                        onClick = { activeCategory = key },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) MobileGold else MobilePanel,
                            contentColor = if (selected) MobileInk else Color.White,
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        when {
            vods.isEmpty() && loading -> MobileLoading()
            vods.isEmpty() && loadError != null -> MobileError(loadError ?: "加载失败") { retry++ }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(vods, key = { index, vod -> "${vod.vodId}:$index" }) { _, vod ->
                    MobilePosterCard(vod) { onVodClick(vod) }
                }
                if (loading) {
                    item(key = "load-more-progress", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MobileGold)
                        }
                    }
                }
                if (loadError != null) {
                    item(key = "load-more-error", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("更多内容暂时没有加载成功", color = MobileMuted, fontSize = 13.sp)
                            Button(onClick = { retry++ }, modifier = Modifier.padding(start = 10.dp)) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSearchScreen(repository: HdaoRepository, onVodClick: (Vod) -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<MobileLoad<List<Vod>>?>(null) }
    fun submit() {
        submitted = query.trim()
        if (submitted.isNotEmpty()) attempt++
    }
    LaunchedEffect(submitted, attempt) {
        if (submitted.isBlank()) return@LaunchedEffect
        state = MobileLoad.Loading
        state = try { MobileLoad.Ready(repository.search(submitted)) }
        catch (error: Exception) { MobileLoad.Failed(error.message ?: "搜索失败") }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("搜索", Modifier.padding(horizontal = 20.dp, vertical = 14.dp), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("片名、演员或导演") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        when (val current = state) {
            null -> Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) { Text("输入关键词查找影片", color = MobileMuted) }
            MobileLoad.Loading -> MobileLoading("正在搜索…")
            is MobileLoad.Failed -> MobileError(current.message) { attempt++ }
            is MobileLoad.Ready -> LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.fillMaxSize().padding(top = 20.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(current.value, key = { it.vodId }) { vod -> MobilePosterCard(vod) { onVodClick(vod) } }
            }
        }
    }
}

@Composable
private fun MobileDetailScreen(
    vodId: Int,
    repository: HdaoRepository,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onVodClick: (Vod) -> Unit,
) {
    var retry by remember(vodId) { mutableIntStateOf(0) }
    var state by remember(vodId) { mutableStateOf<MobileLoad<VodDetail>>(MobileLoad.Loading) }
    LaunchedEffect(vodId, retry) {
        state = try { MobileLoad.Ready(repository.detail(vodId, retry > 0)) }
        catch (error: Exception) { MobileLoad.Failed(error.message ?: "详情加载失败") }
    }
    when (val current = state) {
        MobileLoad.Loading -> MobileLoading("正在加载详情…")
        is MobileLoad.Failed -> MobileError(current.message) { retry++ }
        is MobileLoad.Ready -> {
            val detail = current.value
            val vod = detail.item
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(330.dp)) {
                        AsyncImage(vod.backdropUrl ?: vod.coverUrl, vod.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent, MobileInk))))
                        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(10.dp).background(Color(0x99090B10), RoundedCornerShape(50)).align(Alignment.TopStart)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(vod.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            listOfNotNull(vod.score?.let { "★ $it" }, vod.year, vod.area, vod.genres).joinToString("  ·  "),
                            Modifier.padding(top = 8.dp), color = MobileGold, fontSize = 14.sp,
                        )
                        Text(vod.description ?: "暂无简介", Modifier.padding(top = 14.dp), color = Color(0xFFD2D5DC), fontSize = 15.sp, lineHeight = 22.sp)
                        if (detail.episodes.isNotEmpty()) {
                            MobileActionButton(
                                text = if (detail.episodes.size > 1) "播放第 1 集" else "立即播放",
                                icon = Icons.Rounded.PlayArrow,
                                primary = true,
                                onClick = { onPlay(0) },
                            )
                        }
                    }
                }
                if (detail.episodes.isNotEmpty()) {
                    item {
                        Text("选集 · 共 ${detail.episodes.size} 集", Modifier.padding(start = 20.dp, top = 26.dp, bottom = 10.dp), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(detail.episodes.size) { index ->
                                Button(
                                    onClick = { onPlay(index) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MobilePanel,
                                        contentColor = Color.White,
                                    ),
                                ) {
                                    Text(detail.episodes[index].name)
                                }
                            }
                        }
                    }
                }
                if (detail.related.isNotEmpty()) item { MobileVodRow("相关推荐", detail.related, onVodClick) }
            }
        }
    }
}

@Composable
private fun MobileLoading(message: String = "正在加载…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MobileGold)
            Text(message, Modifier.padding(top = 14.dp), color = MobileMuted)
        }
    }
}

@Composable
private fun MobileError(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("内容暂时没有加载出来", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(message, Modifier.padding(vertical = 12.dp), color = MobileMuted)
            Button(onClick = retry) { Text("重新加载") }
        }
    }
}
