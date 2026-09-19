package tv.hdao.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import tv.hdao.app.data.Vod
import tv.hdao.app.data.WatchEntry

private data class NavigationSpec(
    val key: String,
    val label: String,
    val title: String,
    val icon: ImageVector,
)

@Composable
fun TopNavigation(
    selected: String,
    onSelect: (String, String) -> Unit,
    onCheckUpdate: () -> Unit,
    contentFocusRequester: FocusRequester,
    selectedFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NavigationSpec("home", "首页", "首页", Icons.Rounded.Home),
        NavigationSpec("movie", "电影", "电影", Icons.Rounded.Movie),
        NavigationSpec("tv", "剧集", "电视剧", Icons.Rounded.LiveTv),
        NavigationSpec("variety", "综艺", "综艺", Icons.Rounded.Mic),
        NavigationSpec("documentary", "纪录片", "纪录片", Icons.Rounded.Public),
        NavigationSpec("anime", "动漫", "动漫", Icons.Rounded.Animation),
        NavigationSpec("short-drama", "短剧", "短剧", Icons.Rounded.Bolt),
    )

    Row(
        modifier = modifier.fillMaxWidth().height(60.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF506070A), Color(0xD906070A), Color.Transparent),
                )
            )
            .focusGroup()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            TopNavigationItem(
                item = item,
                selected = selected == item.key,
                onClick = { onSelect(item.key, item.title) },
                contentFocusRequester = contentFocusRequester,
                modifier = if (selected == item.key) {
                    Modifier.focusRequester(selectedFocusRequester)
                } else {
                    Modifier
                },
            )
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.weight(1f))
        // Manual update entry. The automatic check on launch only reacts when a
        // newer release exists, so without this the feature had no visible entry.
        TopNavigationItem(
            item = NavigationSpec("update", "检查更新", "检查更新", Icons.Rounded.Refresh),
            selected = false,
            onClick = onCheckUpdate,
            contentFocusRequester = contentFocusRequester,
        )
        Spacer(Modifier.width(10.dp))
        TopNavigationIconItem(
            icon = Icons.Rounded.Search,
            label = "搜索",
            selected = selected == "search",
            onClick = { onSelect("search", "搜索") },
            contentFocusRequester = contentFocusRequester,
            modifier = if (selected == "search") {
                Modifier.focusRequester(selectedFocusRequester)
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun TopNavigationIconItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            focused -> Color.White
            selected -> Color(0x802B2D32)
            else -> Color.Transparent
        },
        label = "topNavigationIconBackground",
    )
    val foreground = if (focused) Ink else Color.White
    Box(
        modifier = modifier.size(34.dp)
            .focusProperties { down = contentFocusRequester }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(17.dp))
            .background(background)
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = foreground,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun TopNavigationItem(
    item: NavigationSpec,
    selected: Boolean,
    onClick: () -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            focused -> Color.White
            selected -> Color(0x802B2D32)
            else -> Color.Transparent
        },
        label = "topNavigationItemBackground",
    )
    val foreground = when {
        focused -> Ink
        selected -> Color.White
        else -> Color(0xFFD0D3DA)
    }
    Box(
        modifier = modifier.height(38.dp)
            .focusProperties { down = contentFocusRequester }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            item.label,
            color = foreground,
            fontSize = 22.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun LandscapeVodRow(
    title: String,
    items: List<Vod>,
    onVodClick: (Vod) -> Unit,
    onVodFocused: (Vod) -> Unit = {},
    navigationFocusRequester: FocusRequester? = null,
    contentStart: Dp = 146.dp,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
) {
    val fallbackListState = rememberLazyListState()
    Column(modifier) {
        Text(
            title,
            modifier = Modifier.padding(start = contentStart, end = 28.dp, top = 10.dp, bottom = 5.dp),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            state = listState ?: fallbackListState,
            contentPadding = PaddingValues(start = contentStart, end = 28.dp, top = 5.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { index, vod -> "landscape:${vod.vodId}:$index" }) { index, vod ->
                val firstModifier = if (index == 0 && navigationFocusRequester != null) {
                    Modifier.focusProperties { up = navigationFocusRequester }
                } else {
                    Modifier
                }
                LandscapeVodCard(
                    vod = vod,
                    onClick = { onVodClick(vod) },
                    onFocused = { onVodFocused(vod) },
                    modifier = firstModifier,
                )
            }
        }
    }
}

@Composable
private fun LandscapeVodCard(
    vod: Vod,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.055f else 1f, label = "landscapeScale")
    Box(
        modifier = modifier.width(190.dp).height(107.dp).scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(7.dp))
            .border(
                if (focused) BorderStroke(2.dp, Gold) else BorderStroke(1.dp, Color(0xFF2F333B)),
                RoundedCornerShape(7.dp),
            )
            .background(Panel)
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        AsyncImage(
            model = vod.backdropUrl ?: vod.coverUrl,
            contentDescription = vod.title,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x16000000), Color(0xEA050609)))
            )
        )
        vod.score?.let { score ->
            Text(
                text = "★ $score",
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(7.dp)
                    .background(Color(0xDD090B10), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                color = Gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                vod.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            vod.remarks?.let {
                Text(it, color = Color(0xFFD1D4DA), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun ContinueWatchingRow(
    entries: List<WatchEntry>,
    onClick: (WatchEntry) -> Unit,
    navigationFocusRequester: FocusRequester?,
    firstItemFocusRequester: FocusRequester,
    contentStart: Dp = 146.dp,
    listState: LazyListState? = null,
) {
    // Always remembered, so the call order stays stable for callers that pass
    // no state of their own.
    val fallbackListState = rememberLazyListState()
    Column(Modifier.fillMaxWidth().background(Ink)) {
        Text(
            "最近观看",
            modifier = Modifier.padding(start = contentStart, top = 10.dp, bottom = 5.dp),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            state = listState ?: fallbackListState,
            contentPadding = PaddingValues(start = contentStart, end = 28.dp, top = 5.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> "continue:${entry.vodId}:${entry.episodeId}" }) { index, entry ->
                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onClick(entry) },
                    modifier = if (index == 0) {
                        Modifier.focusRequester(firstItemFocusRequester).then(
                            if (navigationFocusRequester != null) {
                                Modifier.focusProperties { up = navigationFocusRequester }
                            } else {
                                Modifier
                            }
                        )
                    } else {
                        Modifier
                    },
                )
            }
        }
        if (entries.isEmpty()) {
            Text(
                "还没有观看记录",
                modifier = Modifier.padding(start = contentStart, top = 8.dp, bottom = 18.dp),
                color = Color(0xFF8F939C),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun PosterVodRow(
    title: String,
    items: List<Vod>,
    onVodClick: (Vod) -> Unit,
    navigationFocusRequester: FocusRequester? = null,
    contentStart: Dp = 146.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            title,
            modifier = Modifier.padding(start = contentStart, end = 28.dp, top = 10.dp, bottom = 5.dp),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            contentPadding = PaddingValues(start = contentStart, end = 28.dp, top = 5.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { index, vod -> "poster-row:${vod.vodId}:$index" }) { index, vod ->
                PosterCard(
                    vod = vod,
                    onClick = { onVodClick(vod) },
                    modifier = if (index == 0 && navigationFocusRequester != null) {
                        Modifier.focusProperties { up = navigationFocusRequester }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(entry: WatchEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.055f else 1f, label = "continueScale")
    Box(
        modifier = modifier.width(190.dp).height(107.dp).scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(7.dp))
            .border(
                if (focused) BorderStroke(2.dp, Gold) else BorderStroke(1.dp, Color(0xFF2F333B)),
                RoundedCornerShape(7.dp),
            )
            .background(Panel)
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        AsyncImage(
            model = entry.imageUrl,
            contentDescription = entry.title,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE5000000)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(entry.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (entry.episodeName.isNotBlank()) {
                Text(entry.episodeName, color = Color(0xFFC8CBD2), fontSize = 10.sp, maxLines = 1)
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF4B4E55))) {
                Box(Modifier.fillMaxWidth(entry.fraction).height(3.dp).background(Gold))
            }
        }
    }
}

@Composable
fun PosterCard(
    vod: Vod,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    cardWidth: Dp = 142.dp,
    cardHeight: Dp = 200.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "posterScale")
    Column(Modifier.width(cardWidth).scale(scale)) {
        Box(
            modifier.width(cardWidth).height(cardHeight)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clip(RoundedCornerShape(8.dp))
                .border(
                    if (focused) BorderStroke(2.dp, Gold) else BorderStroke(1.dp, Color(0xFF343947)),
                    RoundedCornerShape(8.dp),
                )
                .background(Panel)
                .clickable(onClick = onClick)
                .focusable(),
        ) {
            AsyncImage(
                model = vod.coverUrl,
                contentDescription = vod.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            vod.remarks?.let {
                Text(
                    it,
                    modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xCC090B10)).padding(5.dp),
                    color = Gold,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            vod.score?.let { score ->
                Text(
                    text = "★ $score",
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(7.dp)
                        .background(Color(0xDD090B10), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            vod.title,
            modifier = Modifier.padding(top = 7.dp),
            color = if (focused) Color.White else Color(0xFFD2D5DC),
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TvButton(
    text: String,
    primary: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val color by animateColorAsState(
        when {
            focused -> Color.White
            primary -> Gold
            else -> Color(0xD6252931)
        },
        label = "buttonColor",
    )
    Surface(
        modifier = modifier.onFocusChanged { focused = it.isFocused }.focusable(),
        onClick = onClick,
        color = color,
        contentColor = if (focused || primary) Ink else Color.White,
        shape = RoundedCornerShape(7.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.size(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        onClick = onClick,
        color = if (focused) Color.White else Color(0xC922252C),
        contentColor = if (focused) Ink else Color.White,
        shape = RoundedCornerShape(50),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
fun LoadingView(message: String = "正在加载…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(34.dp), color = Gold)
            Spacer(Modifier.height(15.dp))
            Text(message, color = Muted)
        }
    }
}

@Composable
fun ErrorView(message: String, buttonModifier: Modifier = Modifier, retry: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("内容暂时没有加载出来", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Muted, modifier = Modifier.padding(vertical = 10.dp))
            TvButton(
                text = "重新加载",
                primary = true,
                icon = Icons.Rounded.Refresh,
                onClick = retry,
                modifier = buttonModifier,
            )
        }
    }
}

val PlayIcon: ImageVector = Icons.Rounded.PlayArrow
val InfoIcon: ImageVector = Icons.Rounded.Info
