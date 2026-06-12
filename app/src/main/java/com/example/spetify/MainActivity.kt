package com.example.spetify

import android.Manifest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// Spotify-like colors
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyBlack = Color(0xFF121212)
val SpotifyDarkGrey = Color(0xFF282828)
val SpotifyLightGrey = Color(0xFFB3B3B3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        requestPermissionsLauncher.launch(permissionsToRequest)

        setContent {
            val viewModel: MusicViewModel = viewModel { MusicViewModel(applicationContext) }
            val appTheme by viewModel.appTheme.collectAsState()

            val currentColorScheme = when (appTheme) {
                "Deep Blue" -> darkColorScheme(
                    primary = Color(0xFF007BFF),
                    background = Color(0xFF000C18),
                    surface = Color(0xFF000C18),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray
                )
                "Dark Purple" -> darkColorScheme(
                    primary = Color(0xFFBB86FC),
                    background = Color(0xFF100010),
                    surface = Color(0xFF100010),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray
                )
                "Sunset" -> darkColorScheme(
                    primary = Color(0xFFFF4E50),
                    background = Color(0xFF1A1A1A),
                    surface = Color(0xFF1A1A1A),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray
                )
                "Pink" -> darkColorScheme(
                    primary = Color(0xFFFF69B4),
                    background = Color(0xFF1A0010),
                    surface = Color(0xFF1A0010),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray
                )
                "Turquoise" -> darkColorScheme(
                    primary = Color(0xFF40E0D0),
                    background = Color(0xFF001A1A),
                    surface = Color(0xFF001A1A),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray
                )
                "Midnight Gold" -> darkColorScheme(
                    primary = Color(0xFFFFD700),
                    background = Color(0xFF000000),
                    surface = Color(0xFF1A1A1A),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray
                )
                "White Theme" -> lightColorScheme(
                    primary = Color(0xFF1DB954),
                    background = Color.White,
                    surface = Color.White,
                    onBackground = Color.Black,
                    onSurface = Color.Black,
                    onSurfaceVariant = Color.DarkGray
                )
                else -> darkColorScheme(
                    primary = SpotifyGreen,
                    background = SpotifyBlack,
                    surface = SpotifyBlack,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = SpotifyLightGrey
                )
            }

            MaterialTheme(colorScheme = currentColorScheme) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as android.app.Activity).window
                        window.statusBarColor = currentColorScheme.background.toArgb()
                        window.navigationBarColor = currentColorScheme.background.toArgb()
                        
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = (appTheme == "White Theme")
                        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = (appTheme == "White Theme")
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicPlayerScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicPlayerScreen(viewModel: MusicViewModel) {
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val directoryContent by viewModel.directoryContent.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val trackInfoToShow by viewModel.showTrackInfo.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val showLyrics by viewModel.showLyrics.collectAsState()
    val showLyricsFull by viewModel.showLyricsFull.collectAsState()
    val showSortDialog by viewModel.showSortDialog.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val showTagEditorTrack by viewModel.showTagEditor.collectAsState()
    val showLyricsSearchMenu by viewModel.showLyricsSearchMenu.collectAsState()
    val showLyricsSearchState by viewModel.showLyricsSearchDialog.collectAsState()
    val showLyricsEditor by viewModel.showLyricsEditor.collectAsState()
    val plainLyrics by viewModel.plainLyrics.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val showVolumeSlider by viewModel.showVolumeSlider.collectAsState()
    val lang by viewModel.appLanguage.collectAsState()
    val autoScrollQueue by viewModel.autoScrollQueue.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerSecondsRemaining.collectAsState()
    val sleepTimerTotal by viewModel.sleepTimerTotalSeconds.collectAsState()

    var activePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackToMoreMenu by remember { mutableStateOf<AudioTrack?>(null) }
    var trackToPlaylistMenu by remember { mutableStateOf<AudioTrack?>(null) }
    var trackIndexToRemoveFromQueue by remember { mutableStateOf<Int?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var trackToDeletePermanently by remember { mutableStateOf<AudioTrack?>(null) }
    val showQueueManager by viewModel.showQueueManager.collectAsState()
    val savedQueues by viewModel.savedQueues.collectAsState(initial = emptyList())

    val currentQueueName by viewModel.currentQueueName.collectAsState()

    val showSettings by viewModel.showSettings.collectAsState()
    val showGlobalMoreMenu by viewModel.showGlobalMoreMenu.collectAsState()
    val showHelp by viewModel.showHelp.collectAsState()

    var showDirectoryMoreMenu by remember { mutableStateOf(false) }
    var showQueueMoreMenu by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onDirectorySelected(it) }
    }

    val artPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { trackToMoreMenu?.let { track -> viewModel.setCustomArt(track.id, it) } }
        trackToMoreMenu = null
    }

    if (pagerState.currentPage == 2) {
        BackHandler(enabled = true) {
            if (!viewModel.navigateBack()) {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            topBar = {
                val isHome = pagerState.currentPage == 1
                Column(
                    modifier = Modifier
                        .background(if (isHome) Color.Transparent else MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabRow(
                            selectedTabIndex = pagerState.currentPage,
                            modifier = Modifier.weight(1f),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = {},
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            val tabs = listOf(
                                Icons.AutoMirrored.Filled.QueueMusic to Localization.getString("queue", lang),
                                Icons.Default.Home to Localization.getString("home", lang),
                                Icons.Default.Folder to Localization.getString("directory", lang),
                                Icons.Default.LibraryMusic to Localization.getString("library", lang),
                                Icons.Default.Search to Localization.getString("search", lang)
                            )
                            tabs.forEachIndexed { index, (icon, title) ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = title,
                                            tint = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleGlobalMoreMenu(true) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 0.dp
                ) { page ->
                    val topPadding = if (page == 1) 0.dp else paddingValues.calculateTopPadding()
                    Box(
                        modifier = Modifier
                            .padding(top = topPadding)
                            .fillMaxSize()
                    ) {
                        when (page) {
                                0 -> { // Queue
                                QueueViewContent(
                                    queue = currentQueue,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    onPlayPauseClick = { viewModel.togglePlayPause() },
                                    onClearClick = { viewModel.clearQueue() },
                                    onTrackClick = { viewModel.playTrackFromQueue(it) },
                                    onMove = { from, to -> viewModel.moveTrackInQueue(from, to) },
                                    onMoreClick = { index, track ->
                                        trackToMoreMenu = track
                                        trackIndexToRemoveFromQueue = index
                                    },
                                    onSortClick = { viewModel.toggleSortDialog(true) },
                                    onGlobalMoreClick = { showQueueMoreMenu = true },
                                    onHeaderClick = { viewModel.toggleQueueManager(true) },
                                    queueName = if (currentQueueName == "Current Queue") Localization.getString("current_queue", lang) else currentQueueName,
                                    queueIndex = savedQueues.indexOfFirst { it.name == currentQueueName }.let { if (it != -1) it + 1 else null },
                                    lang = lang,
                                    autoScroll = autoScrollQueue,
                                    isActivePage = pagerState.currentPage == 0
                                )
                            }
                            1 -> { // Home (Now Playing)
                                NowPlayingScreen(
                                    track = currentTrack,
                                    isPlaying = isPlaying,
                                    currentPosition = currentPosition,
                                    viewModel = viewModel,
                                    isFavorite = isFavorite,
                                    repeatMode = repeatMode,
                                    showLyrics = viewModel.showLyrics.collectAsState().value,
                                    onAddToPlaylistClick = { trackToMoreMenu = it },
                                    onLongPressArt = { trackToMoreMenu = it; trackIndexToRemoveFromQueue = null }
                                )
                            }
                            2 -> { // Directory (Browser)
                                DirectoryExplorer(
                                    content = directoryContent,
                                    onFolderClick = { viewModel.navigateInto(it.uri) },
                                    onTrackClick = { clickedTrack -> 
                                        viewModel.playAll(directoryContent.tracks, sourceName = directoryContent.path.substringAfterLast(" > "))
                                        viewModel.playTrack(clickedTrack, directoryContent.tracks)
                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                    },
                                    onBackClick = { viewModel.navigateBack() },
                                    onSettingsClick = { directoryPickerLauncher.launch(null) },
                                    onMoreClick = { track ->
                                        trackToMoreMenu = track
                                        trackIndexToRemoveFromQueue = null
                                    },
                                    onSortClick = { viewModel.toggleSortDialog(true) },
                                    onFolderMoreClick = { showDirectoryMoreMenu = true },
                                    lang = lang
                                )
                            }
                            3 -> { // Library (Playlists)
                                if (activePlaylist == null) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        LibraryHeader(onCreateClick = { showCreatePlaylistDialog = true })
                                        PlaylistList(
                                            playlists = playlists,
                                            onPlaylistClick = { activePlaylist = it },
                                            onDeleteClick = { playlistToDelete = it }
                                        )
                                    }
                                } else {
                                    val playlistTracksState = viewModel.getPlaylistTracks(activePlaylist!!.id).collectAsState(initial = emptyList())
                                    val playlistTracks = playlistTracksState.value
                                    PlaylistDetailView(
                                        playlist = activePlaylist!!,
                                        tracks = playlistTracks,
                                        onBackClick = { activePlaylist = null },
                                        onTrackClick = { clickedTrack -> 
                                            viewModel.playAll(playlistTracks, sourceName = activePlaylist!!.name)
                                            viewModel.playTrack(clickedTrack, playlistTracks)
                                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                        },
                                        onMoreClick = { track ->
                                            trackToMoreMenu = track
                                            trackIndexToRemoveFromQueue = null
                                        },
                                        onMove = { from, to -> viewModel.moveTrackInPlaylist(activePlaylist!!.id, from, to) }
                                    )
                                }
                            }
                            4 -> { // Search
                                val searchResults by viewModel.searchResults.collectAsState()
                                val query by viewModel.searchQuery.collectAsState()
                                SearchScreen(
                                    viewModel = viewModel,
                                    onTrackClick = { clickedTrack -> 
                                        viewModel.playAll(searchResults, sourceName = "${Localization.getString("search", lang)}: $query")
                                        viewModel.playTrack(clickedTrack, searchResults)
                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                    },
                                    onMoreClick = { track ->
                                        trackToMoreMenu = track
                                        trackIndexToRemoveFromQueue = null
                                    },
                                    lang = lang
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            SettingsScreen(viewModel = viewModel, onBack = { viewModel.toggleSettings(false) })
        }

        if (showHelp) {
            HelpInfoScreen(viewModel = viewModel, onBack = { viewModel.toggleHelp(false) })
        }

        val showVocalRemover by viewModel.showVocalRemoverScreen.collectAsState()
        if (showVocalRemover) {
            VocalRemoverScreen(viewModel = viewModel, onBack = { viewModel.toggleVocalRemoverScreen(false) })
        }
        
        if (showSleepTimerDialog) {
            SleepTimerDialog(
                remainingSeconds = sleepTimerRemaining,
                totalSeconds = sleepTimerTotal,
                onDurationSelected = { 
                    viewModel.setSleepTimer(it)
                },
                onDismiss = { showSleepTimerDialog = false },
                lang = lang
            )
        }
    }

    if (showGlobalMoreMenu) {
        GlobalMoreActionDialog(
            onDismiss = { viewModel.toggleGlobalMoreMenu(false) },
            onSettings = { viewModel.toggleSettings(true) },
            onHelpInfo = { viewModel.toggleHelp(true) },
            onSleepTimer = { showSleepTimerDialog = true },
            onVocalRemover = { viewModel.toggleVocalRemoverScreen(true) },
            onCloseApp = { viewModel.closeApp() },
            lang = lang
        )
    }

    if (showQueueManager) {
        QueueManagerDialog(
            queues = savedQueues,
            onDismiss = { viewModel.toggleQueueManager(false) },
            onQueueClick = { 
                viewModel.loadSavedQueue(it)
                viewModel.toggleQueueManager(false)
            },
            onRename = { q, n -> viewModel.renameSavedQueue(q, n) },
            onDelete = { viewModel.deleteSavedQueue(it) },
            onMove = { from, to -> viewModel.moveSavedQueue(from, to) },
            lang = lang
        )
    }

    if (showVolumeSlider) {
        VolumeDialog(
            volume = volume,
            onVolumeChange = { viewModel.setVolume(it) },
            onDismiss = { viewModel.toggleVolumeSlider() },
            lang = lang
        )
    }

    if (trackInfoToShow != null) {
        TrackInfoDialog(
            track = trackInfoToShow!!,
            onDismiss = { viewModel.dismissInfo() },
            lang = lang
        )
    }

    if (showLyricsSearchMenu != null) {
        LyricsOptionDialog(
            onAutoSearch = { viewModel.startAutoSearchFromMenu(showLyricsSearchMenu!!) },
            onManualSearch = { viewModel.startManualSearchFromMenu(showLyricsSearchMenu!!) },
            onDismiss = { viewModel.closeLyricsMenu() },
            lang = lang
        )
    }

    if (showTagEditorTrack != null) {
        TagEditorDialog(
            track = showTagEditorTrack!!,
            onDismiss = { viewModel.closeTagEditor() },
            onSave = { t, a, alb, aa, comp, g, lyr ->
                viewModel.saveTrackTags(showTagEditorTrack!!.id, t, a, alb, aa, comp, g, lyr)
            },
            onChangeArt = { artPickerLauncher.launch("image/*") },
            onRemoveArt = { viewModel.removeCustomArt(showTagEditorTrack!!.id) },
            onEditLyrics = { 
                viewModel.closeTagEditor()
                viewModel.openLyricsEditor() 
            },
            lang = lang
        )
    }

    if (showDirectoryMoreMenu) {
        DirectoryMoreActionDialog(
            onDismiss = { showDirectoryMoreMenu = false },
            onPlayAll = { 
                viewModel.playAll(directoryContent.tracks, sourceName = directoryContent.path.substringAfterLast(" > "))
                coroutineScope.launch { pagerState.animateScrollToPage(1) }
            },
            onShuffleAll = { 
                viewModel.playAll(directoryContent.tracks, shuffle = true, sourceName = directoryContent.path.substringAfterLast(" > "))
                coroutineScope.launch { pagerState.animateScrollToPage(1) }
            },
            lang = lang
        )
    }

    if (showQueueMoreMenu) {
        QueueMoreActionDialog(
            onDismiss = { showQueueMoreMenu = false },
            onShareQueue = { viewModel.shareQueue(currentQueue) },
            lang = lang
        )
    }

    if (showSortDialog) {
        SortDialog(
            currentOrder = sortOrder,
            onSortSelected = { viewModel.setSortOrder(it) },
            onDismiss = { viewModel.toggleSortDialog(false) },
            lang = lang
        )
    }

    if (showLyricsFull && currentTrack != null) {
        val isLoading by viewModel.isLyricsLoading.collectAsState()
        LyricsFullDialog(
            track = currentTrack!!,
            plainLyrics = plainLyrics ?: (if (isLoading) Localization.getString("loading_lyrics", lang) else Localization.getString("no_lyrics", lang)),
            onDismiss = { viewModel.closeLyricsFull() },
            onEdit = { viewModel.openLyricsEditor() },
            lang = lang
        )
    }

    if (showLyricsEditor && currentTrack != null) {
        LyricsEditorDialog(
            track = currentTrack!!,
            initialText = plainLyrics ?: "",
            onDismiss = { viewModel.closeLyricsEditor() },
            onSave = { viewModel.saveManualLyrics(currentTrack!!.id, it) },
            lang = lang
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
            },
            lang = lang
        )
    }

    if (trackToMoreMenu != null) {
        MoreActionDialog(
            track = trackToMoreMenu!!,
            playlists = playlists,
            onDismiss = { trackToMoreMenu = null; trackIndexToRemoveFromQueue = null },
            onAddToPlaylistClick = {
                // Now handled by a dedicated dialog
                trackToPlaylistMenu = trackToMoreMenu
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onAddToQueue = {
                viewModel.addToQueue(trackToMoreMenu!!)
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onPlayNext = {
                viewModel.playNext(trackToMoreMenu!!)
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onRemoveFromQueue = trackIndexToRemoveFromQueue?.let { index ->
                { 
                    viewModel.removeFromQueue(index)
                    trackIndexToRemoveFromQueue = null
                    trackToMoreMenu = null
                }
            },
            onDeletePermanently = {
                trackToDeletePermanently = trackToMoreMenu
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onShowInfo = {
                viewModel.showInfo(trackToMoreMenu!!)
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onShowTagEditor = {
                viewModel.openTagEditor(trackToMoreMenu!!)
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onChangeArt = {
                artPickerLauncher.launch("image/*")
            },
            onAutoSearchArt = {
                viewModel.autoSearchArt(trackToMoreMenu!!)
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onYouTubeSearchArt = {
                viewModel.searchYouTubeArt(trackToMoreMenu!!)
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onRemoveArt = {
                viewModel.removeCustomArt(trackToMoreMenu!!.id)
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            onDownloadArt = {
                viewModel.downloadTrackArt(trackToMoreMenu!!)
                trackToMoreMenu = null
                trackIndexToRemoveFromQueue = null
            },
            lang = lang
        )
    }

    if (trackToPlaylistMenu != null) {
        PlaylistSelectionDialog(
            track = trackToPlaylistMenu!!,
            playlists = playlists,
            viewModel = viewModel,
            onDismiss = { trackToPlaylistMenu = null },
            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
            lang = lang
        )
    }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(if (lang == "ru") "Удалить плейлист" else "Delete Playlist") },
            text = { Text(if (lang == "ru") "Вы уверены, что хотите удалить '${playlistToDelete?.name}'?" else "Are you sure you want to delete '${playlistToDelete?.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToDelete?.let { viewModel.deletePlaylist(it) }
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(if (lang == "ru") "Удалить" else "Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(Localization.getString("cancel", lang), color = SpotifyLightGrey)
                }
            },
            containerColor = SpotifyDarkGrey
        )
    }

    if (trackToDeletePermanently != null) {
        AlertDialog(
            onDismissRequest = { trackToDeletePermanently = null },
            title = { Text(Localization.getString("delete_permanently", lang)) },
            text = { Text(if (lang == "ru") "Это навсегда удалит '${trackToDeletePermanently?.title}' с вашего устройства. Это действие нельзя отменить." else "This will permanently delete '${trackToDeletePermanently?.title}' from your device. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        trackToDeletePermanently?.let { viewModel.deleteTrackPermanently(it) }
                        trackToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(if (lang == "ru") "Удалить" else "Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDeletePermanently = null }) {
                    Text(Localization.getString("cancel", lang), color = SpotifyLightGrey)
                }
            },
            containerColor = SpotifyDarkGrey
        )
    }

    if (showLyricsSearchState) {
        var manualTitle by remember { mutableStateOf(currentTrack?.title ?: "") }
        var manualArtist by remember { mutableStateOf(currentTrack?.artist ?: "") }
        AlertDialog(
            onDismissRequest = { viewModel.closeLyricsSearch() },
            title = { Text(if (lang == "ru") "Поиск текста" else "Search Lyrics", color = Color.White) },
            text = {
                Column {
                    TextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text(if (lang == "ru") "Название песни" else "Song Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = SpotifyDarkGrey,
                            unfocusedContainerColor = SpotifyDarkGrey
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = manualArtist,
                        onValueChange = { manualArtist = it },
                        label = { Text(if (lang == "ru") "Исполнитель" else "Artist Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = SpotifyDarkGrey,
                            unfocusedContainerColor = SpotifyDarkGrey
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.searchLyricsManual(manualTitle, manualArtist) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (lang == "ru") "Поиск" else "Search", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeLyricsSearch() }) {
                    Text(Localization.getString("cancel", lang), color = SpotifyLightGrey)
                }
            },
            containerColor = SpotifyDarkGrey
        )
    }
}

@Composable
fun QueueManagerDialog(
    queues: List<SavedQueue>,
    onDismiss: () -> Unit,
    onQueueClick: (SavedQueue) -> Unit,
    onRename: (SavedQueue, String) -> Unit,
    onDelete: (SavedQueue) -> Unit,
    onMove: (Int, Int) -> Unit,
    lang: String
) {
    var editingQueue by remember { mutableStateOf<SavedQueue?>(null) }
    var editName by remember { mutableStateOf("") }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifyDarkGrey),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(Localization.getString("queue", lang), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(queues.size, key = { index -> queues[index].id }) { index ->
                        val queue = queues[index]
                        val isDragging = draggedItemIndex == index

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) draggingOffset else 0f
                                    scaleX = if (isDragging) 1.05f else 1f
                                    scaleY = if (isDragging) 1.05f else 1f
                                    shadowElevation = if (isDragging) 8f else 0f
                                }
                                .background(if (isDragging) SpotifyBlack.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { if (draggedItemIndex == null) onQueueClick(queue) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle, 
                                contentDescription = null, 
                                tint = SpotifyLightGrey,
                                modifier = Modifier
                                    .size(32.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { draggedItemIndex = index },
                                            onDragEnd = {
                                                val targetIndex = (index + (draggingOffset / 56.dp.toPx()).toInt())
                                                    .coerceIn(0, queues.size - 1)
                                                if (targetIndex != index) onMove(index, targetIndex)
                                                draggedItemIndex = null
                                                draggingOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedItemIndex = null
                                                draggingOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                draggingOffset += dragAmount.y
                                            }
                                        )
                                    }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            if (editingQueue?.id == queue.id) {
                                TextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    modifier = Modifier.weight(1f),
                                    trailingIcon = {
                                        IconButton(onClick = { onRename(queue, editName); editingQueue = null }) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = SpotifyGreen)
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    text = "${index + 1}. ${queue.name}",
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { editingQueue = queue; editName = queue.name }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = SpotifyLightGrey, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onDelete(queue) }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(Localization.getString("cancel", lang), color = SpotifyLightGrey)
                }
            }
        }
    }
}

@Composable
fun QueueViewContent(
    queue: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onClearClick: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoreClick: (Int, AudioTrack) -> Unit,
    onSortClick: () -> Unit,
    onGlobalMoreClick: () -> Unit,
    onHeaderClick: () -> Unit,
    queueName: String,
    queueIndex: Int?,
    lang: String,
    autoScroll: Boolean,
    isActivePage: Boolean
) {
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val totalDuration = queue.sumOf { it.duration }

    // Auto-scroll to current track when page becomes active or current track changes
    LaunchedEffect(isActivePage, currentTrack, autoScroll) {
        if (autoScroll && isActivePage && currentTrack != null) {
            val index = queue.indexOfFirst { it.id == currentTrack.id }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { onHeaderClick() },
            color = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (queueIndex != null) "$queueIndex. $queueName" else queueName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearClick) {
                    Icon(Icons.Default.Close, contentDescription = Localization.getString("clear_queue", lang), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Control Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${queue.size} ${Localization.getString("songs", lang)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = formatDuration(totalDuration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSortClick) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = Localization.getString("sort_by", lang), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onGlobalMoreClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Localization.getString("empty_queue", lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(queue.size, key = { index -> "${queue[index].id}_$index" }) { index ->
                    val track = queue[index]
                    val isCurrent = track.id == currentTrack?.id
                    val isDragging = draggedItemIndex == index
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) draggingOffset else 0f
                                scaleX = if (isDragging) 1.05f else 1f
                                scaleY = if (isDragging) 1.05f else 1f
                                shadowElevation = if (isDragging) 8f else 0f
                            }
                            .clickable { onTrackClick(index) }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(if (isDragging) (if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFE0E0E0) else SpotifyDarkGrey) else if (isCurrent) (if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF5F5F5) else SpotifyDarkGrey.copy(alpha = 0.5f)) else Color.Transparent, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle, 
                            contentDescription = "Drag", 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                            modifier = Modifier
                                .size(28.dp)
                                .padding(start = 4.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { draggedItemIndex = index },
                                        onDragEnd = {
                                            val targetIndex = (index + (draggingOffset / 64.dp.toPx()).toInt())
                                                .coerceIn(0, queue.size - 1)
                                            if (targetIndex != index) onMove(index, targetIndex)
                                            draggedItemIndex = null
                                            draggingOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggedItemIndex = null
                                            draggingOffset = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            draggingOffset += dragAmount.y
                                        }
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val artModel = getTrackArtModel(track)
                        Box(
                            modifier = Modifier.size(56.dp).background(if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = artModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = android.R.drawable.ic_media_play)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrent) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = formatDuration(track.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 0.dp)
                        )
                        IconButton(onClick = { onMoreClick(index, track) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VocalRemoverScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val currentLang by viewModel.appLanguage.collectAsState()
    val isProcessingVocal by viewModel.isProcessingVocal.collectAsState()
    val processingStatus by viewModel.processingStatus.collectAsState()
    val targetTrack by viewModel.vocalRemoverTargetTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isDualPlayback by viewModel.isDualPlayback.collectAsState()
    val instrumentalVolume by viewModel.instrumentalVolume.collectAsState()
    val vocalsVolume by viewModel.vocalsVolume.collectAsState()
    val hasSavedResult by viewModel.hasSavedResult.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setVocalRemoverTarget(it) }
    }

    Surface(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { /* Block clicks to underlying screens */ }
        },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    text = if (currentLang == "ru") "Удаление вокала" else "Vocal Remover",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // File Selection Area
                OutlinedCard(
                    onClick = { if (!isProcessingVocal) filePickerLauncher.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = targetTrack?.title ?: (if (currentLang == "ru") "Нажмите, чтобы выбрать файл" else "Tap to select audio file"),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            if (targetTrack != null) {
                                Text(
                                    text = if (currentLang == "ru") "Файл выбран" else "File selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.removeVocalFromCurrentTrack() },
                    enabled = !isProcessingVocal && targetTrack != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (currentLang == "ru") "Запустить обработку (AI)" else "Start AI Processing")
                }

                if (isProcessingVocal) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = processingStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Playback Controls
                if (isDualPlayback) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { viewModel.seekDualPlayback(it.toLong()) },
                            valueRange = 0f..(targetTrack?.duration?.toFloat() ?: 1f).coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDuration(currentPosition), style = MaterialTheme.typography.bodySmall)
                            Text(formatDuration(targetTrack?.duration ?: 0L), style = MaterialTheme.typography.bodySmall)
                        }

                        IconButton(
                            onClick = { viewModel.toggleDualPlayPause() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                // Volume Controls
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentLang == "ru") "Инструментал" else "Instrumental",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Slider(
                            value = instrumentalVolume,
                            onValueChange = { viewModel.setInstrumentalVolume(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentLang == "ru") "Вокал" else "Vocals",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Slider(
                            value = vocalsVolume,
                            onValueChange = { viewModel.setVocalsVolume(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.loadPreviousResult() },
                        enabled = hasSavedResult && !isDualPlayback,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (currentLang == "ru") "Загрузить сохран." else "Load Saved")
                    }

                    Button(
                        onClick = { viewModel.saveProcessedFiles() },
                        enabled = isDualPlayback,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (currentLang == "ru") "Сохранить" else "Save")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val currentLang by viewModel.appLanguage.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = Localization.getString("app_settings", currentLang),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* Search in settings placeholder */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Settings Items
        SettingsItem(
            icon = Icons.Default.Language, 
            title = if (currentLang == "ru") "Язык" else "Language",
            subtitle = if (currentLang == "ru") "Русский" else "English",
            onClick = { showLanguageDialog = true }
        )
        SettingsItem(
            icon = Icons.Default.Palette, 
            title = if (currentLang == "ru") "Интерфейс" else "Interface",
            subtitle = currentTheme,
            onClick = { showThemeDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Localization.getString("auto_scroll_queue", currentLang),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = Localization.getString("auto_scroll_desc", currentLang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val autoScroll by viewModel.autoScrollQueue.collectAsState()
            Switch(
                checked = autoScroll,
                onCheckedChange = { viewModel.setAutoScrollQueue(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

        // Vocal Remover Button
        // Moved to dedicated screen
    }

    if (showLanguageDialog) {
        OptionSelectionDialog(
            title = if (currentLang == "ru") "Выберите язык" else "Select Language",
            options = listOf("English", "Русский"),
            currentOption = if (currentLang == "ru") "Русский" else "English",
            onOptionSelected = { 
                viewModel.setLanguage(if (it == "Русский") "ru" else "en")
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showThemeDialog) {
        OptionSelectionDialog(
            title = if (currentLang == "ru") "Выберите тему" else "Select Theme",
            options = listOf("Default", "Deep Blue", "Dark Purple", "Sunset", "Pink", "Turquoise", "Midnight Gold", "White Theme"),
            currentOption = currentTheme,
            onOptionSelected = { 
                viewModel.setTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun HelpInfoScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val lang by viewModel.appLanguage.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = Localization.getString("help_info", lang),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon Placeholder
            Surface(
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "SPETify",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "${Localization.getString("version", lang)}: 1.2.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = Localization.getString("about_desc", lang),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "© 2026 SPETify Team",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OptionSelectionDialog(
    title: String,
    options: List<String>,
    currentOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == currentOption,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = option, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = title, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp) },
        supportingContent = subtitle?.let { { Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun GlobalMoreActionDialog(
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onHelpInfo: () -> Unit,
    onSleepTimer: () -> Unit,
    onVocalRemover: () -> Unit,
    onCloseApp: () -> Unit,
    lang: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Localization.getString("settings", lang), color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(Localization.getString("app_settings", lang), color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onSettings(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(Localization.getString("help_info", lang), color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onHelpInfo(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(Localization.getString("sleep_timer", lang), color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onSleepTimer(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(Localization.getString("vocal_remover", lang), color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.MicExternalOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onVocalRemover(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                ListItem(
                    headlineContent = { Text(Localization.getString("close_app", lang), color = Color.Red) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.clickable { onCloseApp(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Localization.getString("cancel", lang), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey
    )
}

@Composable
fun QueueMoreActionDialog(
    onDismiss: () -> Unit,
    onShareQueue: () -> Unit,
    lang: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Localization.getString("queue", lang)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(Localization.getString("share_songs", lang)) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable { onShareQueue(); onDismiss() }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Localization.getString("cancel", lang), color = SpotifyLightGrey) }
        },
        containerColor = SpotifyDarkGrey
    )
}

@Composable
fun DirectoryMoreActionDialog(
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    lang: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Localization.getString("folder_actions", lang)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(Localization.getString("play_all", lang)) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    modifier = Modifier.clickable { onPlayAll(); onDismiss() }
                )
                ListItem(
                    headlineContent = { Text(Localization.getString("shuffle_all", lang)) },
                    leadingContent = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                    modifier = Modifier.clickable { onShuffleAll(); onDismiss() }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Localization.getString("cancel", lang), color = SpotifyLightGrey) }
        },
        containerColor = SpotifyDarkGrey
    )
}

@Composable
fun DirectoryExplorer(
    content: DirectoryContent,
    onFolderClick: (Folder) -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMoreClick: (AudioTrack) -> Unit,
    onSortClick: () -> Unit,
    onFolderMoreClick: () -> Unit,
    lang: String
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTracks = remember(content.tracks, searchQuery) {
        if (searchQuery.isBlank()) content.tracks
        else content.tracks.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.artist.contains(searchQuery, ignoreCase = true) 
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Localization.getString("back", lang), tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = content.path.substringAfterLast(" > ").ifEmpty { "Root" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = Localization.getString("settings", lang), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Internal Storage > ${content.path}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${content.folders.size} subfolders, ${content.tracks.size} songs  \u23F1 ${formatDuration(content.totalDuration)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(content.folders, key = { it.uri.toString() }) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderClick(folder) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${content.tracks.size} ${Localization.getString("songs", lang)} in this folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSortClick) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = Localization.getString("sort_by", lang), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onFolderMoreClick) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(filteredTracks, key = { it.id }) { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClick(track) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val artModel = getTrackArtModel(track)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = artModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = android.R.drawable.ic_media_play)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatDuration(track.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { onMoreClick(track) }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Search Bar at bottom
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text(text = Localization.getString("search_hint", lang), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey,
                    unfocusedContainerColor = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }
    }
}

@Composable
fun NowPlayingScreen(
    track: AudioTrack?,
    isPlaying: Boolean,
    currentPosition: Long,
    viewModel: MusicViewModel,
    isFavorite: Boolean,
    repeatMode: Int,
    showLyrics: Boolean,
    onAddToPlaylistClick: (AudioTrack) -> Unit,
    onLongPressArt: (AudioTrack) -> Unit
) {
    val volume by viewModel.volume.collectAsState()
    val showVolumeSlider by viewModel.showVolumeSlider.collectAsState()
    val isWhiteTheme = MaterialTheme.colorScheme.background == Color.White
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val artModel = getTrackArtModel(track)
        // Blurred Background removed per request
        if (artModel != null) {
            AsyncImage(
                model = artModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(8.dp),
                contentScale = ContentScale.Crop,
                alpha = if (isWhiteTheme) 0.2f else 0.4f
            )
        }

        // Gradient overlay for better readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = if (isWhiteTheme) 0.6f else 0.8f))
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar - Simplified, icons moved to main navigation
            Spacer(modifier = Modifier.height(64.dp)) // Added space to avoid menu overlap

            Box(
                modifier = Modifier.weight(1.2f).fillMaxWidth(), // Increased weight slightly to push lower elements down
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    LyricsView(
                        viewModel = viewModel,
                        currentPosition = currentPosition,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Album Art Card with Effects
                    var isPressed by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "ArtScale")
                    val alpha by animateFloatAsState(targetValue = if (isPressed) 0.7f else 1f, label = "ArtAlpha")

                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxWidth(0.85f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                            .pointerInput(track) {
                                detectTapGestures(
                                    onPress = {
                                        try {
                                            isPressed = true
                                            awaitRelease()
                                        } finally {
                                            isPressed = false
                                        }
                                    },
                                    onTap = { viewModel.togglePlayPause() },
                                    onLongPress = { track?.let { onLongPressArt(it) } }
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        val artModelInner = getTrackArtModel(track)
                        if (artModelInner != null) {
                            AsyncImage(
                                model = artModelInner,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(if (isWhiteTheme) Color(0xFFF0F0F0) else SpotifyDarkGrey),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Song Info
            Text(
                text = track?.title ?: "Select a track",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = track?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Interaction Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { track?.let { viewModel.toggleFavorite(it) } }) { 
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                        contentDescription = "Favorite", 
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground 
                    ) 
                }
                IconButton(onClick = { viewModel.showInfo(track) }) { Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onBackground) }
                IconButton(onClick = { if (track != null) onAddToPlaylistClick(track) }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist", tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { viewModel.toggleLyrics() }) { 
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Karaoke", 
                        tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    ) 
                }
                IconButton(onClick = { viewModel.toggleRepeatMode() }) { 
                    Icon(
                        imageVector = when(repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                            else -> Icons.Default.Repeat
                        }, 
                        contentDescription = "Repeat", 
                        tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground 
                    ) 
                }
                IconButton(onClick = { viewModel.toggleShuffleMode() }) { 
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onBackground) 
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar
            val duration = track?.duration ?: 0L
            var sliderPosition by remember { mutableFloatStateOf(0f) }
            var isDragging by remember { mutableStateOf(false) }

            // Sync slider with the actual playback position from ViewModel
            LaunchedEffect(currentPosition) {
                if (!isDragging) {
                    sliderPosition = currentPosition.toFloat()
                }
            }

            Slider(
                value = sliderPosition,
                onValueChange = {
                    isDragging = true
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    viewModel.seekTo(sliderPosition.toLong())
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onBackground,
                    activeTrackColor = MaterialTheme.colorScheme.onBackground,
                    inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatDuration(sliderPosition.toLong()), color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                Text(text = formatDuration(duration), color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp)) // Added bottom margin

            // Bottom Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleVolumeSlider() }) { 
                    Icon(
                        imageVector = if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else if (volume < 0.5f) Icons.AutoMirrored.Filled.VolumeDown else Icons.AutoMirrored.Filled.VolumeUp, 
                        contentDescription = "Volume", 
                        tint = MaterialTheme.colorScheme.onBackground 
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.skipPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(48.dp))
                    }
                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.skipNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(48.dp))
                    }
                }

                IconButton(onClick = { viewModel.openLyricsFull() }) { 
                    Icon(Icons.Default.Description, contentDescription = "Full Lyrics", tint = MaterialTheme.colorScheme.onBackground) 
                }
            }
        }
    }
}

@Composable
fun LibraryHeader(onCreateClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        IconButton(onClick = onCreateClick) {
            Icon(Icons.Default.Add, contentDescription = "Create Playlist", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SortDialog(
    currentOrder: MusicViewModel.SortOrder,
    onSortSelected: (MusicViewModel.SortOrder) -> Unit,
    onDismiss: () -> Unit,
    lang: String
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Localization.getString("sort_by", lang),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                MusicViewModel.SortOrder.entries.forEach { order ->
                    val labelKey = when(order) {
                        MusicViewModel.SortOrder.TITLE -> "title_az"
                        MusicViewModel.SortOrder.ARTIST -> "artist_az"
                        MusicViewModel.SortOrder.DURATION -> "duration_longest"
                        MusicViewModel.SortOrder.DATE_ADDED -> "recently_added"
                        MusicViewModel.SortOrder.FILENAME -> "filename"
                        MusicViewModel.SortOrder.SHUFFLE -> "shuffle_all"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onSortSelected(order)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentOrder == order,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = Localization.getString(labelKey, lang), color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackList(
    tracks: List<AudioTrack>,
    onTrackClick: (AudioTrack) -> Unit,
    onMoreClick: (AudioTrack) -> Unit,
    onMove: ((Int, Int) -> Unit)? = null
) {
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn {
        items(tracks.size, key = { index -> "${tracks[index].id}_$index" }) { index ->
            val track = tracks[index]
            val isDragging = draggedItemIndex == index

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) draggingOffset else 0f
                        scaleX = if (isDragging) 1.05f else 1f
                        scaleY = if (isDragging) 1.05f else 1f
                        shadowElevation = if (isDragging) 8f else 0f
                    }
                    .clickable { onTrackClick(track) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(if (isDragging) (if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFE0E0E0) else SpotifyDarkGrey) else Color.Transparent, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val artModel = getTrackArtModel(track)
                Box(
                    modifier = Modifier.size(56.dp).background(if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = artModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = android.R.drawable.ic_media_play)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onBackground, overflow = TextOverflow.Ellipsis)
                    Text(text = "${track.artist} • ${track.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                if (onMove != null) {
                    Icon(
                        imageVector = Icons.Default.DragHandle, 
                        contentDescription = "Drag", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier
                            .size(32.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { draggedItemIndex = index },
                                    onDragEnd = {
                                        val targetIndex = (index + (draggingOffset / 64.dp.toPx()).toInt())
                                            .coerceIn(0, tracks.size - 1)
                                        if (targetIndex != index) onMove(index, targetIndex)
                                        draggedItemIndex = null
                                        draggingOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggedItemIndex = null
                                        draggingOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        draggingOffset += dragAmount.y
                                    }
                                )
                            }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = { onMoreClick(track) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PlaylistList(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onDeleteClick: (Playlist) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = playlist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    Text(text = "Playlist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDeleteClick(playlist) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    tracks: List<AudioTrack>,
    onBackClick: () -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onMoreClick: (AudioTrack) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(text = playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        TrackList(tracks = tracks, onTrackClick = onTrackClick, onMoreClick = onMoreClick, onMove = onMove)
    }
}

@Composable
fun CompactPlayerBar(
    track: AudioTrack,
    isPlaying: Boolean,
    currentPosition: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = SpotifyDarkGrey,
        tonalElevation = 4.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val artModel = getTrackArtModel(track)
                Box(
                    modifier = Modifier.size(40.dp).background(SpotifyBlack, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = artModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = android.R.drawable.ic_media_play)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, fontWeight = FontWeight.Bold)
                    Text(text = track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = SpotifyLightGrey)
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
            // Mini progress bar
            val progress = if (track.duration > 0) currentPosition.toFloat() / track.duration.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color.White,
                trackColor = SpotifyLightGrey.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, lang: String) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "ru") "Новый плейлист" else "New Playlist") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (lang == "ru") "Имя плейлиста" else "Playlist Name") },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = SpotifyDarkGrey,
                    unfocusedContainerColor = SpotifyDarkGrey
                )
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text(if (lang == "ru") "Создать" else "Create", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Localization.getString("cancel", lang), color = SpotifyLightGrey) }
        },
        containerColor = SpotifyDarkGrey
    )
}

@Composable
fun TrackInfoDialog(track: AudioTrack, onDismiss: () -> Unit, lang: String) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Localization.getString("song_info", lang), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(if (lang == "ru") "Название" else "Title", track.title)
                InfoRow(if (lang == "ru") "Исполнитель" else "Artist", track.artist)
                InfoRow(if (lang == "ru") "Альбом" else "Album", track.album)
                InfoRow(if (lang == "ru") "Длительность" else "Duration", formatDuration(track.duration))
                InfoRow(if (lang == "ru") "Формат" else "Format", track.contentUri.path?.substringAfterLast('.')?.uppercase() ?: "Unknown")
                InfoRow(if (lang == "ru") "Путь" else "Location", track.contentUri.path ?: "Unknown")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(Localization.getString("ok", lang), color = MaterialTheme.colorScheme.primary) }
        },
        containerColor = SpotifyDarkGrey
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    onTrackClick: (AudioTrack) -> Unit,
    onMoreClick: (AudioTrack) -> Unit,
    lang: String
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(Localization.getString("search_hint", lang), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = Localization.getString("cancel", lang), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey,
                unfocusedContainerColor = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        if (searchQuery.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text(if (lang == "ru") "Поиск вашей любимой музыки" else "Search for your favorite music", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (searchResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("${if (lang == "ru") "Треки не найдены для" else "No tracks found for"} \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            TrackList(
                tracks = searchResults,
                onTrackClick = onTrackClick,
                onMoreClick = onMoreClick
            )
        }
    }
}

@Composable
fun MoreActionDialog(
    track: AudioTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onRemoveFromQueue: (() -> Unit)? = null,
    onDeletePermanently: () -> Unit,
    onShowInfo: () -> Unit,
    onShowTagEditor: () -> Unit,
    onChangeArt: () -> Unit,
    onAutoSearchArt: () -> Unit,
    onYouTubeSearchArt: () -> Unit,
    onRemoveArt: () -> Unit,
    onDownloadArt: () -> Unit,
    lang: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ListItem(
                    headlineContent = { Text(Localization.getString("edit_tags", lang), color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onShowTagEditor(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(Localization.getString("song_info", lang), color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onShowInfo(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(Localization.getString("save_album_art", lang), color = MaterialTheme.colorScheme.onBackground) },
                    supportingContent = { Text(if (lang == "ru") "Экспорт в галерею" else "Export to gallery", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onDownloadArt(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (onRemoveFromQueue != null) {
                    ListItem(
                        headlineContent = { Text(if (lang == "ru") "Удалить из этой очереди" else "Remove from this queue", color = MaterialTheme.colorScheme.onBackground) },
                        leadingContent = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { onRemoveFromQueue(); onDismiss() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                ListItem(
                    headlineContent = { Text(if (lang == "ru") "Играть после текущей" else "Play after current song", color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onPlayNext(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(if (lang == "ru") "Добавить в очередь" else "Add to a queue", color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onAddToQueue(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(Localization.getString("add_to_playlist", lang), color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onAddToPlaylistClick() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                ListItem(
                    headlineContent = { Text(if (lang == "ru") "Изменить обложку" else "Change Album Art", color = MaterialTheme.colorScheme.onBackground) },
                    supportingContent = { Text(if (lang == "ru") "Выбрать из галереи" else "Select from Gallery", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onChangeArt() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(if (lang == "ru") "Авто-поиск обложки" else "Auto-find Album Art", color = MaterialTheme.colorScheme.onBackground) },
                    supportingContent = { Text("iTunes", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onAutoSearchArt() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(if (lang == "ru") "Найти на YouTube" else "Find on YouTube", color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onYouTubeSearchArt() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(if (lang == "ru") "Удалить обложку" else "Remove Custom Art", color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onRemoveArt() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                ListItem(
                    headlineContent = { Text(Localization.getString("delete_permanently", lang), color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.clickable { onDeletePermanently(); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text(Localization.getString("cancel", lang), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey
    )
}

@Composable
fun LyricsView(
    viewModel: MusicViewModel,
    currentPosition: Long,
    modifier: Modifier = Modifier
) {
    val syncedLyrics by viewModel.syncedLyrics.collectAsState()
    val plainLyrics by viewModel.plainLyrics.collectAsState()
    val isLoading by viewModel.isLyricsLoading.collectAsState()
    val isWhiteTheme = MaterialTheme.colorScheme.background == Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background((if (isWhiteTheme) Color(0xFFF0F0F0) else SpotifyDarkGrey).copy(alpha = 0.6f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (syncedLyrics.isNotEmpty()) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val currentLineIndex = syncedLyrics.indexOfLast { it.timestamp <= currentPosition }
                    
                    LaunchedEffect(currentLineIndex) {
                        if (currentLineIndex != -1) {
                            listState.animateScrollToItem(currentLineIndex)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        items(syncedLyrics.size) { index ->
                            val line = syncedLyrics[index]
                            val isCurrent = index == currentLineIndex
                            Text(
                                text = line.content,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .graphicsLayer {
                                        alpha = if (isCurrent) 1f else 0.5f
                                        scaleX = if (isCurrent) 1.05f else 1f
                                        scaleY = if (isCurrent) 1.05f else 1f
                                    }
                            )
                        }
                    }
                } else if (plainLyrics != null) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text(
                                text = plainLyrics!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    val message = plainLyrics ?: "No lyrics available"
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = message, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (message.startsWith("Error") || message.contains("No lyrics")) {
                            TextButton(onClick = { viewModel.currentTrack.value?.let { viewModel.retryFetchLyrics(it) } }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Small and compact Manual Search Button
                IconButton(
                    onClick = { viewModel.openLyricsSearch() },
                    modifier = Modifier.size(32.dp).padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search, 
                        contentDescription = "Manual Search", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VolumeDialog(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    lang: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "ru") "Громкость" else "Volume Control", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else if (volume < 0.5f) Icons.AutoMirrored.Filled.VolumeDown else Icons.AutoMirrored.Filled.VolumeUp, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onBackground,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(Localization.getString("ok", lang), color = MaterialTheme.colorScheme.primary) }
        },
        containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey
    )
}

@Composable
fun LyricsEditorDialog(
    track: AudioTrack,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    lang: String
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "ru") "Изменить текст" else "Edit Lyrics", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(300.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey,
                    unfocusedContainerColor = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFF0F0F0) else SpotifyDarkGrey
                )
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text(if (lang == "ru") "Сохранить" else "Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Localization.getString("cancel", lang), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey
    )
}

@Composable
fun LyricsFullDialog(
    track: AudioTrack,
    plainLyrics: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    lang: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = plainLyrics, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (lang == "ru") "Закрыть" else "Close", color = MaterialTheme.colorScheme.primary) }
        },
        containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey
    )
}

@Composable
fun TagEditorDialog(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit,
    onChangeArt: () -> Unit,
    onRemoveArt: () -> Unit,
    onEditLyrics: () -> Unit,
    lang: String
) {
    var title by remember { mutableStateOf(track.title) }
    var album by remember { mutableStateOf(track.album) }
    var artist by remember { mutableStateOf(track.artist) }
    var albumArtist by remember { mutableStateOf(track.albumArtist ?: "") }
    var composer by remember { mutableStateOf(track.composer ?: "") }
    var genre by remember { mutableStateOf(track.genre ?: "") }
    var lyricist by remember { mutableStateOf(track.lyricist ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        text = "Tag editor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { 
                        onSave(title, artist, album, albumArtist, composer, genre, lyricist) 
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album Art Section
                    Box(modifier = Modifier.size(200.dp).padding(16.dp)) {
                        val artModel = getTrackArtModel(track)
                        AsyncImage(
                            model = artModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = android.R.drawable.ic_media_play)
                        )
                        
                        // Edit/Delete buttons on top
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FloatingActionButton(
                                onClick = onChangeArt,
                                containerColor = (if (MaterialTheme.colorScheme.background == Color.White) Color.LightGray else SpotifyDarkGrey).copy(alpha = 0.8f),
                                contentColor = Color.White,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Change Art")
                            }
                            FloatingActionButton(
                                onClick = onRemoveArt,
                                containerColor = (if (MaterialTheme.colorScheme.background == Color.White) Color.LightGray else SpotifyDarkGrey).copy(alpha = 0.8f),
                                contentColor = Color.White,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Art")
                            }
                        }
                    }

                    TextButton(onClick = onEditLyrics) {
                        Text(if (lang == "ru") "ИЗМЕНИТЬ ТЕКСТ" else "EDIT EMBEDDED LYRICS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Input Fields
                    TagInputField("Title", title) { title = it }
                    TagInputField("Album", album) { album = it }
                    TagInputField("Artist", artist) { artist = it }
                    TagInputField("Album-Artist", albumArtist) { albumArtist = it }
                    TagInputField("Composer", composer) { composer = it }
                    TagInputField("Genre", genre) { genre = it }
                    TagInputField("Lyricist", lyricist) { lyricist = it }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun TagInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            ),
            singleLine = true
        )
    }
}

@Composable
fun LyricsOptionDialog(
    onAutoSearch: () -> Unit,
    onManualSearch: () -> Unit,
    onDismiss: () -> Unit,
    lang: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "ru") "Поиск текста" else "Lyrics Search", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(if (lang == "ru") "Авто-поиск" else "Auto Search", color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onAutoSearch() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(if (lang == "ru") "Ручной поиск" else "Manual Search", color = MaterialTheme.colorScheme.onBackground) },
                    leadingContent = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onManualSearch() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Localization.getString("cancel", lang), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey
    )
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun PlaylistSelectionDialog(
    track: AudioTrack,
    playlists: List<Playlist>,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    lang: String
) {
    var isAddMode by remember { mutableStateOf(true) }
    val selectedPlaylists = remember { mutableStateListOf<Long>() }
    val containingPlaylists by viewModel.getPlaylistsContainingTrack(track.id).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabButton(
                        text = if (lang == "ru") "Добавить в плейлисты" else "Add to playlists",
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        isSelected = isAddMode,
                        onClick = { isAddMode = true; selectedPlaylists.clear() },
                        lang = lang,
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = if (lang == "ru") "Удалить из плейлистов" else "Remove from playlists",
                        icon = Icons.Default.PlaylistRemove,
                        isSelected = !isAddMode,
                        onClick = { isAddMode = false; selectedPlaylists.clear() },
                        lang = lang,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        text = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                
                val displayPlaylists = if (isAddMode) {
                    playlists.filter { it.id !in containingPlaylists }
                } else {
                    playlists.filter { it.id in containingPlaylists }
                }

                if (displayPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isAddMode) (if (lang == "ru") "Нет плейлистов для добавления" else "No more playlists to add to") else (if (lang == "ru") "Нет в плейлистах" else "Not in any playlists"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(displayPlaylists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (playlist.id in selectedPlaylists) {
                                            selectedPlaylists.remove(playlist.id)
                                        } else {
                                            selectedPlaylists.add(playlist.id)
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = playlist.id in selectedPlaylists,
                                    onCheckedChange = null, // Handled by row clickable
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = playlist.name, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCreatePlaylistClick,
                    modifier = Modifier.fillMaxWidth(),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "ru") "Новый плейлист" else "New Playlist")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedPlaylists.forEach { playlistId ->
                        if (isAddMode) {
                            viewModel.addTrackToPlaylist(playlistId, track)
                        } else {
                            viewModel.removeTrackFromPlaylist(playlistId, track.id)
                        }
                    }
                    onDismiss()
                },
                enabled = selectedPlaylists.isNotEmpty()
            ) {
                Text(if (isAddMode) (if (lang == "ru") "ДОБАВИТЬ" else "ADD") else (if (lang == "ru") "УДАЛИТЬ" else "REMOVE"), color = if (selectedPlaylists.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.getString("cancel", lang).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey
    )
}

@Composable
fun SleepTimerDialog(
    remainingSeconds: Long?,
    totalSeconds: Long?,
    onDurationSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
    lang: String
) {
    var hText by remember { mutableStateOf("00") }
    var mText by remember { mutableStateOf("00") }
    var sText by remember { mutableStateOf("00") }

    val isRunning = remainingSeconds != null && remainingSeconds > 0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.White else SpotifyDarkGrey),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Localization.getString("sleep_timer_title", lang),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                if (isRunning) {
                    // Visual Countdown
                    val progress = if (totalSeconds != null && totalSeconds > 0) remainingSeconds!!.toFloat() / totalSeconds.toFloat() else 0f
                    
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatSecondsToHms(remainingSeconds!!),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onDurationSelected(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Localization.getString("cancel", lang).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Input mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeInputUnit(value = hText, onValueChange = { if (it.length <= 2) hText = it }, label = "HH")
                        Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 4.dp))
                        TimeInputUnit(value = mText, onValueChange = { if (it.length <= 2) mText = it }, label = "MM")
                        Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 4.dp))
                        TimeInputUnit(value = sText, onValueChange = { if (it.length <= 2) sText = it }, label = "SS")
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val h = hText.toLongOrNull() ?: 0L
                            val m = mText.toLongOrNull() ?: 0L
                            val s = sText.toLongOrNull() ?: 0L
                            val total = (h * 3600) + (m * 60) + s
                            if (total > 0) {
                                onDurationSelected(total)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(if (lang == "ru") "ЗАПУСТИТЬ" else "START", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }
                
                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (lang == "ru") "ЗАКРЫТЬ" else "CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TimeInputUnit(value: String, onValueChange: (String) -> Unit, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextField(
            value = value,
            onValueChange = { if (it.all { char -> char.isDigit() }) onValueChange(it) },
            modifier = Modifier.width(64.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatSecondsToHms(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

@Composable
fun TabButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    lang: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                fontSize = 11.sp
            )
        }
    }
}

fun getTrackArtModel(track: AudioTrack?): Any? {
    if (track == null) return null
    val baseUri = if (track.customArtUri != null) Uri.parse(track.customArtUri) else track.contentUri
    return if (track.artVersion > 0L) {
        baseUri.buildUpon().appendQueryParameter("art_ver", track.artVersion.toString()).build()
    } else {
        baseUri
    }
}
