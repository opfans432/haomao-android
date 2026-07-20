package com.catshelf.reader.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.catshelf.reader.data.CatalogDataSource
import com.catshelf.reader.data.CatalogHome
import com.catshelf.reader.data.Comic
import com.catshelf.reader.data.ComicDetail
import com.catshelf.reader.data.FavoritesStore

private enum class Tab(val label: String, val glyph: String) {
    Discover("发现", "◈"), Explore("搜索", "⌕"), Library("书架", "▤"), Settings("设置", "⚙")
}

private sealed interface Route {
    data object Tabs : Route
    data class Detail(val comic: Comic) : Route
    data class Reader(val comic: Comic, val url: String) : Route
}

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

@Composable
fun HaoMaoApp(
    dataSource: CatalogDataSource,
    favoritesStore: FavoritesStore,
    initialAgeConfirmed: Boolean,
    onAgeConfirmed: () -> Unit,
) {
    var ageConfirmed by rememberSaveable { mutableStateOf(initialAgeConfirmed) }
    var route: Route by remember { mutableStateOf(Route.Tabs) }
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Discover) }
    var favorites by remember { mutableStateOf(favoritesStore.load()) }
    var homeRefreshToken by remember { mutableIntStateOf(0) }
    var homeState: LoadState<CatalogHome> by remember { mutableStateOf(LoadState.Loading) }
    val discoverListState = rememberLazyListState()

    LaunchedEffect(ageConfirmed, homeRefreshToken) {
        if (!ageConfirmed) return@LaunchedEffect
        homeState = LoadState.Loading
        homeState = runCatching { dataSource.home() }
            .fold({ LoadState.Ready(it) }, { LoadState.Failed(it.message ?: "加载失败") })
    }

    fun toggleFavorite(comic: Comic) {
        val updated = if (favorites.any { it.id == comic.id }) {
            favorites.filterNot { it.id == comic.id }
        } else {
            listOf(comic) + favorites
        }
        favorites = updated
        favoritesStore.save(updated)
    }

    if (!ageConfirmed) {
        AgeGate {
            ageConfirmed = true
            onAgeConfirmed()
        }
        return
    }

    when (val destination = route) {
        Route.Tabs -> MainTabs(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            dataSource = dataSource,
            homeState = homeState,
            discoverListState = discoverListState,
            onRefreshHome = { homeRefreshToken++ },
            favorites = favorites,
            onComicClick = { route = Route.Detail(it) },
            onRemoveFavorite = ::toggleFavorite,
        )

        is Route.Detail -> {
            BackHandler { route = Route.Tabs }
            DetailScreen(
                comic = destination.comic,
                dataSource = dataSource,
                isFavorite = favorites.any { it.id == destination.comic.id },
                onBack = { route = Route.Tabs },
                onComicClick = { route = Route.Detail(it) },
                onToggleFavorite = { toggleFavorite(destination.comic) },
                onRead = {
                    route = Route.Reader(
                        comic = destination.comic,
                        url = dataSource.readerUrl(destination.comic.id),
                    )
                },
            )
        }

        is Route.Reader -> ReaderScreen(
            title = destination.comic.title,
            url = destination.url,
            onClose = { route = Route.Detail(destination.comic) },
        )
    }
}

@Composable
private fun AgeGate(onContinue: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF39252F), Color(0xFF111014))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("豪帽！", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(
                    "继续前请确认你已年满 18 岁，并同意 NiceCat 公开网页的用户协议与隐私政策。确认后，应用会保存协议状态，避免阅读时重复弹窗。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { checked = !checked }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text("我已年满 18 岁并同意上述条款")
                }
                Button(onClick = onContinue, enabled = checked, modifier = Modifier.fillMaxWidth()) {
                    Text("进入")
                }
            }
        }
    }
}

