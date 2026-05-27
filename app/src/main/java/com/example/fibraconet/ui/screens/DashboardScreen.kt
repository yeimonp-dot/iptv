package com.example.fibraconet.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import com.example.fibraconet.ui.UpdateState
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.example.fibraconet.R
import com.example.fibraconet.data.model.Channel
import com.example.fibraconet.data.model.ChannelGroup
import com.example.fibraconet.ui.MainViewModel
import com.example.fibraconet.ui.UiState
import kotlinx.coroutines.delay

private enum class DashTab { CANALES, FAVORITOS, RECIENTES }
private enum class ViewType { LIST, GRID }

// ─── Colores base ────────────────────────────────────────────────────────────
private val Cyan    = Color(0xFF00D4FF)
private val BgDeep  = Color(0xFF0D1B2A)
private val BgCard  = Color(0xFF131F2E)
private val BgItem  = Color(0xFF1E2D3D)
private val TextSub = Color(0xFF8899AA)

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onOpenFullscreen: (Channel, List<Channel>) -> Unit,
    onLogout: () -> Unit
) {
    val uiState        by viewModel.uiState.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val favorites      by viewModel.favorites.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val updateState    by viewModel.updateState.collectAsState()

    val context = LocalContext.current
    val isTV = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    var selectedGroupIndex by remember { mutableStateOf(0) }
    var searchQuery        by remember { mutableStateOf("") }
    var showSearch         by remember { mutableStateOf(false) }
    var activeTab          by remember { mutableStateOf(DashTab.CANALES) }
    var viewType           by remember { mutableStateOf(ViewType.LIST) }
    var isPlaying          by remember { mutableStateOf(true) }

    val groups      = (uiState as? UiState.Success)?.groups ?: emptyList()
    val allChannels = (uiState as? UiState.Success)?.allChannels ?: emptyList()

    val displayedChannels = remember(activeTab, selectedGroupIndex, searchQuery, groups, allChannels, favorites, recentChannels) {
        when {
            searchQuery.isNotBlank()     -> allChannels.filter { it.name.contains(searchQuery, ignoreCase = true) }
            activeTab == DashTab.FAVORITOS -> allChannels.filter { favorites.contains(it.id) }
            activeTab == DashTab.RECIENTES -> recentChannels
            groups.isNotEmpty() && selectedGroupIndex < groups.size -> groups[selectedGroupIndex].channels
            else -> allChannels
        }
    }

    // ── ExoPlayer mini (Solo se inicializa en móviles para ahorrar 100% de CPU/Red en TV) ──
    val exoPlayer = remember(isTV) {
        if (isTV) null else {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true)
            
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                .setTsExtractorFlags(
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
                )

            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            ExoPlayer.Builder(context)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(audioAttributes, true)
                .setLoadControl(
                    androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(10_000, 30_000, 2_000, 4_000)
                        .build()
                )
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(12))
                )
                .build()
        }
    }

    // MediaSession: indica al sistema que hay reproducción activa
    val mediaSession = remember(exoPlayer) {
        exoPlayer?.let { player ->
            MediaSession.Builder(context, player).setId("fibraconet-mini").build()
        }
    }
    DisposableEffect(mediaSession) { onDispose { mediaSession?.release() } }

    // Mantener pantalla encendida mientras el mini player tenga un canal activo
    val activity = context as? Activity
    DisposableEffect(currentChannel, isTV) {
        val window = activity?.window
        if (currentChannel != null && !isTV) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (!isTV) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(currentChannel, exoPlayer) {
        val player = exoPlayer
        if (player == null) {
            onDispose {}
        } else {
            val ch = currentChannel
            var mimeIdx = 0
            val mimes = listOf(MimeTypes.VIDEO_MP2T, MimeTypes.APPLICATION_M3U8, null)

            fun loadMini(idx: Int) {
                if (ch == null) return
                val url  = ch.streamUrl
                val mime = when {
                    url.contains(".mpd",  ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                    url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                    else -> mimes.getOrNull(idx)
                }
                val item = if (mime != null) MediaItem.Builder().setUri(url).setMimeType(mime).build()
                           else MediaItem.fromUri(url)
                player.setMediaItem(item)
                player.prepare()
                player.playWhenReady = true
            }

            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) { mimeIdx = 0; isPlaying = player.isPlaying }
                }
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlayerError(error: PlaybackException) {
                    val next = mimeIdx + 1
                    if (next < mimes.size) { mimeIdx = next; loadMini(next) }
                }
            }

            player.addListener(listener)
            if (ch != null) loadMini(0)

            onDispose {
                player.removeListener(listener)
                player.stop()
            }
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer?.release() } }

    // ── TV focus ─────────────────────────────────────────────────────────────
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(displayedChannels.isNotEmpty(), isTV) {
        if (isTV && displayedChannels.isNotEmpty()) {
            try { firstItemFocusRequester.requestFocus() } catch (_: Exception) { }
        }
    }

    // ── Fondo animado ─────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val bgAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgAnim"
    )
    val bgTop    = lerp(Color(0xFF0D1B2A), Color(0xFF091520), bgAnim)
    val bgBottom = lerp(Color(0xFF131F2E), Color(0xFF172333), bgAnim)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom, bgTop)))
    ) {
        if (isTV) {
            TvDashboardLayout(
                viewModel = viewModel,
                groups = groups,
                allChannels = allChannels,
                displayedChannels = displayedChannels,
                favorites = favorites,
                recentChannels = recentChannels,
                selectedGroupIndex = selectedGroupIndex,
                onSelectGroup = { selectedGroupIndex = it },
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                showSearch = showSearch,
                onToggleSearch = { showSearch = it },
                onOpenFullscreen = onOpenFullscreen,
                onLogout = onLogout,
                firstItemFocusRequester = firstItemFocusRequester
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            AnimatedContent(
                                targetState = showSearch,
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                label = "searchAnim"
                            ) { searching ->
                                if (searching) {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Buscar canal...", color = TextSub) },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = Cyan,
                                            focusedIndicatorColor = Cyan,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.fibraconet_logo),
                                        contentDescription = "Fibraconet IPTV",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.height(36.dp).widthIn(max = 180.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            if (showSearch) {
                                IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                                    Icon(Icons.Default.Close, null, tint = Color.White)
                                }
                            } else {
                                IconButton(onClick = { showSearch = true }) {
                                    Icon(Icons.Default.Search, null, tint = Color.White)
                                }
                                IconButton(onClick = {
                                    viewType = if (viewType == ViewType.LIST) ViewType.GRID else ViewType.LIST
                                }) {
                                    Icon(
                                        if (viewType == ViewType.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                        null, tint = Cyan
                                    )
                                }
                                IconButton(onClick = { viewModel.fetchM3U() }) {
                                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                                }
                                IconButton(onClick = onLogout) {
                                    Icon(Icons.Default.Logout, null, tint = Color(0xFFFF6666))
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color(0xFF090F18),
                        tonalElevation = 0.dp
                    ) {
                        val tabs = listOf(
                            Triple(DashTab.CANALES,   Icons.Default.Tv,       "Canales"),
                            Triple(DashTab.FAVORITOS, Icons.Default.Favorite, "Favoritos"),
                            Triple(DashTab.RECIENTES, Icons.Default.History,  "Recientes"),
                        )
                        tabs.forEach { (tab, icon, label) ->
                            val selected = activeTab == tab && searchQuery.isBlank()
                            NavigationBarItem(
                                selected = selected,
                                onClick  = { activeTab = tab; searchQuery = ""; showSearch = false },
                                icon     = { Icon(icon, null) },
                                label    = {
                                    Text(
                                        label,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible
                                    )
                                },
                                alwaysShowLabel = true,
                                colors   = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = if (tab == DashTab.FAVORITOS) Color(0xFFFF4488) else Cyan,
                                    selectedTextColor   = if (tab == DashTab.FAVORITOS) Color(0xFFFF4488) else Cyan,
                                    indicatorColor      = if (tab == DashTab.FAVORITOS) Color(0x222D1E2D) else Color(0xFF1E2D3D),
                                    unselectedIconColor = TextSub,
                                    unselectedTextColor = TextSub
                                )
                            )
                        }
                    }
                },
                containerColor = Color.Transparent
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // Loading bar
                        if (uiState is UiState.Loading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Cyan, trackColor = BgItem
                            )
                        }

                        // Error
                        if (uiState is UiState.Error) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x22FF4444))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Error, null, tint = Color(0xFFFF6666))
                                Text(
                                    (uiState as? UiState.Error)?.message ?: "",
                                    color = Color(0xFFFF9999),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp
                                )
                                TextButton(onClick = { viewModel.fetchM3U() }) {
                                    Text("Reintentar", color = Cyan)
                                }
                            }
                        }

                        // Mini player (solo móvil)
                        AnimatedVisibility(
                            visible = currentChannel != null && !isTV,
                            enter   = slideInVertically(tween(350)) { -it } + fadeIn(tween(350)),
                            exit    = slideOutVertically(tween(350)) { -it } + fadeOut(tween(350))
                        ) {
                            val player = exoPlayer
                            if (player != null) {
                                currentChannel?.let { ch ->
                                    MiniPlayer(
                                        channel   = ch,
                                        exoPlayer = player,
                                        isPlaying = isPlaying,
                                        onPlayPause  = { if (player.isPlaying) player.pause() else player.play(); isPlaying = !isPlaying },
                                        onFullscreen = { onOpenFullscreen(ch, allChannels) },
                                        onClose      = { viewModel.clearChannel(); player.stop() }
                                    )
                                }
                            }
                        }

                        // Group chips (solo en tab Canales, sin búsqueda)
                        AnimatedVisibility(
                            visible = groups.isNotEmpty() && searchQuery.isBlank() && activeTab == DashTab.CANALES,
                            enter   = expandVertically(tween(250)) + fadeIn(tween(250)),
                            exit    = shrinkVertically(tween(250)) + fadeOut(tween(250))
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgDeep)
                                    .padding(vertical = 6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(groups) { index, group ->
                                    GroupChip(group, selectedGroupIndex == index) { selectedGroupIndex = index }
                                }
                            }
                        }

                        // ── Contenido según tab ──────────────────────────────────────
                        AnimatedContent(
                            targetState = if (searchQuery.isNotBlank()) "search" else activeTab.name,
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                            label = "tabContent"
                        ) { target ->

                            val openChannel: (Channel) -> Unit = { channel ->
                                if (currentChannel?.id == channel.id) {
                                    onOpenFullscreen(channel, allChannels)
                                } else {
                                    viewModel.selectChannel(channel)
                                }
                            }

                            when (target) {
                                "search" -> {
                                    if (displayedChannels.isEmpty()) {
                                        EmptyState(Icons.Default.SearchOff, "No se encontraron canales", null)
                                    } else {
                                        ChannelContent(
                                            channels       = displayedChannels,
                                            allChannels    = allChannels,
                                            currentChannel = currentChannel,
                                            favorites      = favorites,
                                            isTV           = false,
                                            viewType       = viewType,
                                            focusRequester = firstItemFocusRequester,
                                            countLabel     = "${displayedChannels.size} resultados para \"$searchQuery\"",
                                            heroChannel    = null,
                                            onToggleFav    = { viewModel.toggleFavorite(it) },
                                            onClick        = openChannel
                                        )
                                    }
                                }

                                DashTab.CANALES.name -> {
                                    if (uiState is UiState.Loading && allChannels.isEmpty()) {
                                        LoadingState()
                                    } else {
                                        ChannelContent(
                                            channels       = displayedChannels,
                                            allChannels    = allChannels,
                                            currentChannel = currentChannel,
                                            favorites      = favorites,
                                            isTV           = false,
                                            viewType       = viewType,
                                            focusRequester = firstItemFocusRequester,
                                            countLabel     = "${displayedChannels.size} canales",
                                            heroChannel    = recentChannels.firstOrNull { it.id != currentChannel?.id },
                                            onToggleFav    = { viewModel.toggleFavorite(it) },
                                            onClick        = openChannel
                                        )
                                    }
                                }

                                DashTab.FAVORITOS.name -> {
                                    if (displayedChannels.isEmpty()) {
                                        EmptyState(
                                            icon       = Icons.Default.FavoriteBorder,
                                            message    = "Aún no tienes favoritos",
                                            subMessage = "Toca ♥ en cualquier canal"
                                        )
                                    } else {
                                        ChannelContent(
                                            channels       = displayedChannels,
                                            allChannels    = allChannels,
                                            currentChannel = currentChannel,
                                            favorites      = favorites,
                                            isTV           = false,
                                            viewType       = viewType,
                                            focusRequester = firstItemFocusRequester,
                                            countLabel     = "${displayedChannels.size} favoritos",
                                            heroChannel    = null,
                                            onToggleFav    = { viewModel.toggleFavorite(it) },
                                            onClick        = openChannel
                                        )
                                    }
                                }

                                DashTab.RECIENTES.name -> {
                                    if (displayedChannels.isEmpty()) {
                                        EmptyState(
                                            icon       = Icons.Default.History,
                                            message    = "No hay canales recientes",
                                            subMessage = "Los canales que veas aparecerán aquí"
                                        )
                                    } else {
                                        ChannelContent(
                                            channels       = displayedChannels,
                                            allChannels    = allChannels,
                                            currentChannel = currentChannel,
                                            favorites      = favorites,
                                            isTV           = false,
                                            viewType       = viewType,
                                            focusRequester = firstItemFocusRequester,
                                            countLabel     = "${displayedChannels.size} recientes",
                                            heroChannel    = null,
                                            onToggleFav    = { viewModel.toggleFavorite(it) },
                                            onClick        = openChannel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Diálogo de Actualización OTA ──
        UpdateDialog(
            updateState = updateState,
            onDismiss = { viewModel.resetUpdateState() },
            onUpdate = { url -> viewModel.startDownload(url) },
            onInstall = { file -> viewModel.installUpdate(file) }
        )
    }
}

// ─── ChannelContent: lista O grid con hero banner opcional ───────────────────
@Composable
private fun ChannelContent(
    channels: List<Channel>,
    allChannels: List<Channel>,
    currentChannel: Channel?,
    favorites: Set<String>,
    isTV: Boolean,
    viewType: ViewType,
    focusRequester: FocusRequester,
    countLabel: String,
    heroChannel: Channel?,
    onToggleFav: (String) -> Unit,
    onClick: (Channel) -> Unit
) {
    if (viewType == ViewType.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isTV) 4 else 2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (heroChannel != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                    HeroBanner(heroChannel, onClick = { onClick(heroChannel) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "count") {
                Text(
                    countLabel, fontSize = 11.sp, color = Color(0xFF556677),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                StaggeredItem(index) {
                    ChannelGridCard(
                        channel    = channel,
                        isActive   = currentChannel?.id == channel.id,
                        isFav      = favorites.contains(channel.id),
                        onToggleFav = { onToggleFav(channel.id) },
                        onClick    = { onClick(channel) }
                    )
                }
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (heroChannel != null) {
                item(key = "hero") {
                    HeroBanner(heroChannel, onClick = { onClick(heroChannel) })
                }
            }
            item(key = "count") {
                Text(
                    countLabel, fontSize = 11.sp, color = Color(0xFF556677),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                val isActive = currentChannel?.id == channel.id
                StaggeredItem(index) {
                    ChannelItem(
                        channel        = channel,
                        isActive       = isActive,
                        isFav          = favorites.contains(channel.id),
                        isTV           = isTV,
                        focusRequester = if (isTV && index == 0) focusRequester else null,
                        onToggleFav    = { onToggleFav(channel.id) },
                        onClick        = { onClick(channel) }
                    )
                }
            }
        }
    }
}

// ─── Stagger wrapper: primeros 12 ítems entran con delay escalonado ───────────
@Composable
private fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    if (index >= 12) { content(); return }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 2 }
    ) {
        content()
    }
}

// ─── Hero banner ─────────────────────────────────────────────────────────────
@Composable
private fun HeroBanner(channel: Channel, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "heroScale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(BgItem)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(180.dp)
    ) {
        // Logo de fondo (muy tenue)
        if (channel.logoUrl.isNotBlank()) {
            AsyncImage(
                model = channel.logoUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.08f
            )
        }
        // Gradiente overlay
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xF00D1B2A), Color(0x880D1B2A), Color.Transparent))
            )
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo del canal
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D1B2A)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = channel.logoUrl, contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else {
                    Icon(Icons.Default.Tv, null, tint = Cyan, modifier = Modifier.size(36.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CONTINUAR VIENDO",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    channel.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.groupTitle.isNotBlank()) {
                    Text(channel.groupTitle, color = TextSub, fontSize = 13.sp, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Cyan)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF0D1B2A), modifier = Modifier.size(16.dp))
                    Text("Ver ahora", color = Color(0xFF0D1B2A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        // Badge LIVE (esquina superior derecha)
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            LiveBadge()
        }
    }
}

