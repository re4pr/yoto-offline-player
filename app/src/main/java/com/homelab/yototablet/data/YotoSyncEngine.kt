package com.homelab.yototablet.data

import com.homelab.yototablet.model.CardDownloadProgress
import com.homelab.yototablet.model.OfflineCardManifest
import com.homelab.yototablet.model.OfflineCatalogFile
import com.homelab.yototablet.model.OfflineStatus
import com.homelab.yototablet.model.OfflineTrackEntry
import com.homelab.yototablet.model.SyncStatusState
import com.homelab.yototablet.model.YotoCardSummary
import com.homelab.yototablet.model.YotoTrack
import com.homelab.yototablet.model.isValidCardId

class YotoSyncEngine(
    private val store: YotoOfflineStore,
    private val apiClient: YotoNetworkClient,
    private val isOnlineCheck: () -> Boolean,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {

    fun syncAll(
        forceRefreshCatalog: Boolean,
        priorityCardIdProvider: () -> String? = { null },
        onProgressUpdate: (List<YotoCardSummary>, Map<String, CardDownloadProgress>, SyncStatusState) -> Unit,
    ): SyncStatusState {
        store.cleanupTempFiles()

        var cards = store.loadCatalog()?.cards.orEmpty()
        var progressMap = store.computeAllProgress(cards)
        val initialOfflineBytes = store.calculateTotalOfflineBytes()

        var state = SyncStatusState(
            isRefreshingCatalog = true,
            isDownloading = false,
            isServerReachable = false,
            lastSyncedAtMs = store.loadCatalog()?.syncedAtMs ?: 0L,
            totalOfflineBytes = initialOfflineBytes,
            statusMessage = "Checking playlists...",
        )
        onProgressUpdate(cards, progressMap, state)

        if (!isOnlineCheck()) {
            val readyCount = countReadyCards(progressMap)
            val offlineState = state.copy(
                isRefreshingCatalog = false,
                isDownloading = false,
                isServerReachable = false,
                statusMessage = if (cards.isEmpty()) {
                    "Offline · Connect to home Wi-Fi to load playlists"
                } else {
                    "Offline · $readyCount playlists ready"
                },
            )
            onProgressUpdate(cards, progressMap, offlineState)
            return offlineState
        }

        var serverReachable = false
        var serverMessage = ""
        var lastSyncedAtMs = state.lastSyncedAtMs
        try {
            val resp = apiClient.fetchCards(forceRefresh = forceRefreshCatalog)
            if (resp.cards.isNotEmpty()) {
                serverReachable = true
                lastSyncedAtMs = clockMs()
                cards = resp.cards.filter { isValidCardId(it.cardId) }
                store.saveCatalog(OfflineCatalogFile(syncedAtMs = lastSyncedAtMs, cards = cards))
                store.pruneRemovedCards(cards.map { it.cardId }.toSet())
            } else if (resp.ready) {
                serverReachable = true
                lastSyncedAtMs = clockMs()
            } else if (resp.message.isNotBlank()) {
                serverMessage = resp.message
            }
        } catch (_: Exception) {
            serverReachable = false
        }

        progressMap = store.computeAllProgress(cards)
        if (!serverReachable) {
            val readyCount = countReadyCards(progressMap)
            val offlineState = state.copy(
                isRefreshingCatalog = false,
                isDownloading = false,
                isServerReachable = false,
                totalOfflineBytes = store.calculateTotalOfflineBytes(),
                statusMessage = when {
                    serverMessage.isNotBlank() && cards.isEmpty() -> serverMessage
                    cards.isEmpty() -> "Home Hub unreachable · Connect to home Wi-Fi"
                    else -> "Offline · $readyCount playlists ready"
                },
            )
            onProgressUpdate(cards, progressMap, offlineState)
            return offlineState
        }

        val pendingIds = cards.filter { store.cardNeedsSync(it) }.map { it.cardId }.toMutableList()
        val totalDownloadableCards = cards.count { progressMap[it.cardId]?.isStreamOnly != true }

        if (pendingIds.isEmpty()) {
            val readyCount = countReadyCards(progressMap)
            val doneState = state.copy(
                isRefreshingCatalog = false,
                isDownloading = false,
                isServerReachable = true,
                lastSyncedAtMs = lastSyncedAtMs,
                completedCards = readyCount,
                totalDownloadableCards = totalDownloadableCards,
                totalOfflineBytes = store.calculateTotalOfflineBytes(),
                statusMessage = "All $readyCount playlists ready offline",
            )
            onProgressUpdate(cards, progressMap, doneState)
            return doneState
        }

        val cardById = cards.associateBy { it.cardId }
        var hadAnyError = false

        while (pendingIds.isNotEmpty()) {
            val priorityId = priorityCardIdProvider()
            val nextId = if (priorityId != null && priorityId in pendingIds) {
                pendingIds.remove(priorityId)
                priorityId
            } else {
                pendingIds.removeAt(0)
            }
            val card = cardById[nextId] ?: continue

            state = state.copy(
                isRefreshingCatalog = false,
                isDownloading = true,
                isServerReachable = true,
                lastSyncedAtMs = lastSyncedAtMs,
                currentCardId = card.cardId,
                currentCardTitle = card.title,
                completedCards = countDownloadedStaticCards(cards, store),
                totalDownloadableCards = totalDownloadableCards,
                currentTrackIndex = 0,
                currentCardTotalTracks = card.trackCount.coerceAtLeast(1),
                totalOfflineBytes = store.calculateTotalOfflineBytes(),
                statusMessage = "Downloading ${card.title}...",
            )
            progressMap = store.computeAllProgress(cards, activeDownloadingCardId = card.cardId)
            onProgressUpdate(cards, progressMap, state)

            val ok = syncSingleCard(
                card = card,
                onTrackProgress = { trackDone, trackTotal ->
                    progressMap = store.computeAllProgress(cards, activeDownloadingCardId = card.cardId)
                    state = state.copy(
                        completedCards = countDownloadedStaticCards(cards, store),
                        currentTrackIndex = trackDone,
                        currentCardTotalTracks = trackTotal,
                        totalOfflineBytes = store.calculateTotalOfflineBytes(),
                        statusMessage = "Downloading ${card.title} ($trackDone/$trackTotal)",
                    )
                    onProgressUpdate(cards, progressMap, state)
                },
            )
            if (!ok) {
                hadAnyError = true
            }
        }

        progressMap = store.computeAllProgress(cards, activeDownloadingCardId = null)
        val completedStatic = countDownloadedStaticCards(cards, store)
        val finalTotalStatic = cards.count {
            val p = progressMap[it.cardId]
            p == null || !p.isStreamOnly
        }
        val finalStatusMsg = when {
            completedStatic >= finalTotalStatic && !hadAnyError ->
                "All $completedStatic playlists ready offline"
            hadAnyError ->
                "$completedStatic of $finalTotalStatic playlists downloaded"
            else ->
                "All $completedStatic playlists ready offline"
        }

        val finalState = SyncStatusState(
            isRefreshingCatalog = false,
            isDownloading = false,
            isServerReachable = true,
            lastSyncedAtMs = lastSyncedAtMs,
            currentCardId = null,
            currentCardTitle = "",
            completedCards = completedStatic,
            totalDownloadableCards = finalTotalStatic,
            currentTrackIndex = 0,
            currentCardTotalTracks = 0,
            totalOfflineBytes = store.calculateTotalOfflineBytes(),
            statusMessage = finalStatusMsg,
        )
        onProgressUpdate(cards, progressMap, finalState)
        return finalState
    }

    private fun syncSingleCard(
        card: YotoCardSummary,
        onTrackProgress: (done: Int, total: Int) -> Unit,
    ): Boolean {
        val existingManifest = store.loadCardManifest(card.cardId)
        val cardUpdated = existingManifest != null && store.isManifestStale(card, existingManifest)

        // 1. Download cover image if missing or card updated
        var coverFileName: String? = existingManifest?.coverFileName
        val coverDest = store.coverFile(card.cardId)
        val needCover = card.coverUrl.isNotBlank() && (cardUpdated || !coverDest.isFile || coverDest.length() == 0L)
        if (needCover) {
            runCatching {
                apiClient.downloadToFile(apiClient.resolveUrl(card.coverUrl), coverDest)
                coverFileName = coverDest.name
            }
        } else if (coverDest.isFile && coverDest.length() > 0L) {
            coverFileName = coverDest.name
        }

        // 2. Fetch or reuse track manifest
        val detailTracks = if (existingManifest == null || existingManifest.tracks.isEmpty() || cardUpdated) {
            val detail = runCatching {
                apiClient.fetchCardDetail(card.cardId, forceRefresh = cardUpdated)
            }.getOrNull() ?: return false
            if (!detail.playable || detail.tracks.isEmpty()) {
                return false
            }
            detail.tracks
        } else {
            null
        }

        val workingEntries: MutableList<OfflineTrackEntry> = if (detailTracks != null) {
            detailTracks.mapIndexed { idx, tr ->
                val prevMatch = if (!cardUpdated) {
                    existingManifest?.tracks?.getOrNull(idx)
                } else {
                    null
                }
                val expectedName = store.deriveTrackFileName(idx, tr)
                val prevFileOk = prevMatch?.localFileName == expectedName && runCatching {
                    val f = store.audioTrackFile(card.cardId, expectedName)
                    f.isFile && f.length() > 0L
                }.getOrDefault(false)

                OfflineTrackEntry(
                    id = tr.id,
                    index = tr.index,
                    title = tr.title,
                    chapterTitle = tr.chapterTitle,
                    durationSeconds = tr.durationSeconds,
                    format = tr.format,
                    type = tr.type,
                    remoteUrl = tr.url,
                    localFileName = if (prevFileOk) expectedName else null,
                    fileSizeBytes = if (prevFileOk) store.audioTrackFile(card.cardId, expectedName).length() else 0L,
                )
            }.toMutableList()
        } else {
            existingManifest!!.tracks.toMutableList()
        }

        // While card update is in progress, keep updatedAt empty so interruption resumes cleanly
        val inProgressUpdatedAt = if (cardUpdated) "" else card.updatedAt
        val staticIndices = workingEntries.indices.filter { !workingEntries[it].type.equals("stream", ignoreCase = true) }
        val totalStatic = staticIndices.size

        if (totalStatic == 0) {
            store.saveCardManifest(
                OfflineCardManifest(
                    cardId = card.cardId,
                    title = card.title,
                    author = card.author,
                    updatedAt = card.updatedAt,
                    source = card.source,
                    coverFileName = coverFileName,
                    tracks = workingEntries,
                    syncedAtMs = clockMs(),
                )
            )
            return true
        }

        var allOk = true
        var doneCount = 0
        for (idx in staticIndices) {
            val entry = workingEntries[idx]
            val targetName = entry.localFileName?.takeIf { it.isNotBlank() } ?: store.deriveTrackFileName(
                idx,
                YotoTrack(
                    id = entry.id,
                    index = entry.index,
                    title = entry.title,
                    chapterTitle = entry.chapterTitle,
                    durationSeconds = entry.durationSeconds,
                    format = entry.format,
                    type = entry.type,
                    url = entry.remoteUrl,
                ),
            )
            val destFile = store.audioTrackFile(card.cardId, targetName)
            val alreadyValid = !cardUpdated && entry.localFileName == targetName && destFile.isFile && destFile.length() > 0L

            if (alreadyValid) {
                doneCount++
                onTrackProgress(doneCount, totalStatic)
                continue
            }

            val remoteUrl = apiClient.resolveUrl(entry.remoteUrl)
            if (remoteUrl.isBlank()) {
                allOk = false
                continue
            }

            try {
                val bytes = apiClient.downloadToFile(remoteUrl, destFile)
                workingEntries[idx] = entry.copy(localFileName = targetName, fileSizeBytes = bytes)
                doneCount++
                store.saveCardManifest(
                    OfflineCardManifest(
                        cardId = card.cardId,
                        title = card.title,
                        author = card.author,
                        updatedAt = inProgressUpdatedAt,
                        source = card.source,
                        coverFileName = coverFileName,
                        tracks = workingEntries.toList(),
                        syncedAtMs = clockMs(),
                    )
                )
                onTrackProgress(doneCount, totalStatic)
            } catch (_: Exception) {
                allOk = false
            }
        }

        if (allOk) {
            val keepFiles = workingEntries.mapNotNull { it.localFileName }.toSet()
            store.pruneStaleTracksInCard(card.cardId, keepFiles)
            store.saveCardManifest(
                OfflineCardManifest(
                    cardId = card.cardId,
                    title = card.title,
                    author = card.author,
                    updatedAt = card.updatedAt,
                    source = card.source,
                    coverFileName = coverFileName,
                    tracks = workingEntries.toList(),
                    syncedAtMs = clockMs(),
                )
            )
        } else if (workingEntries.any { it.localFileName != null } || existingManifest == null) {
            store.saveCardManifest(
                OfflineCardManifest(
                    cardId = card.cardId,
                    title = card.title,
                    author = card.author,
                    updatedAt = inProgressUpdatedAt,
                    source = card.source,
                    coverFileName = coverFileName,
                    tracks = workingEntries.toList(),
                    syncedAtMs = clockMs(),
                )
            )
        }

        return allOk
    }

    private fun countReadyCards(progressMap: Map<String, CardDownloadProgress>): Int {
        return progressMap.values.count { it.status == OfflineStatus.DOWNLOADED }
    }

    private fun countDownloadedStaticCards(
        cards: List<YotoCardSummary>,
        store: YotoOfflineStore,
    ): Int {
        return cards.count { store.computeCardProgress(it).status == OfflineStatus.DOWNLOADED }
    }
}
