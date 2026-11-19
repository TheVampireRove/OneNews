package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ArticleAudioController
import com.example.data.ArticleFeedAggregator
import com.example.data.ArticleWebExtractor
import com.example.data.FeedChannelPreview
import com.example.data.MockNewsRepository
import com.example.data.RssFeedParser
import com.example.data.ThemePreferencesRepository
import com.example.data.WebExtractionResult
import com.example.model.AppDestination
import com.example.model.AudioPlaybackState
import com.example.model.BriefSchedule
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.NewsSource
import com.example.model.PageTurnAnimation
import com.example.model.ThemeMode
import com.example.model.ThemeSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

data class OneNewsUiState(
    val articles: List<NewsArticle> = emptyList(),
    val filteredArticles: List<NewsArticle> = emptyList(),
    val nowBriefArticles: List<NewsArticle> = emptyList(),
    val availableSources: List<NewsSource> = emptyList(),
    val enabledSourceIds: Set<String> = emptySet(),
    val customCategories: List<String> = emptyList(),
    val activeDestination: AppDestination = AppDestination.FEED,
    val selectedCategory: NewsCategory = NewsCategory.ALL,
    val selectedCustomCategory: String? = null,
    val searchQuery: String = "",
    val isSearchExpanded: Boolean = false,
    val selectedArticleId: String? = null,
    val hasUserManuallySelectedArticle: Boolean = false,
    val isInitialFeedLoading: Boolean = true,
    val isCompactReaderOpen: Boolean = false,
    val isFullScreenBookOpen: Boolean = false,
    val pageFlipDirection: Int = 1,
    val feedFirstVisibleIndex: Int = 0,
    val feedFirstVisibleOffset: Int = 0,
    val readerScrollPositions: Map<String, Int> = emptyMap(),
    val readerPagePositions: Map<String, Int> = emptyMap(),
    val bookmarkedIds: Set<String> = emptySet(),
    val offlineSavedIds: Set<String> = emptySet(),
    val dismissedNudgeIds: Set<String> = emptySet(),
    val extractingArticleIds: Set<String> = emptySet(),
    val isRefreshingFeed: Boolean = false,
    val themeSettings: ThemeSettings = ThemeSettings(),
    val audioState: AudioPlaybackState = AudioPlaybackState(),
    val snackbarMessage: String? = null,
) {
    val selectedArticle: NewsArticle?
        get() = selectedArticleId?.let { id -> articles.find { it.id == id } }

    val savedArticles: List<NewsArticle>
        get() = articles.filter { it.id in bookmarkedIds || it.id in offlineSavedIds }
}

