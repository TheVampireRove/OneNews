package com.example.data

import com.example.model.NewsCategory
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI
import java.util.Locale

data class FeedChannelPreview(
    val resolvedUrl: String,
    val channelTitle: String? = null,
    val channelDescription: String? = null,
    val channelCategories: List<String> = emptyList(),
    val suggestedCategory: NewsCategory? = null,
    val detectionReason: String? = null,
    val isValidFeed: Boolean = false,
)

data class FeedCategoryOption(
    val builtIn: NewsCategory,
    val customName: String? = null,
) {
    val label: String
        get() = customName?.trim()?.takeIf { it.isNotEmpty() } ?: builtIn.pickerLabel

    val shortBadge: String
        get() = customName?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (builtIn == NewsCategory.ALL) "Top" else builtIn.label

    val key: String
        get() = if (!customName.isNullOrBlank()) {
            "custom:${customName.trim().lowercase(Locale.US)}"
        } else {
            "builtin:${builtIn.name}"
        }
}

object FeedCategoryDetector {

    private val localKeywords = listOf(
        "local", "city", "county", "metro", "neighborhood", "municipal", "civic",
        "mayor", "transit", "commuter", "community", "town", "regional", "downtown",
        "statewide", "district", "sfchronicle", "sfgate", "seattletimes", "latimes",
        "chicagotribune", "bostonglobe", "gothamist", "wnyc", "kqed", "patch.com",
        "san francisco", "seattle", "new york", "nyc", "chicago", "austin", "boston",
        "los angeles", "bay area", "california", "texas", "washington",
    )

    private val scienceKeywords = listOf(
        "science", "scientific", "nature", "nasa", "physics", "astronomy", "astrophysics",
        "quantum", "biology", "chemistry", "climate", "earth observatory", "space",
        "cosmos", "quanta", "quantamagazine", "scientificamerican", "sciam", "newscientist",
        "phys.org", "arxiv", "neuroscience", "genome", "ecology", "geology", "research",
        "observatory", "exoplanet", "laboratory",
    )

    private val worldKeywords = listOf(
        "world", "international", "global", "foreign", "geopolitics", "diplomacy",
        "reuters", "bbc", "apnews", "associated press", "economist", "financial times",
        "ft.com", "nikkei", "aljazeera", "theguardian/world", "foreignpolicy", "europe",
        "asia", "africa", "middle east", "americas", "united nations", "nato", "trade",
    )

    private val cultureKeywords = listOf(
        "culture", "arts", "art", "music", "film", "cinema", "movie", "books", "book",
        "literature", "architecture", "architectural", "design", "theater", "theatre",
        "museum", "gallery", "newyorker", "pitchfork", "atlantic", "entertainment",
        "criticism", "fashion", "vinyl", "album", "culinary", "food", "exhibition",
    )

    private val techKeywords = listOf(
        "tech", "technology", "theverge", "verge", "arstechnica", "wired", "techcrunch",
        "9to5google", "9to5mac", "android", "pixel", "apple", "software", "hardware",
        "developer", "programming", "semiconductor", "silicon", "chip", "ai",
        "cybersecurity", "security", "computing", "robotics", "ieee", "technologyreview",
        "engadget", "hacker news", "ycombinator", "gadget", "foldable",
    )

