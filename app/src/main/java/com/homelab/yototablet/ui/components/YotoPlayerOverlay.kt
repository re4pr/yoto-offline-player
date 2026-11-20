package com.homelab.yototablet.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.homelab.yototablet.media.PlayerUiState
import com.homelab.yototablet.model.PlayableTrack
import com.homelab.yototablet.model.YotoCardSummary
import com.homelab.yototablet.model.formatDurationMs
import java.util.Locale

@Composable
fun YotoPlayerOverlay(
    playerState: PlayerUiState,
    loadingCard: YotoCardSummary?,
    loadingCoverModel: Any?,
    errorMessage: String?,
    accentColor: Color,
    secondaryColor: Color,
    onPrevTrack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onSeekTo: (Long) -> Unit,
    onStop: () -> Unit,
    onMinimize: () -> Unit,
) {
    BackHandler(onBack = onMinimize)

    val effectiveError = errorMessage ?: playerState.errorMessage
    val coverModel = if (loadingCard != null) loadingCoverModel else playerState.activeCoverModel
    val cardTitle = loadingCard?.title?.takeIf { it.isNotBlank() } ?: playerState.activeCardTitle
    val cardAuthor = loadingCard?.author?.takeIf { it.isNotBlank() } ?: playerState.activeCardAuthor
    val trackListState = rememberLazyListState()

    LaunchedEffect(playerState.currentTrackIndex, playerState.activeCardId) {
        if (playerState.currentTrackIndex in playerState.tracks.indices) {
            runCatching { trackListState.animateScrollToItem(playerState.currentTrackIndex) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB07080F))
            .systemBarsPadding()
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF141726),
            border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.72f)),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                loadingCard != null -> {
                    LoadingPlayerContent(
                        coverModel = coverModel,
                        cardTitle = cardTitle,
                        accentColor = accentColor,
                        secondaryColor = secondaryColor,
                        onMinimize = onMinimize,
                    )
                }
                effectiveError != null && playerState.tracks.isEmpty() -> {
                    ErrorPlayerContent(
                        coverModel = coverModel,
                        cardTitle = cardTitle,
                        errorText = effectiveError,
                        accentColor = accentColor,
                        secondaryColor = secondaryColor,
                        onMinimize = onMinimize,
                    )
                }
                else -> {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                    ) {
                        val isLandscape = maxWidth >= 700.dp
                        if (isLandscape) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(22.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(330.dp)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        YotoArtworkBox(
                                            coverModel = coverModel,
                                            title = cardTitle,
                                            accentColor = accentColor,
                                            secondaryColor = secondaryColor,
                                            size = 176.dp,
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        CardAndTrackMeta(
                                            cardTitle = cardTitle,
                                            cardAuthor = cardAuthor,
                                            playerState = playerState,
                                            accentColor = accentColor,
                                            secondaryColor = secondaryColor,
                                            centered = true,
                                        )
                                    }
                                    TransportAndProgressBlock(
                                        playerState = playerState,
                                        effectiveError = effectiveError,
                                        accentColor = accentColor,
                                        onPrevTrack = onPrevTrack,
                                        onTogglePlayPause = onTogglePlayPause,
                                        onNextTrack = onNextTrack,
                                        onSeekTo = onSeekTo,
                                        onStop = onStop,
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    TracksPaneHeader(
                                        accentColor = accentColor,
                                        onMinimize = onMinimize,
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    TrackListColumn(
                                        tracks = playerState.tracks,
                                        currentTrackIndex = playerState.currentTrackIndex,
                                        isPlaying = playerState.isPlaying,
                                        accentColor = accentColor,
                                        trackListState = trackListState,
                                        onSelectTrack = onSelectTrack,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    YotoArtworkBox(
                                        coverModel = coverModel,
                                        title = cardTitle,
                                        accentColor = accentColor,
                                        secondaryColor = secondaryColor,
                                        size = 92.dp,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        CardAndTrackMeta(
                                            cardTitle = cardTitle,
                                            cardAuthor = cardAuthor,
                                            playerState = playerState,
                                            accentColor = accentColor,
                                            secondaryColor = secondaryColor,
                                            centered = false,
                                        )
                                    }
                                    Surface(
                                        onClick = onMinimize,
                                        shape = CircleShape,
                                        color = Color(0xFF23273D),
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = "Keep playing",
                                                tint = Color.White,
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                TransportAndProgressBlock(
                                    playerState = playerState,
                                    effectiveError = effectiveError,
                                    accentColor = accentColor,
                                    onPrevTrack = onPrevTrack,
                                    onTogglePlayPause = onTogglePlayPause,
                                    onNextTrack = onNextTrack,
                                    onSeekTo = onSeekTo,
                                    onStop = onStop,
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Tracks & Chapters",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                TrackListColumn(
                                    tracks = playerState.tracks,
                                    currentTrackIndex = playerState.currentTrackIndex,
                                    isPlaying = playerState.isPlaying,
                                    accentColor = accentColor,
                                    trackListState = trackListState,
                                    onSelectTrack = onSelectTrack,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingPlayerContent(
    coverModel: Any?,
    cardTitle: String,
    accentColor: Color,
    secondaryColor: Color,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YotoArtworkBox(
                coverModel = coverModel,
                title = cardTitle,
                accentColor = accentColor,
                secondaryColor = secondaryColor,
                size = 160.dp,
            )
            Text(
                text = cardTitle.ifBlank { "Yoto Playlist" },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Preparing playlist audio...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD0D5E4),
                )
            }
            OverlayPillAction(
                label = "Cancel",
                icon = Icons.Rounded.Close,
                accentColor = accentColor,
                highlighted = false,
                onClick = onMinimize,
            )
        }
    }
}

@Composable
private fun ErrorPlayerContent(
    coverModel: Any?,
    cardTitle: String,
    errorText: String,
    accentColor: Color,
    secondaryColor: Color,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YotoArtworkBox(
                coverModel = coverModel,
                title = cardTitle,
                accentColor = accentColor,
                secondaryColor = secondaryColor,
                size = 144.dp,
            )
            Text(
                text = cardTitle.ifBlank { "Yoto Playback" },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFB4AB),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            OverlayPillAction(
                label = "Close",
                icon = Icons.Rounded.Close,
                accentColor = accentColor,
                highlighted = true,
                onClick = onMinimize,
            )
        }
    }
}

@Composable
private fun CardAndTrackMeta(
    cardTitle: String,
    cardAuthor: String,
    playerState: PlayerUiState,
    accentColor: Color,
    secondaryColor: Color,
    centered: Boolean,
) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = cardTitle.ifBlank { "Yoto Playlist" },
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = playerState.currentTrackTitle.ifBlank { cardAuthor },
            style = MaterialTheme.typography.bodyLarge,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Track ${playerState.currentTrackIndex + 1} of ${playerState.tracks.size.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB4BCD1),
            )
            if (playerState.isPlayingOffline) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF0E2920),
                    border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.5f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OfflinePin,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "OFFLINE",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4ADE80),
                        )
                    }
                }
            } else if (playerState.isLiveStream) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = secondaryColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportAndProgressBlock(
    playerState: PlayerUiState,
    effectiveError: String?,
    accentColor: Color,
    onPrevTrack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onStop: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (effectiveError != null) {
            Text(
                text = effectiveError,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFB4AB),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        if (playerState.isLiveStream && playerState.durationMs <= 0L) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (playerState.isBuffering) "BUFFERING LIVE STREAM" else "LIVE AUDIO STREAM",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            val totalMs = playerState.durationMs.coerceAtLeast(1L)
            var isDragging by remember(playerState.activeCardId, playerState.currentTrackIndex) {
                mutableStateOf(false)
            }
            var dragMs by remember(playerState.activeCardId, playerState.currentTrackIndex) {
                mutableFloatStateOf(playerState.positionMs.toFloat())
            }
            val displayPosMs = if (isDragging) dragMs.toLong() else playerState.positionMs

            Slider(
                value = displayPosMs.toFloat().coerceIn(0f, totalMs.toFloat()),
                onValueChange = { newVal ->
                    isDragging = true
                    dragMs = newVal
                },
                onValueChangeFinished = {
                    onSeekTo(dragMs.toLong())
                    isDragging = false
                },
                valueRange = 0f..totalMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDurationMs(displayPosMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD0D5E4),
                )
                Text(
                    text = if (playerState.isBuffering) "Buffering..." else formatDurationMs(playerState.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD0D5E4),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportCircleButton(
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                accentColor = accentColor,
                primary = false,
                onClick = onPrevTrack,
            )
            TransportCircleButton(
                icon = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                accentColor = accentColor,
                primary = true,
                onClick = onTogglePlayPause,
            )
            TransportCircleButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                accentColor = accentColor,
                primary = false,
                onClick = onNextTrack,
            )
            TransportCircleButton(
                icon = Icons.Rounded.Stop,
                contentDescription = "Stop",
                accentColor = Color(0xFFFF6B6B),
                primary = false,
                onClick = onStop,
            )
        }
    }
}

