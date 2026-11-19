package com.example.model

import androidx.annotation.DrawableRes

enum class NewsCategory(val label: String, val pickerLabel: String = label) {
    ALL("All", "Top / For You"),
    TECH("Tech"),
    WORLD("World"),
    LOCAL("Local"),
    SCIENCE("Science"),
    CULTURE("Culture"),
}

data class NewsSource(
    val id: String,
    val name: String,
    val category: NewsCategory,
    val feedUrl: String,
    val description: String = "",
    val isCustom: Boolean = false,
    val isDefaultEnabled: Boolean = true,
    val customCategory: String? = null,
) {
    val cleanCustomCategory: String?
        get() = customCategory?.trim()?.takeIf { it.isNotEmpty() }

    val assignedCategoryLabel: String
        get() = cleanCustomCategory ?: category.pickerLabel

    val assignedCategoryBadge: String
        get() = cleanCustomCategory ?: if (category == NewsCategory.ALL) "Top" else category.label
}

data class EventMetadata(
    val title: String,
    val dateTimeLabel: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val locationLabel: String,
    val description: String,
)

data class LocationMetadata(
    val venueName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

data class NewsArticle(
    val id: String,
    val headline: String,
    val subtitle: String,
    val source: String,
    val timestamp: String,
    val publishedAtEpochMillis: Long = 0L,
    val category: NewsCategory,
    val readingTimeMinutes: Int,
    val hasAudioBrief: Boolean,
    val audioDurationSeconds: Int,
    val audioSummary: String,
    @DrawableRes val imageResId: Int? = null,
    val imageUrl: String? = null,
    val articleUrl: String? = null,
    val paragraphs: List<String>,
    val eventMetadata: EventMetadata? = null,
    val locationMetadata: LocationMetadata? = null,
    val isBreaking: Boolean = false,
    val isFeaturedForBrief: Boolean = false,
    val isFullTextExtracted: Boolean = false,
    val fullTextExtractionFailed: Boolean = false,
    val customCategory: String? = null,
) {
    val displayCategoryLabel: String
        get() = customCategory?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (category == NewsCategory.ALL) "Top" else category.label
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class BriefSchedule(val label: String) {
    MORNING_AND_EVENING("Morning & Evening"),
    MORNING_ONLY("Morning Only"),
    EVENING_ONLY("Evening Only"),
}

enum class PageTurnAnimation(val label: String, val shortLabel: String) {
    KINDLE_CURL("Kindle Page Curl", "Page Curl"),
    SIMPLE_SLIDE("Simple Slide", "Slide"),
    INSTANT("Instant (None)", "Instant"),
}

data class LocalLocationPreset(
    val id: String,
    val displayName: String,
    val cityShort: String,
    val transitCorridor: String,
    val landmarkName: String,
    val landmarkAddress: String,
    val latitude: Double,
    val longitude: Double,
)

data class ThemeSettings(
    val useDynamicColor: Boolean = true,
    val useAmoledBlack: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val readerFontScale: Float = 1.0f,
    val audioPlaybackSpeed: Float = 1.0f,
    val useSerifHeadlines: Boolean = false,
    val useSystemFont: Boolean = false,
    val enableNowBrief: Boolean = true,
    val showNowNudgeChips: Boolean = true,
    val smartNowNudgeAutoHide: Boolean = true,
    val enableDualPaneLayout: Boolean = true,
    val fullScreenBookReader: Boolean = false,
    val isPaginatedReadingMode: Boolean = false,
    val pageTurnAnimation: PageTurnAnimation = PageTurnAnimation.KINDLE_CURL,
    val localNewsLocation: String = "San Francisco, CA",
    val localNewsRadiusMiles: Int = 25,
    val briefTopics: Set<NewsCategory> = setOf(
        NewsCategory.TECH,
        NewsCategory.WORLD,
        NewsCategory.SCIENCE,
    ),
    val briefSchedule: BriefSchedule = BriefSchedule.MORNING_AND_EVENING,
)

enum class AppDestination(val label: String) {
    FEED("Feed"),
    SAVED("Saved"),
    SETTINGS("Settings"),
}

data class AudioPlaybackState(
    val activeArticleId: String? = null,
    val isNowBriefQueue: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val durationSeconds: Int = 120,
    val playbackSpeed: Float = 1.0f,
    val spokenText: String = "",
)
