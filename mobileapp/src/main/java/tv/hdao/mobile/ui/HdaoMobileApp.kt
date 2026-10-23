package tv.hdao.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import tv.hdao.mobile.R

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

    fun openCategory(key: String, title: String) = navigate(MobileScreen.Category(key, title))

    fun openTab(next: MobileScreen) = openRoot(next)

    BackHandler(enabled = screen != MobileScreen.Home, onBack = ::goBack)
    val showBottomBar = screen is MobileScreen.Home || screen is MobileScreen.Search || screen is MobileScreen.Category

    Scaffold(
        containerColor = MobileInk,
        // Background art can extend under the status bar; each header handles it.
        // Scaffold measures the real bottom bar, including its navigation inset.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
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
                            "home" -> openTab(MobileScreen.Home)
                            "search" -> openTab(MobileScreen.Search)
                            "movie" -> openTab(MobileScreen.Category("movie", "电影"))
                            "tv" -> openTab(MobileScreen.Category("tv", "剧集"))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
            when (val current = screen) {
                MobileScreen.Home -> MobileHomeScreen(
                    repository = repository,
                    watchProgress = progress,
                    onVodClick = { navigate(MobileScreen.Detail(it.vodId)) },
                    onPlayVod = { navigate(MobileScreen.Player(it.vodId, 0)) },
                    onContinue = { navigate(MobileScreen.Player(it.vodId, it.episodeIndex)) },
                    onCategorySelect = ::openCategory,
                    onSearch = { openRoot(MobileScreen.Search) },
                )
                MobileScreen.Search -> MobileSearchScreen(repository, ::openCategory) {
                    navigate(MobileScreen.Detail(it.vodId))
                }
                is MobileScreen.Category -> MobileCategoryScreen(current.key, current.title, repository, ::openCategory) {
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
        }
    }
}

@Composable
private fun MobileBottomBar(selected: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("home", "首页", Icons.Rounded.Home),
        Triple("search", "搜索", Icons.Rounded.Search),
        Triple("movie", "电影", Icons.Rounded.Movie),
        Triple("tv", "剧集", Icons.Rounded.Tv),
    )
    NavigationBar(containerColor = MobileInk) {
        items.forEach { (key, label, icon) ->
            NavigationBarItem(
                selected = selected == key,
                onClick = { onSelect(key) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = MobilePanel,
                    unselectedIconColor = MobileMuted,
                    unselectedTextColor = MobileMuted,
                ),
            )
        }
    }
}