internal data class ArticleSelectionResolution(
    val selectedArticleId: String?,
    val hasUserManuallySelectedArticle: Boolean,
)

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = ThemePreferencesRepository(application)
    private val audioController = ArticleAudioController(application, viewModelScope)
    private val webExtractor = ArticleWebExtractor(application)

    private val baseArticles = MockNewsRepository.getArticles()
    private val curatedSources = MockNewsRepository.getCuratedSources()
    private val defaultEnabledIds = curatedSources.filter { it.isDefaultEnabled }.map { it.id }.toSet()

    private val liveArticlesBySourceId = MutableStateFlow<Map<String, List<NewsArticle>>>(emptyMap())
    private val ogImageOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    private val fullTextOverrides = MutableStateFlow<Map<String, WebExtractionResult>>(emptyMap())
    private val extractingIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val isRefreshingFlow = MutableStateFlow(false)
    private val isInitialFeedLoadingFlow = MutableStateFlow(true)
    private val clockRefreshMillisFlow = MutableStateFlow(System.currentTimeMillis())

    @Volatile
    private var initialLiveSyncTriggered = false

    private val fetchedSourceIds = java.util.Collections.synchronizedSet(HashSet<String>())
    private val ogLookupInFlight = java.util.Collections.synchronizedSet(HashSet<String>())
    private val extractionInFlight = java.util.Collections.synchronizedSet(HashSet<String>())
    private val networkSemaphore = Semaphore(4)
    private val bgExceptionHandler = CoroutineExceptionHandler { _, _ -> }

    private val _uiState = MutableStateFlow(
        OneNewsUiState(
            availableSources = curatedSources,
            enabledSourceIds = defaultEnabledIds,
            selectedArticleId = null,
            hasUserManuallySelectedArticle = false,
            isInitialFeedLoading = true,
        )
    )
    val uiState: StateFlow<OneNewsUiState> = _uiState.asStateFlow()

    val hasUserManuallySelectedArticle: Boolean
        get() = _uiState.value.hasUserManuallySelectedArticle

    init {
        viewModelScope.launch {
            val categoryMetaFlow = combine(
                prefsRepo.customCategoriesFlow,
                prefsRepo.sourceCategoryOverridesFlow,
            ) { customCats, overrides ->
                customCats to overrides
            }

            val settingsAndAudio = combine(
                prefsRepo.themeSettingsFlow,
                prefsRepo.bookmarkedIdsFlow,
                prefsRepo.offlineSavedIdsFlow,
                prefsRepo.enabledSourceIdsFlow,
                prefsRepo.customSourcesFlow,
            ) { settings, bookmarks, offline, enabledSrcIds, customSources ->
                BaseSettingsSnapshot(settings, bookmarks, offline, enabledSrcIds, customSources)
            }.combine(categoryMetaFlow) { base, (customCats, overrides) ->
                SettingsSnapshot(
                    settings = base.settings,
                    bookmarks = base.bookmarks,
                    offline = base.offline,
                    enabledSourceIds = base.enabledSourceIds,
                    customSources = base.customSources,
                    customCategories = customCats,
                    categoryOverrides = overrides,
                )
            }.combine(audioController.engineState) { snap, audioEngine ->
                snap to audioEngine
            }

            val refreshStateFlow = combine(
                isRefreshingFlow,
                isInitialFeedLoadingFlow,
                clockRefreshMillisFlow,
            ) { isRefreshing, isInitialLoading, clockNowMillis ->
                Triple(isRefreshing, isInitialLoading, clockNowMillis)
            }

            val networkFlow = combine(
                liveArticlesBySourceId,
                ogImageOverrides,
                fullTextOverrides,
                extractingIdsFlow,
                refreshStateFlow,
            ) { liveMap, ogMap, fullTextMap, extractingIds, (isRefreshing, isInitialLoading, clockNowMillis) ->
                NetworkSnapshot(
                    liveMap = liveMap,
                    ogMap = ogMap,
                    fullTextMap = fullTextMap,
                    extractingIds = extractingIds,
                    isRefreshing = isRefreshing,
                    isInitialFeedLoading = isInitialLoading,
                    clockNowMillis = clockNowMillis,
                )
            }

            combine(settingsAndAudio, networkFlow) { (snap, audioEngine), net ->
                Triple(snap, audioEngine, net)
            }.collect { (snap, audioEngine, net) ->
                try {
                    val allSources = (curatedSources + snap.customSources)
                        .distinctBy { it.id }
                        .map { src ->
                            val override = snap.categoryOverrides[src.id]
                            if (override != null) {
                                src.copy(
                                    category = override.first,
                                    customCategory = override.second,
                                )
                            } else {
                                src
                            }
                        }
                    val allCustomCategories = (snap.customCategories + allSources.mapNotNull { it.cleanCustomCategory })
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                        .sorted()

                    val isInitialTrigger = !initialLiveSyncTriggered
                    if (isInitialTrigger) {
                        initialLiveSyncTriggered = true
                    }
                    syncEnabledLiveFeeds(
                        allSources = allSources,
                        enabledSourceIds = snap.enabledSourceIds,
                        isInitialLoad = isInitialTrigger,
                    )

                    val effectiveInitialLoading = isInitialFeedLoadingFlow.value
                    val activeArticles = computeActiveArticles(
                        allSources = allSources,
                        enabledSourceIds = snap.enabledSourceIds,
                        localArea = snap.settings.localNewsLocation,
                        radiusMiles = snap.settings.localNewsRadiusMiles,
                        liveMap = net.liveMap,
                        ogMap = net.ogMap,
                        fullTextMap = net.fullTextMap,
                        nowMillis = net.clockNowMillis,
                        isInitialFeedLoading = effectiveInitialLoading,
                    )

                    _uiState.update { current ->
                        val validCustomSelected = current.selectedCustomCategory?.takeIf { sel ->
                            allCustomCategories.any { it.equals(sel, ignoreCase = true) }
                        }
                        val nextFiltered = filterArticles(
                            articles = activeArticles,
                            category = current.selectedCategory,
                            query = current.searchQuery,
                            selectedCustomCategory = validCustomSelected,
                        )
                        val activeListForSelection = when (current.activeDestination) {
                            AppDestination.SAVED -> activeArticles.filter {
                                it.id in snap.bookmarks || it.id in snap.offline
                            }
                            else -> nextFiltered
                        }
                        val resolvedSelection = resolveSelection(
                            activeFeedArticles = activeListForSelection,
                            currentSelectedId = current.selectedArticleId,
                            hasUserManuallySelectedArticle = current.hasUserManuallySelectedArticle,
                            isInitialFeedLoading = effectiveInitialLoading,
                        )
                        current.copy(
                            articles = activeArticles,
                            filteredArticles = nextFiltered,
                            nowBriefArticles = computeNowBriefArticles(activeArticles, snap.settings.briefTopics),
                            availableSources = allSources,
                            enabledSourceIds = snap.enabledSourceIds,
                            customCategories = allCustomCategories,
                            selectedCustomCategory = validCustomSelected,
                            selectedArticleId = resolvedSelection.selectedArticleId,
                            hasUserManuallySelectedArticle = resolvedSelection.hasUserManuallySelectedArticle,
                            isInitialFeedLoading = effectiveInitialLoading,
                            bookmarkedIds = snap.bookmarks,
                            offlineSavedIds = snap.offline,
                            extractingArticleIds = net.extractingIds,
                            isRefreshingFeed = net.isRefreshing,
                            themeSettings = snap.settings,
                            audioState = current.audioState.copy(
                                isPlaying = audioEngine.isPlaying,
                                progress = audioEngine.progress,
                                durationSeconds = audioEngine.durationSeconds,
                                playbackSpeed = audioEngine.speed,
                            ),
                        )
                    }

                    _uiState.value.selectedArticle?.let { ensureFullTextExtracted(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Preserve last known good UI state instead of crashing on malformed feed/prefs update
                }
            }
        }
    }

    private fun syncEnabledLiveFeeds(
        allSources: List<NewsSource>,
        enabledSourceIds: Set<String>,
        isInitialLoad: Boolean = false,
    ) {
        val pendingSources = allSources.filter { source ->
            source.id in enabledSourceIds &&
                source.feedUrl.startsWith("http", ignoreCase = true) &&
                !source.feedUrl.contains(".example/") &&
                fetchedSourceIds.add(source.id)
        }
        if (pendingSources.isEmpty()) {
            if (isInitialLoad && isInitialFeedLoadingFlow.value) {
                isInitialFeedLoadingFlow.value = false
            }
            return
        }

        viewModelScope.launch(bgExceptionHandler) {
            try {
                val parsedBySource = pendingSources.map { source ->
                    async {
                        val parsed = try {
                            val xml = networkSemaphore.withPermit {
                                webExtractor.fetchFeedText(source.feedUrl)
                            }
                            if (!xml.isNullOrBlank()) {
                                RssFeedParser.parseFeed(xml, source, maxItems = 6)
                            } else {
                                emptyList()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                        source.id to parsed
                    }
                }.awaitAll()

                val nonEmpty = parsedBySource.filter { it.second.isNotEmpty() }
                if (nonEmpty.isNotEmpty()) {
                    liveArticlesBySourceId.update { current ->
                        current + nonEmpty.toMap()
                    }
                    clockRefreshMillisFlow.value = System.currentTimeMillis()
                    val articlesNeedingOg = nonEmpty.flatMap { it.second }.filter {
                        it.imageUrl == null && !it.articleUrl.isNullOrBlank()
                    }
                    enrichMissingOgImages(articlesNeedingOg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                if (isInitialLoad || isInitialFeedLoadingFlow.value) {
                    isInitialFeedLoadingFlow.value = false
                }
            }
        }
    }

    fun refreshFeed() {
        if (isRefreshingFlow.value) return
        val currentState = _uiState.value
        val activeLiveSources = currentState.availableSources.filter { source ->
            source.id in currentState.enabledSourceIds &&
                source.feedUrl.startsWith("http", ignoreCase = true) &&
                !source.feedUrl.contains(".example/")
        }

        isRefreshingFlow.value = true
        viewModelScope.launch(bgExceptionHandler) {
            val startedAt = System.currentTimeMillis()
            try {
                if (activeLiveSources.isNotEmpty()) {
                    val parsedBySource = activeLiveSources.map { source ->
                        fetchedSourceIds.add(source.id)
                        async {
                            val parsed = try {
                                val xml = networkSemaphore.withPermit {
                                    webExtractor.fetchFeedText(source.feedUrl)
                                }
                                if (!xml.isNullOrBlank()) {
                                    RssFeedParser.parseFeed(xml, source, maxItems = 6)
                                } else {
                                    emptyList()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                            source.id to parsed
                        }
                    }.awaitAll()

                    val nonEmpty = parsedBySource.filter { it.second.isNotEmpty() }
                    if (nonEmpty.isNotEmpty()) {
                        liveArticlesBySourceId.update { current ->
                            current + nonEmpty.toMap()
                        }
                        val articlesNeedingOg = nonEmpty.flatMap { it.second }.filter {
                            it.imageUrl == null && !it.articleUrl.isNullOrBlank()
                        }
                        enrichMissingOgImages(articlesNeedingOg)
                    }
                }

                clockRefreshMillisFlow.value = System.currentTimeMillis()
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < 450L) {
                    kotlinx.coroutines.delay(450L - elapsed)
                }
                showSnackbar("Feed refreshed")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showSnackbar("Feed refreshed (some sources unreachable)")
            } finally {
                isRefreshingFlow.value = false
            }
        }
    }

    private fun enrichMissingOgImages(articles: List<NewsArticle>) {
        val urlsToFetch = articles.mapNotNull { it.articleUrl }
            .distinct()
            .filter { ogLookupInFlight.add(it) }
        if (urlsToFetch.isEmpty()) return

        viewModelScope.launch(bgExceptionHandler) {
            try {
                val results = urlsToFetch.map { url ->
                    async {
                        val ogUrl = try {
                            networkSemaphore.withPermit {
                                webExtractor.fetchOgImage(url)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                        url to ogUrl
                    }
                }.awaitAll()

                val found = results.mapNotNull { (url, img) ->
                    if (!img.isNullOrBlank()) url to img else null
                }.toMap()
                if (found.isNotEmpty()) {
                    ogImageOverrides.update { it + found }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun ensureFullTextExtracted(article: NewsArticle) {
        val url = article.articleUrl ?: return
        if (!url.startsWith("http", ignoreCase = true) || url.contains(".example/")) return
        if (article.isFullTextExtracted || article.fullTextExtractionFailed) return
        if (!RssFeedParser.needsWebExtraction(article.paragraphs)) return
        if (fullTextOverrides.value.containsKey(url)) return
        if (!extractionInFlight.add(article.id)) return

        extractingIdsFlow.update { it + article.id }
        viewModelScope.launch(bgExceptionHandler) {
            try {
                val extracted = webExtractor.extractFullArticle(url)
                fullTextOverrides.update { it + (url to extracted) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                extractingIdsFlow.update { it - article.id }
                extractionInFlight.remove(article.id)
            }
        }
    }

    private fun computeActiveArticles(
        allSources: List<NewsSource>,
        enabledSourceIds: Set<String>,
        localArea: String,
        radiusMiles: Int,
        liveMap: Map<String, List<NewsArticle>>,
        ogMap: Map<String, String>,
        fullTextMap: Map<String, WebExtractionResult>,
        nowMillis: Long,
        isInitialFeedLoading: Boolean = false,
    ): List<NewsArticle> {
        val enabledSources = allSources.filter { it.id in enabledSourceIds }
        val enabledByName = enabledSources.associateBy { it.name }

        val localizedList = MockNewsRepository.getLocalizedArticlesForArea(
            areaName = localArea,
            radiusMiles = radiusMiles,
            nowMillis = nowMillis,
        )
        val localizedById = localizedList.associateBy { it.id }

        val mergedBase = baseArticles.map { article ->
            localizedById[article.id] ?: article
        } + localizedList.filter { loc -> baseArticles.none { it.id == loc.id } }

        val activeBase = mergedBase.mapNotNull { article ->
            val matchedSource = enabledByName[article.source] ?: return@mapNotNull null
            if (article.category == matchedSource.category && article.customCategory == matchedSource.cleanCustomCategory) {
                article
            } else {
                article.copy(
                    category = matchedSource.category,
                    customCategory = matchedSource.cleanCustomCategory,
                )
            }
        }
        val baseSourceNames = mergedBase.map { it.source }.toSet()

        val liveEnabledArticles = enabledSources.flatMap { source ->
            val liveItems = liveMap[source.id].orEmpty()
            if (liveItems.isNotEmpty()) {
                liveItems.map { item ->
                    if (item.category == source.category && item.customCategory == source.cleanCustomCategory) {
                        item
                    } else {
                        item.copy(
                            category = source.category,
                            customCategory = source.cleanCustomCategory,
                        )
                    }
                }
            } else if (source.name !in baseSourceNames &&
                (source.feedUrl.contains(".example/") || !isInitialFeedLoading)
            ) {
                listOf(MockNewsRepository.buildArticleForSource(source, nowMillis))
            } else {
                emptyList()
            }
        }

        val enriched = (liveEnabledArticles + activeBase).map { article ->
            val url = article.articleUrl
            val extraction = if (url != null) fullTextMap[url] else null
            val ogImage = if (url != null) ogMap[url] else null
            val updatedImage = article.imageUrl ?: extraction?.imageUrl ?: ogImage
            val updatedParagraphs = if (extraction != null && extraction.paragraphs.isNotEmpty()) {
                extraction.paragraphs
            } else {
                article.paragraphs
            }
            val updatedReadMins = if (extraction != null && extraction.paragraphs.isNotEmpty()) {
                (updatedParagraphs.sumOf { it.split(Regex("\\s+")).size } / 210).coerceIn(2, 18)
            } else {
                article.readingTimeMinutes
            }

            if (updatedImage == article.imageUrl && extraction == null) {
                article
            } else {
                article.copy(
                    imageUrl = updatedImage,
                    articleUrl = extraction?.canonicalUrl ?: article.articleUrl,
                    paragraphs = updatedParagraphs,
                    readingTimeMinutes = updatedReadMins,
                    isFullTextExtracted = extraction != null && !extraction.extractionFailed && extraction.paragraphs.isNotEmpty(),
                    fullTextExtractionFailed = extraction?.extractionFailed == true,
                )
            }
        }

        return ArticleFeedAggregator.mergeDeduplicateAndSort(
            articles = enriched,
            nowMillis = nowMillis,
        )
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update { current ->
            val targetList = when (destination) {
                AppDestination.SAVED -> current.savedArticles
                AppDestination.FEED -> current.filteredArticles
                AppDestination.SETTINGS -> null
            }
            val resolved = if (targetList != null) {
                val keepManual = current.hasUserManuallySelectedArticle &&
                    current.selectedArticleId != null &&
                    targetList.any { it.id == current.selectedArticleId }
                resolveSelection(
                    activeFeedArticles = targetList,
                    currentSelectedId = if (keepManual) current.selectedArticleId else null,
                    hasUserManuallySelectedArticle = keepManual,
                    isInitialFeedLoading = current.isInitialFeedLoading,
                )
            } else {
                ArticleSelectionResolution(
                    selectedArticleId = current.selectedArticleId,
                    hasUserManuallySelectedArticle = current.hasUserManuallySelectedArticle,
                )
            }
            current.copy(
                activeDestination = destination,
                selectedArticleId = resolved.selectedArticleId,
                hasUserManuallySelectedArticle = resolved.hasUserManuallySelectedArticle,
                isCompactReaderOpen = false,
                isFullScreenBookOpen = false,
            )
        }
    }

    fun selectCategory(category: NewsCategory) {
        _uiState.update { current ->
            val nextFiltered = filterArticles(
                articles = current.articles,
                category = category,
                query = current.searchQuery,
                selectedCustomCategory = null,
            )
            // Reset manual selection flag when switching category tabs so the reader pane
            // immediately previews the newest sorted story in the newly selected category.
            val resolved = resolveSelection(
                activeFeedArticles = nextFiltered,
                currentSelectedId = null,
                hasUserManuallySelectedArticle = false,
                isInitialFeedLoading = current.isInitialFeedLoading,
            )
            current.copy(
                selectedCategory = category,
                selectedCustomCategory = null,
                filteredArticles = nextFiltered,
                selectedArticleId = resolved.selectedArticleId,
                hasUserManuallySelectedArticle = resolved.hasUserManuallySelectedArticle,
            )
        }
        _uiState.value.selectedArticle?.let { ensureFullTextExtracted(it) }
    }

    fun selectCustomCategory(customCategory: String?) {
        val clean = customCategory?.trim()?.takeIf { it.isNotEmpty() }
        _uiState.update { current ->
            val nextFiltered = filterArticles(
                articles = current.articles,
                category = NewsCategory.ALL,
                query = current.searchQuery,
                selectedCustomCategory = clean,
            )
            val resolved = resolveSelection(
                activeFeedArticles = nextFiltered,
                currentSelectedId = null,
                hasUserManuallySelectedArticle = false,
                isInitialFeedLoading = current.isInitialFeedLoading,
            )
            current.copy(
                selectedCategory = NewsCategory.ALL,
                selectedCustomCategory = clean,
                filteredArticles = nextFiltered,
                selectedArticleId = resolved.selectedArticleId,
                hasUserManuallySelectedArticle = resolved.hasUserManuallySelectedArticle,
            )
        }
        _uiState.value.selectedArticle?.let { ensureFullTextExtracted(it) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            val nextFiltered = filterArticles(
                articles = current.articles,
                category = current.selectedCategory,
                query = query,
                selectedCustomCategory = current.selectedCustomCategory,
            )
            val resolved = resolveSelection(
                activeFeedArticles = nextFiltered,
                currentSelectedId = current.selectedArticleId,
                hasUserManuallySelectedArticle = current.hasUserManuallySelectedArticle,
                isInitialFeedLoading = current.isInitialFeedLoading,
            )
            current.copy(
                searchQuery = query,
                filteredArticles = nextFiltered,
                selectedArticleId = resolved.selectedArticleId,
                hasUserManuallySelectedArticle = resolved.hasUserManuallySelectedArticle,
            )
        }
    }

    fun toggleSearchExpanded(expanded: Boolean? = null) {
        _uiState.update { current ->
            val next = expanded ?: !current.isSearchExpanded
            val nextQuery = if (!next) "" else current.searchQuery
            val nextFiltered = filterArticles(
                articles = current.articles,
                category = current.selectedCategory,
                query = nextQuery,
                selectedCustomCategory = current.selectedCustomCategory,
            )
            val resolved = resolveSelection(
                activeFeedArticles = nextFiltered,
                currentSelectedId = current.selectedArticleId,
                hasUserManuallySelectedArticle = current.hasUserManuallySelectedArticle,
                isInitialFeedLoading = current.isInitialFeedLoading,
            )
            current.copy(
                isSearchExpanded = next,
                searchQuery = nextQuery,
                filteredArticles = nextFiltered,
                selectedArticleId = resolved.selectedArticleId,
                hasUserManuallySelectedArticle = resolved.hasUserManuallySelectedArticle,
            )
        }
    }

    fun openArticle(articleId: String, openCompactReader: Boolean = true) {
        _uiState.update { current ->
            val oldIndex = current.articles.indexOfFirst { it.id == current.selectedArticleId }
            val newIndex = current.articles.indexOfFirst { it.id == articleId }
            val dir = if (oldIndex < 0 || newIndex >= oldIndex) 1 else -1
            val openBook = current.themeSettings.fullScreenBookReader ||
                !current.themeSettings.enableDualPaneLayout
            current.copy(
                selectedArticleId = articleId,
                hasUserManuallySelectedArticle = true,
                isCompactReaderOpen = openCompactReader && !openBook,
                isFullScreenBookOpen = openBook,
                pageFlipDirection = dir,
            )
        }
        _uiState.value.selectedArticle?.let { ensureFullTextExtracted(it) }
    }

    fun closeCompactReader() {
        _uiState.update {
            it.copy(
                isCompactReaderOpen = false,
                isFullScreenBookOpen = false,
            )
        }
    }

    fun closeFullScreenBookReader() {
        _uiState.update { it.copy(isFullScreenBookOpen = false) }
    }

    fun navigateNextArticle() {
        val currentList = activeArticleList()
        if (currentList.isEmpty()) return
        val currentId = _uiState.value.selectedArticleId
        val idx = currentList.indexOfFirst { it.id == currentId }
        val nextIdx = if (idx < 0 || idx >= currentList.lastIndex) 0 else idx + 1
        _uiState.update {
            it.copy(
                selectedArticleId = currentList[nextIdx].id,
                hasUserManuallySelectedArticle = true,
                pageFlipDirection = 1,
            )
        }
        _uiState.value.selectedArticle?.let { ensureFullTextExtracted(it) }
    }

    fun navigatePreviousArticle() {
        val currentList = activeArticleList()
        if (currentList.isEmpty()) return
        val currentId = _uiState.value.selectedArticleId
        val idx = currentList.indexOfFirst { it.id == currentId }
        val prevIdx = if (idx <= 0) currentList.lastIndex else idx - 1
        _uiState.update {
            it.copy(
                selectedArticleId = currentList[prevIdx].id,
                hasUserManuallySelectedArticle = true,
                pageFlipDirection = -1,
            )
        }
        _uiState.value.selectedArticle?.let { ensureFullTextExtracted(it) }
    }

    fun updateFeedScrollPosition(index: Int, offset: Int) {
        _uiState.update {
            if (it.feedFirstVisibleIndex == index && it.feedFirstVisibleOffset == offset) {
                it
            } else {
                it.copy(
                    feedFirstVisibleIndex = index,
                    feedFirstVisibleOffset = offset,
                )
            }
        }
    }

    fun updateReaderScrollPosition(articleId: String, scrollValue: Int) {
        _uiState.update { current ->
            if (current.readerScrollPositions[articleId] == scrollValue) {
                current
            } else {
                current.copy(
                    readerScrollPositions = current.readerScrollPositions + (articleId to scrollValue)
                )
            }
        }
    }

    fun updateReaderPagePosition(articleId: String, pageIndex: Int) {
        val safePage = pageIndex.coerceAtLeast(0)
        _uiState.update { current ->
            if (current.readerPagePositions[articleId] == safePage) {
                current
            } else {
                current.copy(
                    readerPagePositions = current.readerPagePositions + (articleId to safePage)
                )
            }
        }
    }

    fun toggleBookmark(articleId: String) {
        val current = _uiState.value.bookmarkedIds
        val updated = if (articleId in current) current - articleId else current + articleId
        val added = articleId in updated
        viewModelScope.launch { prefsRepo.setBookmarkedIds(updated) }
        showSnackbar(if (added) "Bookmarked to Saved" else "Removed from bookmarks")
    }

    fun toggleOfflineSave(articleId: String) {
        val current = _uiState.value.offlineSavedIds
        val updated = if (articleId in current) current - articleId else current + articleId
        val added = articleId in updated
        viewModelScope.launch { prefsRepo.setOfflineSavedIds(updated) }
        showSnackbar(if (added) "Saved for offline reading" else "Removed offline copy")
    }

    fun saveAllNowBriefOffline() {
        val briefIds = _uiState.value.nowBriefArticles.map { it.id }.toSet()
        val updated = _uiState.value.offlineSavedIds + briefIds
        viewModelScope.launch { prefsRepo.setOfflineSavedIds(updated) }
        showSnackbar("Saved ${briefIds.size} Editor's Brief articles offline")
    }

    fun clearOfflineCache() {
        viewModelScope.launch { prefsRepo.setOfflineSavedIds(emptySet()) }
        showSnackbar("Offline article cache cleared")
    }

    fun dismissNowNudge(articleId: String) {
        _uiState.update { it.copy(dismissedNudgeIds = it.dismissedNudgeIds + articleId) }
    }

    fun restoreNowNudge(articleId: String) {
        _uiState.update { it.copy(dismissedNudgeIds = it.dismissedNudgeIds - articleId) }
    }

    fun playNowBriefAudio() {
        val briefArticles = _uiState.value.nowBriefArticles
        if (briefArticles.isEmpty()) return
        val script = briefArticles.joinToString(separator = " ... Next story: ") { article ->
            "${article.headline}. ${article.audioSummary.ifBlank { article.subtitle }}"
        }
        val speed = _uiState.value.themeSettings.audioPlaybackSpeed
        _uiState.update {
            it.copy(
                audioState = AudioPlaybackState(
                    activeArticleId = null,
                    isNowBriefQueue = true,
                    title = "2-Min Editor's Brief",
                    subtitle = "${briefArticles.size} top stories • ${
                        _uiState.value.themeSettings.briefTopics.joinToString(", ") { t -> t.label }
                    }",
                    isPlaying = true,
                    progress = 0f,
                    durationSeconds = 120,
                    playbackSpeed = speed,
                    spokenText = script,
                )
            )
        }
        audioController.play(
            text = script,
            durationSeconds = 120,
            speed = speed,
            startProgress = 0f,
        )
    }

    fun playArticleAudio(article: NewsArticle) {
        val currentAudio = _uiState.value.audioState
        if (currentAudio.activeArticleId == article.id && !currentAudio.isNowBriefQueue) {
            audioController.togglePlayPause()
            return
        }
        val text = "${article.headline}. ${article.subtitle}. ${
            article.audioSummary.ifBlank { article.paragraphs.joinToString(" ") }
        }"
        val duration = if (article.audioDurationSeconds > 0) article.audioDurationSeconds else article.readingTimeMinutes * 45
        val speed = _uiState.value.themeSettings.audioPlaybackSpeed
        _uiState.update {
            it.copy(
                audioState = AudioPlaybackState(
                    activeArticleId = article.id,
                    isNowBriefQueue = false,
                    title = article.headline,
                    subtitle = "${article.source} • Audio Brief",
                    isPlaying = true,
                    progress = 0f,
                    durationSeconds = duration,
                    playbackSpeed = speed,
                    spokenText = text,
                )
            )
        }
        audioController.play(
            text = text,
            durationSeconds = duration,
            speed = speed,
            startProgress = 0f,
        )
    }

    fun toggleAudioPlayPause() {
        audioController.togglePlayPause()
    }

    fun seekAudio(progress: Float) {
        audioController.seekTo(progress)
    }

    fun cycleAudioSpeed() {
        val speeds = listOf(0.8f, 1.0f, 1.25f, 1.5f)
        val current = _uiState.value.audioState.playbackSpeed
        val nextIdx = (speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f } + 1) % speeds.size
        val nextSpeed = speeds[nextIdx]
        audioController.setSpeed(nextSpeed)
        setDefaultAudioSpeed(nextSpeed)
    }

    fun stopAudio() {
        audioController.stop()
        _uiState.update { it.copy(audioState = AudioPlaybackState()) }
    }

    // Source controls
    fun toggleSourceEnabled(sourceId: String) {
        val current = _uiState.value.enabledSourceIds
        val updated = if (sourceId in current) {
            if (current.size <= 1) {
                showSnackbar("Keep at least one active source")
                return
            }
            current - sourceId
        } else {
            fetchedSourceIds.remove(sourceId)
            current + sourceId
        }
        viewModelScope.launch { prefsRepo.setEnabledSourceIds(updated) }
    }

    suspend fun previewFeedForAdd(feedUrl: String): FeedChannelPreview {
        return webExtractor.previewFeed(
            feedUrl = feedUrl,
            localHintLocation = _uiState.value.themeSettings.localNewsLocation,
        )
    }

    fun addFeed(
        url: String,
        title: String,
        category: NewsCategory,
        customCategory: String? = null,
    ) {
        val cleanName = title.replace(Regex("[\\r\\n|]+"), " ").trim()
        if (cleanName.isBlank()) {
            showSnackbar("Enter a source name")
            return
        }
        val rawUrl = url.replace(Regex("[\\r\\n|\\s]+"), "").trim()
        val cleanUrl = if (rawUrl.isBlank()) {
            val slug = cleanName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "customfeed" }
            "https://$slug.example/rss"
        } else {
            val withScheme = if (rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true)
            ) {
                rawUrl
            } else {
                "https://$rawUrl"
            }
            val validated = withScheme.toHttpUrlOrNull()
            if (validated == null) {
                showSnackbar("Enter a valid http/https feed URL")
                return
            }
            validated.toString()
        }
        val cleanCustomCat = customCategory?.replace(Regex("[\\r\\n|]+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedCategory = if (cleanCustomCat != null) NewsCategory.ALL else category
        val newSource = NewsSource(
            id = "custom_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            name = cleanName,
            category = resolvedCategory,
            feedUrl = cleanUrl,
            description = "Custom RSS / Atom feed",
            isCustom = true,
            isDefaultEnabled = true,
            customCategory = cleanCustomCat,
        )
        _uiState.update { current ->
            val updatedSources = (current.availableSources + newSource).distinctBy { it.id }
            val updatedEnabled = current.enabledSourceIds + newSource.id
            val updatedCustomCats = if (cleanCustomCat != null &&
                current.customCategories.none { it.equals(cleanCustomCat, ignoreCase = true) }
            ) {
                (current.customCategories + cleanCustomCat).sorted()
            } else {
                current.customCategories
            }
            val addedArticle = MockNewsRepository.buildArticleForSource(newSource)
            val updatedArticles = ArticleFeedAggregator.mergeDeduplicateAndSort(
                articles = listOf(addedArticle) + current.articles,
            )
            val updatedFiltered = filterArticles(
                articles = updatedArticles,
                category = current.selectedCategory,
                query = current.searchQuery,
                selectedCustomCategory = current.selectedCustomCategory,
            )
            val resolved = resolveSelection(
                activeFeedArticles = updatedFiltered,
                currentSelectedId = current.selectedArticleId,
                hasUserManuallySelectedArticle = current.hasUserManuallySelectedArticle,
                isInitialFeedLoading = current.isInitialFeedLoading,
            )
            current.copy(
                availableSources = updatedSources,
                enabledSourceIds = updatedEnabled,
                customCategories = updatedCustomCats,
                articles = updatedArticles,
                filteredArticles = updatedFiltered,
                selectedArticleId = resolved.selectedArticleId,
                hasUserManuallySelectedArticle = resolved.hasUserManuallySelectedArticle,
            )
        }
        viewModelScope.launch(bgExceptionHandler) { prefsRepo.addCustomSource(newSource) }
        showSnackbar("Added $cleanName to ${newSource.assignedCategoryLabel}")
    }

    fun addManualSource(
        name: String,
        feedUrl: String,
        category: NewsCategory,
        customCategory: String? = null,
    ) {
        addFeed(url = feedUrl, title = name, category = category, customCategory = customCategory)
    }

    fun reassignSourceCategory(
        sourceId: String,
        category: NewsCategory,
        customCategory: String? = null,
    ) {
        val cleanCustom = customCategory?.replace(Regex("[\\r\\n|]+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedBuiltIn = if (cleanCustom != null) NewsCategory.ALL else category
        val sourceName = _uiState.value.availableSources.find { it.id == sourceId }?.name ?: "Feed"
        val targetLabel = cleanCustom ?: category.pickerLabel
        _uiState.update { current ->
            val updatedSources = current.availableSources.map { src ->
                if (src.id == sourceId) {
                    src.copy(category = resolvedBuiltIn, customCategory = cleanCustom)
                } else {
                    src
                }
            }
            val updatedCustomCats = if (cleanCustom != null &&
                current.customCategories.none { it.equals(cleanCustom, ignoreCase = true) }
            ) {
                (current.customCategories + cleanCustom).sorted()
            } else {
                current.customCategories
            }
            val updatedArticles = current.articles.map { art ->
                if (art.source == sourceName) {
                    art.copy(category = resolvedBuiltIn, customCategory = cleanCustom)
                } else {
                    art
                }
            }
            val updatedFiltered = filterArticles(
                articles = updatedArticles,
                category = current.selectedCategory,
                query = current.searchQuery,
                selectedCustomCategory = current.selectedCustomCategory,
            )
            val resolved = resolveSelection(
                activeFeedArticles = updatedFiltered,
                currentSelectedId = current.selectedArticleId,
                hasUserManuallySelectedArticle = current.hasUserManuallySelectedArticle,
                isInitialFeedLoading = current.isInitialFeedLoading,
            )
            current.copy(
                availableSources = updatedSources,
                customCategories = updatedCustomCats,
                articles = updatedArticles,
                filteredArticles = updatedFiltered,
                selectedArticleId = resolved.selectedArticleId,
                hasUserManuallySelectedArticle = resolved.hasUserManuallySelectedArticle,
            )
        }
        viewModelScope.launch(bgExceptionHandler) {
            prefsRepo.reassignSourceCategory(
                sourceId = sourceId,
                category = category,
                customCategory = cleanCustom,
            )
        }
        showSnackbar("Moved $sourceName to $targetLabel")
    }

    fun createCustomCategory(categoryName: String) {
        val clean = categoryName.replace(Regex("[\\r\\n|]+"), " ").trim()
        if (clean.isBlank()) return
        viewModelScope.launch(bgExceptionHandler) {
            prefsRepo.addCustomCategory(clean)
        }
    }

    fun removeCustomSource(sourceId: String) {
        viewModelScope.launch(bgExceptionHandler) {
            liveArticlesBySourceId.update { it - sourceId }
            fetchedSourceIds.remove(sourceId)
            prefsRepo.removeCustomSource(sourceId)
        }
        showSnackbar("Removed custom source")
    }

    // Theme & Settings updates
    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(useDynamicColor = enabled) } }
    }

    fun setUseAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(useAmoledBlack = enabled) } }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(themeMode = mode) } }
    }

    fun setReaderFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.85f, 1.4f)
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(readerFontScale = clamped) } }
    }

    fun setDefaultAudioSpeed(speed: Float) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(audioPlaybackSpeed = speed) } }
        audioController.setSpeed(speed)
    }

    fun setUseSerifHeadlines(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(useSerifHeadlines = enabled) } }
    }

    fun setUseSystemFont(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(useSystemFont = enabled) } }
    }

    fun setEnableNowBrief(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(enableNowBrief = enabled) } }
    }

    fun setShowNowNudgeChips(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(showNowNudgeChips = enabled) } }
    }

    fun setSmartNowNudgeAutoHide(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(smartNowNudgeAutoHide = enabled) } }
    }

    fun setEnableDualPaneLayout(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                themeSettings = current.themeSettings.copy(enableDualPaneLayout = enabled),
                isCompactReaderOpen = if (!enabled) false else current.isCompactReaderOpen,
            )
        }
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(enableDualPaneLayout = enabled) } }
    }

    fun setFullScreenBookReader(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(fullScreenBookReader = enabled) } }
    }

    fun setPaginatedReadingMode(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(isPaginatedReadingMode = enabled) } }
    }

    fun setPageTurnAnimation(animation: PageTurnAnimation) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(pageTurnAnimation = animation) } }
    }

    fun setLocalNewsLocation(location: String) {
        val clean = location.trim().ifBlank { return }
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(localNewsLocation = clean) } }
        showSnackbar("Local news updated for $clean")
    }

    fun setLocalNewsRadiusMiles(miles: Int) {
        viewModelScope.launch {
            prefsRepo.updateSettings { it.copy(localNewsRadiusMiles = miles.coerceIn(5, 100)) }
        }
    }

    fun toggleBriefTopic(topic: NewsCategory) {
        if (topic == NewsCategory.ALL) return
        viewModelScope.launch {
            prefsRepo.updateSettings { current ->
                val updated = if (topic in current.briefTopics && current.briefTopics.size > 1) {
                    current.briefTopics - topic
                } else {
                    current.briefTopics + topic
                }
                current.copy(briefTopics = updated)
            }
        }
    }

    fun setBriefSchedule(schedule: BriefSchedule) {
        viewModelScope.launch { prefsRepo.updateSettings { it.copy(briefSchedule = schedule) } }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun activeArticleList(): List<NewsArticle> {
        val state = _uiState.value
        return when (state.activeDestination) {
            AppDestination.SAVED -> state.savedArticles.ifEmpty { state.articles }
            else -> state.filteredArticles.ifEmpty { state.articles }
        }
    }

    private fun filterArticles(
        articles: List<NewsArticle>,
        category: NewsCategory,
        query: String,
        selectedCustomCategory: String? = null,
    ): List<NewsArticle> {
        return ArticleFeedAggregator.filterAndSort(
            articles = articles,
            category = category,
            query = query,
            selectedCustomCategory = selectedCustomCategory,
        )
    }

    private fun computeNowBriefArticles(
        articles: List<NewsArticle>,
        topics: Set<NewsCategory>,
    ): List<NewsArticle> {
        val matching = articles.filter { it.customCategory.isNullOrBlank() && it.category in topics }
        val prioritized = matching.sortedWith(
            compareByDescending<NewsArticle> { it.isFeaturedForBrief || it.isBreaking }
                .thenByDescending { it.publishedAtEpochMillis }
        )
        return prioritized.take(4).ifEmpty { articles.take(3) }
    }

    override fun onCleared() {
        super.onCleared()
        audioController.shutdown()
    }

    companion object {
        internal fun resolveSelection(
            activeFeedArticles: List<NewsArticle>,
            currentSelectedId: String?,
            hasUserManuallySelectedArticle: Boolean,
            isInitialFeedLoading: Boolean,
        ): ArticleSelectionResolution {
            if (isInitialFeedLoading && !hasUserManuallySelectedArticle) {
                return ArticleSelectionResolution(
                    selectedArticleId = null,
                    hasUserManuallySelectedArticle = false,
                )
            }
            if (hasUserManuallySelectedArticle &&
                currentSelectedId != null &&
                activeFeedArticles.any { it.id == currentSelectedId }
            ) {
                return ArticleSelectionResolution(
                    selectedArticleId = currentSelectedId,
                    hasUserManuallySelectedArticle = true,
                )
            }
            return ArticleSelectionResolution(
                selectedArticleId = activeFeedArticles.firstOrNull()?.id,
                hasUserManuallySelectedArticle = false,
            )
        }
    }
}

private data class BaseSettingsSnapshot(
    val settings: ThemeSettings,
    val bookmarks: Set<String>,
    val offline: Set<String>,
    val enabledSourceIds: Set<String>,
    val customSources: List<NewsSource>,
)

private data class SettingsSnapshot(
    val settings: ThemeSettings,
    val bookmarks: Set<String>,
    val offline: Set<String>,
    val enabledSourceIds: Set<String>,
    val customSources: List<NewsSource>,
    val customCategories: List<String>,
    val categoryOverrides: Map<String, Pair<NewsCategory, String?>>,
)

private data class NetworkSnapshot(
    val liveMap: Map<String, List<NewsArticle>>,
    val ogMap: Map<String, String>,
    val fullTextMap: Map<String, WebExtractionResult>,
    val extractingIds: Set<String>,
    val isRefreshing: Boolean,
    val isInitialFeedLoading: Boolean,
    val clockNowMillis: Long,
)
