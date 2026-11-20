package com.homelab.yototablet.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.homelab.yototablet.BuildConfig
import com.homelab.yototablet.model.CardDownloadProgress
import com.homelab.yototablet.model.OfflineStatus
import com.homelab.yototablet.model.PlayableCard
import com.homelab.yototablet.model.SyncStatusState
import com.homelab.yototablet.model.YotoCardSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class YotoOfflineSyncManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _baseUrl = MutableStateFlow(readBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    val store = YotoOfflineStore(File(appContext.filesDir, "yoto_offline"))
    val apiClient = HttpYotoApiClient { _baseUrl.value }
    private val syncEngine = YotoSyncEngine(
        store = store,
        apiClient = apiClient,
        isOnlineCheck = ::isNetworkAvailable,
    )

    private val _cards = MutableStateFlow<List<YotoCardSummary>>(emptyList())
    val cards: StateFlow<List<YotoCardSummary>> = _cards.asStateFlow()

    private val _cardProgress = MutableStateFlow<Map<String, CardDownloadProgress>>(emptyMap())
    val cardProgress: StateFlow<Map<String, CardDownloadProgress>> = _cardProgress.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatusState())
    val syncStatus: StateFlow<SyncStatusState> = _syncStatus.asStateFlow()

    @Volatile
    private var priorityCardId: String? = null

    @Volatile
    private var lastProcessSyncElapsedMs: Long = 0L

    private var networkCallbackRegistered = false

    fun initialize() {
        store.cleanupTempFiles()
        val cachedCatalog = store.loadCatalog()
        val initialCards = cachedCatalog?.cards.orEmpty()
        val initialProgress = store.computeAllProgress(initialCards)
        val totalBytes = store.calculateTotalOfflineBytes()
        val readyCount = initialProgress.values.count { it.status == OfflineStatus.DOWNLOADED }
        val totalStatic = initialCards.count { initialProgress[it.cardId]?.isStreamOnly != true }
        val lastSynced = cachedCatalog?.syncedAtMs ?: prefs.getLong(KEY_LAST_SYNC_MS, 0L)

        _cards.value = initialCards
        _cardProgress.value = initialProgress
        _syncStatus.value = SyncStatusState(
            isRefreshingCatalog = false,
            isDownloading = false,
            isServerReachable = isNetworkAvailable(),
            lastSyncedAtMs = lastSynced,
            completedCards = readyCount,
            totalDownloadableCards = totalStatic,
            totalOfflineBytes = totalBytes,
            statusMessage = when {
                initialCards.isEmpty() -> "Connecting to home hub..."
                readyCount >= totalStatic -> "All $readyCount playlists ready offline"
                else -> "$readyCount of $totalStatic playlists ready offline"
            },
        )
        registerNetworkCallbackIfNeeded()
    }

    fun updateBaseUrl(newUrl: String) {
        val clean = newUrl.trim().trimEnd('/').ifBlank { BuildConfig.DEFAULT_HUB_URL }
        prefs.edit().putString(KEY_BASE_URL, clean).apply()
        _baseUrl.value = clean
        triggerManualSync()
    }

    fun getSavedThemeVariantId(): String? {
        return prefs.getString(KEY_THEME_VARIANT, null)?.takeIf { it.isNotBlank() }
    }

    fun setSavedThemeVariantId(variantId: String?) {
        prefs.edit().putString(KEY_THEME_VARIANT, variantId ?: "").apply()
    }

    fun isNetworkAvailable(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun triggerAutoSyncIfNeeded(minIntervalMs: Long = DEFAULT_AUTO_SYNC_INTERVAL_MS) {
        scope.launch {
            val nowElapsed = SystemClock.elapsedRealtime()
            val hasIncomplete = _cards.value.isEmpty() || _cards.value.any { store.cardNeedsSync(it) }
            val elapsedSinceLast = nowElapsed - lastProcessSyncElapsedMs
            if (lastProcessSyncElapsedMs == 0L || hasIncomplete || elapsedSinceLast >= minIntervalMs) {
                runSyncSuspending(forceRefresh = true)
            }
        }
    }

    fun triggerManualSync() {
        scope.launch {
            runSyncSuspending(forceRefresh = true)
        }
    }

    suspend fun runSyncSuspending(forceRefresh: Boolean = true): SyncStatusState = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val finalState = syncEngine.syncAll(
                forceRefreshCatalog = forceRefresh,
                priorityCardIdProvider = { priorityCardId },
            ) { updatedCards, updatedProgress, updatedStatus ->
                _cards.value = updatedCards
                _cardProgress.value = updatedProgress
                _syncStatus.value = updatedStatus
            }
            if (finalState.isServerReachable) {
                lastProcessSyncElapsedMs = SystemClock.elapsedRealtime()
                if (finalState.lastSyncedAtMs > 0L) {
                    prefs.edit().putLong(KEY_LAST_SYNC_MS, finalState.lastSyncedAtMs).apply()
                }
            }
            priorityCardId = null
            finalState
        }
    }

    suspend fun prioritizeAndResolveCard(card: YotoCardSummary): Result<PlayableCard> = withContext(Dispatchers.IO) {
        priorityCardId = card.cardId
        val online = isNetworkAvailable()
        val localPlayable = store.resolvePlayableCard(
            card = card,
            remoteFallbackDetail = null,
            resolveRemoteUrl = apiClient::resolveUrl,
        )

        if (localPlayable != null) {
            if (localPlayable.isLiveStreamOnly) {
                return@withContext if (online) {
                    Result.success(localPlayable)
                } else {
                    Result.failure(IllegalStateException("${card.title} is live radio and needs Wi-Fi."))
                }
            }

            if (localPlayable.isFullyOffline) {
                return@withContext Result.success(localPlayable)
            }

            if (!online) {
                val offlineTracks = localPlayable.tracks.filter { it.isOffline }
                return@withContext if (offlineTracks.isNotEmpty()) {
                    Result.success(
                        localPlayable.copy(
                            tracks = offlineTracks,
                            isFullyOffline = false,
                        )
                    )
                } else {
                    Result.failure(
                        IllegalStateException("Connect to home Wi-Fi once to download ${card.title}.")
                    )
                }
            }

            // Online and incomplete: play right away over LAN and prioritize offline download
            triggerAutoSyncIfNeeded(minIntervalMs = 0L)
            return@withContext Result.success(localPlayable)
        }

        if (!online) {
            return@withContext Result.failure(
                IllegalStateException("Connect to home Wi-Fi once to download ${card.title}.")
            )
        }

        val detail = runCatching { apiClient.fetchCardDetail(card.cardId, forceRefresh = false) }.getOrNull()
        if (detail == null || !detail.playable || detail.tracks.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException(
                    detail?.message?.ifBlank { null }
                        ?: "Could not load ${card.title}. Make sure tablet is on home Wi-Fi."
                )
            )
        }

        val resolved = store.resolvePlayableCard(
            card = card,
            remoteFallbackDetail = detail,
            resolveRemoteUrl = apiClient::resolveUrl,
        ) ?: return@withContext Result.failure(
            IllegalStateException("No playable tracks found on ${card.title}.")
        )

        if (!resolved.isLiveStreamOnly && !resolved.isFullyOffline) {
            triggerAutoSyncIfNeeded(minIntervalMs = 0L)
        }
        Result.success(resolved)
    }

    private fun registerNetworkCallbackIfNeeded() {
        if (networkCallbackRegistered) return
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        scope.launch {
                            delay(2_000L)
                            triggerAutoSyncIfNeeded(minIntervalMs = 60_000L)
                        }
                    }

                    override fun onLost(network: Network) {
                        val online = isNetworkAvailable()
                        if (!online) {
                            val current = _syncStatus.value
                            if (!current.isDownloading && !current.isRefreshingCatalog) {
                                val readyCount = _cardProgress.value.values.count {
                                    it.status == OfflineStatus.DOWNLOADED
                                }
                                _syncStatus.value = current.copy(
                                    isServerReachable = false,
                                    statusMessage = if (_cards.value.isEmpty()) {
                                        "Offline · Connect to home Wi-Fi"
                                    } else {
                                        "Offline · $readyCount playlists ready"
                                    },
                                )
                            }
                        }
                    }
                }
            )
            networkCallbackRegistered = true
        }
    }

    private fun readBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_HUB_URL)
            ?.trim()
            ?.trimEnd('/')
            ?.ifBlank { BuildConfig.DEFAULT_HUB_URL }
            ?: BuildConfig.DEFAULT_HUB_URL
    }

    companion object {
        private const val PREFS_NAME = "yoto_tablet_prefs"
        private const val KEY_BASE_URL = "hub_api_base_url"
        private const val KEY_THEME_VARIANT = "theme_variant_id"
        private const val KEY_LAST_SYNC_MS = "last_sync_ms"
        private const val DEFAULT_AUTO_SYNC_INTERVAL_MS = 5 * 60_000L
    }
}