@Composable
private fun MobileCategoryTabs(selected: String? = null, onSelect: (String, String) -> Unit) {
    val tabState = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = mobileCategories.indexOfFirst { it.first == selected }
        if (index >= 0) tabState.animateScrollToItem(index)
    }
    LazyRow(
        state = tabState,
        modifier = Modifier.fillMaxWidth().background(Color(0x66000000)),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(mobileCategories, key = { it.first }) { (key, label) ->
            Box(
                Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
                    .background(if (selected == key) MobilePanel else Color.Transparent, RoundedCornerShape(4.dp))
                    .selectable(selected = selected == key, role = Role.Tab, onClick = { onSelect(key, label) })
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected == key) Color.White else MobileMuted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1)
            }
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
    onCategorySelect: (String, String) -> Unit,
    onSearch: () -> Unit,
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
            val heroes = catalog.hero.distinctBy { it.vodId }.ifEmpty {
                catalog.rows.flatMap { it.items }.distinctBy { it.vodId }.take(6)
            }
            val continueEntries = watchProgress.recent()
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
                val layout = mobileHomeLayout(maxHeight.value, LocalDensity.current.fontScale, topInset.value)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "first-screen") {
                        if (heroes.isNotEmpty()) {
                            MobileHomeFirstScreen(heroes, layout, onPlayVod, onVodClick, onCategorySelect, onSearch)
                        } else {
                            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                                MobileCategoryTabs(onSelect = onCategorySelect)
                                Text("片库暂时没有推荐", Modifier.padding(20.dp), color = MobileMuted)
                            }
                        }
                    }
                    item(key = "recent-progress") { MobileContinueRow(continueEntries, onContinue) }
                    catalog.rows.forEach { row ->
                        item(key = "catalog:${row.category}") { MobileVodRow(row.title, row.items.take(20), onVodClick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileHomeFirstScreen(
    vods: List<Vod>,
    layout: MobileHomeLayout,
    onPlay: (Vod) -> Unit,
    onDetails: (Vod) -> Unit,
    onCategorySelect: (String, String) -> Unit,
    onSearch: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { vods.size })
    val railState = rememberLazyListState()
    val currentPage = pagerState.currentPage
    var advance by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentPage, vods.size) {
        railState.animateScrollToItem(currentPage)
        if (vods.size <= 1) return@LaunchedEffect
        delay(6_000L)
        if (!pagerState.isScrollInProgress && !railState.isScrollInProgress) {
            pagerState.animateScrollToPage((currentPage + 1) % vods.size)
        }
    }
    LaunchedEffect(advance) {
        if (advance > 0) pagerState.animateScrollToPage((pagerState.currentPage + 1) % vods.size)
    }
    Column(Modifier.fillMaxWidth().height(layout.firstScreenHeightDp.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            HorizontalPager(state = pagerState, key = { vods[it].vodId }, modifier = Modifier.fillMaxSize()) { page ->
                val vod = vods[page]
                MobileHero(vod, layout, { onPlay(vod) }, { onDetails(vod) })
            }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                if (!layout.compact) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(painterResource(R.drawable.hdao_launcher_art), contentDescription = null, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Search, "搜索", tint = Color.White)
                        }
                    }
                }
                MobileCategoryTabs(onSelect = onCategorySelect)
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("最近热播", color = Color.White, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${currentPage + 1} / ${vods.size}", color = MobileMuted, fontSize = 12.sp)
            IconButton(onClick = { advance++ }, enabled = vods.size > 1, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下一部推荐", tint = Color.White)
            }
        }
        LazyRow(
            state = railState,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            itemsIndexed(vods, key = { _, vod -> vod.vodId }) { index, vod ->
                MobilePosterCard(vod, Modifier.width(layout.posterWidthDp.dp), selected = index == currentPage) { onDetails(vod) }
            }
        }
    }
}

