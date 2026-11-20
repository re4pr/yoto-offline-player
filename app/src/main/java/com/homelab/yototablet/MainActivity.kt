package com.homelab.yototablet

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.homelab.yototablet.model.OfflineStatus
import com.homelab.yototablet.model.SyncStatusState
import com.homelab.yototablet.model.YotoCardSummary
import com.homelab.yototablet.ui.components.MagicalBackdrop
import com.homelab.yototablet.ui.components.MiniPlayerBar
import com.homelab.yototablet.ui.components.ParentSettingsDialog
import com.homelab.yototablet.ui.components.YotoCardTile
import com.homelab.yototablet.ui.components.YotoPlayerOverlay
import com.homelab.yototablet.ui.theme.YotoTabletTheme
import com.homelab.yototablet.ui.theme.nextMagicalWorld
import com.homelab.yototablet.ui.theme.resolveMagicalWorld
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class PlaylistFilter(val label: String) {
    ALL("All"),
    OFFLINE("Offline"),
    MYO("My Cards"),
    LIBRARY("Yoto"),
}

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* non-blocking: background audio works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        requestNotificationPermissionIfNeeded()

        val app = application as YotoApplication
        val syncManager = app.syncManager
        val playerController = app.playerController

        setContent {
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            val cards by syncManager.cards.collectAsState()
            val cardProgress by syncManager.cardProgress.collectAsState()
            val syncStatus by syncManager.syncStatus.collectAsState()
            val currentBaseUrl by syncManager.baseUrl.collectAsState()
            val playerState by playerController.uiState.collectAsState()

            var currentWorld by remember {
                mutableStateOf(resolveMagicalWorld(syncManager.getSavedThemeVariantId()))
            }
            var selectedFilter by remember { mutableStateOf(PlaylistFilter.ALL) }
            var showPlayerOverlay by remember { mutableStateOf(false) }
            var openingCard by remember { mutableStateOf<YotoCardSummary?>(null) }
            var overlayError by remember { mutableStateOf<String?>(null) }
            var showParentDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                syncManager.triggerAutoSyncIfNeeded()
            }

            LaunchedEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        syncManager.triggerAutoSyncIfNeeded()
                        playerController.refreshProgress()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
            }

            LaunchedEffect(playerState.isActive, playerState.isPlaying, showPlayerOverlay) {
                while (playerState.isActive && (playerState.isPlaying || showPlayerOverlay)) {
                    playerController.refreshProgress()
                    delay(500L)
                }
            }

            val filteredCards = remember(cards, cardProgress, selectedFilter) {
                when (selectedFilter) {
                    PlaylistFilter.ALL -> cards
                    PlaylistFilter.OFFLINE -> cards.filter {
                        cardProgress[it.cardId]?.status == OfflineStatus.DOWNLOADED
                    }
                    PlaylistFilter.MYO -> cards.filter { it.source == "myo" }
                    PlaylistFilter.LIBRARY -> cards.filter { it.source != "myo" }
                }
            }

            YotoTabletTheme(world = currentWorld) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MagicalBackdrop(
                        particleMode = currentWorld.particleMode,
                        accentColor = currentWorld.accentColor,
                        secondaryColor = currentWorld.secondaryColor,
                        surfaceTint = currentWorld.surfaceTint,
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                    ) {
                        HeaderBar(
                            worldLabel = currentWorld.label,
                            syncStatus = syncStatus,
                            accentColor = currentWorld.accentColor,
                            secondaryColor = currentWorld.secondaryColor,
                            onLongHoldTitle = { showParentDialog = true },
                            onCycleWorld = {
                                val next = nextMagicalWorld(currentWorld.id)
                                currentWorld = next
                                syncManager.setSavedThemeVariantId(next.id)
                            },
                            onRefresh = { syncManager.triggerManualSync() },
                        )

                        if (syncStatus.isDownloading || syncStatus.isRefreshingCatalog) {
                            SyncProgressBanner(
                                syncStatus = syncStatus,
                                accentColor = currentWorld.accentColor,
                                secondaryColor = currentWorld.secondaryColor,
                            )
                        }

                        FilterChipsRow(
                            selectedFilter = selectedFilter,
                            cards = cards,
                            offlineCount = cardProgress.values.count { it.status == OfflineStatus.DOWNLOADED },
                            accentColor = currentWorld.accentColor,
                            onSelectFilter = { selectedFilter = it },
                        )

                        if (filteredCards.isEmpty()) {
                            EmptyLibraryState(
                                hasAnyCards = cards.isNotEmpty(),
                                syncStatus = syncStatus,
                                accentColor = currentWorld.accentColor,
                                onRefresh = { syncManager.triggerManualSync() },
                                onResetFilter = { selectedFilter = PlaylistFilter.ALL },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 156.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(
                                    start = 18.dp,
                                    end = 18.dp,
                                    top = 8.dp,
                                    bottom = if (playerState.isActive) 104.dp else 24.dp,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                items(
                                    items = filteredCards,
                                    key = { it.cardId },
                                ) { card ->
                                    val isCurrent = playerState.isActive && playerState.activeCardId == card.cardId
                                    val progress = cardProgress[card.cardId]
                                    YotoCardTile(
                                        card = card,
                                        progress = progress,
                                        coverRemoteUrl = syncManager.apiClient.resolveUrl(card.coverUrl),
                                        accentColor = currentWorld.accentColor,
                                        secondaryColor = currentWorld.secondaryColor,
                                        isActiveCard = isCurrent,
                                        isPlaying = isCurrent && playerState.isPlaying,
                                        isOpening = openingCard?.cardId == card.cardId,
                                        onClick = {
                                            if (isCurrent) {
                                                overlayError = null
                                                showPlayerOverlay = true
                                            } else {
                                                openingCard = card
                                                overlayError = null
                                                showPlayerOverlay = true
                                                scope.launch {
                                                    val res = syncManager.prioritizeAndResolveCard(card)
                                                    if (openingCard?.cardId != card.cardId) return@launch
                                                    openingCard = null
                                                    res.onSuccess { playable ->
                                                        playerController.playCard(playable, startIndex = 0)
                                                    }.onFailure { err ->
                                                        overlayError = err.message ?: "Could not play ${card.title}."
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (playerState.isActive && !showPlayerOverlay) {
                        MiniPlayerBar(
                            playerState = playerState,
                            accentColor = currentWorld.accentColor,
                            secondaryColor = currentWorld.secondaryColor,
                            onExpand = {
                                overlayError = null
                                showPlayerOverlay = true
                            },
                            onPrevTrack = { playerController.previousTrack() },
                            onTogglePlayPause = { playerController.togglePlayPause() },
                            onNextTrack = { playerController.nextTrack() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .systemBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }

                    if (showPlayerOverlay) {
                        val loadingCoverModel: Any? = openingCard?.let { card ->
                            cardProgress[card.cardId]?.coverLocalPath
                                ?.let { File(it) }
                                ?.takeIf { it.isFile && it.length() > 0L }
                                ?: syncManager.apiClient.resolveUrl(card.coverUrl).takeIf { it.isNotBlank() }
                        }
                        YotoPlayerOverlay(
                            playerState = playerState,
                            loadingCard = openingCard,
                            loadingCoverModel = loadingCoverModel,
                            errorMessage = overlayError,
                            accentColor = currentWorld.accentColor,
                            secondaryColor = currentWorld.secondaryColor,
                            onPrevTrack = { playerController.previousTrack() },
                            onTogglePlayPause = { playerController.togglePlayPause() },
                            onNextTrack = { playerController.nextTrack() },
                            onSelectTrack = { idx -> playerController.seekToTrack(idx) },
                            onSeekTo = { posMs -> playerController.seekToPosition(posMs) },
                            onStop = {
                                openingCard = null
                                overlayError = null
                                showPlayerOverlay = false
                                playerController.stop()
                            },
                            onMinimize = {
                                openingCard = null
                                overlayError = null
                                showPlayerOverlay = false
                            },
                        )
                    }

                    if (showParentDialog) {
                        ParentSettingsDialog(
                            currentBaseUrl = currentBaseUrl,
                            syncStatus = syncStatus,
                            accentColor = currentWorld.accentColor,
                            onDismiss = { showParentDialog = false },
                            onForceSyncAll = { syncManager.triggerManualSync() },
                            onSaveBaseUrl = { newUrl -> syncManager.updateBaseUrl(newUrl) },
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun HeaderBar(
    worldLabel: String,
    syncStatus: SyncStatusState,
    accentColor: Color,
    secondaryColor: Color,
    onLongHoldTitle: () -> Unit,
    onCycleWorld: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var releasedOrCancelled = false
                        withTimeoutOrNull(1500L) {
                            waitForUpOrCancellation()
                            releasedOrCancelled = true
                        }
                        if (!releasedOrCancelled) {
                            onLongHoldTitle()
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(accentColor, secondaryColor))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF0A0B10),
                    modifier = Modifier.size(26.dp),
                )
            }

            Column {
                Text(
                    text = BuildConfig.APP_DISPLAY_NAME,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = worldLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9AA3BD),
                    )
                    Icon(
                        imageVector = if (syncStatus.isServerReachable) {
                            Icons.Rounded.OfflinePin
                        } else {
                            Icons.Rounded.CloudOff
                        },
                        contentDescription = null,
                        tint = if (syncStatus.isServerReachable) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = syncStatus.statusMessage.ifBlank { "Ready" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD0D5E4),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderPillButton(
                label = "World",
                icon = Icons.Rounded.AutoAwesome,
                accentColor = accentColor,
                spinning = false,
                onClick = onCycleWorld,
            )
            HeaderPillButton(
                label = if (syncStatus.isDownloading || syncStatus.isRefreshingCatalog) "Syncing" else "Refresh",
                icon = Icons.Rounded.Refresh,
                accentColor = accentColor,
                spinning = syncStatus.isDownloading || syncStatus.isRefreshingCatalog,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun HeaderPillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    spinning: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xB8161929),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.48f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            if (spinning) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SyncProgressBanner(
    syncStatus: SyncStatusState,
    accentColor: Color,
    secondaryColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC141829),
        border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.40f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = syncStatus.statusMessage.ifBlank { "Syncing playlists for offline listening..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (syncStatus.totalDownloadableCards > 0) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${syncStatus.completedCards}/${syncStatus.totalDownloadableCards} ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (syncStatus.isDownloading && syncStatus.currentCardTotalTracks > 0) {
                val trackFraction = (syncStatus.currentTrackIndex.toFloat() / syncStatus.currentCardTotalTracks.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { trackFraction },
                    color = accentColor,
                    trackColor = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            } else {
                LinearProgressIndicator(
                    color = accentColor,
                    trackColor = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: PlaylistFilter,
    cards: List<YotoCardSummary>,
    offlineCount: Int,
    accentColor: Color,
    onSelectFilter: (PlaylistFilter) -> Unit,
) {
    val myoCount = remember(cards) { cards.count { it.source == "myo" } }
    val libCount = remember(cards) { cards.count { it.source != "myo" } }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
    ) {
        items(PlaylistFilter.entries) { filter ->
            val selected = filter == selectedFilter
            val count = when (filter) {
                PlaylistFilter.ALL -> cards.size
                PlaylistFilter.OFFLINE -> offlineCount
                PlaylistFilter.MYO -> myoCount
                PlaylistFilter.LIBRARY -> libCount
            }
            Surface(
                onClick = { onSelectFilter(filter) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) accentColor else Color(0x9E171B2B),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) accentColor else Color.White.copy(alpha = 0.16f),
                ),
            ) {
                Text(
                    text = if (count > 0) "${filter.label} ($count)" else filter.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color(0xFF0A0B10) else Color(0xFFE4E8F5),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    hasAnyCards: Boolean,
    syncStatus: SyncStatusState,
    accentColor: Color,
    onRefresh: () -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xCC141726),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                if (syncStatus.isRefreshingCatalog || syncStatus.isDownloading) {
                    CircularProgressIndicator(color = accentColor)
                    Text(
                        text = "Loading playlists...",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                } else if (hasAnyCards) {
                    Text(
                        text = "No playlists match this filter yet.",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Button(
                        onClick = onResetFilter,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color(0xFF0A0B10),
                        ),
                    ) {
                        Text("Show All Playlists", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Connect to home Wi-Fi once to download playlists",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = syncStatus.statusMessage.ifBlank { "Make sure tablet is on home Wi-Fi" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD0D5E4),
                    )
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color(0xFF0A0B10),
                        ),
                    ) {
                        Text("Try Refresh", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
