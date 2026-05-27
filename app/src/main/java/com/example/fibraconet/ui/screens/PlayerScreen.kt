package com.example.fibraconet.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.fibraconet.data.model.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    onBack: () -> Unit,
    onPreviousChannel: (() -> Unit)? = null,
    onNextChannel: (() -> Unit)? = null
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    val scope    = rememberCoroutineScope()

    val isTV = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    var showControls  by remember { mutableStateOf(true) }
    var isPlaying     by remember { mutableStateOf(true) }
    var isBuffering   by remember { mutableStateOf(true) }
    var hasError      by remember { mutableStateOf(false) }
    var errorMessage  by remember { mutableStateOf("") }
    var isFullscreen  by remember { mutableStateOf(false) }
    var retryCount    by remember { mutableStateOf(0) }
    var autoRetryCount by remember { mutableStateOf(0) }

    // Ref para cancelar reintentos pendientes al cambiar canal
    val retryJobRef = remember { arrayOfNulls<Job>(1) }

    // Orden de MIME types según tipo de URL
    val mimeTypes = remember(channel.streamUrl) {
        val url = channel.streamUrl
        when {
            url.contains(".m3u8", ignoreCase = true) ->
                listOf(MimeTypes.APPLICATION_M3U8, MimeTypes.VIDEO_MP2T, null)
            url.contains(".mpd", ignoreCase = true) ->
                listOf(MimeTypes.APPLICATION_MPD, MimeTypes.APPLICATION_M3U8, null)
            else -> listOf(MimeTypes.VIDEO_MP2T, MimeTypes.APPLICATION_M3U8, null)
        }
    }

    val exoPlayer: ExoPlayer = remember {
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

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioMimeTypes(
                        MimeTypes.AUDIO_AAC,
                        MimeTypes.AUDIO_MPEG,
                        MimeTypes.AUDIO_MPEG_L2
                    )
                    .setMaxAudioChannelCount(2)
            )
        }

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 60_000, 3_000, 5_000)
                    .build()
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(12))
            )
            .build()
    }

    // Reinicio completo (cambia MIME type o canal)
    fun loadStream(mimeIndex: Int) {
        val url  = channel.streamUrl
        val mime = mimeTypes.getOrNull(mimeIndex)
        val meta = MediaMetadata.Builder()
            .setTitle(channel.name)
            .setArtist(channel.groupTitle.ifBlank { "En vivo" })
            .build()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(meta)
            .apply { if (mime != null) setMimeType(mime) }
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        hasError    = false
        isBuffering = true
    }

    // Reconexión suave: detiene limpiamente y re-prepara sin cambiar el MediaItem
    fun softReconnect() {
        exoPlayer.stop()
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        hasError    = false
        isBuffering = true
    }

    fun setFullscreen(on: Boolean) {
        isFullscreen = on
        activity?.let { act ->
            val window = act.window
            val ctrl = WindowCompat.getInsetsController(window, window.decorView)
            if (on) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ── MediaSession: le indica al sistema (Android TV) que hay reproducción activa ──
    // Sin esto, el televisor aplica el timeout de pantalla normal aunque haya video
    val mediaSession = remember {
        MediaSession.Builder(context, exoPlayer).setId("fibraconet-player").build()
    }

    // ── Pantalla siempre encendida mientras el reproductor está activo ─────────
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setFullscreen(false)
            mediaSession.release()
            exoPlayer.release()
        }
    }

    // ── Listener de eventos del player (se recrea cuando cambia el canal) ─────
    DisposableEffect(channel) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    isPlaying = exoPlayer.isPlaying
                    hasError  = false
                    retryCount     = 0
                    autoRetryCount = 0
                }
                // Stream live terminó momentáneamente → reconectar en silencio
                if (state == Player.STATE_ENDED) {
                    retryJobRef[0]?.cancel()
                    retryJobRef[0] = scope.launch {
                        delay(1_500)
                        softReconnect()
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: PlaybackException) {
                val isNetworkError =
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                val isFormatError =
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED

                val next = retryCount + 1
                when {
                    // Error de formato → cambiar MIME type inmediatamente (no visible para el usuario)
                    isFormatError && next < mimeTypes.size -> {
                        retryCount = next
                        loadStream(next)
                    }
                    // Error de RED → reconexión SUAVE: reutiliza el MediaItem, sin reiniciar el stream visualmente
                    isNetworkError -> {
                        if (autoRetryCount < 20) {
                            autoRetryCount++
                            retryJobRef[0]?.cancel()
                            retryJobRef[0] = scope.launch {
                                hasError = false
                                delay(3_000)
                                softReconnect()
                            }
                        } else {
                            hasError = true; isBuffering = false
                            errorMessage = "Sin señal. Verifica tu conexión."
                        }
                    }
                    // Cualquier otro error → reconexión suave rápida, sin reload completo
                    autoRetryCount < 10 -> {
                        autoRetryCount++
                        retryJobRef[0]?.cancel()
                        retryJobRef[0] = scope.launch {
                            hasError = false
                            delay(3_000)
                            softReconnect()
                        }
                    }
                    else -> {
                        hasError     = true; isBuffering = false
                        errorMessage = "Código: ${error.errorCode} — ${error.cause?.message ?: "Error desconocido"}"
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ── Carga el stream cuando cambia el canal ────────────────────────────────
    LaunchedEffect(channel) {
        retryJobRef[0]?.cancel()
        retryJobRef[0] = null
        retryCount     = 0
        autoRetryCount = 0
        loadStream(0)
    }

    // ── Auto-ocultar controles ────────────────────────────────────────────────
    LaunchedEffect(showControls) {
        if (showControls) { delay(5000); showControls = false }
    }

    BackHandler { if (isFullscreen) setFullscreen(false) else onBack() }

    // Focus para TV (recibe teclas del control remoto)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTV) try { focusRequester.requestFocus() } catch (_: Exception) { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            // D-Pad TV: izquierda/derecha = canal anterior/siguiente
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft  -> { onPreviousChannel?.invoke(); true }
                        Key.DirectionRight -> { onNextChannel?.invoke(); true }
                        Key.DirectionUp, Key.DirectionDown -> { showControls = true; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            showControls = !showControls; true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable { showControls = !showControls }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Buffering ─────────────────────────────────────────────────────────
        AnimatedVisibility(visible = isBuffering && !hasError, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Color(0xFF00D4FF), modifier = Modifier.size(56.dp), strokeWidth = 3.dp)
                    Text("Cargando stream...", color = Color(0xFF8899AA), fontSize = 13.sp)
                }
            }
        }

        // ── Error ─────────────────────────────────────────────────────────────
        AnimatedVisibility(visible = hasError, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF6666), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Error de reproducción", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(errorMessage, color = Color(0xFF8899AA), fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBack) { Text("Volver", color = Color(0xFF8899AA)) }
                        Button(
                            onClick = { retryCount = 0; autoRetryCount = 0; loadStream(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
                        ) { Text("Reintentar", color = Color(0xFF0D1B2A)) }
                    }
                }
            }
        }

        // ── Controles ─────────────────────────────────────────────────────────
        AnimatedVisibility(visible = showControls && !hasError, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Barra superior
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(16.dp).align(Alignment.TopCenter)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerIconButton(
                            onClick = { if (isFullscreen) setFullscreen(false) else onBack() },
                            icon = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        if (channel.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = channel.logoUrl, contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (channel.groupTitle.isNotBlank())
                                Text(channel.groupTitle, color = Color(0xFF8899AA), fontSize = 12.sp)
                        }
                        // PiP solo en móvil (no en TV)
                        if (!isTV) {
                            PlayerIconButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        activity?.enterPictureInPictureMode(
                                            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                        )
                                    }
                                },
                                icon = Icons.Default.PictureInPicture,
                                contentDescription = "PiP",
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        PlayerIconButton(
                            onClick = { setFullscreen(!isFullscreen) },
                            icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Pantalla Completa",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Controles centrales: Anterior / Play-Pause / Siguiente
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onPreviousChannel != null) {
                        PlayerIconButton(
                            onClick = onPreviousChannel,
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Canal Anterior",
                            modifier = Modifier.size(if (isTV) 64.dp else 56.dp),
                            iconSize = if (isTV) 38.dp else 32.dp
                        )
                    } else {
                        Spacer(Modifier.size(if (isTV) 64.dp else 56.dp))
                    }

                    PlayerIconButton(
                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pausa",
                        modifier = Modifier.size(if (isTV) 84.dp else 72.dp),
                        iconSize = if (isTV) 48.dp else 40.dp
                    )

                    if (onNextChannel != null) {
                        PlayerIconButton(
                            onClick = onNextChannel,
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Canal Siguiente",
                            modifier = Modifier.size(if (isTV) 64.dp else 56.dp),
                            iconSize = if (isTV) 38.dp else 32.dp
                        )
                    } else {
                        Spacer(Modifier.size(if (isTV) 64.dp else 56.dp))
                    }
                }

                // Barra inferior
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(16.dp).align(Alignment.BottomCenter)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            channel.name, color = Color.White.copy(alpha = 0.8f),
                            fontSize = if (isTV) 16.sp else 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        LiveBadge(fontSize = if (isTV) 14.sp else 11.sp)
                    }
                }

                // Hint D-Pad en TV
                if (isTV) {
                    Text(
                        "◀ Canal anterior   ▶ Canal siguiente",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveBadge(fontSize: androidx.compose.ui.unit.TextUnit = 11.sp) {
    val pulse = rememberInfiniteTransition(label = "livePulse")
    val dotScale by pulse.animateFloat(
        initialValue = 0.7f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(Color(0xBBCC0000), RoundedCornerShape(5.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .scale(dotScale)
                .background(Color.White, CircleShape)
        )
        Text("EN VIVO", color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun PlayerIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    tintColor: Color = Color.White
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "btnScale")
    val bg = if (isFocused) Color(0xFF00D4FF) else Color(0x66000000)
    val iconTint = if (isFocused) Color(0xFF0D1B2A) else tintColor
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}
