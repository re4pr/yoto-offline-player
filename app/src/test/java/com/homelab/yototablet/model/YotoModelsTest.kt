package com.homelab.yototablet.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YotoModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun yotoCardsResponse_deserializesUnicodeAndStreamCards() {
        val raw = """
            {
              "ready": true,
              "needs_auth": false,
              "cached": false,
              "message": "",
              "count": 3,
              "cards": [
                {
                  "card_id": "cYBL6",
                  "title": "เพลงเด็กอนุบาล (Thai Kids Songs)",
                  "description": "รวมเพลงเด็ก",
                  "author": "",
                  "category": "",
                  "cover_url": "/media/yoto-cache/covers/cYBL6.png",
                  "track_count": 26,
                  "duration_seconds": 4602,
                  "source": "myo",
                  "last_played_at": "2026-11-20T13:18:49.503Z",
                  "updated_at": "2026-11-20T13:18:49.503Z"
                },
                {
                  "card_id": "6AuPf",
                  "title": "Magyar Népdalok és Gyerekdalok",
                  "author": "",
                  "category": "",
                  "cover_url": "/media/yoto-cache/covers/6AuPf.png",
                  "track_count": 26,
                  "duration_seconds": 3096,
                  "source": "myo"
                },
                {
                  "card_id": "5rJ03",
                  "title": "Yoto Sleep Radio",
                  "author": "Yoto",
                  "category": "radio",
                  "cover_url": "/media/yoto-cache/covers/5rJ03.png",
                  "track_count": 1,
                  "duration_seconds": 0,
                  "source": "library"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<YotoCardsResponse>(raw)
        assertTrue(parsed.ready)
        assertFalse(parsed.needsAuth)
        assertEquals(3, parsed.cards.size)
        assertEquals("เพลงเด็กอนุบาล (Thai Kids Songs)", parsed.cards[0].title)
        assertEquals("Magyar Népdalok és Gyerekdalok", parsed.cards[1].title)
        assertEquals("radio", parsed.cards[2].category)
    }

    @Test
    fun yotoCardDetailResponse_deserializesStaticAndLiveStreamTracks() {
        val raw = """
            {
              "ready": true,
              "playable": true,
              "card_id": "5rJ03",
              "title": "Yoto Sleep Radio",
              "author": "Yoto",
              "cover_url": "/media/yoto-cache/covers/5rJ03.png",
              "message": "",
              "count": 1,
              "tracks": [
                {
                  "id": "1",
                  "index": 0,
                  "title": "Yoto Sleep Radio Live",
                  "chapter_title": "Yoto Sleep Radio",
                  "duration_seconds": 0.0,
                  "format": "aac",
                  "type": "stream",
                  "url": "https://s3.radio.co/s74390585c/listen",
                  "icon_url": ""
                }
              ]
            }
        """.trimIndent()

        val detail = json.decodeFromString<YotoCardDetailResponse>(raw)
        assertTrue(detail.ready)
        assertTrue(detail.playable)
        assertEquals("stream", detail.tracks.single().type)
        assertEquals("https://s3.radio.co/s74390585c/listen", detail.tracks.single().url)
    }

    @Test
    fun formattingAndValidationHelpers_workAsExpected() {
        val card = YotoCardSummary(
            cardId = "dc5Ih",
            title = "Buddhist Tales for Kids",
            trackCount = 18,
            durationSeconds = 6315,
            source = "myo",
        )
        assertEquals("18 tracks · 1h 45m", formatCardSubtitle(card))
        assertEquals("4:35", formatDurationMs(275_000L))
        assertEquals("1.5 MB", formatBytes(1_572_864L))
        assertTrue(isValidCardId("dc5Ih_01-a"))
        assertFalse(isValidCardId("../etc"))
        assertFalse(isValidCardId("a/b"))
        assertFalse(isValidCardId(""))
    }
}
