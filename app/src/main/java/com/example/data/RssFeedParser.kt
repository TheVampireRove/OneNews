package com.example.data

import android.os.Build
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.NewsSource
import org.json.JSONObject
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object RssFeedParser {

    private data class MediaCandidate(
        val url: String,
        val width: Int,
        val priority: Int, // 2 = media:content, 1 = media:thumbnail
    )

    private val truncationPhrases = listOf(
        "this story is curated from",
        "read more at",
        "continue reading",
        "appeared first on",
        "read the full story",
        "click here to read",
        "[...]",
        "[…]",
    )

    fun parseFeed(
        rawBody: String,
        source: NewsSource,
        maxItems: Int = 8,
        fetchTimeMillis: Long = System.currentTimeMillis(),
    ): List<NewsArticle> {
        return try {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return emptyList()

            val parsed = if (trimmed.startsWith("{")) {
                parseJsonFeed(trimmed, source, maxItems, fetchTimeMillis)
            } else {
                parseXmlFeed(trimmed, source, maxItems, fetchTimeMillis)
            }
            parsed
                .distinctBy { it.id }
                .sortedWith(compareByDescending<NewsArticle> { it.publishedAtEpochMillis }.thenBy { it.id })
                .take(maxItems)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun needsWebExtraction(paragraphs: List<String>): Boolean {
        if (paragraphs.isEmpty()) return true
        val joined = paragraphs.joinToString("\n").trim()
        if (joined.length < 700) return true
        if (paragraphs.size <= 2 && joined.length < 900) return true
        val lastTrimmed = paragraphs.lastOrNull()?.trim().orEmpty()
        if (
            lastTrimmed.endsWith("...") ||
            lastTrimmed.endsWith("…") ||
            lastTrimmed.endsWith("[...]") ||
            lastTrimmed.endsWith("[…]") ||
            lastTrimmed.endsWith("&#8230;")
        ) {
            return true
        }
        val lower = joined.lowercase()
        return truncationPhrases.any { it in lower }
    }

    fun stripTrailingFeedEllipsis(paragraphs: List<String>): List<String> {
        if (paragraphs.isEmpty()) return paragraphs
        val cleaned = paragraphs.mapIndexedNotNull { idx, raw ->
            var text = raw.trim()
            if (idx == paragraphs.lastIndex || text.endsWith("[...]") || text.endsWith("[…]")) {
                text = text
                    .replace(Regex("\\s*(?:\\[\\.{3}]|\\[…]|&#8230;)\\s*$"), "")
                    .replace(Regex("(?i)\\s*(?:read more|continue reading|read the full story)\\s*(?:\\.\\.\\.|…)?\\s*$"), "")
                    .trim()
            }
            if (idx == paragraphs.lastIndex && (text.endsWith("...") || text.endsWith("…"))) {
                text = text.replace(Regex("\\s*(?:\\.{3}|…)\\s*$"), "").trim()
            }
            text.takeIf { it.isNotBlank() }
        }
        return cleaned.ifEmpty { paragraphs }
    }

    fun normalizeImageUrl(rawUrl: String?, baseUrl: String?): String? {
        val cleaned = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (cleaned.startsWith("data:", ignoreCase = true)) return null
        if (cleaned.startsWith("javascript:", ignoreCase = true)) return null

        val protocolFixed = if (cleaned.startsWith("//")) "https:$cleaned" else cleaned
        val resolved = try {
            val base = baseUrl?.trim()?.takeIf { it.startsWith("http") }
            if (protocolFixed.startsWith("http://", ignoreCase = true) ||
                protocolFixed.startsWith("https://", ignoreCase = true)
            ) {
                protocolFixed
            } else if (base != null) {
                URI(base).resolve(protocolFixed).toString()
            } else {
                null
            }
        } catch (_: Exception) {
            if (protocolFixed.startsWith("http", ignoreCase = true)) protocolFixed else null
        } ?: return null

        val httpsUpgraded = if (resolved.startsWith("http://", ignoreCase = true)) {
            "https://" + resolved.substring(7)
        } else {
            resolved
        }
        return if (isLikelyTrackingOrIcon(httpsUpgraded)) null else httpsUpgraded
    }

    fun extractParagraphsFromHtml(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        return try {
            val doc = Jsoup.parseBodyFragment(html)
            ArticleTextCleaner.stripCodeContainers(doc)
            doc.select("figure figcaption, aside").remove()

            val result = mutableListOf<String>()
            val seen = HashSet<String>()

            fun addCleanParagraph(raw: String, prefix: String = "") {
                val clean = ArticleTextCleaner.cleanParagraphText(raw) ?: return
                if (clean.length < 18) return
                val lower = clean.lowercase()
                if (truncationPhrases.any { lower.startsWith(it) || (lower.contains("appeared first on") && clean.length < 180) }) {
                    return
                }
                val formatted = if (prefix.isNotEmpty() && !clean.startsWith(prefix)) "$prefix$clean" else clean
                if (seen.add(formatted)) {
                    result.add(formatted)
                }
            }

            val blocks = doc.body().select("h2, h3, h4, p, blockquote, li")
            if (blocks.isNotEmpty()) {
                for (el in blocks) {
                    if (el.tagName() == "p" && el.parents().any { it.tagName() in setOf("blockquote", "li") }) {
                        continue
                    }
                    val preserved = ArticleTextCleaner.extractElementTextPreservingBreaks(el)
                    when (el.tagName().lowercase()) {
                        "li" -> addCleanParagraph(preserved, "• ")
                        "blockquote" -> addCleanParagraph(preserved, "“")
                            .also {
                                if (result.isNotEmpty() && result.last().startsWith("“") && !result.last().endsWith("”")) {
                                    val updated = result.removeAt(result.lastIndex) + "”"
                                    result.add(updated)
                                }
                            }
                        else -> addCleanParagraph(preserved)
                    }
                }
            }

            if (result.isEmpty()) {
                val cleanedHtml = doc.body().html()
                val fallbackText = Jsoup.parse(
                    cleanedHtml.replace(Regex("(?i)<br\\s*/?>"), "\n")
                        .replace(Regex("(?i)</(p|div|li|h[1-6]|blockquote)>"), "\n")
                ).text()
                fallbackText.split('\n')
                    .map { it.trim() }
                    .filter { it.length >= 20 }
                    .forEach { addCleanParagraph(it) }
            }

            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun looksLikePlainHtmlWithoutFeedItems(raw: String): Boolean {
        val sample = raw.take(4096).lowercase(Locale.US)
        val startsHtml = sample.contains("<!doctype html") || sample.contains("<html")
        if (!startsHtml) return false
        val lower = raw.lowercase(Locale.US)
        return !lower.contains("<rss") &&
            !lower.contains("<feed") &&
            !lower.contains("<rdf:rdf") &&
            !lower.contains("<item") &&
            !lower.contains("<entry")
    }

    private fun parseXmlFeed(
        xml: String,
        source: NewsSource,
        maxItems: Int,
        fetchTimeMillis: Long,
    ): List<NewsArticle> {
        if (looksLikePlainHtmlWithoutFeedItems(xml)) {
            return emptyList()
        }
        val pullResult = try {
            parseXmlFeedWithPullParser(xml, source, maxItems, fetchTimeMillis)
        } catch (_: Exception) {
            null
        }
        if (pullResult != null && pullResult.isNotEmpty()) {
            return pullResult
        }
        return parseXmlFeedWithJsoup(xml, source, maxItems, fetchTimeMillis)
    }

    private fun parseXmlFeedWithPullParser(
        xml: String,
        source: NewsSource,
        maxItems: Int,
        fetchTimeMillis: Long,
    ): List<NewsArticle> {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val scanLimit = maxOf(maxItems * 3, 24)
        val articles = mutableListOf<NewsArticle>()
        var inItem = false
        var isAtomEntry = false
        var itemIndex = 0

        var channelLastBuildDateRaw = ""
        var channelPubDateRaw = ""
        var channelUpdatedRaw = ""

        var title = ""
        var link = ""
        var description = ""
        var summary = ""
        var contentEncoded = ""
        var atomContent = ""
        var pubDateTag = ""
        var dcDateTag = ""
        var publishedTag = ""
        var updatedTag = ""
        var guid = ""
        var enclosureImage: String? = null
        val mediaCandidates = mutableListOf<MediaCandidate>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.orEmpty().lowercase(Locale.US)
                    if (tag == "item" || tag == "entry") {
                        inItem = true
                        isAtomEntry = tag == "entry"
                        title = ""
                        link = ""
                        description = ""
                        summary = ""
                        contentEncoded = ""
                        atomContent = ""
                        pubDateTag = ""
                        dcDateTag = ""
                        publishedTag = ""
                        updatedTag = ""
                        guid = ""
                        enclosureImage = null
                        mediaCandidates.clear()
                    } else if (!inItem) {
                        when (tag) {
                            "lastbuilddate" -> {
                                val text = safeNextText(parser)
                                if (channelLastBuildDateRaw.isBlank() && text.isNotBlank()) {
                                    channelLastBuildDateRaw = text
                                }
                            }
                            "pubdate" -> {
                                val text = safeNextText(parser)
                                if (channelPubDateRaw.isBlank() && text.isNotBlank()) {
                                    channelPubDateRaw = text
                                }
                            }
                            "updated" -> {
                                val text = safeNextText(parser)
                                if (channelUpdatedRaw.isBlank() && text.isNotBlank()) {
                                    channelUpdatedRaw = text
                                }
                            }
                        }
                    } else {
                        when {
                            tag == "media:content" || tag.endsWith(":content") && tag.startsWith("media") -> {
                                val urlAttr = parser.getAttributeValue(null, "url")
                                val mediumAttr = parser.getAttributeValue(null, "medium").orEmpty()
                                val typeAttr = parser.getAttributeValue(null, "type").orEmpty()
                                val widthAttr = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                val looksImage = mediumAttr.isEmpty() ||
                                    mediumAttr.equals("image", ignoreCase = true) ||
                                    typeAttr.startsWith("image/", ignoreCase = true)
                                if (!urlAttr.isNullOrBlank() && looksImage) {
                                    mediaCandidates.add(MediaCandidate(urlAttr, widthAttr, priority = 2))
                                }
                            }
                            tag == "media:thumbnail" || tag.endsWith(":thumbnail") -> {
                                val urlAttr = parser.getAttributeValue(null, "url")
                                val widthAttr = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                if (!urlAttr.isNullOrBlank()) {
                                    mediaCandidates.add(MediaCandidate(urlAttr, widthAttr, priority = 1))
                                }
                            }
                            tag == "enclosure" -> {
                                val urlAttr = parser.getAttributeValue(null, "url")
                                val typeAttr = parser.getAttributeValue(null, "type").orEmpty()
                                if (!urlAttr.isNullOrBlank() &&
                                    (typeAttr.startsWith("image/", ignoreCase = true) || hasImageExtension(urlAttr))
                                ) {
                                    if (enclosureImage == null) {
                                        enclosureImage = urlAttr
                                    }
                                }
                            }
                            tag == "link" -> {
                                val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                val href = parser.getAttributeValue(null, "href").orEmpty()
                                val typeAttr = parser.getAttributeValue(null, "type").orEmpty()
                                if (href.isNotBlank()) {
                                    if (rel == "enclosure" && typeAttr.startsWith("image/", ignoreCase = true)) {
                                        if (enclosureImage == null) enclosureImage = href
                                    } else if (rel.isEmpty() || rel == "alternate") {
                                        if (link.isBlank()) link = href
                                    }
                                } else if (!isAtomEntry) {
                                    val text = safeNextText(parser)
                                    if (text.isNotBlank() && link.isBlank()) link = text
                                }
                            }
                            tag == "title" -> title = safeNextText(parser)
                            tag == "description" -> description = safeNextText(parser)
                            tag == "summary" -> summary = safeNextText(parser)
                            tag == "content:encoded" || tag == "encoded" -> contentEncoded = safeNextText(parser)
                            tag == "content" -> atomContent = safeNextText(parser)
                            tag == "pubdate" -> {
                                val text = safeNextText(parser)
                                if (pubDateTag.isBlank() && text.isNotBlank()) pubDateTag = text
                            }
                            tag == "dc:date" -> {
                                val text = safeNextText(parser)
                                if (dcDateTag.isBlank() && text.isNotBlank()) dcDateTag = text
                            }
                            tag == "published" -> {
                                val text = safeNextText(parser)
                                if (publishedTag.isBlank() && text.isNotBlank()) publishedTag = text
                            }
                            tag == "updated" -> {
                                val text = safeNextText(parser)
                                if (updatedTag.isBlank() && text.isNotBlank()) updatedTag = text
                            }
                            tag == "guid" || tag == "id" -> {
                                val text = safeNextText(parser)
                                if (guid.isBlank() && text.isNotBlank()) guid = text
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name.orEmpty().lowercase(Locale.US)
                    if (inItem && (tag == "item" || tag == "entry")) {
                        inItem = false
                        val cleanTitle = Jsoup.parseBodyFragment(title).text().trim()
                        val cleanLink = link.trim().ifBlank {
                            if (guid.startsWith("http")) guid.trim() else ""
                        }
                        if (cleanTitle.isNotBlank()) {
                            val channelFallbackMillis = listOf(
                                channelLastBuildDateRaw,
                                channelPubDateRaw,
                                channelUpdatedRaw,
                            ).firstNotNullOfOrNull { parsePublishedEpochMillis(it) }

                            val parsedEpochMillis = listOf(
                                pubDateTag,
                                dcDateTag,
                                publishedTag,
                                updatedTag,
                            ).firstNotNullOfOrNull { parsePublishedEpochMillis(it) }

                            val fallbackBase = channelFallbackMillis ?: fetchTimeMillis
                            val publishedAtEpochMillis = parsedEpochMillis
                                ?: (fallbackBase - (itemIndex * 60_000L))

                            val article = buildFeedArticle(
                                source = source,
                                itemIdSeed = guid.ifBlank { cleanLink.ifBlank { cleanTitle } },
                                title = cleanTitle,
                                link = cleanLink.ifBlank { null },
                                rawContentHtml = contentEncoded.ifBlank { atomContent },
                                rawDescriptionHtml = description.ifBlank { summary },
                                publishedAtEpochMillis = publishedAtEpochMillis,
                                nowMillis = fetchTimeMillis,
                                mediaCandidates = mediaCandidates,
                                enclosureImage = enclosureImage,
                            )
                            articles.add(article)
                            itemIndex++
                            if (articles.size >= scanLimit) break
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun parseXmlFeedWithJsoup(
        xml: String,
        source: NewsSource,
        maxItems: Int,
        fetchTimeMillis: Long,
    ): List<NewsArticle> {
        return try {
            val doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
            val scanLimit = maxOf(maxItems * 3, 24)
            val entries = doc.select("item, entry")
            if (entries.isEmpty()) return emptyList()

            val channelFallbackMillis = listOf(
                doc.selectFirst("channel > lastBuildDate")?.text(),
                doc.selectFirst("channel > pubDate")?.text(),
                doc.selectFirst("feed > updated")?.text(),
            ).firstNotNullOfOrNull { parsePublishedEpochMillis(it) } ?: fetchTimeMillis

            val articles = mutableListOf<NewsArticle>()
            for ((index, el) in entries.withIndex()) {
                if (articles.size >= scanLimit) break
                try {
                    val rawTitle = el.selectFirst("title")?.text().orEmpty()
                    val cleanTitle = Jsoup.parseBodyFragment(rawTitle).text().trim()
                    if (cleanTitle.isBlank()) continue

                    val guid = el.selectFirst("guid, id")?.text().orEmpty().trim()
                    val atomLink = el.select("link[href]").firstOrNull { linkEl ->
                        val rel = linkEl.attr("rel").trim()
                        rel.isEmpty() || rel.equals("alternate", ignoreCase = true)
                    }?.attr("href").orEmpty().trim()
                    val textLink = el.selectFirst("link")?.text().orEmpty().trim()
                    val cleanLink = atomLink.ifBlank { textLink }.ifBlank {
                        if (guid.startsWith("http", ignoreCase = true)) guid else ""
                    }

                    val description = el.selectFirst("description")?.text().orEmpty()
                    val summary = el.selectFirst("summary")?.text().orEmpty()
                    val contentEncoded = el.selectFirst("content|encoded, encoded")?.text().orEmpty()
                    val atomContent = el.selectFirst("content")?.text().orEmpty()

                    val parsedEpochMillis = listOfNotNull(
                        el.selectFirst("pubDate")?.text(),
                        el.selectFirst("dc|date")?.text(),
                        el.selectFirst("published")?.text(),
                        el.selectFirst("updated")?.text(),
                    ).firstNotNullOfOrNull { parsePublishedEpochMillis(it) }

                    val publishedAtEpochMillis = parsedEpochMillis
                        ?: (channelFallbackMillis - (index * 60_000L))

                    val mediaCandidates = mutableListOf<MediaCandidate>()
                    for (mc in el.select("media|content, content")) {
                        val tagName = mc.tagName().lowercase(Locale.US)
                        if (tagName != "media:content" && !tagName.endsWith(":content")) continue
                        val urlAttr = mc.attr("url").trim()
                        val mediumAttr = mc.attr("medium").trim()
                        val typeAttr = mc.attr("type").trim()
                        val widthAttr = mc.attr("width").toIntOrNull() ?: 0
                        val looksImage = mediumAttr.isEmpty() ||
                            mediumAttr.equals("image", ignoreCase = true) ||
                            typeAttr.startsWith("image/", ignoreCase = true)
                        if (urlAttr.isNotEmpty() && looksImage) {
                            mediaCandidates.add(MediaCandidate(urlAttr, widthAttr, priority = 2))
                        }
                    }
                    for (mt in el.select("media|thumbnail, thumbnail")) {
                        val tagName = mt.tagName().lowercase(Locale.US)
                        if (tagName != "media:thumbnail" && !tagName.endsWith(":thumbnail")) continue
                        val urlAttr = mt.attr("url").trim()
                        val widthAttr = mt.attr("width").toIntOrNull() ?: 0
                        if (urlAttr.isNotEmpty()) {
                            mediaCandidates.add(MediaCandidate(urlAttr, widthAttr, priority = 1))
                        }
                    }

                    val enclosureImage = el.select("enclosure").firstOrNull { enc ->
                        val urlAttr = enc.attr("url").trim()
                        val typeAttr = enc.attr("type").trim()
                        urlAttr.isNotEmpty() &&
                            (typeAttr.startsWith("image/", ignoreCase = true) || hasImageExtension(urlAttr))
                    }?.attr("url")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: el.select("link[rel=enclosure][href]").firstOrNull { linkEl ->
                            linkEl.attr("type").startsWith("image/", ignoreCase = true)
                        }?.attr("href")?.trim()?.takeIf { it.isNotEmpty() }

                    articles.add(
                        buildFeedArticle(
                            source = source,
                            itemIdSeed = guid.ifBlank { cleanLink.ifBlank { cleanTitle } },
                            title = cleanTitle,
                            link = cleanLink.ifBlank { null },
                            rawContentHtml = contentEncoded.ifBlank { atomContent },
                            rawDescriptionHtml = description.ifBlank { summary },
                            publishedAtEpochMillis = publishedAtEpochMillis,
                            nowMillis = fetchTimeMillis,
                            mediaCandidates = mediaCandidates,
                            enclosureImage = enclosureImage,
                        )
                    )
                } catch (_: Exception) {
                    // Skip malformed item cleanly without failing the rest of the feed
                }
            }
            articles
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseJsonFeed(
        jsonString: String,
        source: NewsSource,
        maxItems: Int,
        fetchTimeMillis: Long,
    ): List<NewsArticle> {
        return try {
            val root = JSONObject(jsonString)
            val feedFallbackMillis = listOf(
                root.optString("date_published"),
                root.optString("date_modified"),
            ).firstNotNullOfOrNull { parsePublishedEpochMillis(it) } ?: fetchTimeMillis

            val items = root.optJSONArray("items") ?: return emptyList()
            val scanLimit = maxOf(maxItems * 3, 24)
            val results = mutableListOf<NewsArticle>()
            for (i in 0 until minOf(items.length(), scanLimit)) {
                val obj = items.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                if (title.isBlank()) continue
                val url = obj.optString("url").ifBlank { obj.optString("external_url") }.trim().ifBlank { null }
                val id = obj.optString("id").ifBlank { url ?: title }
                val contentHtml = obj.optString("content_html")
                val contentText = obj.optString("content_text")
                val summary = obj.optString("summary")
                val parsedEpochMillis = listOf(
                    obj.optString("date_published"),
                    obj.optString("date_modified"),
                    obj.optString("published"),
                    obj.optString("updated"),
                ).firstNotNullOfOrNull { parsePublishedEpochMillis(it) }
                val publishedAtEpochMillis = parsedEpochMillis
                    ?: (feedFallbackMillis - (results.size * 60_000L))

                val rawImage = obj.optString("image").ifBlank { obj.optString("banner_image") }.ifBlank { null }
                val mediaList = if (rawImage != null) {
                    listOf(MediaCandidate(rawImage, width = 1200, priority = 2))
                } else {
                    emptyList()
                }

                results.add(
                    buildFeedArticle(
                        source = source,
                        itemIdSeed = id,
                        title = title,
                        link = url,
                        rawContentHtml = contentHtml.ifBlank { contentText },
                        rawDescriptionHtml = summary,
                        publishedAtEpochMillis = publishedAtEpochMillis,
                        nowMillis = fetchTimeMillis,
                        mediaCandidates = mediaList,
                        enclosureImage = null,
                    )
                )
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildFeedArticle(
        source: NewsSource,
        itemIdSeed: String,
        title: String,
        link: String?,
        rawContentHtml: String,
        rawDescriptionHtml: String,
        publishedAtEpochMillis: Long,
        nowMillis: Long,
        mediaCandidates: List<MediaCandidate>,
        enclosureImage: String?,
    ): NewsArticle {
        val baseForUrls = link ?: source.feedUrl

        // Multi-tier Image URL Extraction Pipeline:
        // a) <media:content> or <media:thumbnail> (largest width attribute first)
        val mediaImageUrl = mediaCandidates
            .sortedWith(
                compareByDescending<MediaCandidate> { it.width }
                    .thenByDescending { it.priority }
            )
            .firstNotNullOfOrNull { normalizeImageUrl(it.url, baseForUrls) }

        // b) <enclosure type="image/...">
        val enclosureUrl = if (mediaImageUrl == null) {
            normalizeImageUrl(enclosureImage, baseForUrls)
        } else {
            null
        }

        // c) First valid <img src="..."> embedded inside <content:encoded>/<content>/<description>
        val embeddedImgUrl = if (mediaImageUrl == null && enclosureUrl == null) {
            extractFirstValidImgFromHtml(rawContentHtml, baseForUrls)
                ?: extractFirstValidImgFromHtml(rawDescriptionHtml, baseForUrls)
        } else {
            null
        }

        val resolvedImageUrl = mediaImageUrl ?: enclosureUrl ?: embeddedImgUrl

        // Full-text feed parsing first: check <content:encoded> / Atom <content> before falling back
        val contentParagraphs = if (rawContentHtml.isNotBlank()) {
            extractParagraphsFromHtml(rawContentHtml)
        } else {
            emptyList()
        }
        val descriptionParagraphs = if (rawDescriptionHtml.isNotBlank()) {
            extractParagraphsFromHtml(rawDescriptionHtml)
        } else {
            emptyList()
        }
        val paragraphs = when {
            contentParagraphs.sumOf { it.length } >= descriptionParagraphs.sumOf { it.length } &&
                contentParagraphs.isNotEmpty() -> contentParagraphs
            descriptionParagraphs.isNotEmpty() -> descriptionParagraphs
            else -> listOfNotNull(
                Jsoup.parseBodyFragment(rawDescriptionHtml.ifBlank { rawContentHtml })
                    .also { ArticleTextCleaner.stripCodeContainers(it) }
                    .text()
                    .let { ArticleTextCleaner.cleanParagraphText(it) }
            )
        }

        val isCompleteFeedText = !needsWebExtraction(paragraphs)
        val cleanedParagraphs = if (isCompleteFeedText) {
            stripTrailingFeedEllipsis(paragraphs)
        } else {
            paragraphs
        }.filter { it.isNotBlank() }

        val rawSummaryText = Jsoup.parseBodyFragment(rawDescriptionHtml.ifBlank { rawContentHtml })
            .also { ArticleTextCleaner.stripCodeContainers(it) }
            .text()
            .let { ArticleTextCleaner.cleanParagraphText(it).orEmpty() }
        val subtitle = buildCleanSubtitle(rawSummaryText, cleanedParagraphs)

        val totalWords = cleanedParagraphs.sumOf { it.split(Regex("\\s+")).size }
        val readMinutes = (totalWords / 210).coerceIn(2, 14)
        val stableId = "rss_${source.id}_${itemIdSeed.hashCode().toUInt().toString(16)}"

        return NewsArticle(
            id = stableId,
            headline = title,
            subtitle = subtitle,
            source = source.name,
            timestamp = formatRelativeTimestamp(publishedAtEpochMillis, nowMillis),
            publishedAtEpochMillis = publishedAtEpochMillis,
            category = source.category,
            readingTimeMinutes = readMinutes,
            hasAudioBrief = subtitle.isNotBlank() || cleanedParagraphs.isNotEmpty(),
            audioDurationSeconds = (readMinutes * 26).coerceIn(55, 160),
            audioSummary = subtitle.ifBlank { cleanedParagraphs.firstOrNull().orEmpty() },
            imageResId = null,
            imageUrl = resolvedImageUrl,
            articleUrl = link,
            paragraphs = cleanedParagraphs,
            isFullTextExtracted = isCompleteFeedText,
            fullTextExtractionFailed = false,
            customCategory = source.cleanCustomCategory,
        )
    }

    private fun extractFirstValidImgFromHtml(html: String, baseUrl: String?): String? {
        if (html.isBlank()) return null
        return try {
            val doc = Jsoup.parseBodyFragment(html)
            for (img in doc.select("img")) {
                val width = img.attr("width").toIntOrNull()
                val height = img.attr("height").toIntOrNull()
                if ((width != null && width <= 4) || (height != null && height <= 4)) {
                    continue
                }
                val candidate = img.attr("src").ifBlank {
                    img.attr("data-src").ifBlank { img.attr("data-original") }
                }
                val normalized = normalizeImageUrl(candidate, baseUrl)
                if (normalized != null) return normalized
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCleanSubtitle(rawSummary: String, paragraphs: List<String>): String {
        val candidate = rawSummary.ifBlank { paragraphs.firstOrNull().orEmpty() }
            .replace(Regex("(?i)(read more|continue reading).*$"), "")
            .trim()
        if (candidate.isBlank()) return "Full dispatch available in reader view."
        return if (candidate.length > 180) {
            candidate.take(177).substringBeforeLast(' ').ifBlank { candidate.take(177) } + "…"
        } else {
            candidate
        }
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun hasImageExtension(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase(Locale.US)
        return clean.endsWith(".jpg") ||
            clean.endsWith(".jpeg") ||
            clean.endsWith(".png") ||
            clean.endsWith(".webp") ||
            clean.endsWith(".avif")
    }

    private fun isLikelyTrackingOrIcon(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (lower.contains("1x1") ||
            lower.contains("pixel") ||
            lower.contains("beacon") ||
            lower.contains("spacer") ||
            lower.contains("doubleclick") ||
            lower.contains("scorecardresearch") ||
            lower.contains("feedsportal") ||
            lower.contains("feedburner.com/~") ||
            lower.contains("/favicon") ||
            lower.endsWith("favicon.ico") ||
            lower.contains("emoji")
        ) {
            return true
        }
        return false
    }

    private val rfcAndIsoPatterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss XXX",
        "EEE, d MMM yyyy HH:mm:ss XXX",
        "EEE, dd MMM yy HH:mm:ss Z",
        "EEE, dd MMM yy HH:mm:ss zzz",
        "dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss zzz",
        "d MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss Z",
        "yyyy-MM-dd HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    private const val MIN_PLAUSIBLE_EPOCH_MILLIS = 631_152_000_000L // 1990-01-01T00:00:00Z

    fun parsePublishedEpochMillis(rawDate: String?): Long? {
        val raw = rawDate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = raw
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2})"), "$1T$2")
            .replace(Regex("\\s+UT$"), " GMT")
            .replace(Regex("\\s+UTC$"), " +0000")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val epoch = Instant.parse(normalized).toEpochMilli()
                if (epoch >= MIN_PLAUSIBLE_EPOCH_MILLIS) return epoch
            } catch (_: Exception) {
            }
            try {
                val epoch = OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
                if (epoch >= MIN_PLAUSIBLE_EPOCH_MILLIS) return epoch
            } catch (_: Exception) {
            }
            try {
                val epoch = ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
                if (epoch >= MIN_PLAUSIBLE_EPOCH_MILLIS) return epoch
            } catch (_: Exception) {
            }
        }

        // Truncate sub-millisecond fractional digits (e.g. .123456 -> .123) for SimpleDateFormat
        val sdfCandidate = normalized.replace(Regex("\\.(\\d{3})\\d+"), ".$1")
        for (pattern in rfcAndIsoPatterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val pos = ParsePosition(0)
                val date = fmt.parse(sdfCandidate, pos)
                if (date != null && pos.index == sdfCandidate.length && date.time >= MIN_PLAUSIBLE_EPOCH_MILLIS) {
                    return date.time
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun formatRelativeTimestamp(
        epochMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        if (epochMillis <= 0L) return "Recent"
        val diffMillis = (nowMillis - epochMillis).coerceAtLeast(0L)
        val diffMins = diffMillis / 60_000L
        return when {
            diffMins < 2L -> "Just now"
            diffMins < 60L -> "${diffMins}m ago"
            diffMins < 24L * 60L -> "${diffMins / 60L}h ago"
            else -> {
                val nowCal = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = nowMillis }
                val artCal = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = epochMillis }
                val pattern = if (nowCal.get(Calendar.YEAR) == artCal.get(Calendar.YEAR)) {
                    "MMM d"
                } else {
                    "MMM d, yyyy"
                }
                SimpleDateFormat(pattern, Locale.US).apply {
                    this.timeZone = timeZone
                }.format(artCal.time)
            }
        }
    }

    fun formatRelativeTimestamp(
        rawDate: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val parsedMillis = parsePublishedEpochMillis(rawDate) ?: return "Just now"
        return formatRelativeTimestamp(parsedMillis, nowMillis)
    }
}
