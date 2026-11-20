package com.homelab.yototablet.data

import com.homelab.yototablet.model.OfflineCardManifest
import com.homelab.yototablet.model.OfflineCatalogFile
import com.homelab.yototablet.model.OfflineStatus
import com.homelab.yototablet.model.OfflineTrackEntry
import com.homelab.yototablet.model.YotoCardSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class YotoOfflineStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun catalogAndManifest_persistAtomicallyAndCleanTempFiles() {
        val root = tempFolder.newFolder("yoto_offline")
        val store = YotoOfflineStore(root)
        store.ensureBaseDirs()

        File(root, ".tmp_catalog.json").writeText("broken")
        File(root, "covers/.tmp_c1.png").writeText("broken")

        val card = YotoCardSummary(
            cardId = "dc5Ih",
            title = "Buddhist Tales",
            coverUrl = "/media/yoto-cache/covers/dc5Ih.png",
            trackCount = 2,
            updatedAt = "2026-11-20T13:19:06Z",
        )
        store.saveCatalog(OfflineCatalogFile(syncedAtMs = 12345L, cards = listOf(card)))
        store.cleanupTempFiles()

        val loadedCat = store.loadCatalog()
        assertNotNull(loadedCat)
        assertEquals(1, loadedCat!!.cards.size)
        assertEquals("dc5Ih", loadedCat.cards.first().cardId)
        assertFalse(File(root, ".tmp_catalog.json").exists())
        assertFalse(File(root, "covers/.tmp_c1.png").exists())
    }

    @Test
    fun progressAndNeedsSync_rejectZeroByteFilesAndDetectStaleness() {
        val root = tempFolder.newFolder("yoto_offline_2")
        val store = YotoOfflineStore(root)

        val card = YotoCardSummary(
            cardId = "7JtVV",
            title = "Frozen Stories",
            coverUrl = "/media/yoto-cache/covers/7JtVV.png",
            trackCount = 2,
            updatedAt = "v1",
        )

        assertEquals(OfflineStatus.PENDING, store.computeCardProgress(card).status)
        assertTrue(store.cardNeedsSync(card))

        store.coverFile("7JtVV").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val track1 = store.audioTrackFile("7JtVV", "001.m4a").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(10, 20, 30))
        }
        // Track 2 is 0 bytes — must NOT count as downloaded
        store.audioTrackFile("7JtVV", "002.m4a").apply {
            writeBytes(ByteArray(0))
        }

        store.saveCardManifest(
            OfflineCardManifest(
                cardId = "7JtVV",
                title = "Frozen Stories",
                updatedAt = "v1",
                coverFileName = "7JtVV.png",
                tracks = listOf(
                    OfflineTrackEntry(id = "1", index = 0, title = "One", remoteUrl = "/a/1.m4a", localFileName = "001.m4a", fileSizeBytes = track1.length()),
                    OfflineTrackEntry(id = "2", index = 1, title = "Two", remoteUrl = "/a/2.m4a", localFileName = "002.m4a", fileSizeBytes = 0L),
                ),
            )
        )

        val partial = store.computeCardProgress(card)
        assertEquals(OfflineStatus.PARTIAL, partial.status)
        assertEquals(1, partial.downloadedTracks)
        assertEquals(2, partial.totalDownloadableTracks)
        assertTrue(store.cardNeedsSync(card))

        // Fix track 2 with real bytes
        store.audioTrackFile("7JtVV", "002.m4a").writeBytes(byteArrayOf(40, 50, 60, 70))
        val downloaded = store.computeCardProgress(card)
        assertEquals(OfflineStatus.DOWNLOADED, downloaded.status)
        assertEquals(2, downloaded.downloadedTracks)
        assertFalse(store.cardNeedsSync(card))

        // Updating card metadata makes it stale
        val updatedCard = card.copy(updatedAt = "v2")
        assertTrue(store.cardNeedsSync(updatedCard))
        assertEquals(OfflineStatus.PARTIAL, store.computeCardProgress(updatedCard).status)
    }

    @Test
    fun streamCardAndPruningAndTraversalProtection_workSafely() {
        val root = tempFolder.newFolder("yoto_offline_3")
        val store = YotoOfflineStore(root)

        val radioCard = YotoCardSummary(
            cardId = "5rJ03",
            title = "Yoto Sleep Radio",
            category = "radio",
            coverUrl = "/media/yoto-cache/covers/5rJ03.png",
            trackCount = 1,
        )
        store.coverFile("5rJ03").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(7, 8, 9))
        }
        store.saveCardManifest(
            OfflineCardManifest(
                cardId = "5rJ03",
                title = "Yoto Sleep Radio",
                coverFileName = "5rJ03.png",
                tracks = listOf(
                    OfflineTrackEntry(
                        id = "1",
                        index = 0,
                        title = "Live",
                        type = "stream",
                        remoteUrl = "https://example.com/stream",
                        localFileName = null,
                    )
                ),
            )
        )

        val radioProg = store.computeCardProgress(radioCard)
        assertEquals(OfflineStatus.LIVE_STREAM_ONLY, radioProg.status)
        assertTrue(radioProg.isStreamOnly)
        assertFalse(store.cardNeedsSync(radioCard))

        val playable = store.resolvePlayableCard(radioCard) { "http://192.168.1.100:9595$it" }
        assertNotNull(playable)
        assertTrue(playable!!.isLiveStreamOnly)
        assertFalse(playable.isFullyOffline)
        assertNull(playable.tracks.single().localFilePath)

        // Create removed card files and verify pruning cleans them up
        store.coverFile("oldCard").writeBytes(byteArrayOf(1, 2))
        store.audioTrackFile("oldCard", "001.ogg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(3, 4))
        }
        val pruned = store.pruneRemovedCards(setOf("5rJ03"))
        assertEquals(2, pruned)
        assertFalse(store.coverFile("oldCard").exists())
        assertFalse(store.cardAudioDir("oldCard").exists())

        // Verify path traversal is rejected
        var rejected = false
        try {
            store.audioTrackFile("5rJ03", "../escape.ogg")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
