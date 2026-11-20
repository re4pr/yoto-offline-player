package com.homelab.yototablet.data

import com.homelab.yototablet.model.YotoCardDetailResponse
import com.homelab.yototablet.model.YotoCardsResponse
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface YotoNetworkClient {
    fun resolveUrl(relativeOrAbsolute: String): String
    fun fetchCards(forceRefresh: Boolean): YotoCardsResponse
    fun fetchCardDetail(cardId: String, forceRefresh: Boolean): YotoCardDetailResponse
    fun downloadToFile(url: String, targetFile: File): Long
}

class HttpYotoApiClient(
    private val baseUrlProvider: () -> String,
) : YotoNetworkClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun resolveUrl(relativeOrAbsolute: String): String {
        val raw = relativeOrAbsolute.trim()
        if (raw.isEmpty()) return ""
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        val base = baseUrlProvider().trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }
        val path = if (raw.startsWith("/")) raw else "/$raw"
        return "$base$path"
    }

    override fun fetchCards(forceRefresh: Boolean): YotoCardsResponse {
        val base = baseUrlProvider().trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }
        val suffix = if (forceRefresh) "?refresh=true" else ""
        val body = httpGetJson("$base/api/yoto/cards$suffix", readTimeoutMs = 18_000)
        return json.decodeFromString<YotoCardsResponse>(body)
    }

    override fun fetchCardDetail(cardId: String, forceRefresh: Boolean): YotoCardDetailResponse {
        val base = baseUrlProvider().trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }
        val encodedId = URLEncoder.encode(cardId, Charsets.UTF_8.name())
        val suffix = if (forceRefresh) "?refresh=true" else ""
        val body = httpGetJson("$base/api/yoto/cards/$encodedId$suffix", readTimeoutMs = 20_000)
        return json.decodeFromString<YotoCardDetailResponse>(body)
    }

    override fun downloadToFile(url: String, targetFile: File): Long {
        val parent = targetFile.parentFile ?: throw IOException("Missing parent directory for ${targetFile.path}")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create directory ${parent.path}")
        }
        val tempFile = File(parent, ".tmp_${targetFile.name}")
        var lastError: IOException? = null

        for (attempt in 0 until 3) {
            tempFile.delete()
            var conn: HttpURLConnection? = null
            try {
                conn = openFollowingRedirects(url, connectTimeoutMs = 5_000, readTimeoutMs = 60_000)
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IOException("HTTP $code downloading $url")
                }
                val expectedLen = conn.contentLengthLong
                var written = 0L
                BufferedInputStream(conn.inputStream, BUFFER_SIZE).use { input ->
                    BufferedOutputStream(FileOutputStream(tempFile), BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                written += read
                            }
                        }
                        output.flush()
                    }
                }
                if (written <= 0L) {
                    throw IOException("Downloaded empty file from $url")
                }
                if (expectedLen > 0L && written != expectedLen) {
                    throw IOException("Incomplete download from $url: got $written of $expectedLen bytes")
                }
                moveAtomicOrReplace(tempFile, targetFile)
                return written
            } catch (ioe: IOException) {
                lastError = ioe
                tempFile.delete()
                if (attempt < 2) {
                    try {
                        Thread.sleep(500L * (attempt + 1))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw ioe
                    }
                }
            } finally {
                conn?.disconnect()
            }
        }
        throw lastError ?: IOException("Failed to download $url")
    }

    private fun httpGetJson(urlStr: String, readTimeoutMs: Int): String {
        val conn = openFollowingRedirects(
            startUrl = urlStr,
            connectTimeoutMs = 3_000,
            readTimeoutMs = readTimeoutMs,
            acceptHeader = "application/json",
        )
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
            if (code !in 200..299) {
                throw IOException("HTTP $code: $body")
            }
            body
        } finally {
            conn.disconnect()
        }
    }

    private fun openFollowingRedirects(
        startUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        acceptHeader: String? = null,
    ): HttpURLConnection {
        var current = startUrl
        for (hop in 0 until 5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                if (!acceptHeader.isNullOrBlank()) {
                    setRequestProperty("Accept", acceptHeader)
                }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) {
                    throw IOException("Redirect $code with empty Location from $current")
                }
                current = URL(URL(current), loc).toString()
                continue
            }
            return conn
        }
        throw IOException("Too many redirects fetching $startUrl")
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://192.168.1.100:9595"
        private const val BUFFER_SIZE = 131_072
    }
}

internal fun moveAtomicOrReplace(source: File, target: File) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: Exception) {
        if (target.exists()) {
            target.delete()
        }
        if (!source.renameTo(target)) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
