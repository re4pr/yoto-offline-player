package com.homelab.yototablet.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.homelab.yototablet.BuildConfig
import com.homelab.yototablet.model.PlayableCard
import com.homelab.yototablet.model.PlayableTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlayerUiState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val activeCardId: String = "",
    val activeCardTitle: String = "",
    val activeCardAuthor: String = "",
    val activeCoverModel: Any? = null,
    val tracks: List<PlayableTrack> = emptyList(),
    val currentTrackIndex: Int = 0,
    val currentTrackTitle: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLiveStream: Boolean = false,
    val isPlayingOffline: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(UnstableApi::class)
class YotoPlayerController(context: Context) {

    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingPlayRequest: Pair<PlayableCard, Int>? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromController(clearError = true)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromController(clearError = false)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromController(clearError = playbackState == Player.STATE_READY)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (_uiState.value.isActive) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.localizedMessage ?: "Playback failed",
                )
            }
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, YotoPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mc ->
                        controller = mc
                        mc.addListener(playerListener)
                        val pending = pendingPlayRequest
                        pendingPlayRequest = null
                        if (pending != null) {
                            playCard(pending.first, pending.second)
                        } else {
                            syncFromController(clearError = false)
                        }
                    }
                    .onFailure {
                        controllerFuture = null
                    }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun playCard(card: PlayableCard, startIndex: Int = 0) {
        val validTracks = card.tracks.filter { !it.localFilePath.isNullOrBlank() || it.remoteUrl.isNotBlank() }
        if (validTracks.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No playable audio found on ${card.title}.",
            )
            return
        }

        val mc = controller
        if (mc == null) {
            pendingPlayRequest = card to startIndex
            connect()
            return
        }

        val safeIndex = startIndex.coerceIn(0, validTracks.lastIndex)
        val artworkUri = card.coverLocalPath
            ?.let { File(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
            ?.let { Uri.fromFile(it) }
            ?: card.coverRemoteUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }

        val mediaItems = validTracks.mapIndexed { idx, track ->
            val localFile = track.localFilePath?.let { File(it) }?.takeIf { it.isFile && it.length() > 0L }
            val uri = if (localFile != null) Uri.fromFile(localFile) else Uri.parse(track.remoteUrl)
            val extras = Bundle().apply {
                putString(EXTRA_CARD_ID, card.cardId)
                putString(EXTRA_CARD_TITLE, card.title)
                putString(EXTRA_CARD_AUTHOR, card.author)
                putString(EXTRA_COVER_LOCAL, card.coverLocalPath ?: "")
                putString(EXTRA_COVER_REMOTE, card.coverRemoteUrl)
                putString(EXTRA_TRACK_ID, track.id)
                putInt(EXTRA_TRACK_INDEX, idx)
                putString(EXTRA_TRACK_TITLE, track.title)
                putString(EXTRA_CHAPTER_TITLE, track.chapterTitle)
                putFloat(EXTRA_DURATION_SEC, track.durationSeconds)
                putString(EXTRA_FORMAT, track.format)
                putString(EXTRA_TYPE, track.type)
                putString(EXTRA_REMOTE_URL, track.remoteUrl)
                putString(EXTRA_LOCAL_PATH, localFile?.absolutePath ?: "")
                putBoolean(EXTRA_IS_OFFLINE, localFile != null)
            }
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(track.id.ifBlank { "${card.cardId}_$idx" })
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title.ifBlank { "Track ${idx + 1}" })
                        .setArtist(card.author.ifBlank { BuildConfig.APP_DISPLAY_NAME })
                        .setAlbumTitle(card.title)
                        .setArtworkUri(artworkUri)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        val initialTrack = validTracks[safeIndex]
        val coverModel: Any? = card.coverLocalPath
            ?.let { File(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
            ?: card.coverRemoteUrl.takeIf { it.isNotBlank() }

        _uiState.value = PlayerUiState(
            isActive = true,
            isPlaying = true,
            isBuffering = true,
            activeCardId = card.cardId,
            activeCardTitle = card.title,
            activeCardAuthor = card.author,
            activeCoverModel = coverModel,
            tracks = validTracks,
            currentTrackIndex = safeIndex,
            currentTrackTitle = initialTrack.title,
            positionMs = 0L,
            durationMs = (initialTrack.durationSeconds * 1000f).toLong().coerceAtLeast(0L),
            isLiveStream = initialTrack.type.equals("stream", ignoreCase = true),
            isPlayingOffline = initialTrack.isOffline,
            errorMessage = null,
        )

        mc.setMediaItems(mediaItems, safeIndex, 0L)
        mc.repeatMode = Player.REPEAT_MODE_OFF
        mc.prepare()
        mc.play()
    }

    fun togglePlayPause() {
        val mc = controller ?: return
        if (mc.mediaItemCount == 0) return
        if (mc.isPlaying) {
            mc.pause()
        } else {
            if (mc.playbackState == Player.STATE_ENDED) {
                mc.seekTo(0, 0L)
            }
            mc.play()
        }
        syncFromController(clearError = true)
    }

    fun previousTrack() {
        val mc = controller ?: return
        if (mc.mediaItemCount <= 0) return
        if (mc.currentPosition > 3_000L) {
            mc.seekTo(0L)
        } else if (mc.hasPreviousMediaItem()) {
            mc.seekToPreviousMediaItem()
        } else {
            mc.seekTo(0, 0L)
        }
        mc.play()
        syncFromController(clearError = true)
    }

    fun nextTrack() {
        val mc = controller ?: return
        if (mc.mediaItemCount <= 0) return
        if (mc.hasNextMediaItem()) {
            mc.seekToNextMediaItem()
        } else {
            mc.seekToDefaultPosition(0)
        }
        mc.play()
        syncFromController(clearError = true)
    }

    fun seekToTrack(index: Int) {
        val mc = controller ?: return
        if (index !in 0 until mc.mediaItemCount) return
        mc.seekToDefaultPosition(index)
        mc.play()
        syncFromController(clearError = true)
    }

    fun seekToPosition(positionMs: Long) {
        val mc = controller ?: return
        if (mc.mediaItemCount <= 0) return
        mc.seekTo(positionMs.coerceAtLeast(0L))
        syncFromController(clearError = false)
    }

    fun stop() {
        pendingPlayRequest = null
        controller?.let { mc ->
            runCatching {
                mc.pause()
                mc.stop()
                mc.clearMediaItems()
            }
        }
        _uiState.value = PlayerUiState()
    }

    fun refreshProgress() {
        val mc = controller ?: return
        if (!_uiState.value.isActive && mc.mediaItemCount == 0) return
        syncFromController(clearError = false)
    }

    private fun syncFromController(clearError: Boolean) {
        val mc = controller ?: return
        val itemCount = mc.mediaItemCount
        if (itemCount == 0 || mc.playbackState == Player.STATE_IDLE) {
            if (_uiState.value.isActive && mc.mediaItemCount == 0) {
                _uiState.value = PlayerUiState()
            }
            return
        }

        val reconstructedTracks = mutableListOf<PlayableTrack>()
        var cardId = _uiState.value.activeCardId
        var cardTitle = _uiState.value.activeCardTitle
        var cardAuthor = _uiState.value.activeCardAuthor
        var coverLocal = ""
        var coverRemote = ""

        for (i in 0 until itemCount) {
            val item = mc.getMediaItemAt(i)
            val extras = item.mediaMetadata.extras
            if (extras != null) {
                if (i == 0) {
                    cardId = extras.getString(EXTRA_CARD_ID, cardId)
                    cardTitle = extras.getString(EXTRA_CARD_TITLE, cardTitle)
                    cardAuthor = extras.getString(EXTRA_CARD_AUTHOR, cardAuthor)
                    coverLocal = extras.getString(EXTRA_COVER_LOCAL, "")
                    coverRemote = extras.getString(EXTRA_COVER_REMOTE, "")
                }
                val localPath = extras.getString(EXTRA_LOCAL_PATH, "").takeIf { it.isNotBlank() }
                reconstructedTracks.add(
                    PlayableTrack(
                        id = extras.getString(EXTRA_TRACK_ID, item.mediaId),
                        index = extras.getInt(EXTRA_TRACK_INDEX, i),
                        title = extras.getString(
                            EXTRA_TRACK_TITLE,
                            item.mediaMetadata.title?.toString() ?: "Track ${i + 1}",
                        ),
                        chapterTitle = extras.getString(EXTRA_CHAPTER_TITLE, ""),
                        durationSeconds = extras.getFloat(EXTRA_DURATION_SEC, 0f),
                        format = extras.getString(EXTRA_FORMAT, ""),
                        type = extras.getString(EXTRA_TYPE, "audio"),
                        remoteUrl = extras.getString(EXTRA_REMOTE_URL, ""),
                        localFilePath = localPath,
                        isOffline = extras.getBoolean(EXTRA_IS_OFFLINE, localPath != null),
                    )
                )
            }
        }

        val tracks = if (reconstructedTracks.isNotEmpty()) reconstructedTracks else _uiState.value.tracks
        val idx = mc.currentMediaItemIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        val activeTrack = tracks.getOrNull(idx)
        val rawDuration = mc.duration
        val fallbackDurationMs = activeTrack?.durationSeconds?.let { (it * 1000f).toLong() } ?: 0L
        val effectiveDuration = if (rawDuration != C.TIME_UNSET && rawDuration > 0L) {
            rawDuration
        } else {
            fallbackDurationMs.coerceAtLeast(0L)
        }
        val effectivePos = if (mc.playbackState == Player.STATE_ENDED && effectiveDuration > 0L) {
            effectiveDuration
        } else {
            mc.currentPosition.coerceAtLeast(0L)
        }
        val coverModel: Any? = coverLocal
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
            ?: coverRemote.takeIf { it.isNotBlank() }
            ?: _uiState.value.activeCoverModel

        val isStream = activeTrack?.type.equals("stream", ignoreCase = true)
        _uiState.value = _uiState.value.copy(
            isActive = true,
            isPlaying = mc.isPlaying,
            isBuffering = mc.playbackState == Player.STATE_BUFFERING,
            activeCardId = cardId,
            activeCardTitle = cardTitle,
            activeCardAuthor = cardAuthor,
            activeCoverModel = coverModel,
            tracks = tracks,
            currentTrackIndex = idx,
            currentTrackTitle = activeTrack?.title
                ?.takeIf { it.isNotBlank() }
                ?: mc.currentMediaItem?.mediaMetadata?.title?.toString()
                ?: _uiState.value.currentTrackTitle,
            positionMs = effectivePos,
            durationMs = effectiveDuration,
            isLiveStream = isStream,
            isPlayingOffline = activeTrack?.isOffline ?: false,
            errorMessage = if (clearError) null else _uiState.value.errorMessage,
        )
    }

    companion object {
        private const val EXTRA_CARD_ID = "yoto_card_id"
        private const val EXTRA_CARD_TITLE = "yoto_card_title"
        private const val EXTRA_CARD_AUTHOR = "yoto_card_author"
        private const val EXTRA_COVER_LOCAL = "yoto_cover_local"
        private const val EXTRA_COVER_REMOTE = "yoto_cover_remote"
        private const val EXTRA_TRACK_ID = "yoto_track_id"
        private const val EXTRA_TRACK_INDEX = "yoto_track_index"
        private const val EXTRA_TRACK_TITLE = "yoto_track_title"
        private const val EXTRA_CHAPTER_TITLE = "yoto_chapter_title"
        private const val EXTRA_DURATION_SEC = "yoto_duration_sec"
        private const val EXTRA_FORMAT = "yoto_format"
        private const val EXTRA_TYPE = "yoto_type"
        private const val EXTRA_REMOTE_URL = "yoto_remote_url"
        private const val EXTRA_LOCAL_PATH = "yoto_local_path"
        private const val EXTRA_IS_OFFLINE = "yoto_is_offline"
    }
}
