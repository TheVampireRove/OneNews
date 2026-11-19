package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BriefSchedule
import com.example.model.NewsCategory
import com.example.model.NewsSource
import com.example.model.PageTurnAnimation
import com.example.model.ThemeMode
import com.example.model.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onenews_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val USE_AMOLED_BLACK = booleanPreferencesKey("use_amoled_black")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_FONT_SCALE = floatPreferencesKey("reader_font_scale")
        val AUDIO_SPEED = floatPreferencesKey("audio_speed")
        val USE_SERIF_HEADLINES = booleanPreferencesKey("use_serif_headlines")
        val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        val ENABLE_NOW_BRIEF = booleanPreferencesKey("enable_now_brief")
        val SHOW_NOW_NUDGE_CHIPS = booleanPreferencesKey("show_now_nudge_chips")
        val SMART_NUDGE_AUTO_HIDE = booleanPreferencesKey("smart_nudge_auto_hide")
        val ENABLE_DUAL_PANE_LAYOUT = booleanPreferencesKey("enable_dual_pane_layout")
        val FULL_SCREEN_BOOK_READER = booleanPreferencesKey("full_screen_book_reader")
        val IS_PAGINATED_READING_MODE = booleanPreferencesKey("is_paginated_reading_mode")
        val PAGE_TURN_ANIMATION = stringPreferencesKey("page_turn_animation")
        val LOCAL_NEWS_LOCATION = stringPreferencesKey("local_news_location")
        val LOCAL_NEWS_RADIUS_MILES = intPreferencesKey("local_news_radius_miles")
        val BRIEF_TOPICS = stringSetPreferencesKey("brief_topics")
        val BRIEF_SCHEDULE = stringPreferencesKey("brief_schedule")
        val BOOKMARKED_IDS = stringSetPreferencesKey("bookmarked_ids")
        val OFFLINE_SAVED_IDS = stringSetPreferencesKey("offline_saved_ids")
        val ENABLED_SOURCE_IDS = stringSetPreferencesKey("enabled_source_ids")
        val CUSTOM_SOURCES_SERIALIZED = stringSetPreferencesKey("custom_sources_serialized")
        val CUSTOM_CATEGORY_LIST = stringSetPreferencesKey("custom_category_list")
        val SOURCE_CATEGORY_OVERRIDES = stringSetPreferencesKey("source_category_overrides")
    }

    private val defaultEnabledSourceIds: Set<String> = MockNewsRepository.getCuratedSources()
        .filter { it.isDefaultEnabled }
        .map { it.id }
        .toSet()

    private val safeDataFlow: Flow<Preferences> = context.themeDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val themeSettingsFlow: Flow<ThemeSettings> = safeDataFlow.map { prefs ->
        readSettingsFromPrefs(prefs)
    }

    val bookmarkedIdsFlow: Flow<Set<String>> = safeDataFlow.map { prefs ->
        prefs[Keys.BOOKMARKED_IDS] ?: setOf("art_02", "art_04")
    }

    val offlineSavedIdsFlow: Flow<Set<String>> = safeDataFlow.map { prefs ->
        prefs[Keys.OFFLINE_SAVED_IDS] ?: setOf("art_01")
    }

    val enabledSourceIdsFlow: Flow<Set<String>> = safeDataFlow.map { prefs ->
        prefs[Keys.ENABLED_SOURCE_IDS] ?: defaultEnabledSourceIds
    }

    val customSourcesFlow: Flow<List<NewsSource>> = safeDataFlow.map { prefs ->
        deserializeCustomSources(prefs[Keys.CUSTOM_SOURCES_SERIALIZED] ?: emptySet())
    }

    val customCategoriesFlow: Flow<List<String>> = safeDataFlow.map { prefs ->
        (prefs[Keys.CUSTOM_CATEGORY_LIST] ?: emptySet())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    val sourceCategoryOverridesFlow: Flow<Map<String, Pair<NewsCategory, String?>>> = safeDataFlow.map { prefs ->
        deserializeCategoryOverrides(prefs[Keys.SOURCE_CATEGORY_OVERRIDES] ?: emptySet())
    }

    suspend fun updateSettings(transform: (ThemeSettings) -> ThemeSettings) {
        context.themeDataStore.edit { prefs ->
            val current = readSettingsFromPrefs(prefs)
            val updated = transform(current)
            prefs[Keys.USE_DYNAMIC_COLOR] = updated.useDynamicColor
            prefs[Keys.USE_AMOLED_BLACK] = updated.useAmoledBlack
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.READER_FONT_SCALE] = updated.readerFontScale
            prefs[Keys.AUDIO_SPEED] = updated.audioPlaybackSpeed
            prefs[Keys.USE_SERIF_HEADLINES] = updated.useSerifHeadlines
            prefs[Keys.USE_SYSTEM_FONT] = updated.useSystemFont
            prefs[Keys.ENABLE_NOW_BRIEF] = updated.enableNowBrief
            prefs[Keys.SHOW_NOW_NUDGE_CHIPS] = updated.showNowNudgeChips
            prefs[Keys.SMART_NUDGE_AUTO_HIDE] = updated.smartNowNudgeAutoHide
            prefs[Keys.ENABLE_DUAL_PANE_LAYOUT] = updated.enableDualPaneLayout
            prefs[Keys.FULL_SCREEN_BOOK_READER] = updated.fullScreenBookReader
            prefs[Keys.IS_PAGINATED_READING_MODE] = updated.isPaginatedReadingMode
            prefs[Keys.PAGE_TURN_ANIMATION] = updated.pageTurnAnimation.name
            prefs[Keys.LOCAL_NEWS_LOCATION] = updated.localNewsLocation
            prefs[Keys.LOCAL_NEWS_RADIUS_MILES] = updated.localNewsRadiusMiles
            prefs[Keys.BRIEF_TOPICS] = updated.briefTopics.map { it.name }.toSet()
            prefs[Keys.BRIEF_SCHEDULE] = updated.briefSchedule.name
        }
    }

    suspend fun setBookmarkedIds(ids: Set<String>) {
        context.themeDataStore.edit { prefs -> prefs[Keys.BOOKMARKED_IDS] = ids }
    }

    suspend fun setOfflineSavedIds(ids: Set<String>) {
        context.themeDataStore.edit { prefs -> prefs[Keys.OFFLINE_SAVED_IDS] = ids }
    }

    suspend fun setEnabledSourceIds(ids: Set<String>) {
        context.themeDataStore.edit { prefs -> prefs[Keys.ENABLED_SOURCE_IDS] = ids }
    }

    suspend fun addCustomSource(source: NewsSource) {
        context.themeDataStore.edit { prefs ->
            val currentCustom = deserializeCustomSources(prefs[Keys.CUSTOM_SOURCES_SERIALIZED] ?: emptySet())
            val nextCustom = currentCustom.filterNot { it.id == source.id } + source
            prefs[Keys.CUSTOM_SOURCES_SERIALIZED] = serializeCustomSources(nextCustom)
            val currentEnabled = prefs[Keys.ENABLED_SOURCE_IDS] ?: defaultEnabledSourceIds
            prefs[Keys.ENABLED_SOURCE_IDS] = currentEnabled + source.id
            val customCat = source.cleanCustomCategory
            if (customCat != null) {
                val existingCats = prefs[Keys.CUSTOM_CATEGORY_LIST] ?: emptySet()
                if (existingCats.none { it.equals(customCat, ignoreCase = true) }) {
                    prefs[Keys.CUSTOM_CATEGORY_LIST] = existingCats + customCat
                }
            }
        }
    }

    suspend fun removeCustomSource(sourceId: String) {
        context.themeDataStore.edit { prefs ->
            val currentCustom = deserializeCustomSources(prefs[Keys.CUSTOM_SOURCES_SERIALIZED] ?: emptySet())
            val nextCustom = currentCustom.filterNot { it.id == sourceId }
            prefs[Keys.CUSTOM_SOURCES_SERIALIZED] = serializeCustomSources(nextCustom)
            val currentEnabled = prefs[Keys.ENABLED_SOURCE_IDS] ?: defaultEnabledSourceIds
            prefs[Keys.ENABLED_SOURCE_IDS] = currentEnabled - sourceId
            val overrides = deserializeCategoryOverrides(prefs[Keys.SOURCE_CATEGORY_OVERRIDES] ?: emptySet())
            if (sourceId in overrides) {
                prefs[Keys.SOURCE_CATEGORY_OVERRIDES] = serializeCategoryOverrides(overrides - sourceId)
            }
        }
    }

    suspend fun addCustomCategory(categoryName: String) {
        val clean = categoryName.replace(Regex("[|\\r\\n]+"), " ").trim()
        if (clean.isBlank()) return
        context.themeDataStore.edit { prefs ->
            val existing = prefs[Keys.CUSTOM_CATEGORY_LIST] ?: emptySet()
            if (existing.none { it.equals(clean, ignoreCase = true) }) {
                prefs[Keys.CUSTOM_CATEGORY_LIST] = existing + clean
            }
        }
    }

    suspend fun reassignSourceCategory(
        sourceId: String,
        category: NewsCategory,
        customCategory: String?,
    ) {
        val cleanCustom = customCategory?.replace(Regex("[|\\r\\n]+"), " ")?.trim()?.takeIf { it.isNotBlank() }
        val resolvedBuiltIn = if (cleanCustom != null) NewsCategory.ALL else category
        context.themeDataStore.edit { prefs ->
            val currentCustom = deserializeCustomSources(prefs[Keys.CUSTOM_SOURCES_SERIALIZED] ?: emptySet())
            val matchingCustom = currentCustom.find { it.id == sourceId }
            if (matchingCustom != null) {
                val updatedList = currentCustom.map { src ->
                    if (src.id == sourceId) {
                        src.copy(category = resolvedBuiltIn, customCategory = cleanCustom)
                    } else {
                        src
                    }
                }
                prefs[Keys.CUSTOM_SOURCES_SERIALIZED] = serializeCustomSources(updatedList)
            }
            val currentOverrides = deserializeCategoryOverrides(
                prefs[Keys.SOURCE_CATEGORY_OVERRIDES] ?: emptySet()
            ).toMutableMap()
            currentOverrides[sourceId] = resolvedBuiltIn to cleanCustom
            prefs[Keys.SOURCE_CATEGORY_OVERRIDES] = serializeCategoryOverrides(currentOverrides)

            if (cleanCustom != null) {
                val existingCats = prefs[Keys.CUSTOM_CATEGORY_LIST] ?: emptySet()
                if (existingCats.none { it.equals(cleanCustom, ignoreCase = true) }) {
                    prefs[Keys.CUSTOM_CATEGORY_LIST] = existingCats + cleanCustom
                }
            }
        }
    }

    private fun readSettingsFromPrefs(prefs: Preferences): ThemeSettings {
        val mode = prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.find { it.name == name }
        } ?: ThemeMode.DARK

        val schedule = prefs[Keys.BRIEF_SCHEDULE]?.let { name ->
            BriefSchedule.entries.find { it.name == name }
        } ?: BriefSchedule.MORNING_AND_EVENING

        val topicNames = prefs[Keys.BRIEF_TOPICS]
        val topics = if (topicNames.isNullOrEmpty()) {
            setOf(NewsCategory.TECH, NewsCategory.WORLD, NewsCategory.SCIENCE)
        } else {
            topicNames.mapNotNull { name -> NewsCategory.entries.find { it.name == name } }
                .filter { it != NewsCategory.ALL }
                .toSet()
                .ifEmpty { setOf(NewsCategory.TECH, NewsCategory.WORLD, NewsCategory.SCIENCE) }
        }

        val pageTurnAnim = prefs[Keys.PAGE_TURN_ANIMATION]?.let { name ->
            PageTurnAnimation.entries.find { it.name == name }
        } ?: PageTurnAnimation.KINDLE_CURL

        return ThemeSettings(
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
            useAmoledBlack = prefs[Keys.USE_AMOLED_BLACK] ?: true,
            themeMode = mode,
            readerFontScale = (prefs[Keys.READER_FONT_SCALE] ?: 1.0f).coerceIn(0.85f, 1.4f),
            audioPlaybackSpeed = (prefs[Keys.AUDIO_SPEED] ?: 1.0f).coerceIn(0.8f, 1.5f),
            useSerifHeadlines = prefs[Keys.USE_SERIF_HEADLINES] ?: false,
            useSystemFont = prefs[Keys.USE_SYSTEM_FONT] ?: false,
            enableNowBrief = prefs[Keys.ENABLE_NOW_BRIEF] ?: true,
            showNowNudgeChips = prefs[Keys.SHOW_NOW_NUDGE_CHIPS] ?: true,
            smartNowNudgeAutoHide = prefs[Keys.SMART_NUDGE_AUTO_HIDE] ?: true,
            enableDualPaneLayout = prefs[Keys.ENABLE_DUAL_PANE_LAYOUT] ?: true,
            fullScreenBookReader = prefs[Keys.FULL_SCREEN_BOOK_READER] ?: false,
            isPaginatedReadingMode = prefs[Keys.IS_PAGINATED_READING_MODE] ?: false,
            pageTurnAnimation = pageTurnAnim,
            localNewsLocation = prefs[Keys.LOCAL_NEWS_LOCATION]?.ifBlank { "San Francisco, CA" } ?: "San Francisco, CA",
            localNewsRadiusMiles = (prefs[Keys.LOCAL_NEWS_RADIUS_MILES] ?: 25).coerceIn(5, 100),
            briefTopics = topics,
            briefSchedule = schedule,
        )
    }

    private fun serializeCustomSources(sources: List<NewsSource>): Set<String> {
        return serializeCustomSourceEntries(sources)
    }

    private fun deserializeCustomSources(raw: Set<String>): List<NewsSource> {
        return deserializeCustomSourceEntries(raw)
    }

    companion object {
        internal fun serializeCustomSourceEntries(sources: List<NewsSource>): Set<String> {
            return sources
                .distinctBy { it.id }
                .mapNotNull { src ->
                    val safeId = src.id.replace(Regex("[|\\r\\n]+"), "_").trim()
                    val safeName = src.name.replace(Regex("[|\\r\\n]+"), " ").trim()
                    val safeUrl = src.feedUrl.replace(Regex("[|\\r\\n\\s]+"), "").trim()
                    val safeCustomCat = src.cleanCustomCategory?.replace(Regex("[|\\r\\n]+"), " ")?.trim().orEmpty()
                    val safeCat = if (safeCustomCat.isNotEmpty()) NewsCategory.ALL else src.category
                    if (safeId.isBlank() || safeName.isBlank()) null
                    else if (safeCustomCat.isNotEmpty()) {
                        "$safeId|$safeName|${safeCat.name}|$safeUrl|$safeCustomCat"
                    } else {
                        "$safeId|$safeName|${safeCat.name}|$safeUrl"
                    }
                }
                .toSet()
        }

        internal fun deserializeCustomSourceEntries(raw: Set<String>): List<NewsSource> {
            return raw.mapNotNull { entry ->
                try {
                    val parts = entry.split("|")
                    if (parts.size < 4) return@mapNotNull null
                    val id = parts[0].trim()
                    val name = parts[1].trim()
                    if (id.isBlank() || name.isBlank()) return@mapNotNull null
                    val parsedCat = NewsCategory.entries.find { it.name == parts[2].trim() } ?: NewsCategory.ALL
                    val url = parts[3].trim()
                    val customCat = if (parts.size >= 5) {
                        parts.drop(4).joinToString(" ").trim().takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                    NewsSource(
                        id = id,
                        name = name,
                        category = if (customCat != null) NewsCategory.ALL else parsedCat,
                        feedUrl = url,
                        description = "Custom manual feed",
                        isCustom = true,
                        isDefaultEnabled = true,
                        customCategory = customCat,
                    )
                } catch (_: Exception) {
                    null
                }
            }
                .distinctBy { it.id }
                .sortedBy { it.name }
        }

        internal fun serializeCategoryOverrides(
            overrides: Map<String, Pair<NewsCategory, String?>>,
        ): Set<String> {
            return overrides.entries.mapNotNull { (sourceId, pair) ->
                val safeId = sourceId.replace(Regex("[|\\r\\n]+"), "_").trim()
                val safeCustom = pair.second?.replace(Regex("[|\\r\\n]+"), " ")?.trim().orEmpty()
                val safeBuiltIn = if (safeCustom.isNotEmpty()) NewsCategory.ALL else pair.first
                if (safeId.isBlank()) null else "$safeId|${safeBuiltIn.name}|$safeCustom"
            }.toSet()
        }

        internal fun deserializeCategoryOverrides(
            raw: Set<String>,
        ): Map<String, Pair<NewsCategory, String?>> {
            val result = LinkedHashMap<String, Pair<NewsCategory, String?>>()
            for (entry in raw) {
                try {
                    val parts = entry.split("|")
                    if (parts.size < 2) continue
                    val id = parts[0].trim()
                    val cat = NewsCategory.entries.find { it.name == parts[1].trim() } ?: continue
                    val custom = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                    if (id.isNotBlank()) {
                        result[id] = (if (custom != null) NewsCategory.ALL else cat) to custom
                    }
                } catch (_: Exception) {
                }
            }
            return result
        }
    }
}