@Composable
private fun TracksPaneHeader(
    accentColor: Color,
    onMinimize: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tracks & Chapters",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        OverlayPillAction(
            label = "Keep Playing",
            icon = Icons.Rounded.KeyboardArrowDown,
            accentColor = accentColor,
            highlighted = false,
            onClick = onMinimize,
        )
    }
}

@Composable
private fun TrackListColumn(
    tracks: List<PlayableTrack>,
    currentTrackIndex: Int,
    isPlaying: Boolean,
    accentColor: Color,
    trackListState: androidx.compose.foundation.lazy.LazyListState,
    onSelectTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = trackListState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        modifier = modifier,
    ) {
        itemsIndexed(
            items = tracks,
            key = { idx, tr -> "${tr.id}_$idx" },
        ) { index, track ->
            YotoTrackRowItem(
                index = index,
                track = track,
                isSelected = index == currentTrackIndex,
                isPlaying = isPlaying && index == currentTrackIndex,
                accentColor = accentColor,
                onClick = { onSelectTrack(index) },
            )
        }
    }
}

@Composable
private fun YotoArtworkBox(
    coverModel: Any?,
    title: String,
    accentColor: Color,
    secondaryColor: Color,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.35f),
                        secondaryColor.copy(alpha = 0.35f),
                        Color(0xFF20253A),
                    )
                )
            )
            .border(1.5.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(size * 0.36f),
        )
        if (coverModel != null) {
            AsyncImage(
                model = coverModel,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TransportCircleButton(
    icon: ImageVector,
    contentDescription: String,
    accentColor: Color,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val btnSize = if (primary) 62.dp else 50.dp
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (primary) accentColor else Color(0xFF23273D),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.6f)),
        modifier = Modifier.size(btnSize),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (primary) Color(0xFF0A0B0F) else Color.White,
                modifier = Modifier.size(if (primary) 34.dp else 26.dp),
            )
        }
    }
}

@Composable
private fun OverlayPillAction(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) accentColor.copy(alpha = 0.24f) else Color(0xFF23273D),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun YotoTrackRowItem(
    index: Int,
    track: PlayableTrack,
    isSelected: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.18f) else Color(0xFF1C2034),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.10f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier.width(30.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(19.dp),
                    )
                } else {
                    Text(
                        text = String.format(Locale.US, "%02d", index + 1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) accentColor else Color(0xFF9AA3BD),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.ifBlank { "Track ${index + 1}" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) Color.White else Color(0xFFE4E8F5),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.chapterTitle.isNotBlank() &&
                    !track.chapterTitle.equals(track.title, ignoreCase = true) &&
                    !track.chapterTitle.equals("Final", ignoreCase = true)
                ) {
                    Text(
                        text = track.chapterTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9AA3BD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when {
                    track.type.equals("stream", ignoreCase = true) && track.durationSeconds <= 0f -> "LIVE"
                    track.durationSeconds > 0f -> formatDurationMs((track.durationSeconds * 1000f).toLong())
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) accentColor else Color(0xFFA8B1C9),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