    fun buildCategoryOptions(customCategories: List<String>): List<FeedCategoryOption> {
        val builtIns = listOf(
            FeedCategoryOption(NewsCategory.ALL),
            FeedCategoryOption(NewsCategory.TECH),
            FeedCategoryOption(NewsCategory.WORLD),
            FeedCategoryOption(NewsCategory.LOCAL),
            FeedCategoryOption(NewsCategory.SCIENCE),
            FeedCategoryOption(NewsCategory.CULTURE),
        )
        val builtInNamesLower = builtIns.map { it.label.lowercase(Locale.US) }.toSet() + setOf("all", "top", "for you")
        val custom = customCategories
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase(Locale.US) !in builtInNamesLower }
            .distinctBy { it.lowercase(Locale.US) }
            .map { FeedCategoryOption(builtIn = NewsCategory.ALL, customName = it) }
        return builtIns + custom
    }

    fun extractChannelPreview(
        rawBody: String,
        resolvedUrl: String,
        localHintLocation: String = "",
    ): FeedChannelPreview {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) {
            val (cat, reason) = suggestCategoryFromMetadata(
                feedUrl = resolvedUrl,
                localHintLocation = localHintLocation,
            )
            return FeedChannelPreview(
                resolvedUrl = resolvedUrl,
                suggestedCategory = cat,
                detectionReason = reason,
                isValidFeed = false,
            )
        }

        if (trimmed.startsWith("{")) {
            return try {
                val root = JSONObject(trimmed)
                val title = root.optString("title").trim().takeIf { it.isNotEmpty() }
                val desc = root.optString("description").trim().takeIf { it.isNotEmpty() }
                val hasItems = (root.optJSONArray("items")?.length() ?: 0) > 0 || title != null
                val (cat, reason) = suggestCategoryFromMetadata(
                    feedUrl = resolvedUrl,
                    channelTitle = title.orEmpty(),
                    channelDescription = desc.orEmpty(),
                    localHintLocation = localHintLocation,
                )
                FeedChannelPreview(
                    resolvedUrl = resolvedUrl,
                    channelTitle = title,
                    channelDescription = desc,
                    suggestedCategory = cat,
                    detectionReason = reason,
                    isValidFeed = hasItems,
                )
            } catch (_: Exception) {
                FeedChannelPreview(resolvedUrl = resolvedUrl, isValidFeed = false)
            }
        }

        return try {
            val doc = Jsoup.parse(trimmed, "", Parser.xmlParser())
            val isFeedDoc = doc.select("rss, feed, rdf\\:RDF, channel, item, entry").isNotEmpty()
            val channelTitle = (
                doc.selectFirst("channel > title")?.text()
                    ?: doc.selectFirst("feed > title")?.text()
                )?.let { Jsoup.parseBodyFragment(it).text().trim() }
                ?.takeIf { it.isNotBlank() }

            val channelDesc = (
                doc.selectFirst("channel > description")?.text()
                    ?: doc.selectFirst("feed > subtitle")?.text()
                )?.let { Jsoup.parseBodyFragment(it).text().trim() }
                ?.takeIf { it.isNotBlank() }

            val categoryTags = buildList {
                doc.select("channel > category, feed > category").forEach { el ->
                    val text = el.text().trim()
                    val term = el.attr("term").trim()
                    val label = el.attr("label").trim()
                    if (text.isNotEmpty()) add(text)
                    if (term.isNotEmpty()) add(term)
                    if (label.isNotEmpty()) add(label)
                }
                doc.select("item > category, entry > category").take(10).forEach { el ->
                    val text = el.text().trim()
                    val term = el.attr("term").trim()
                    if (text.isNotEmpty()) add(text)
                    if (term.isNotEmpty()) add(term)
                }
            }.distinct()

            val (suggested, reason) = suggestCategoryFromMetadata(
                feedUrl = resolvedUrl,
                channelTitle = channelTitle.orEmpty(),
                channelDescription = channelDesc.orEmpty(),
                channelCategories = categoryTags,
                localHintLocation = localHintLocation,
            )

            FeedChannelPreview(
                resolvedUrl = resolvedUrl,
                channelTitle = channelTitle,
                channelDescription = channelDesc,
                channelCategories = categoryTags,
                suggestedCategory = suggested,
                detectionReason = reason,
                isValidFeed = isFeedDoc,
            )
        } catch (_: Exception) {
            val (cat, reason) = suggestCategoryFromMetadata(
                feedUrl = resolvedUrl,
                localHintLocation = localHintLocation,
            )
            FeedChannelPreview(
                resolvedUrl = resolvedUrl,
                suggestedCategory = cat,
                detectionReason = reason,
                isValidFeed = false,
            )
        }
    }

    fun suggestCategoryFromMetadata(
        feedUrl: String,
        channelTitle: String = "",
        channelDescription: String = "",
        channelCategories: List<String> = emptyList(),
        localHintLocation: String = "",
    ): Pair<NewsCategory?, String?> {
        val cleanUrl = feedUrl.trim()
        val domainAndPath = try {
            val withScheme = if (cleanUrl.startsWith("http://", true) || cleanUrl.startsWith("https://", true)) {
                cleanUrl
            } else {
                "https://$cleanUrl"
            }
            val uri = URI(withScheme)
            "${uri.host.orEmpty()} ${uri.path.orEmpty()} ${uri.query.orEmpty()}".lowercase(Locale.US)
        } catch (_: Exception) {
            cleanUrl.lowercase(Locale.US)
        }

        val titleLower = channelTitle.lowercase(Locale.US)
        val descLower = channelDescription.lowercase(Locale.US)
        val catsLower = channelCategories.joinToString(" ").lowercase(Locale.US)

        val dynamicLocalHints = localHintLocation
            .split(",", " ")
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.length >= 3 }
        val allLocalKeywords = (localKeywords + dynamicLocalHints).distinct()

        fun scoreKeywords(keywords: List<String>): Pair<Int, String?> {
            var score = 0
            var matchedHint: String? = null
            for (kw in keywords) {
                val pattern = if (kw.contains(".") || kw.contains("/") || kw.contains(" ")) {
                    Regex(Regex.escape(kw))
                } else {
                    Regex("\\b${Regex.escape(kw)}\\b")
                }
                if (catsLower.isNotEmpty() && pattern.containsMatchIn(catsLower)) {
                    score += 4
                    if (matchedHint == null) matchedHint = kw
                }
                if (titleLower.isNotEmpty() && pattern.containsMatchIn(titleLower)) {
                    score += 3
                    if (matchedHint == null) matchedHint = kw
                }
                if (domainAndPath.isNotEmpty() && pattern.containsMatchIn(domainAndPath)) {
                    score += 3
                    if (matchedHint == null) matchedHint = kw
                }
                if (descLower.isNotEmpty() && pattern.containsMatchIn(descLower)) {
                    score += 1
                    if (matchedHint == null) matchedHint = kw
                }
            }
            return score to matchedHint
        }

        val candidates = listOf(
            NewsCategory.LOCAL to scoreKeywords(allLocalKeywords),
            NewsCategory.SCIENCE to scoreKeywords(scienceKeywords),
            NewsCategory.WORLD to scoreKeywords(worldKeywords),
            NewsCategory.CULTURE to scoreKeywords(cultureKeywords),
            NewsCategory.TECH to scoreKeywords(techKeywords),
        )

        val best = candidates.maxByOrNull { it.second.first } ?: return null to null
        val (bestCategory, scorePair) = best
        val (bestScore, matchedKeyword) = scorePair
        if (bestScore < 2) return null to null

        val reason = if (matchedKeyword != null) {
            "Matched \"$matchedKeyword\" in feed details"
        } else {
            "Matched ${bestCategory.label.lowercase(Locale.US)} topics"
        }
        return bestCategory to reason
    }
}
