package com.example.data

import com.example.model.NewsArticle
import com.example.model.NewsCategory
import java.net.URI
import java.util.Locale

object ArticleFeedAggregator {

    private val trackingParamNames = setOf(
        "fbclid",
        "gclid",
        "dclid",
        "mc_cid",
        "mc_eid",
        "ref",
        "source",
        "ncid",
        "ocid",
        "cmpid",
        "guccounter",
        "taid",
        "ito",
    )

    fun canonicalizeUrl(rawUrl: String?): String? {
        val cleaned = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val uri = URI(cleaned)
            val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return cleaned.lowercase(Locale.US)
            val rawPath = uri.path.orEmpty()
            val normalizedPath = if (rawPath.length > 1) rawPath.trimEnd('/') else rawPath
            val keptQuery = uri.rawQuery
                ?.split('&')
                ?.filter { pair ->
                    val key = pair.substringBefore('=').lowercase(Locale.US)
                    key.isNotEmpty() && !key.startsWith("utm_") && key !in trackingParamNames
                }
                ?.sorted()
                ?.joinToString("&")
                .orEmpty()
            buildString {
                append(host)
                append(normalizedPath)
                if (keptQuery.isNotEmpty()) {
                    append('?')
                    append(keptQuery)
                }
            }
        } catch (_: Exception) {
            cleaned
                .substringBefore('#')
                .trimEnd('/')
                .lowercase(Locale.US)
        }
    }

    fun normalizeTitleKey(headline: String): String {
        return headline
            .lowercase(Locale.US)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun mergeDeduplicateAndSort(
        articles: List<NewsArticle>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<NewsArticle> {
        if (articles.isEmpty()) return emptyList()

        val accepted = mutableListOf<NewsArticle>()
        val indexById = HashMap<String, Int>()
        val indexByCanonicalUrl = HashMap<String, Int>()
        val indexByTitleKey = HashMap<String, Int>()

        for (incoming in articles) {
            val canonicalUrl = canonicalizeUrl(incoming.articleUrl)
            val titleKey = normalizeTitleKey(incoming.headline).takeIf { it.length >= 10 }

            val existingIdx = indexById[incoming.id]
                ?: canonicalUrl?.let { indexByCanonicalUrl[it] }
                ?: titleKey?.let { indexByTitleKey[it] }

            if (existingIdx == null) {
                val targetIdx = accepted.size
                accepted.add(incoming)
                indexById[incoming.id] = targetIdx
                if (canonicalUrl != null) indexByCanonicalUrl[canonicalUrl] = targetIdx
                if (titleKey != null) indexByTitleKey[titleKey] = targetIdx
            } else {
                val merged = choosePreferred(accepted[existingIdx], incoming)
                accepted[existingIdx] = merged
                indexById[merged.id] = existingIdx
                canonicalizeUrl(merged.articleUrl)?.let { indexByCanonicalUrl[it] = existingIdx }
                normalizeTitleKey(merged.headline).takeIf { it.length >= 10 }?.let {
                    indexByTitleKey[it] = existingIdx
                }
            }
        }

        return accepted
            .map { article ->
                val dynamicTimestamp = if (article.publishedAtEpochMillis > 0L) {
                    RssFeedParser.formatRelativeTimestamp(article.publishedAtEpochMillis, nowMillis)
                } else {
                    article.timestamp.ifBlank { "Just now" }
                }
                if (dynamicTimestamp == article.timestamp) {
                    article
                } else {
                    article.copy(timestamp = dynamicTimestamp)
                }
            }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<NewsArticle> { it.publishedAtEpochMillis }
                    .thenBy { it.id }
            )
    }

    fun filterAndSort(
        articles: List<NewsArticle>,
        category: NewsCategory,
        query: String,
        selectedCustomCategory: String? = null,
    ): List<NewsArticle> {
        val trimmed = query.trim().lowercase(Locale.US)
        val cleanCustom = selectedCustomCategory?.trim()?.takeIf { it.isNotEmpty() }
        return articles
            .filter { article ->
                val matchesCategory = when {
                    cleanCustom != null -> article.customCategory.equals(cleanCustom, ignoreCase = true)
                    category == NewsCategory.ALL -> true
                    else -> article.customCategory.isNullOrBlank() && article.category == category
                }
                val matchesQuery = trimmed.isEmpty() ||
                    article.headline.lowercase(Locale.US).contains(trimmed) ||
                    article.subtitle.lowercase(Locale.US).contains(trimmed) ||
                    article.source.lowercase(Locale.US).contains(trimmed) ||
                    article.displayCategoryLabel.lowercase(Locale.US).contains(trimmed)
                matchesCategory && matchesQuery
            }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<NewsArticle> { it.publishedAtEpochMillis }
                    .thenBy { it.id }
            )
    }

    private fun choosePreferred(existing: NewsArticle, incoming: NewsArticle): NewsArticle {
        val newestEpoch = maxOf(existing.publishedAtEpochMillis, incoming.publishedAtEpochMillis)
        val existingScore = qualityScore(existing)
        val incomingScore = qualityScore(incoming)
        val winner = when {
            incoming.publishedAtEpochMillis > existing.publishedAtEpochMillis + 300_000L -> incoming
            existing.publishedAtEpochMillis > incoming.publishedAtEpochMillis + 300_000L -> existing
            incomingScore > existingScore -> incoming
            else -> existing
        }
        return winner.copy(
            publishedAtEpochMillis = newestEpoch,
            imageUrl = winner.imageUrl ?: existing.imageUrl ?: incoming.imageUrl,
            imageResId = winner.imageResId ?: existing.imageResId ?: incoming.imageResId,
            articleUrl = winner.articleUrl ?: existing.articleUrl ?: incoming.articleUrl,
        )
    }

    private fun qualityScore(article: NewsArticle): Int {
        var score = 0
        if (!article.imageUrl.isNullOrBlank()) score += 4
        if (article.isFullTextExtracted) score += 3
        if (!article.articleUrl.isNullOrBlank()) score += 2
        score += article.paragraphs.sumOf { it.length }.coerceAtMost(2000) / 400
        return score
    }
}
