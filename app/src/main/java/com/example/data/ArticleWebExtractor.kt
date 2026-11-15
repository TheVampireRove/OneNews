package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class WebExtractionResult(
    val paragraphs: List<String>,
    val imageUrl: String?,
    val canonicalUrl: String,
    val extractionFailed: Boolean,
)

class ArticleWebExtractor(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "onenews_web_cache").apply { mkdirs() }
    private val ogCacheFile = File(cacheDir, "og_image_cache.json")

    private val ogImageCache = ConcurrentHashMap<String, String>() // "" = cached miss
    private val fullTextCache = ConcurrentHashMap<String, WebExtractionResult>()

    private val feedClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Short 3-second timeout specifically for Open Graph <head> lookups
    private val ogClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Readability full-text extraction client following publisher & Google News redirects
    private val articleClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    init {
        loadOgCacheFromDisk()
    }

    suspend fun fetchFeedText(feedUrl: String): String? = withContext(Dispatchers.IO) {
        val cleanUrl = feedUrl.trim()
        val parsedHttpUrl = cleanUrl.toHttpUrlOrNull() ?: return@withContext null
        if (cleanUrl.contains(".example/")) {
            return@withContext null
        }
        try {
            val (finalUrl, bodyText) = executeBoundedFeedCall(parsedHttpUrl.toString())
                ?: return@withContext null
            if (!looksLikeHtmlDocument(bodyText)) {
                return@withContext bodyText
            }
            // User or curated URL resolved to an HTML homepage — check for an advertised RSS/Atom feed link
            val discoveredFeedUrl = discoverAlternateFeedUrl(bodyText, finalUrl)
                ?.takeIf { !it.equals(finalUrl, ignoreCase = true) && !it.contains(".example/") }
                ?.toHttpUrlOrNull()
                ?.toString()
                ?: return@withContext null
            val (_, discoveredBody) = executeBoundedFeedCall(discoveredFeedUrl)
                ?: return@withContext null
            discoveredBody.takeIf { !looksLikeHtmlDocument(it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun previewFeed(
        feedUrl: String,
        localHintLocation: String = "",
    ): FeedChannelPreview = withContext(Dispatchers.IO) {
        val cleanUrl = feedUrl.trim()
        val withScheme = if (cleanUrl.startsWith("http://", true) || cleanUrl.startsWith("https://", true)) {
            cleanUrl
        } else {
            "https://$cleanUrl"
        }
        val parsedHttpUrl = withScheme.toHttpUrlOrNull()
        if (parsedHttpUrl == null || withScheme.contains(".example/")) {
            val (cat, reason) = FeedCategoryDetector.suggestCategoryFromMetadata(
                feedUrl = cleanUrl,
                localHintLocation = localHintLocation,
            )
            return@withContext FeedChannelPreview(
                resolvedUrl = cleanUrl,
                suggestedCategory = cat,
                detectionReason = reason,
                isValidFeed = false,
            )
        }
        val targetUrl = parsedHttpUrl.toString()
        val feedXml = fetchFeedText(targetUrl)
        if (!feedXml.isNullOrBlank()) {
            FeedCategoryDetector.extractChannelPreview(
                rawBody = feedXml,
                resolvedUrl = targetUrl,
                localHintLocation = localHintLocation,
            )
        } else {
            val (cat, reason) = FeedCategoryDetector.suggestCategoryFromMetadata(
                feedUrl = targetUrl,
                localHintLocation = localHintLocation,
            )
            FeedChannelPreview(
                resolvedUrl = targetUrl,
                suggestedCategory = cat,
                detectionReason = reason,
                isValidFeed = false,
            )
        }
    }

    private fun executeBoundedFeedCall(url: String): Pair<String, String>? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", RSS_USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/feed+json, application/xml, text/xml;q=0.9, */*;q=0.8")
            .build()
        return feedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.US)
            if (contentType.startsWith("image/") ||
                contentType.startsWith("video/") ||
                contentType.startsWith("audio/") ||
                contentType.contains("application/octet-stream") ||
                contentType.contains("application/pdf")
            ) {
                return@use null
            }
            val text = readBoundedText(response.body, MAX_FEED_BYTES)
            if (text.isNullOrBlank()) null else response.request.url.toString() to text
        }
    }

    private fun looksLikeHtmlDocument(text: String): Boolean {
        val head = text.trimStart().take(4096).lowercase(Locale.US)
        val hasHtmlMarker = head.contains("<!doctype html") || head.contains("<html")
        if (!hasHtmlMarker) return false
        return !head.contains("<rss") && !head.contains("<feed") && !head.contains("<rdf:rdf")
    }

    private fun discoverAlternateFeedUrl(html: String, baseUrl: String): String? {
        return try {
            val doc = Jsoup.parse(html, baseUrl)
            val feedLink = doc.select(
                "link[rel~=alternate][type*=rss], " +
                    "link[rel~=alternate][type*=atom], " +
                    "link[rel~=alternate][type*=feed+json], " +
                    "link[rel~=alternate][type*=xml]"
            ).firstOrNull() ?: return null
            val absHref = feedLink.absUrl("href").trim().ifBlank { feedLink.attr("href").trim() }
            absHref.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchOgImage(articleUrl: String): String? = withContext(Dispatchers.IO) {
        val cleanUrl = articleUrl.trim()
        if (!cleanUrl.startsWith("http", ignoreCase = true) || cleanUrl.contains(".example/")) {
            return@withContext null
        }
        ogImageCache[cleanUrl]?.let { cached ->
            return@withContext cached.ifBlank { null }
        }

        val resolved = try {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", MOBILE_BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            ogClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    val finalUrl = response.request.url.toString()
                    val headHtml = readHeadSnippet(response.body?.charStream())
                    extractMetaImageFromHtml(headHtml, finalUrl)
                }
            }
        } catch (_: Exception) {
            null
        }

        ogImageCache[cleanUrl] = resolved.orEmpty()
        persistOgCacheToDisk()
        resolved
    }

    suspend fun extractFullArticle(articleUrl: String): WebExtractionResult = withContext(Dispatchers.IO) {
        val cleanUrl = articleUrl.trim()
        if (!cleanUrl.startsWith("http", ignoreCase = true) || cleanUrl.contains(".example/")) {
            return@withContext WebExtractionResult(
                paragraphs = emptyList(),
                imageUrl = null,
                canonicalUrl = cleanUrl,
                extractionFailed = false,
            )
        }

        fullTextCache[cleanUrl]?.let { return@withContext it }
        readFullTextFromDisk(cleanUrl)?.let { diskCached ->
            fullTextCache[cleanUrl] = diskCached
            return@withContext diskCached
        }

        val result = try {
            val (code, finalUrl, html) = fetchHtmlWithRedirectSupport(cleanUrl)
            if (code in setOf(401, 402, 403, 429, 451) || html.isNullOrBlank()) {
                WebExtractionResult(
                    paragraphs = emptyList(),
                    imageUrl = null,
                    canonicalUrl = finalUrl,
                    extractionFailed = true,
                )
            } else {
                val doc = Jsoup.parse(html, finalUrl)
                val ogImage = extractMetaImageFromDocument(doc, finalUrl)
                if (ogImage != null) {
                    ogImageCache[cleanUrl] = ogImage
                    persistOgCacheToDisk()
                }
                if (isBlockedOrPaywalled(doc)) {
                    WebExtractionResult(
                        paragraphs = emptyList(),
                        imageUrl = ogImage,
                        canonicalUrl = finalUrl,
                        extractionFailed = true,
                    )
                } else {
                    val extractedParagraphs = extractReadableParagraphs(doc)
                    val tooShort = extractedParagraphs.sumOf { it.length } < 260
                    WebExtractionResult(
                        paragraphs = if (tooShort) emptyList() else extractedParagraphs,
                        imageUrl = ogImage,
                        canonicalUrl = finalUrl,
                        extractionFailed = tooShort,
                    )
                }
            }
        } catch (_: Exception) {
            WebExtractionResult(
                paragraphs = emptyList(),
                imageUrl = null,
                canonicalUrl = cleanUrl,
                extractionFailed = true,
            )
        }

        fullTextCache[cleanUrl] = result
        writeFullTextToDisk(cleanUrl, result)
        result
    }

    private fun fetchHtmlWithRedirectSupport(url: String): Triple<Int, String, String?> {
        var currentUrl = url
        repeat(3) {
            val validUrl = currentUrl.toHttpUrlOrNull() ?: return Triple(400, currentUrl, null)
            val request = Request.Builder()
                .url(validUrl)
                .header("User-Agent", MOBILE_BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            articleClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val body = readBoundedText(response.body, MAX_ARTICLE_BYTES)
                if (!response.isSuccessful) {
                    return Triple(response.code, finalUrl, body)
                }
                if (!body.isNullOrBlank() && finalUrl.contains("news.google.com", ignoreCase = true)) {
                    val doc = Jsoup.parse(body, finalUrl)
                    val metaRefresh = doc.selectFirst("meta[http-equiv=refresh]")?.attr("content")
                        ?.substringAfter("url=", "")
                        ?.trim()
                        ?.takeIf { it.startsWith("http") }
                    val externalAnchor = doc.select("a[href^=http]")
                        .firstOrNull { a ->
                            val href = a.attr("href")
                            !href.contains("google.com", ignoreCase = true)
                        }?.attr("href")
                    val nextHop = metaRefresh ?: externalAnchor
                    if (nextHop != null && nextHop != currentUrl) {
                        currentUrl = nextHop
                        return@use
                    }
                }
                return Triple(response.code, finalUrl, body)
            }
        }
        return Triple(200, currentUrl, null)
    }

    private fun readBoundedText(body: ResponseBody?, maxBytes: Long): String? {
        if (body == null) return null
        return try {
            val contentLength = body.contentLength()
            if (contentLength > maxBytes) {
                val source = body.source()
                source.request(maxBytes)
                val toRead = minOf(source.buffer.size, maxBytes)
                source.buffer.readUtf8(toRead)
            } else {
                val source = body.source()
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0L
                while (total < maxBytes) {
                    val allowed = minOf(buffer.size.toLong(), maxBytes - total).toInt()
                    val read = source.read(buffer, 0, allowed)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readHeadSnippet(reader: java.io.Reader?): String {
        if (reader == null) return ""
        return try {
            val sb = StringBuilder()
            val buffer = CharArray(4096)
            var total = 0
            while (total < 65_536) {
                val read = reader.read(buffer)
                if (read <= 0) break
                sb.append(buffer, 0, read)
                total += read
                val lower = sb.toString().lowercase(Locale.US)
                if (lower.contains("</head>")) break
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractMetaImageFromHtml(html: String, baseUrl: String): String? {
        if (html.isBlank()) return null
        return extractMetaImageFromDocument(Jsoup.parse(html, baseUrl), baseUrl)
    }

    private fun extractMetaImageFromDocument(doc: Document, baseUrl: String): String? {
        val candidates = listOfNotNull(
            doc.selectFirst("meta[property=og:image:secure_url]")?.attr("content"),
            doc.selectFirst("meta[property=og:image]")?.attr("content"),
            doc.selectFirst("meta[name=og:image]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:image:src]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:image]")?.attr("content"),
            doc.selectFirst("meta[property=twitter:image]")?.attr("content"),
            doc.selectFirst("link[rel=image_src]")?.attr("href"),
        )
        return candidates.firstNotNullOfOrNull { RssFeedParser.normalizeImageUrl(it, baseUrl) }
    }

    private fun isBlockedOrPaywalled(doc: Document): Boolean {
        val title = doc.title().lowercase(Locale.US)
        if (title.contains("just a moment") ||
            title.contains("access denied") ||
            title.contains("attention required") ||
            title.contains("403 forbidden")
        ) {
            return true
        }
        val bodyText = doc.body()?.text()?.lowercase(Locale.US).orEmpty()
        if (bodyText.length < 400 && (
                bodyText.contains("enable javascript") ||
                    bodyText.contains("verify you are human") ||
                    bodyText.contains("subscribe to continue reading") ||
                    bodyText.contains("subscription required")
                )
        ) {
            return true
        }
        return false
    }

    private fun extractReadableParagraphs(doc: Document): List<String> {
        // Strip boilerplate containers before DOM scoring
        doc.select(
            "script, style, noscript, nav, header, footer, aside, form, iframe, svg, button, " +
                "[role=navigation], [role=banner], [role=complementary], " +
                "[class*=cookie], [id*=cookie], [class*=consent], [id*=consent], " +
                "[class*=advert], [class*=ad-slot], [id*=ad-], " +
                "[class*=related], [class*=recirc], [class*=recommended], " +
                "[class*=newsletter], [class*=subscribe], [class*=paywall], " +
                "[class*=author-bio], [class*=byline-bio], [class*=share], [class*=social], " +
                "[class*=comment], [id*=comments], [class*=promo]"
        ).remove()

        val semanticSelectors = listOf(
            "article",
            "[itemprop=articleBody]",
            "[data-testid=article-body]",
            "div.article-body",
            "div.story-body",
            "div.entry-content",
            "div.post-content",
            "div.c-entry-content",
            "main",
        )

        var bestContainer: Element? = null
        var bestScore = -1.0

        for (selector in semanticSelectors) {
            for (el in doc.select(selector)) {
                val score = scoreContainer(el, semanticBonus = 1.45)
                if (score > bestScore) {
                    bestScore = score
                    bestContainer = el
                }
            }
        }

        // Fall back to container with highest <p> text density if semantic tags weren't rich enough
        if (bestScore < 280.0) {
            for (el in doc.select("div, section")) {
                val score = scoreContainer(el, semanticBonus = 1.0)
                if (score > bestScore) {
                    bestScore = score
                    bestContainer = el
                }
            }
        }

        val target = bestContainer ?: doc.body() ?: return emptyList()
        val blocks = target.select("h2, h3, p, blockquote, li")
        val paragraphs = mutableListOf<String>()
        val seen = HashSet<String>()

        val skipPhrases = listOf(
            "this story is curated from",
            "sign up for",
            "subscribe to",
            "follow us on",
            "all rights reserved",
            "copyright ©",
            "cookie policy",
            "terms of service",
            "read more:",
            "advertisement",
        )

        for (el in blocks) {
            if (el.tagName() == "p" && el.parents().any { it.tagName() in setOf("blockquote", "li") }) {
                continue
            }
            val rawText = el.text().replace(Regex("\\s+"), " ").trim()
            if (rawText.length < 24) continue
            val lower = rawText.lowercase(Locale.US)
            if (skipPhrases.any { lower.contains(it) && rawText.length < 220 }) continue

            val formatted = when (el.tagName().lowercase(Locale.US)) {
                "li" -> "• $rawText"
                "blockquote" -> "“$rawText”"
                else -> rawText
            }
            if (seen.add(formatted)) {
                paragraphs.add(formatted)
            }
        }

        return RssFeedParser.stripTrailingFeedEllipsis(paragraphs)
    }

    private fun scoreContainer(el: Element, semanticBonus: Double): Double {
        val pElements = el.select("> p, > div > p, p")
        if (pElements.isEmpty()) return 0.0
        var textScore = 0.0
        for (p in pElements) {
            val len = p.text().trim().length
            if (len >= 35) {
                textScore += len
            }
        }
        if (textScore <= 0.0) return 0.0
        val totalLen = el.text().length.coerceAtLeast(1)
        val linkLen = el.select("a").sumOf { it.text().length }
        val linkDensity = (linkLen.toDouble() / totalLen.toDouble()).coerceIn(0.0, 0.95)
        return textScore * (1.0 - linkDensity) * semanticBonus
    }

    private fun loadOgCacheFromDisk() {
        try {
            if (!ogCacheFile.exists()) return
            val json = JSONObject(ogCacheFile.readText())
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                ogImageCache[k] = json.optString(k, "")
            }
        } catch (_: Exception) {
        }
    }

    private fun persistOgCacheToDisk() {
        try {
            val json = JSONObject()
            ogImageCache.entries.take(400).forEach { (k, v) ->
                json.put(k, v)
            }
            ogCacheFile.writeText(json.toString())
        } catch (_: Exception) {
        }
    }

    private fun fullTextCacheFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(24)
        return File(cacheDir, "article_$hex.json")
    }

    private fun readFullTextFromDisk(url: String): WebExtractionResult? {
        return try {
            val file = fullTextCacheFile(url)
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            val arr = json.optJSONArray("paragraphs") ?: JSONArray()
            val paragraphs = (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).takeIf { it.isNotBlank() }
            }
            WebExtractionResult(
                paragraphs = paragraphs,
                imageUrl = json.optString("imageUrl").takeIf { it.isNotBlank() },
                canonicalUrl = json.optString("canonicalUrl", url),
                extractionFailed = json.optBoolean("extractionFailed", false),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeFullTextToDisk(url: String, result: WebExtractionResult) {
        try {
            val json = JSONObject().apply {
                put("canonicalUrl", result.canonicalUrl)
                put("imageUrl", result.imageUrl ?: "")
                put("extractionFailed", result.extractionFailed)
                put("paragraphs", JSONArray(result.paragraphs))
            }
            fullTextCacheFile(url).writeText(json.toString())
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MAX_FEED_BYTES = 2L * 1024L * 1024L
        private const val MAX_ARTICLE_BYTES = 2L * 1024L * 1024L
        private const val RSS_USER_AGENT = "OneNews/4.0 (Android Foldable Reader)"
        private const val MOBILE_BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 15; SM-F956U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
