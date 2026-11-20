package com.homelab.yototablet.data

import com.homelab.yototablet.model.OfflineStatus
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class YotoSyncEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun syncAll_downloadsAllPlaylists_skipsLiveStreams_playsOffline_andSyncsIncrementally() {
        val root = tempFolder.newFolder("yoto_sync_test")
        val store = YotoOfflineStore(root)

        val audioDownloadCount = AtomicInteger(0)
        val liveStreamDownloadAttempted = AtomicBoolean(false)
        val includeNewCard = AtomicBoolean(false)
        val removeSecondCard = AtomicBoolean(false)
        val dc5IhVersion = AtomicInteger(1)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/yoto/cards") { ex ->
            val path = ex.requestURI.path
            val body = when (path) {
                "/api/yoto/cards" -> {
                    val cards = mutableListOf(
                        """{"card_id":"dc5Ih","title":"Buddhist Tales","cover_url":"/media/yoto-cache/covers/dc5Ih.png","track_count":2,"duration_seconds":300,"source":"myo","updated_at":"v${dc5IhVersion.get()}"}"""
                    )
                    if (!removeSecondCard.get()) {
                        cards += """{"card_id":"7JtVV","title":"Frozen Stories","cover_url":"/media/yoto-cache/covers/7JtVV.png","track_count":1,"duration_seconds":180,"source":"library","updated_at":"v1"}"""
                    }
                    cards += """{"card_id":"5rJ03","title":"Yoto Sleep Radio","category":"radio","cover_url":"/media/yoto-cache/covers/5rJ03.png","track_count":1,"duration_seconds":0,"source":"library","updated_at":"v1"}"""
                    if (includeNewCard.get()) {
                        cards += """{"card_id":"new01","title":"New Moana","cover_url":"/media/yoto-cache/covers/new01.png","track_count":1,"duration_seconds":120,"source":"myo","updated_at":"v1"}"""
                    }
                    """{"ready":true,"needs_auth":false,"cached":false,"count":${cards.size},"cards":[${cards.joinToString(",")}]}"""
                }
                "/api/yoto/cards/dc5Ih" -> {
                    val v = dc5IhVersion.get()
                    """{"ready":true,"playable":true,"card_id":"dc5Ih","title":"Buddhist Tales","cover_url":"/media/yoto-cache/covers/dc5Ih.png","count":2,"tracks":[{"id":"1","index":0,"title":"Track A v$v","duration_seconds":120,"format":"opus","type":"audio","url":"/media/yoto-cache/audio/dc5Ih/001.ogg"},{"id":"2","index":1,"title":"Track B v$v","duration_seconds":180,"format":"opus","type":"audio","url":"/media/yoto-cache/audio/dc5Ih/002.ogg"}]}"""
                }
                "/api/yoto/cards/7JtVV" -> {
                    """{"ready":true,"playable":true,"card_id":"7JtVV","title":"Frozen Stories","cover_url":"/media/yoto-cache/covers/7JtVV.png","count":1,"tracks":[{"id":"1","index":0,"title":"Elsa","duration_seconds":180,"format":"aac","type":"audio","url":"/media/yoto-cache/audio/7JtVV/001.m4a"}]}"""
                }
                "/api/yoto/cards/5rJ03" -> {
                    """{"ready":true,"playable":true,"card_id":"5rJ03","title":"Yoto Sleep Radio","cover_url":"/media/yoto-cache/covers/5rJ03.png","count":1,"tracks":[{"id":"live","index":0,"title":"Sleep Radio","duration_seconds":0,"format":"aac","type":"stream","url":"http://127.0.0.1:${server.address.port}/live/radio"}]}"""
                }
                "/api/yoto/cards/new01" -> {
                    """{"ready":true,"playable":true,"card_id":"new01","title":"New Moana","cover_url":"/media/yoto-cache/covers/new01.png","count":1,"tracks":[{"id":"1","index":0,"title":"How Far","duration_seconds":120,"format":"mp3","type":"audio","url":"/media/yoto-cache/audio/new01/001.mp3"}]}"""
                }
                else -> {
                    ex.sendResponseHeaders(404, 0)
                    ex.close()
                    return@createContext
                }
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/media/yoto-cache/") { ex ->
            val path = ex.requestURI.path
            val payload = if (path.contains("/audio/")) {
                audioDownloadCount.incrementAndGet()
                "FAKE_AUDIO_BYTES_${path}".toByteArray(Charsets.UTF_8)
            } else {
                "FAKE_PNG_BYTES_${path}".toByteArray(Charsets.UTF_8)
            }
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        }
        server.createContext("/live/radio") { ex ->
            liveStreamDownloadAttempted.set(true)
            ex.sendResponseHeaders(500, 0)
            ex.close()
        }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val api = HttpYotoApiClient { baseUrl }
            val online = AtomicBoolean(true)
            val engine = YotoSyncEngine(store, api, isOnlineCheck = { online.get() }, clockMs = { 100_000L })

            // 1. Initial sync downloads all static playlists & skips live radio stream
            var latestProgress = emptyMap<String, com.homelab.yototablet.model.CardDownloadProgress>()
            var latestCards = emptyList<com.homelab.yototablet.model.YotoCardSummary>()
            val status1 = engine.syncAll(forceRefreshCatalog = true) { c, p, _ ->
                latestCards = c
                latestProgress = p
            }

            assertTrue(status1.isServerReachable)
            assertEquals(3, latestCards.size)
            assertEquals(2, status1.completedCards)
            assertEquals(2, status1.totalDownloadableCards)
            assertEquals(3, audioDownloadCount.get())
            assertFalse(liveStreamDownloadAttempted.get())
            assertEquals(OfflineStatus.DOWNLOADED, latestProgress["dc5Ih"]?.status)
            assertEquals(OfflineStatus.DOWNLOADED, latestProgress["7JtVV"]?.status)
            assertEquals(OfflineStatus.LIVE_STREAM_ONLY, latestProgress["5rJ03"]?.status)

            // 2. Re-sync immediately with no changes -> 0 additional audio downloads
            audioDownloadCount.set(0)
            val status2 = engine.syncAll(forceRefreshCatalog = false) { _, _, _ -> }
            assertEquals(0, audioDownloadCount.get())
            assertEquals(2, status2.completedCards)

            // 3. Simulate offline device -> cached playlists still resolve strictly from local files!
            online.set(false)
            val offlineStatus = engine.syncAll(forceRefreshCatalog = true) { _, _, _ -> }
            assertFalse(offlineStatus.isServerReachable)
            val dc5Card = latestCards.first { it.cardId == "dc5Ih" }
            val playableOffline = store.resolvePlayableCard(dc5Card) { api.resolveUrl(it) }
            assertNotNull(playableOffline)
            assertTrue(playableOffline!!.isFullyOffline)
            assertEquals(2, playableOffline.tracks.size)
            assertTrue(playableOffline.tracks.all { it.isOffline && it.localFilePath != null && File(it.localFilePath).isFile })

            // 4. Back online with catalog changes: update dc5Ih to v2, add new01, remove 7JtVV
            online.set(true)
            dc5IhVersion.set(2)
            includeNewCard.set(true)
            removeSecondCard.set(true)
            audioDownloadCount.set(0)

            val status3 = engine.syncAll(forceRefreshCatalog = true) { c, p, _ ->
                latestCards = c
                latestProgress = p
            }
            assertEquals(3, latestCards.size)
            assertEquals(3, audioDownloadCount.get()) // 2 updated tracks for dc5Ih + 1 new track for new01
            assertEquals(2, status3.completedCards) // dc5Ih + new01
            assertEquals(OfflineStatus.DOWNLOADED, latestProgress["new01"]?.status)
            assertFalse(store.cardAudioDir("7JtVV").exists())
            assertFalse(store.coverFile("7JtVV").exists())
        } finally {
            server.stop(0)
        }
    }
}