// ─── Grid card ────────────────────────────────────────────────────────────────
@Composable
private fun ChannelGridCard(
    channel: Channel,
    isActive: Boolean,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.93f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "cardScale")

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(BgItem)
            .then(
                if (isActive) Modifier.border(2.dp, Cyan, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(150.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D1B2A)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = channel.logoUrl, contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                } else {
                    Icon(Icons.Default.Tv, null, tint = if (isActive) Cyan else Color(0xFF334455), modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                channel.name,
                color = if (isActive) Cyan else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        // Favorito esquina superior derecha
        IconButton(
            onClick = onToggleFav,
            modifier = Modifier.size(32.dp).align(Alignment.TopEnd)
        ) {
            Icon(
                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                null,
                tint = if (isFav) Color(0xFFFF4488) else Color(0xFF334455),
                modifier = Modifier.size(16.dp)
            )
        }
        // Icono "reproduciendo"
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Cyan),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF0D1B2A), modifier = Modifier.size(12.dp))
            }
        }
    }
}

// ─── Mini player (móvil) — tamaño hero ───────────────────────────────────────
@OptIn(UnstableApi::class)
@Composable
private fun MiniPlayer(
    channel: Channel,
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onFullscreen: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(BgItem)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Cyan.copy(alpha = 0.7f), Color(0xFF6B00FF).copy(alpha = 0.4f), Color.Transparent)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .height(180.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video preview (lado izquierdo, ancho fijo para no comerse la info)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(170.dp)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .clickable(onClick = onFullscreen)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer; useController = false
                            keepScreenOn = true
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                    LiveBadge(fontSize = 9.sp)
                }
                Icon(
                    Icons.Default.Fullscreen, null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp)
                )
            }

            // Info del canal
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 8.dp, top = 10.dp, bottom = 10.dp, start = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        if (channel.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = channel.logoUrl, contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .height(22.dp)
                                    .widthIn(max = 70.dp)
                                    .weight(1f, fill = false)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextSub, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        channel.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    if (channel.groupTitle.isNotBlank()) {
                        Text(
                            channel.groupTitle,
                            color = TextSub,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Controles compactos
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Cyan)
                            .clickable(onClick = onPlayPause)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color(0xFF0D1B2A), modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isPlaying) "Pausar" else "Ver",
                            color = Color(0xFF0D1B2A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgCard)
                            .clickable(onClick = onFullscreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.OpenInFull, null, tint = Cyan, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ─── Group chip ───────────────────────────────────────────────────────────────
@Composable
private fun GroupChip(group: ChannelGroup, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected, onClick = onClick,
        label = {
            Text(
                "${group.name} (${group.channels.size})",
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Cyan,
            selectedLabelColor     = Color(0xFF0D1B2A),
            containerColor         = BgItem,
            labelColor             = TextSub
        )
    )
}

// ─── Channel list item ────────────────────────────────────────────────────────
@Composable
private fun ChannelItem(
    channel: Channel,
    isActive: Boolean,
    isFav: Boolean,
    isTV: Boolean,
    focusRequester: FocusRequester?,
    onToggleFav: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val highlighted = isActive || isFocused

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onClick(); true }
                        Key.DirectionRight -> if (isTV) { onToggleFav(); true } else false
                        else -> false
                    }
                } else false
            }
            .background(
                when {
                    isActive  -> Color(0xFF0A3040)
                    isFocused -> Color(0xFF1E3A4A)
                    else      -> Color.Transparent
                }
            )
            .then(if (isFocused) Modifier.border(2.dp, Cyan) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (isTV) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(if (isTV) 64.dp else 50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BgItem)
                .then(
                    if (highlighted) Modifier.border(1.5.dp, Cyan, RoundedCornerShape(10.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl, contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            } else {
                Icon(
                    Icons.Default.Tv, null,
                    tint = if (highlighted) Cyan else Color(0xFF334455),
                    modifier = Modifier.size(if (isTV) 30.dp else 24.dp)
                )
            }
        }

        // Nombre y grupo
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                color = if (highlighted) Cyan else Color.White,
                fontSize = if (isTV) 18.sp else 14.sp,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (channel.groupTitle.isNotBlank()) {
                Text(
                    channel.groupTitle,
                    color = TextSub,
                    fontSize = if (isTV) 13.sp else 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (isTV && isFocused) {
                Text("OK → Ver  •  ▶ → Favorito", color = Cyan.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }

        // Favorito
        if (isTV) {
            Icon(
                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                null,
                tint = if (isFav) Color(0xFFFF4488) else Color(0xFF445566).copy(alpha = if (isFocused) 1f else 0.5f),
                modifier = Modifier.size(20.dp)
            )
        } else {
            IconButton(onClick = onToggleFav, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    null,
                    tint = if (isFav) Color(0xFFFF4488) else Color(0xFF445566),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Estado reproducción
        if (isActive) {
            Icon(Icons.Default.VolumeUp, null, tint = Cyan, modifier = Modifier.size(if (isTV) 22.dp else 18.dp))
        } else if (!isTV) {
            Icon(Icons.Default.PlayCircleOutline, null, tint = Color(0xFF334455), modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(color = Color(0xFF1A2840), thickness = 0.5.dp)
}

// ─── Estados vacío / cargando ─────────────────────────────────────────────────
@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    subMessage: String?
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Color(0xFF334455), modifier = Modifier.size(64.dp))
            Text(message, color = Color(0xFF556677), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subMessage != null) {
                Text(subMessage, color = Color(0xFF445566), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp)
            Text("Cargando canales...", color = TextSub, fontSize = 14.sp)
        }
    }
}

// ─── Composables de Diseño para Android TV (Control Remoto) ───────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvDashboardLayout(
    viewModel: MainViewModel,
    groups: List<ChannelGroup>,
    allChannels: List<Channel>,
    displayedChannels: List<Channel>,
    favorites: Set<String>,
    recentChannels: List<Channel>,
    selectedGroupIndex: Int,
    onSelectGroup: (Int) -> Unit,
    activeTab: DashTab,
    onSelectTab: (DashTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onOpenFullscreen: (Channel, List<Channel>) -> Unit,
    onLogout: () -> Unit,
    firstItemFocusRequester: FocusRequester
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Columna 1: Menú Lateral (Sidebar)
        TvSidebarMenu(
            activeTab = activeTab,
            onSelectTab = onSelectTab,
            showSearch = showSearch,
            onToggleSearch = onToggleSearch,
            onLogout = onLogout,
            firstItemFocusRequester = firstItemFocusRequester
        )
        
        // Columna 2: Lista de Categorías/Grupos (Solo en pestaña de Canales y si no hay búsqueda activa)
        val showGroupsColumn = activeTab == DashTab.CANALES && !showSearch
        if (showGroupsColumn && groups.isNotEmpty()) {
            TvGroupList(
                groups = groups,
                selectedIndex = selectedGroupIndex,
                onSelectGroup = onSelectGroup
            )
        }
        
        // Columna 3: Contenido Principal (Cuadrícula de canales y cabecera)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabecera / Buscador
            if (showSearch) {
                var isSearchFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Buscar canal por nombre...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Cyan) },
                    trailingIcon = {
                        IconButton(onClick = { 
                            onToggleSearch(false)
                            onSearchQueryChange("")
                        }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Color(0xFF1E2D3D),
                        focusedLabelColor = Cyan,
                        unfocusedLabelColor = TextSub,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Cyan,
                        focusedContainerColor = Color(0xFF090F18),
                        unfocusedContainerColor = Color(0xFF090F18),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isSearchFocused = it.isFocused }
                        .border(
                            width = if (isSearchFocused) 1.5.dp else 0.dp,
                            color = if (isSearchFocused) Cyan else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                )
            } else {
                val categoryName = when (activeTab) {
                    DashTab.CANALES -> groups.getOrNull(selectedGroupIndex)?.name ?: "Canales"
                    DashTab.FAVORITOS -> "Favoritos"
                    DashTab.RECIENTES -> "Recientes"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = categoryName.uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${displayedChannels.size} canales disponibles",
                            color = TextSub,
                            fontSize = 12.sp
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color(0xFF131F2E), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = Cyan, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Presiona OK en el control remoto para ver el canal",
                            color = TextSub,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            // Grid de Canales
            if (displayedChannels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.SearchOff else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = Color(0xFF334455),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (showSearch) "No se encontraron canales" else "No hay canales en esta sección",
                            color = TextSub,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(displayedChannels, key = { _, ch -> ch.id }) { index, channel ->
                        val context = LocalContext.current
                        TvChannelGridCard(
                            channel = channel,
                            isActive = viewModel.currentChannel.collectAsState().value?.id == channel.id,
                            isFav = favorites.contains(channel.id),
                            onToggleFav = {
                                val isCurrentlyFav = favorites.contains(channel.id)
                                viewModel.toggleFavorite(channel.id)
                                val msg = if (isCurrentlyFav) "Eliminado de favoritos" else "Añadido a favoritos"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onClick = {
                                viewModel.selectChannel(channel)
                                onOpenFullscreen(channel, displayedChannels)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSidebarMenu(
    activeTab: DashTab,
    onSelectTab: (DashTab) -> Unit,
    showSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onLogout: () -> Unit,
    firstItemFocusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    // Cambiamos el ancho de forma instantánea al tener/perder el foco para evitar
    // animaciones costosas que gatillan re-measurements continuos en toda la jerarquía de TV.
    val width = if (isFocused) 220.dp else 70.dp
    
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(Color(0xFF090F18))
            .onFocusChanged { isFocused = it.hasFocus }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isFocused) {
                Text(
                    text = "FIBRACONET",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(32.dp).align(Alignment.Center)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SidebarItem(
            icon = Icons.Default.Search,
            label = "Buscar",
            isSelected = showSearch,
            isSidebarExpanded = isFocused,
            onClick = { 
                onToggleSearch(true)
                onSelectTab(DashTab.CANALES)
            }
        )
        
        SidebarItem(
            icon = Icons.Default.Tv,
            label = "En Vivo",
            isSelected = !showSearch && activeTab == DashTab.CANALES,
            isSidebarExpanded = isFocused,
            modifier = Modifier.focusRequester(firstItemFocusRequester),
            onClick = { 
                onToggleSearch(false)
                onSelectTab(DashTab.CANALES) 
            }
        )
        
        SidebarItem(
            icon = Icons.Default.Favorite,
            label = "Favoritos",
            isSelected = !showSearch && activeTab == DashTab.FAVORITOS,
            isSidebarExpanded = isFocused,
            onClick = { 
                onToggleSearch(false)
                onSelectTab(DashTab.FAVORITOS) 
            }
        )
        
        SidebarItem(
            icon = Icons.Default.History,
            label = "Recientes",
            isSelected = !showSearch && activeTab == DashTab.RECIENTES,
            isSidebarExpanded = isFocused,
            onClick = { 
                onToggleSearch(false)
                onSelectTab(DashTab.RECIENTES) 
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = Color(0xFF1E2D3D),
            thickness = 1.dp
        )
        Spacer(modifier = Modifier.height(16.dp))

        SidebarItem(
            icon = Icons.Default.Logout,
            label = "Cerrar Sesión",
            isSelected = false,
            isSidebarExpanded = isFocused,
            tint = Color(0xFFFF6666),
            onClick = onLogout
        )
    }
}

@Composable
private fun SidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isSidebarExpanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Cyan,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val itemBg = when {
        isFocused -> Color(0xFF1E2D3D)
        isSelected -> Color(0xFF0D1B2A)
        else -> Color.Transparent
    }
    val itemScale by animateFloatAsState(if (isFocused) 1.04f else 1f, label = "itemScale")
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .scale(itemScale)
            .clip(RoundedCornerShape(8.dp))
            .background(itemBg)
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) Cyan else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isSidebarExpanded) Arrangement.Start else Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected || isFocused) tint else TextSub,
            modifier = Modifier.size(24.dp)
        )
        if (isSidebarExpanded) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = if (isSelected || isFocused) Color.White else TextSub,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvGroupList(
    groups: List<ChannelGroup>,
    selectedIndex: Int,
    onSelectGroup: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color(0xFF0D1B2A).copy(alpha = 0.5f))
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF1E2D3D), Color.Transparent)),
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = "CATEGORÍAS",
                    color = TextSub,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            itemsIndexed(groups) { index, group ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = selectedIndex == index
                val itemBg = when {
                    isFocused -> Color(0xFF1E3A4A)
                    isSelected -> Color(0xFF0C2538)
                    else -> Color.Transparent
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(itemBg)
                        .border(
                            width = if (isFocused) 1.5.dp else 0.dp,
                            color = if (isFocused) Cyan else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSelectGroup(index) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (isSelected || isFocused) Cyan else TextSub,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = group.name,
                        color = if (isSelected || isFocused) Color.White else TextSub,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = group.channels.size.toString(),
                        color = if (isSelected || isFocused) Cyan.copy(alpha = 0.8f) else TextSub.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvChannelGridCard(
    channel: Channel,
    isActive: Boolean,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "cardScale")
    val borderCol = when {
        isFocused -> Cyan
        isActive -> Cyan.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    
    val context = LocalContext.current
    // Downsampling y deshabilitación de crossfade/animaciones para lograr un rendimiento óptimo en TV.
    val imageRequest = remember(channel.logoUrl) {
        ImageRequest.Builder(context)
            .data(channel.logoUrl.ifBlank { null })
            .crossfade(false)
            .size(100, 100)
            .build()
    }

    Box(
        modifier = Modifier
            .scale(cardScale)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(2.dp, borderCol, RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleFav
            )
            .height(130.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF090F18)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = if (isFocused || isActive) Cyan else Color(0xFF334455),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = channel.name,
                color = if (isFocused || isActive) Cyan else Color.White,
                fontSize = 12.sp,
                fontWeight = if (isFocused || isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        
        if (isFav) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF4488),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(14.dp)
            )
        }
        
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Cyan),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color(0xFF090F18),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit,
    onInstall: (File) -> Unit
) {
    if (updateState is UpdateState.Idle) return

    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(enabled = false) {}, // Interceptar clics de fondo
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131F2E)),
            border = BorderStroke(1.5.dp, Cyan.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(420.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(48.dp)
                )

                when (updateState) {
                    is UpdateState.NewVersionAvailable -> {
                        val info = updateState.updateInfo
                        Text(
                            text = "ACTUALIZACIÓN DISPONIBLE",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Versión: v${info.versionName}",
                            color = Cyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = info.releaseNotes,
                            color = TextSub,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2D3D)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Más tarde", color = Color.White)
                            }
                            Button(
                                onClick = { onUpdate(info.apkUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                            ) {
                                Text("Actualizar", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        LaunchedEffect(Unit) {
                            try { focusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    is UpdateState.Downloading -> {
                        val pct = (updateState.progress * 100).toInt()
                        Text(
                            text = "DESCARGANDO ACTUALIZACIÓN",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            color = Cyan,
                            trackColor = Color(0xFF1E2D3D),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "$pct%",
                            color = Cyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text(
                            text = "DESCARGA COMPLETADA",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "La actualización está lista para ser instalada.",
                            color = TextSub,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2D3D)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = Color.White)
                            }
                            Button(
                                onClick = { onInstall(updateState.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                            ) {
                                Text("Instalar", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        LaunchedEffect(Unit) {
                            try { focusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    is UpdateState.Error -> {
                        Text(
                            text = "ERROR DE ACTUALIZACIÓN",
                            color = Color(0xFFFF6666),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = updateState.message,
                            color = TextSub,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2D3D)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        ) {
                            Text("Cerrar", color = Color.White)
                        }
                        LaunchedEffect(Unit) {
                            try { focusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    is UpdateState.Idle -> {}
                }
            }
        }
    }
}