@Composable
private fun MainTabs(
    selectedTab: Tab,
    onSelectTab: (Tab) -> Unit,
    dataSource: CatalogDataSource,
    homeState: LoadState<CatalogHome>,
    discoverListState: LazyListState,
    onRefreshHome: () -> Unit,
    favorites: List<Comic>,
    onComicClick: (Comic) -> Unit,
    onRemoveFavorite: (Comic) -> Unit,
) {
    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(8.dp),
            ) {
                Tab.entries.forEach { tab ->
                    val active = tab == selectedTab
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            tab.glyph,
                            fontSize = 20.sp,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            tab.label,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                Tab.Discover -> DiscoverScreen(
                    state = homeState,
                    listState = discoverListState,
                    onRefresh = onRefreshHome,
                    onComicClick = onComicClick,
                )

                Tab.Explore -> SearchScreen(dataSource, onComicClick)
                Tab.Library -> LibraryScreen(favorites, onComicClick, onRemoveFavorite)
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    compact: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = 20.dp,
                vertical = if (compact) 8.dp else 18.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "豪帽！",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                lineHeight = 14.sp,
            )
            Text(
                title,
                fontSize = if (compact) 27.sp else 30.sp,
                lineHeight = if (compact) 31.sp else 36.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 13.sp else 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 0.dp),
            ) {
                Text(actionLabel, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DiscoverScreen(
    state: LoadState<CatalogHome>,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onComicClick: (Comic) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header(
            title = "发现",
            subtitle = "切换页面不会重复刷新，按需手动更新",
            actionLabel = "刷新",
            actionEnabled = state !is LoadState.Loading,
            compact = true,
            onAction = onRefresh,
        )
        Box(Modifier.fillMaxSize()) {
            when (state) {
                LoadState.Loading -> LoadingScreen("正在读取真实目录…")
                is LoadState.Failed -> ErrorScreen(state.message, onRefresh)
                is LoadState.Ready -> {
                    val home = state.value
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        home.featured?.let { comic -> item { FeaturedComic(comic, onComicClick) } }
                        home.sections.forEach { section ->
                            item {
                                Text(
                                    section.title,
                                    Modifier.padding(horizontal = 20.dp),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(section.comics, key = { it.id }) { comic ->
                                        ComicCard(comic, Modifier.width(150.dp), onComicClick)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(dataSource: CatalogDataSource, onComicClick: (Comic) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var state: LoadState<List<Comic>> by remember { mutableStateOf(LoadState.Ready(emptyList())) }

    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect
        state = LoadState.Loading
        state = runCatching { dataSource.search(submitted) }
            .fold({ LoadState.Ready(it) }, { LoadState.Failed(it.message ?: "搜索失败") })
    }

    Column(Modifier.fillMaxSize()) {
        Header("搜索", "输入标题、作者或标签")
        Surface(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (query.isBlank()) Text("搜索真实内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { submitted = query.trim() }, enabled = query.isNotBlank()) { Text("搜索") }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val current = state) {
            LoadState.Loading -> LoadingScreen("搜索中…")
            is LoadState.Failed -> ErrorScreen(current.message) { submitted = query.trim() }
            is LoadState.Ready -> {
                if (submitted.isNotBlank() && current.value.isEmpty()) {
                    EmptyMessage("没有找到结果")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(145.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(current.value, key = { it.id }) { comic -> ComicCard(comic, Modifier, onComicClick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    comic: Comic,
    dataSource: CatalogDataSource,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onComicClick: (Comic) -> Unit,
    onToggleFavorite: () -> Unit,
    onRead: () -> Unit,
) {
    var state: LoadState<ComicDetail> by remember(comic.id) { mutableStateOf(LoadState.Loading) }
    LaunchedEffect(comic.id) {
        state = runCatching { dataSource.detail(comic.id) }
            .fold({ LoadState.Ready(it) }, { LoadState.Failed(it.message ?: "详情加载失败") })
    }
    when (val current = state) {
        LoadState.Loading -> LoadingScreen("正在读取详情…", onBack)
        is LoadState.Failed -> ErrorScreen(current.message, onBack)
        is LoadState.Ready -> DetailContent(
            detail = current.value,
            isFavorite = isFavorite,
            onBack = onBack,
            onComicClick = onComicClick,
            onToggleFavorite = onToggleFavorite,
            onRead = onRead,
        )
    }
}

@Composable
private fun DetailContent(
    detail: ComicDetail,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onComicClick: (Comic) -> Unit,
    onToggleFavorite: () -> Unit,
    onRead: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(430.dp).background(Color.Black)) {
                Poster(detail.imageUrl, Modifier.fillMaxSize())
                Text(
                    "←",
                    Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = .65f))
                        .clickable(onClick = onBack)
                        .padding(10.dp),
                    color = Color.White,
                    fontSize = 24.sp,
                )
            }
        }
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(detail.title, fontSize = 25.sp, fontWeight = FontWeight.Black)
                if (detail.alternateTitle.isNotBlank() && detail.alternateTitle != detail.title) {
                    Text(detail.alternateTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (detail.rating.isNotBlank()) Text("评分 ${detail.rating}", color = MaterialTheme.colorScheme.primary)
                    if (detail.uploader.isNotBlank()) Text("上传者 ${detail.uploader}")
                }
                if (detail.updatedAt.isNotBlank()) {
                    Text("更新 ${detail.updatedAt}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(detail.tags.take(18)) { tag ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(99.dp)) {
                            Text(tag, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRead,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(15.dp),
                    ) {
                        Text("应用内阅读", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
                    ) {
                        Text(if (isFavorite) "已收藏" else "收藏")
                    }
                }
                Text(
                    "目录与详情保持原生界面；阅读页在应用内专用阅读器中加载公开页面，并由网站自身完成动态安全校验。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (detail.recommendations.isNotEmpty()) {
                    Text("相关作品", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(detail.recommendations, key = { it.id }) { item ->
                    ComicCard(item, Modifier.width(150.dp), onComicClick)
                }
            }
        }
    }
}

@Composable
private fun FeaturedComic(comic: Comic, onClick: (Comic) -> Unit) {
    Card(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { onClick(comic) },
        shape = RoundedCornerShape(26.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            Poster(comic.imageUrl, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("推荐", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    comic.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun ComicCard(comic: Comic, modifier: Modifier, onClick: (Comic) -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable { onClick(comic) }) {
        Poster(comic.imageUrl, Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(18.dp)))
        Spacer(Modifier.height(8.dp))
        Text(comic.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Poster(url: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
        contentDescription = null,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun LibraryScreen(
    favorites: List<Comic>,
    onComicClick: (Comic) -> Unit,
    onRemoveFavorite: (Comic) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header("书架", "已收藏 ${favorites.size} 部作品")
        if (favorites.isEmpty()) {
            EmptyMessage("在作品详情页点击“收藏”，内容会保存在本机")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(145.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(favorites, key = { it.id }) { comic ->
                    Column {
                        ComicCard(comic, Modifier.fillMaxWidth(), onComicClick)
                        Text(
                            "移出书架",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onRemoveFavorite(comic) }.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("设置", "连接状态与项目说明") }
        item { InfoCard("真实数据模式", "首页、搜索和详情来自 NiceCat 公开网页的 Nuxt 数据，不使用内置虚构目录。") }
        item { InfoCard("本地书架", "收藏记录只保存在本机，可从详情页收藏，也可在书架中移除。") }
        item { InfoCard("应用内阅读", "公开阅读页在应用内专用 WebView 中加载，网站动态校验仍由网站自身执行，不绕过访问控制。") }
        item { InfoCard("第三方声明", "本应用与 NiceCat 无隶属关系，界面、名称和交互均为独立设计。") }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingScreen(message: String, onBack: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator()
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            onBack?.let { OutlinedButton(onClick = it) { Text("返回") } }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("加载失败", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
