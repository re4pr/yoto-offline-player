package com.homelab.yototablet.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

private val VALID_CARD_ID_REGEX = Regex("^[A-Za-z0-9_-]{2,64}$")

fun isValidCardId(cardId: String): Boolean = VALID_CARD_ID_REGEX.matches(cardId.trim())

@Serializable
data class YotoCardsResponse(
    val ready: Boolean = false,
    @SerialName("needs_auth") val needsAuth: Boolean = false,
    val cached: Boolean = false,
    val message: String = "",
    val count: Int = 0,
    val cards: List<YotoCardSummary> = emptyList(),
)

@Serializable
data class YotoCardSummary(
    @SerialName("card_id") val cardId: String = "",
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val category: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    val source: String = "library",
    @SerialName("last_played_at") val lastPlayedAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class YotoCardDetailResponse(
    val ready: Boolean = false,
    val playable: Boolean = false,
    @SerialName("card_id") val cardId: String = "",
    val title: String = "",
    val author: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    val message: String = "",
    val count: Int = 0,
    val tracks: List<YotoTrack> = emptyList(),
)

@Serializable
data class YotoTrack(
    val id: String = "",
    val index: Int = 0,
    val title: String = "",
    @SerialName("chapter_title") val chapterTitle: String = "",
    @SerialName("duration_seconds") val durationSeconds: Float = 0f,
    val format: String = "",
    val type: String = "audio",
    val url: String = "",
    @SerialName("icon_url") val iconUrl: String = "",
)

@Serializable
data class OfflineCatalogFile(
    val syncedAtMs: Long = 0L,
    val cards: List<YotoCardSummary> = emptyList(),
)

@Serializable
data class OfflineCardManifest(
    val cardId: String,
    val title: String = "",
    val author: String = "",
    val updatedAt: String = "",
    val source: String = "library",
    val coverFileName: String? = null,
    val tracks: List<OfflineTrackEntry> = emptyList(),
    val syncedAtMs: Long = 0L,
)

@Serializable
data class OfflineTrackEntry(
    val id: String = "",
    val index: Int = 0,
    val title: String = "",
    val chapterTitle: String = "",
    val durationSeconds: Float = 0f,
    val format: String = "",
    val type: String = "audio",
    val remoteUrl: String = "",
    val localFileName: String? = null,
    val fileSizeBytes: Long = 0L,
)

enum class OfflineStatus {
    DOWNLOADED,
    DOWNLOADING,
    PARTIAL,
    PENDING,
    LIVE_STREAM_ONLY,
}

data class CardDownloadProgress(
    val cardId: String,
    val status: OfflineStatus = OfflineStatus.PENDING,
    val downloadedTracks: Int = 0,
    val totalDownloadableTracks: Int = 0,
    val isStreamOnly: Boolean = false,
    val localBytes: Long = 0L,
    val coverLocalPath: String? = null,
)

data class PlayableTrack(
    val id: String,
    val index: Int,
    val title: String,
    val chapterTitle: String,
    val durationSeconds: Float,
    val format: String,
    val type: String,
    val remoteUrl: String,
    val localFilePath: String?,
    val isOffline: Boolean,
)

data class PlayableCard(
    val cardId: String,
    val title: String,
    val author: String,
    val coverLocalPath: String?,
    val coverRemoteUrl: String,
    val tracks: List<PlayableTrack>,
    val isFullyOffline: Boolean,
    val isLiveStreamOnly: Boolean,
)

data class SyncStatusState(
    val isRefreshingCatalog: Boolean = false,
    val isDownloading: Boolean = false,
    val isServerReachable: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val currentCardId: String? = null,
    val currentCardTitle: String = "",
    val completedCards: Int = 0,
    val totalDownloadableCards: Int = 0,
    val currentTrackIndex: Int = 0,
    val currentCardTotalTracks: Int = 0,
    val totalOfflineBytes: Long = 0L,
    val statusMessage: String = "",
)

fun formatCardSubtitle(card: YotoCardSummary): String {
    if (card.trackCount > 0) {
        val dur = formatTotalDurationShort(card.durationSeconds)
        val countLabel = if (card.trackCount == 1) "1 track" else "${card.trackCount} tracks"
        return if (dur.isNotEmpty()) "$countLabel · $dur" else countLabel
    }
    if (card.category.equals("radio", ignoreCase = true)) {
        return "Live Radio"
    }
    val dur = formatTotalDurationShort(card.durationSeconds)
    if (dur.isNotEmpty()) return dur
    return card.author.ifBlank { if (card.source == "myo") "MYO Card" else "Yoto Card" }
}

fun formatTotalDurationShort(durationSeconds: Int): String {
    if (durationSeconds <= 0) return ""
    val mins = (durationSeconds / 60).coerceAtLeast(1)
    return if (mins >= 60) {
        val hrs = mins / 60
        val rem = mins % 60
        if (rem > 0) "${hrs}h ${rem}m" else "${hrs}h"
    } else {
        "$mins min"
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}
