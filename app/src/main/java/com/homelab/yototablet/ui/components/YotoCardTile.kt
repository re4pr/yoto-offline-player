package com.homelab.yototablet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.homelab.yototablet.model.CardDownloadProgress
import com.homelab.yototablet.model.OfflineStatus
import com.homelab.yototablet.model.YotoCardSummary
import com.homelab.yototablet.model.formatCardSubtitle
import java.io.File

@Composable
fun YotoCardTile(
    card: YotoCardSummary,
    progress: CardDownloadProgress?,
    coverRemoteUrl: String,
    accentColor: Color,
    secondaryColor: Color,
    isActiveCard: Boolean,
    isPlaying: Boolean,
    isOpening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeText = when {
        isActiveCard && isPlaying -> "PLAYING"
        isActiveCard -> "PAUSED"
        card.source == "myo" -> "MYO"
        else -> ""
    }
    val coverModel: Any? = progress?.coverLocalPath
        ?.let { File(it) }
        ?.takeIf { it.isFile && it.length() > 0L }
        ?: coverRemoteUrl.takeIf { it.isNotBlank() }

    val status = progress?.status ?: OfflineStatus.PENDING
    val subtitle = formatCardSubtitle(card)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC161826),
        border = BorderStroke(
            width = if (isActiveCard) 2.5.dp else 1.2.dp,
            color = when {
                isActiveCard -> accentColor
                status == OfflineStatus.DOWNLOADED -> secondaryColor.copy(alpha = 0.35f)
                else -> Color.White.copy(alpha = 0.16f)
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.84f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.30f),
                            secondaryColor.copy(alpha = 0.24f),
                            Color(0xFF1A1E32),
                        )
                    )
                ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.50f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 32.dp)
                    .size(58.dp),
            )
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x2807080D),
                            0.42f to Color(0x4D07080D),
                            1.0f to Color(0xF007080D),
                        )
                    )
            )

            // Top-left status pill (PLAYING / PAUSED / MYO)
            if (badgeText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActiveCard) accentColor else Color(0xCC121420),
                    border = if (isActiveCard) null else BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActiveCard) Color(0xFF0A0B0F) else accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Top-right offline / downloading / live stream indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            ) {
                when {
                    isOpening || status == OfflineStatus.DOWNLOADING -> {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC121420),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(
                                    color = accentColor,
                                    strokeWidth = 2.2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    status == OfflineStatus.DOWNLOADED -> {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC0D231C),
                            border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.65f)),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Rounded.OfflinePin,
                                    contentDescription = "Available offline",
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                    status == OfflineStatus.LIVE_STREAM_ONLY -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xCC0E2233),
                            border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.65f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Radio,
                                    contentDescription = "Live stream",
                                    tint = secondaryColor,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = secondaryColor,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xB8121420),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = "Waiting for download",
                                    tint = Color(0xFFD0D5E4),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Bottom title, subtitle & optional track download progress
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActiveCard) secondaryColor else Color(0xFFD0D5E4),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if ((status == OfflineStatus.DOWNLOADING || status == OfflineStatus.PARTIAL) &&
                    progress != null &&
                    progress.totalDownloadableTracks > 0
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val pct = (progress.downloadedTracks.toFloat() / progress.totalDownloadableTracks.toFloat())
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { pct },
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