@Composable
private fun MobileHero(vod: Vod, layout: MobileHomeLayout, onPlay: () -> Unit, onDetails: () -> Unit) {
    val compact = layout.compact
    val verticalPadding = if (compact) 12f else 18f
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = vod.backdropUrl ?: vod.coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55000000), Color(0x33000000), MobileInk))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x66000000), Color.Transparent))))
        Column(
            Modifier.align(Alignment.BottomStart).padding(
                start = 18.dp,
                top = (layout.headerHeightDp + layout.copyTopGapDp + verticalPadding).dp,
                end = 18.dp,
                bottom = verticalPadding.dp,
            )
        ) {
            Text(vod.title, color = Color.White, fontSize = if (compact) 26.sp else 32.sp, lineHeight = if (compact) 29.sp else 35.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(vod.score?.let { "★ $it" }, vod.year, vod.area, vod.genres).joinToString("  ·  "),
                modifier = Modifier.padding(top = 6.dp),
                color = Color(0xFFE5E5E5),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Text(
                    vod.description ?: "发现更多精彩内容",
                    modifier = Modifier.padding(top = 8.dp),
                    color = Color(0xFFDDDDDD),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MobileActionButton("播放", Icons.Rounded.PlayArrow, true, onPlay, Modifier.weight(1.55f))
                MobileActionButton("详情", Icons.Rounded.Info, false, onDetails, Modifier.weight(1f))
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
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MobileAccent else Color(0xDD2F2F2F),
            contentColor = if (primary) MobileInk else Color.White,
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MobileVodRow(title: String, vods: List<Vod>, onVodClick: (Vod) -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        Text(title, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vods, key = { it.vodId }) { vod -> MobilePosterCard(vod) { onVodClick(vod) } }
        }
    }
}

@Composable
private fun MobilePosterCard(vod: Vod, modifier: Modifier = Modifier.width(116.dp), selected: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clickable(onClickLabel = "查看详情", onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)).background(MobilePanel)
                .border(1.dp, if (selected) Color(0xFF999999) else Color.Transparent, RoundedCornerShape(4.dp))
        ) {
            AsyncImage(vod.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            vod.score?.let { MobileScoreBadge(it, Modifier.align(Alignment.TopStart)) }
            vod.remarks?.let {
                Text(
                    it,
                    Modifier.align(Alignment.BottomEnd).background(Color(0xCC000000)).padding(horizontal = 5.dp, vertical = 3.dp),
                    color = Color(0xFFE8E8E8),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            (if (selected) "• " else "") + vod.title,
            Modifier.padding(top = 7.dp),
            color = if (selected) Color.White else Color(0xFFD2D2D2),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MobileScoreBadge(score: String, modifier: Modifier = Modifier) {
    Text(
        text = score,
        modifier = modifier.padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 3.dp),
        color = Color(0xFFE8E8E8),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun MobileContinueRow(entries: List<WatchEntry>, onContinue: (WatchEntry) -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        Text("最近观看", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (entries.isEmpty()) {
            Text("还没有观看记录", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = MobileMuted, fontSize = 14.sp)
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.vodId }) { entry ->
                    Column(Modifier.width(220.dp).clickable(onClickLabel = "继续观看") { onContinue(entry) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)).background(MobilePanel)) {
                            AsyncImage(entry.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0xFF4D4D4D))) {
                                Box(Modifier.fillMaxWidth(entry.fraction).height(3.dp).background(MobileAccent))
                            }
                        }
                        Text(entry.title, Modifier.padding(top = 7.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.episodeName, color = MobileMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileCategoryScreen(
    category: String,
    title: String,
    repository: HdaoRepository,
    onCategorySelect: (String, String) -> Unit,
    onVodClick: (Vod) -> Unit,
) {
    val gridState = remember(category) { LazyGridState() }
    var retry by remember(category) { mutableIntStateOf(0) }
    var requestedPage by remember(category) { mutableIntStateOf(1) }
    var loadedPage by remember(category) { mutableIntStateOf(0) }
    var totalPages by remember(category) { mutableIntStateOf(1) }
    var vods by remember(category) { mutableStateOf(emptyList<Vod>()) }
    var loading by remember(category) { mutableStateOf(true) }
    var loadError by remember(category) { mutableStateOf<String?>(null) }
    LaunchedEffect(category, requestedPage, retry) {
        loading = true
        loadError = null
        try {
            val result = repository.category(category, requestedPage)
            vods = if (requestedPage == 1) result.items else mergeVodPages(vods, result.items)
            loadedPage = result.page.coerceAtLeast(requestedPage)
            totalPages = result.totalPages.coerceAtLeast(loadedPage)
        } catch (error: Exception) {
            loadError = error.message ?: "加载失败"
        } finally {
            loading = false
        }
    }
    val shouldLoadMore by remember(category, gridState) {
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
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
        MobileCategoryTabs(selected = category, onSelect = onCategorySelect)
        Text(title, Modifier.padding(horizontal = 18.dp, vertical = 18.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        when {
            vods.isEmpty() && loading -> MobileLoading()
            vods.isEmpty() && loadError != null -> MobileError(loadError ?: "加载失败") { retry++ }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(mobilePosterColumns(maxWidth.value)),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(vods, key = { index, vod -> "${vod.vodId}:$index" }) { _, vod ->
                        MobilePosterCard(vod, Modifier.fillMaxWidth()) { onVodClick(vod) }
                    }
                    if (loading) {
                        item(key = "load-more-progress", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MobileAccent)
                            }
                        }
                    }
                    if (loadError != null) {
                        item(key = "load-more-error", span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("更多内容暂时没有加载成功", color = MobileMuted, fontSize = 13.sp)
                                Button(onClick = { retry++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSearchScreen(repository: HdaoRepository, onCategorySelect: (String, String) -> Unit, onVodClick: (Vod) -> Unit) {
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
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
        MobileCategoryTabs(onSelect = onCategorySelect)
        Text("搜索", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            placeholder = { Text("片名、演员或导演") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        when (val current = state) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("输入关键词查找影片", color = MobileMuted) }
            MobileLoad.Loading -> MobileLoading("正在搜索…")
            is MobileLoad.Failed -> MobileError(current.message) { attempt++ }
            is MobileLoad.Ready -> BoxWithConstraints(Modifier.fillMaxSize().padding(top = 20.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(mobilePosterColumns(maxWidth.value)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(current.value, key = { it.vodId }) { vod -> MobilePosterCard(vod, Modifier.fillMaxWidth()) { onVodClick(vod) } }
                }
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
    var synopsisExpanded by remember(vodId) { mutableStateOf(false) }
    var synopsisOverflows by remember(vodId) { mutableStateOf(false) }
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
            Box(Modifier.fillMaxSize()) {
                AsyncImage(vod.backdropUrl ?: vod.coverUrl, null, Modifier.fillMaxWidth().height(430.dp), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxWidth().height(430.dp).background(Brush.verticalGradient(listOf(Color(0xBB000000), Color(0x99000000), MobileInk))))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 20.dp)) {
                            IconButton(onClick = onBack, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp).size(48.dp).background(Color(0x99000000), RoundedCornerShape(4.dp))) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(vod.coverUrl, null, Modifier.width(90.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)).background(MobilePanel), contentScale = ContentScale.Crop)
                                Column(Modifier.weight(1f)) {
                                    Text(vod.title, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
                                    Text(
                                        listOfNotNull(vod.score?.let { "★ $it" }, vod.year, vod.area, vod.genres, vod.remarks).joinToString("  ·  "),
                                        Modifier.padding(top = 10.dp), color = Color(0xFFE5E5E5), fontSize = 13.sp,
                                    )
                                }
                            }
                            if (detail.episodes.isNotEmpty()) {
                                MobileActionButton(
                                    text = "立即播放",
                                    icon = Icons.Rounded.PlayArrow,
                                    primary = true,
                                    onClick = { onPlay(0) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                                )
                            } else {
                                Text("暂时没有可播放的剧集", Modifier.padding(top = 20.dp), color = MobileMuted)
                            }
                            Text(
                                vod.description ?: "暂无简介",
                                Modifier.padding(top = 14.dp),
                                color = Color(0xFFD0D0D0),
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { if (!synopsisExpanded) synopsisOverflows = it.hasVisualOverflow },
                            )
                            if (synopsisOverflows || synopsisExpanded) {
                                TextButton(
                                    onClick = { synopsisExpanded = !synopsisExpanded },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(if (synopsisExpanded) "收起简介" else "展开简介", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (detail.episodes.isNotEmpty()) {
                        item {
                            Text("选集 · 共 ${detail.episodes.size} 集", Modifier.padding(start = 20.dp, top = 26.dp, bottom = 10.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(detail.episodes.size) { index ->
                                    Button(
                                        onClick = { onPlay(index) },
                                        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 74.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MobilePanel, contentColor = Color.White),
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Text(detail.episodes[index].name)
                                    }
                                }
                            }
                        }
                    }
                    if (detail.related.isNotEmpty()) item { MobileVodRow("你可能还喜欢", detail.related, onVodClick) }
                }
            }
        }
    }
}

@Composable
private fun MobileLoading(message: String = "正在加载…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MobileAccent)
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
            Button(onClick = retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新加载") }
        }
    }
}
