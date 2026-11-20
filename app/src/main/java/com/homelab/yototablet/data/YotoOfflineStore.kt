package com.homelab.yototablet.data

import com.homelab.yototablet.model.CardDownloadProgress
import com.homelab.yototablet.model.OfflineCardManifest
import com.homelab.yototablet.model.OfflineCatalogFile
import com.homelab.yototablet.model.OfflineStatus
import com.homelab.yototablet.model.OfflineTrackEntry
import com.homelab.yototablet.model.PlayableCard
import com.homelab.yototablet.model.PlayableTrack
import com.homelab.yototablet.model.YotoCardDetailResponse
import com.homelab.yototablet.model.YotoCardSummary
import com.homelab.yototablet.model.YotoTrack
import com.homelab.yototablet.model.isValidCardId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

class YotoOfflineStore(private val rootDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val coversDir = File(rootDir, "covers")
    private val audioDir = File(rootDir, "audio")
    private val catalogFile = File(rootDir, "catalog.json")

    private val canonicalRootPath: String by lazy {
        rootDir.mkdirs()
        rootDir.canonicalPath
    }

    fun ensureBaseDirs() {
        rootDir.mkdirs()
        coversDir.mkdirs()
        audioDir.mkdirs()
    }

    fun cleanupTempFiles() {
        ensureBaseDirs()
        rootDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith(".tmp_")) f.delete()
        }
        coversDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith(".tmp_")) f.delete()
        }
        audioDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.name.startsWith(".tmp_")) f.delete()
                }
            }
        }
    }

    fun loadCatalog(): OfflineCatalogFile? {
        if (!catalogFile.isFile || catalogFile.length() == 0L) return null
        return runCatching {
            json.decodeFromString<OfflineCatalogFile>(catalogFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun saveCatalog(catalog: OfflineCatalogFile) {
        ensureBaseDirs()
        val tmp = File(rootDir, ".tmp_catalog.json")
        tmp.writeText(json.encodeToString(catalog), Charsets.UTF_8)
        moveAtomicOrReplace(tmp, catalogFile)
    }

    fun coverFile(cardId: String): File {
        require(isValidCardId(cardId)) { "Invalid cardId: $cardId" }
        return ensureInsideRoot(File(coversDir, "$cardId.png"))
    }

    fun cardAudioDir(cardId: String): File {
        require(isValidCardId(cardId)) { "Invalid cardId: $cardId" }
        return ensureInsideRoot(File(audioDir, cardId))
    }

    fun audioTrackFile(cardId: String, fileName: String): File {
        require(isValidCardId(cardId)) { "Invalid cardId: $cardId" }
        val cleanName = fileName.trim()
        require(
            cleanName.isNotEmpty() &&
                !cleanName.contains("/") &&
                !cleanName.contains("\\") &&
                !cleanName.contains("..")
        ) { "Invalid track filename: $fileName" }
        return ensureInsideRoot(File(cardAudioDir(cardId), cleanName))
    }

    fun manifestFile(cardId: String): File {
        return ensureInsideRoot(File(cardAudioDir(cardId), "manifest.json"))
    }

    fun loadCardManifest(cardId: String): OfflineCardManifest? {
        if (!isValidCardId(cardId)) return null
        val mf = manifestFile(cardId)
        if (!mf.isFile || mf.length() == 0L) return null
        return runCatching {
            json.decodeFromString<OfflineCardManifest>(mf.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun saveCardManifest(manifest: OfflineCardManifest) {
        require(isValidCardId(manifest.cardId)) { "Invalid cardId: ${manifest.cardId}" }
        val dir = cardAudioDir(manifest.cardId)
        dir.mkdirs()
        val tmp = File(dir, ".tmp_manifest.json")
        tmp.writeText(json.encodeToString(manifest), Charsets.UTF_8)
        moveAtomicOrReplace(tmp, manifestFile(manifest.cardId))
    }

    fun deriveTrackFileName(index: Int, track: YotoTrack): String {
        val urlPath = track.url.substringBefore('?').substringBefore('#')
        val urlBase = urlPath.substringAfterLast('/', "")
        if (STRICT_LOCAL_TRACK_REGEX.matches(urlBase)) {
            return urlBase.lowercase(Locale.US)
        }
        val urlExt = urlBase.substringAfterLast('.', "").lowercase(Locale.US)
        val ext = when {
            urlExt in VALID_AUDIO_EXTS -> urlExt
            else -> extFromFormat(track.format)
        }
        return String.format(Locale.US, "%03d.%s", index + 1, ext)
    }

    fun computeCardProgress(
        card: YotoCardSummary,
        activeDownloadingCardId: String? = null,
    ): CardDownloadProgress {
        if (!isValidCardId(card.cardId)) {
            return CardDownloadProgress(cardId = card.cardId, status = OfflineStatus.PENDING)
        }
        val cover = coverFile(card.cardId)
        val coverValid = cover.isFile && cover.length() > 0L
        val coverBytes = if (coverValid) cover.length() else 0L
        val coverPath = if (coverValid) cover.absolutePath else null

        val manifest = loadCardManifest(card.cardId)
        if (manifest == null) {
            val streamHint = card.category.equals("radio", ignoreCase = true)
            val status = when {
                activeDownloadingCardId == card.cardId -> OfflineStatus.DOWNLOADING
                streamHint -> OfflineStatus.LIVE_STREAM_ONLY
                else -> OfflineStatus.PENDING
            }
            return CardDownloadProgress(
                cardId = card.cardId,
                status = status,
                downloadedTracks = 0,
                totalDownloadableTracks = if (streamHint) 0 else card.trackCount.coerceAtLeast(0),
                isStreamOnly = streamHint,
                localBytes = coverBytes,
                coverLocalPath = coverPath,
            )
        }

        val staticTracks = manifest.tracks.filter { !it.type.equals("stream", ignoreCase = true) }
        val isStreamOnly = manifest.tracks.isNotEmpty() && staticTracks.isEmpty()

        var downloadedCount = 0
        var audioBytes = 0L
        for (tr in staticTracks) {
            val fname = tr.localFileName
            if (!fname.isNullOrBlank()) {
                val file = runCatching { audioTrackFile(card.cardId, fname) }.getOrNull()
                if (file != null && file.isFile && file.length() > 0L) {
                    downloadedCount++
                    audioBytes += file.length()
                }
            }
        }

        val isStale = isManifestStale(card, manifest)
        val expectedStaticTotal = when {
            isStreamOnly -> 0
            card.trackCount > 0 && isStale -> maxOf(staticTracks.size, card.trackCount)
            else -> staticTracks.size
        }

        val coverReady = card.coverUrl.isBlank() || coverValid
        val status = when {
            isStreamOnly -> OfflineStatus.LIVE_STREAM_ONLY
            activeDownloadingCardId == card.cardId -> OfflineStatus.DOWNLOADING
            !isStale && staticTracks.isNotEmpty() && downloadedCount == staticTracks.size && coverReady ->
                OfflineStatus.DOWNLOADED
            downloadedCount > 0 -> OfflineStatus.PARTIAL
            else -> OfflineStatus.PENDING
        }

        return CardDownloadProgress(
            cardId = card.cardId,
            status = status,
            downloadedTracks = downloadedCount,
            totalDownloadableTracks = expectedStaticTotal,
            isStreamOnly = isStreamOnly,
            localBytes = coverBytes + audioBytes,
            coverLocalPath = coverPath,
        )
    }

    fun computeAllProgress(
        cards: List<YotoCardSummary>,
        activeDownloadingCardId: String? = null,
    ): Map<String, CardDownloadProgress> {
        return cards.associate { card ->
            card.cardId to computeCardProgress(card, activeDownloadingCardId)
        }
    }

    fun cardNeedsSync(card: YotoCardSummary): Boolean {
        if (!isValidCardId(card.cardId)) return false
        val cover = coverFile(card.cardId)
        if (card.coverUrl.isNotBlank() && (!cover.isFile || cover.length() == 0L)) {
            return true
        }
        val manifest = loadCardManifest(card.cardId) ?: return true
        if (manifest.tracks.isEmpty()) return true
        if (isManifestStale(card, manifest)) return true
        for (tr in manifest.tracks) {
            if (tr.type.equals("stream", ignoreCase = true)) continue
            val fname = tr.localFileName ?: return true
            val f = runCatching { audioTrackFile(card.cardId, fname) }.getOrNull() ?: return true
            if (!f.isFile || f.length() == 0L) return true
        }
        return false
    }

    fun isManifestStale(card: YotoCardSummary, manifest: OfflineCardManifest): Boolean {
        if (card.updatedAt.isNotBlank() && manifest.updatedAt != card.updatedAt) return true
        if (card.trackCount > 0 && manifest.tracks.size != card.trackCount) return true
        return false
    }

    fun resolvePlayableCard(
        card: YotoCardSummary,
        remoteFallbackDetail: YotoCardDetailResponse? = null,
        resolveRemoteUrl: (String) -> String,
    ): PlayableCard? {
        if (!isValidCardId(card.cardId)) return null
        val cover = coverFile(card.cardId)
        val coverLocalPath = if (cover.isFile && cover.length() > 0L) cover.absolutePath else null
        val manifest = loadCardManifest(card.cardId)

        val playableTracks: List<PlayableTrack> = when {
            manifest != null && manifest.tracks.isNotEmpty() -> {
                manifest.tracks.map { tr ->
                    val isStream = tr.type.equals("stream", ignoreCase = true)
                    val localFile = if (!isStream && !tr.localFileName.isNullOrBlank()) {
                        runCatching { audioTrackFile(card.cardId, tr.localFileName) }.getOrNull()
                            ?.takeIf { it.isFile && it.length() > 0L }
                    } else {
                        null
                    }
                    PlayableTrack(
                        id = tr.id,
                        index = tr.index,
                        title = tr.title,
                        chapterTitle = tr.chapterTitle,
                        durationSeconds = tr.durationSeconds,
                        format = tr.format,
                        type = tr.type,
                        remoteUrl = resolveRemoteUrl(tr.remoteUrl),
                        localFilePath = localFile?.absolutePath,
                        isOffline = localFile != null,
                    )
                }
            }
            remoteFallbackDetail != null && remoteFallbackDetail.tracks.isNotEmpty() -> {
                remoteFallbackDetail.tracks.mapIndexed { idx, tr ->
                    val isStream = tr.type.equals("stream", ignoreCase = true)
                    val candidateFile = if (!isStream) {
                        runCatching { audioTrackFile(card.cardId, deriveTrackFileName(idx, tr)) }.getOrNull()
                            ?.takeIf { it.isFile && it.length() > 0L }
                    } else {
                        null
                    }
                    PlayableTrack(
                        id = tr.id,
                        index = tr.index,
                        title = tr.title,
                        chapterTitle = tr.chapterTitle,
                        durationSeconds = tr.durationSeconds,
                        format = tr.format,
                        type = tr.type,
                        remoteUrl = resolveRemoteUrl(tr.url),
                        localFilePath = candidateFile?.absolutePath,
                        isOffline = candidateFile != null,
                    )
                }
            }
            else -> return null
        }

        if (playableTracks.isEmpty()) return null
        val isStreamOnly = playableTracks.all { it.type.equals("stream", ignoreCase = true) }
        val isFullyOffline = !isStreamOnly && playableTracks.all {
            it.type.equals("stream", ignoreCase = true) || it.isOffline
        }

        return PlayableCard(
            cardId = card.cardId,
            title = card.title.ifBlank { remoteFallbackDetail?.title ?: manifest?.title ?: "Yoto Card" },
            author = card.author.ifBlank { remoteFallbackDetail?.author ?: manifest?.author ?: "" },
            coverLocalPath = coverLocalPath,
            coverRemoteUrl = resolveRemoteUrl(card.coverUrl.ifBlank { remoteFallbackDetail?.coverUrl ?: "" }),
            tracks = playableTracks,
            isFullyOffline = isFullyOffline,
            isLiveStreamOnly = isStreamOnly,
        )
    }

    fun pruneRemovedCards(validCardIds: Set<String>): Int {
        ensureBaseDirs()
        var removedCount = 0
        coversDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".png")) {
                val cid = file.name.removeSuffix(".png")
                if (cid !in validCardIds) {
                    if (file.delete()) removedCount++
                }
            }
        }
        audioDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in validCardIds) {
                if (dir.deleteRecursively()) removedCount++
            }
        }
        return removedCount
    }

    fun pruneStaleTracksInCard(cardId: String, keepFileNames: Set<String>) {
        if (!isValidCardId(cardId)) return
        val dir = cardAudioDir(cardId)
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name != "manifest.json" && file.name !in keepFileNames) {
                file.delete()
            }
        }
    }

    fun calculateTotalOfflineBytes(): Long {
        ensureBaseDirs()
        var total = 0L
        coversDir.listFiles()?.forEach { f ->
            if (f.isFile && !f.name.startsWith(".tmp_")) total += f.length()
        }
        audioDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && !f.name.startsWith(".tmp_") && f.name != "manifest.json") {
                        total += f.length()
                    }
                }
            }
        }
        return total
    }

    private fun ensureInsideRoot(file: File): File {
        val targetCanonical = file.canonicalPath
        val prefix = canonicalRootPath.trimEnd(File.separatorChar) + File.separatorChar
        if (targetCanonical != canonicalRootPath && !targetCanonical.startsWith(prefix)) {
            throw SecurityException("Path escapes offline root: ${file.path}")
        }
        return file
    }

    companion object {
        private val VALID_AUDIO_EXTS = setOf("ogg", "opus", "m4a", "aac", "mp4", "mp3", "wav", "flac")
        private val STRICT_LOCAL_TRACK_REGEX = Regex("^\\d{3,4}\\.(ogg|opus|m4a|aac|mp4|mp3|wav|flac)$", RegexOption.IGNORE_CASE)

        private fun extFromFormat(format: String): String {
            return when (format.trim().lowercase(Locale.US)) {
                "opus", "ogg" -> "ogg"
                "aac", "x-m4a", "m4a", "mp4" -> "m4a"
                "mp3", "mpeg" -> "mp3"
                "flac", "wav" -> format.trim().lowercase(Locale.US)
                else -> "ogg"
            }
        }
    }
}
