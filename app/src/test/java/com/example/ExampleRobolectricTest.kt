package com.example

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.ArticleFeedAggregator
import com.example.data.FeedCategoryDetector
import com.example.data.FeedChannelPreview
import com.example.data.MockNewsRepository
import com.example.data.RssFeedParser
import com.example.data.ThemePreferencesRepository
import com.example.model.AppDestination
import com.example.model.AudioPlaybackState
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.NewsSource
import com.example.model.PageTurnAnimation
import com.example.model.ThemeSettings
import com.example.ui.NewsViewModel
import com.example.ui.OneNewsUiState
import com.example.ui.components.FloatingBottomNavBar
import com.example.ui.components.PredictiveBackMotionSpecs
import com.example.ui.feed.NewsFeedPane
import com.example.ui.reader.KindlePaginatedReader
import com.example.ui.reader.findCleanWordSplitOffset
import com.example.ui.settings.AddFeedDialog
import com.example.ui.settings.SourcesAndFeedsCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OneNews", appName)
    }

    @Test
    fun `mock repository provides 12 diverse articles across all categories`() {
        val articles = MockNewsRepository.getArticles()
        assertEquals(12, articles.size)

        val categories = articles.map { it.category }.toSet()
        assertTrue(NewsCategory.TECH in categories)
        assertTrue(NewsCategory.WORLD in categories)
        assertTrue(NewsCategory.LOCAL in categories)
        assertTrue(NewsCategory.SCIENCE in categories)
        assertTrue(NewsCategory.CULTURE in categories)

        assertTrue(articles.any { it.eventMetadata != null })
        assertTrue(articles.any { it.locationMetadata != null })
        assertTrue(articles.any { it.eventMetadata == null && it.locationMetadata == null })
        assertTrue(articles.any { it.hasAudioBrief })
        assertTrue(articles.all { it.paragraphs.size >= 2 })
    }

    @Test
    fun `curated sources cover all five genres and omit synced from url boilerplate`() {
        val curated = MockNewsRepository.getCuratedSources()
        assertTrue(curated.size >= 30)
        val genres = curated.map { it.category }.toSet()
        assertTrue(NewsCategory.TECH in genres)
        assertTrue(NewsCategory.WORLD in genres)
        assertTrue(NewsCategory.LOCAL in genres)
        assertTrue(NewsCategory.SCIENCE in genres)
        assertTrue(NewsCategory.CULTURE in genres)

        val customSource = curated.first { !it.isDefaultEnabled }
        val built = MockNewsRepository.buildArticleForSource(customSource)
        assertEquals(customSource.name, built.source)
        assertEquals(customSource.category, built.category)
        assertTrue(built.paragraphs.isNotEmpty())
        assertFalse(built.subtitle.contains("Synced from", ignoreCase = true))
        assertFalse(built.paragraphs.joinToString(" ").contains("This story is curated from", ignoreCase = true))
    }

    @Test
    fun `localized news area presets generate local articles for requested city`() {
        val presets = MockNewsRepository.getLocalPresets()
        assertTrue(presets.size >= 6)

        val seattleArticles = MockNewsRepository.getLocalizedArticlesForArea("Seattle, WA", 25)
        assertTrue(seattleArticles.isNotEmpty())
        assertTrue(seattleArticles.all { it.category == NewsCategory.LOCAL })
        assertTrue(seattleArticles.any { it.headline.contains("Seattle", ignoreCase = true) })
        assertTrue(seattleArticles.any { it.locationMetadata?.venueName?.contains("Seattle", ignoreCase = true) == true })
    }

    @Test
    fun `page turn animation options match Kindle curl, slide, and instant modes`() {
        assertEquals(3, PageTurnAnimation.entries.size)
        assertTrue(PageTurnAnimation.KINDLE_CURL in PageTurnAnimation.entries)
        assertTrue(PageTurnAnimation.SIMPLE_SLIDE in PageTurnAnimation.entries)
        assertTrue(PageTurnAnimation.INSTANT in PageTurnAnimation.entries)
    }

    @Test
    fun `rss parser selects largest media content image and prefers full content encoded html`() {
        val source = NewsSource(
            id = "test_ars",
            name = "Ars Technica",
            category = NewsCategory.TECH,
            feedUrl = "https://feeds.arstechnica.com/arstechnica/index",
            description = "Tech reporting",
        )
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Ars Technica</title>
                <item>
                  <title>Foldable Kernel Scheduler Benchmark</title>
                  <link>https://arstechnica.com/gadgets/2026/11/foldable-kernel/</link>
                  <description>Short teaser summary.</description>
                  <content:encoded><![CDATA[
                    <img src="https://cdn.example.com/pixel.gif" width="1" height="1" />
                    <p>Engineers demonstrated a new thermal-aware core scheduler designed specifically for dual-screen foldables.</p>
                    <p>In extended workload benchmarks, frame pacing improved by twenty-four percent across split-screen multitasking sessions.</p>
                  ]]></content:encoded>
                  <media:thumbnail url="//cdn.arstechnica.net/thumb-small.jpg" width="320" />
                  <media:content url="//cdn.arstechnica.net/hero-large.jpg" width="1600" medium="image" />
                </item>
                <item>
                  <title>Enclosure Image Story</title>
                  <link>https://arstechnica.com/gadgets/2026/11/enclosure-story/</link>
                  <enclosure url="/images/cover-hero.jpg" type="image/jpeg" length="12345" />
                  <description><![CDATA[<p>Read more at <a href="https://arstechnica.com">Ars</a></p><p>Another concise paragraph here.</p>]]></description>
                </item>
                <item>
                  <title>Embedded Img Fallback Story</title>
                  <link>https://arstechnica.com/gadgets/2026/11/img-fallback/</link>
                  <description><![CDATA[
                    <img src="https://tracker.doubleclick.net/1x1.gif" width="1" height="1" />
                    <img src="//images.arstechnica.net/lead-photo.webp" width="900" height="600" />
                    <p>Battery density advances promise lighter foldable chassis next year.</p>
                  ]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = RssFeedParser.parseFeed(rssXml, source)
        assertEquals(3, parsed.size)

        // Item 1: Largest media:content wins & protocol-relative // normalized to https://
        assertEquals("https://cdn.arstechnica.net/hero-large.jpg", parsed[0].imageUrl)
        assertTrue(parsed[0].paragraphs.any { it.contains("thermal-aware core scheduler") })
        assertFalse(parsed[0].subtitle.contains("Synced from", ignoreCase = true))

        // Item 2: Enclosure relative path resolved against article domain
        assertEquals("https://arstechnica.com/images/cover-hero.jpg", parsed[1].imageUrl)
        assertTrue(RssFeedParser.needsWebExtraction(parsed[1].paragraphs))

        // Item 3: Skips tracking pixel and picks real lead image from HTML
        assertEquals("https://images.arstechnica.net/lead-photo.webp", parsed[2].imageUrl)
    }

    @Test
    fun `theme settings expose useSystemFont toggle defaulting to false`() {
        val defaults = ThemeSettings()
        assertFalse(defaults.useSystemFont)
        assertTrue(defaults.copy(useSystemFont = true).useSystemFont)
    }

    @Test
    fun `predictive back motion specs compute edge-aware scale, translation, and corner radius`() {
        val idle = PredictiveBackMotionSpecs.computeSurfaceTransform(
            rawProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            verticalTouchDeltaPx = 0f,
            containerWidthPx = 1080f,
            containerHeightPx = 2400f,
            marginPx = 24f,
        )
        assertEquals(1f, idle.scale, 0.0001f)
        assertEquals(0f, idle.cornerRadiusDp, 0.0001f)

        val leftFull = PredictiveBackMotionSpecs.computeSurfaceTransform(
            rawProgress = 1f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            verticalTouchDeltaPx = 200f,
            containerWidthPx = 1080f,
            containerHeightPx = 2400f,
            marginPx = 24f,
        )
        assertEquals(0.90f, leftFull.scale, 0.001f)
        assertEquals(28f, leftFull.cornerRadiusDp, 0.001f)
        assertTrue(leftFull.translationX > 0f)
        assertTrue(leftFull.translationY > 0f)

        val rightFull = PredictiveBackMotionSpecs.computeSurfaceTransform(
            rawProgress = 1f,
            swipeEdge = BackEventCompat.EDGE_RIGHT,
            verticalTouchDeltaPx = -200f,
            containerWidthPx = 1080f,
            containerHeightPx = 2400f,
            marginPx = 24f,
        )
        assertEquals(0.90f, rightFull.scale, 0.001f)
        assertTrue(rightFull.translationX < 0f)
        assertTrue(rightFull.translationY < 0f)
    }

    @Test
    fun `ui state exposes isRefreshingFeed flag defaulting to false`() {
        val state = OneNewsUiState()
        assertFalse(state.isRefreshingFeed)
        assertTrue(state.copy(isRefreshingFeed = true).isRefreshingFeed)
    }

    @Test
    fun `rss parser flags long teaser snippets ending in ellipsis for web extraction and strips trailing ellipses`() {
        val longEllipsisTeaser = listOf(
            "A".repeat(480) + " engineers observed substantial gains across split-screen benchmarks...",
        )
        assertTrue(RssFeedParser.needsWebExtraction(longEllipsisTeaser))

        val unicodeEllipsisTeaser = listOf(
            "B".repeat(520) + " policy analysts are reviewing regional transit milestones…",
        )
        assertTrue(RssFeedParser.needsWebExtraction(unicodeEllipsisTeaser))

        val fullArticle = listOf(
            "Engineers deployed a comprehensive thermal scheduling update across multi-core foldable architectures, delivering steady frame times during demanding multitasking sessions.",
            "Independent hardware labs verified lower sustained chassis temperatures during extended reading and playback workloads across diverse network conditions.",
            "Software maintainers noted that the open kernel patches will roll out broadly over the coming quarter alongside documentation for adaptive layout developers.",
            "Editorial partners praised the clarity of long-form reporting delivered directly inside native Android reading panes without abrupt snippet cutoffs.",
            "Readers testing the new pipeline across tall and compact screens reported complete continuity from the opening summary through the closing remarks.",
        )
        assertFalse(RssFeedParser.needsWebExtraction(fullArticle))

        val dirty = listOf(
            "Opening paragraph remains intact.",
            "Closing paragraph had an RSS stub […]",
        )
        val cleaned = RssFeedParser.stripTrailingFeedEllipsis(dirty)
        assertEquals("Closing paragraph had an RSS stub", cleaned.last())
    }

    @Test
    fun `word boundary splitter never cuts inside a word across pages`() {
        val sample = "Adaptive typography measures every paragraph cleanly without slicing words."
        val midWordIndex = sample.indexOf("paragraph") + 4 // inside "paragraph"
        val cleanOffset = findCleanWordSplitOffset(sample, midWordIndex)
        assertTrue(sample[cleanOffset].isWhitespace())
        assertEquals("Adaptive typography measures every", sample.substring(0, cleanOffset).trimEnd())
        assertEquals("paragraph cleanly without slicing words.", sample.substring(cleanOffset).trimStart())
    }

    @Test
    fun `kindle paginated reader splits full multi-paragraph article beyond page 2 and renders End of Article footer`() {
        val paragraphs = (1..8).map { idx ->
            "Paragraph $idx details how modern foldable hardware and adaptive typography engines dynamically allocate viewport height across consecutive reading pages without clipping sentences or injecting premature ellipses. Marker_$idx."
        }
        val article = NewsArticle(
            id = "long_test_article",
            headline = "Dynamic TextMeasurer Pagination Eliminates Premature Truncation",
            subtitle = "Every paragraph renders across as many discrete pages as the viewport requires.",
            source = "OneNews Engineering",
            timestamp = "Just now",
            category = NewsCategory.TECH,
            readingTimeMinutes = 5,
            hasAudioBrief = false,
            audioDurationSeconds = 0,
            audioSummary = "",
            paragraphs = paragraphs,
            isFullTextExtracted = true,
        )

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 340.dp, height = 560.dp)) {
                    KindlePaginatedReader(
                        article = article,
                        themeSettings = ThemeSettings(
                            isPaginatedReadingMode = true,
                            pageTurnAnimation = PageTurnAnimation.INSTANT,
                            showNowNudgeChips = false,
                        ),
                        toolbarsVisible = false,
                        isExtractingFullText = false,
                        onToggleToolbars = {},
                        onPreviousArticle = {},
                        onNextArticle = {},
                        onAddCalendarEvent = {},
                        onOpenMaps = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("kindle_page_indicator_pill").assertIsDisplayed()

        // Tap right edge repeatedly to advance through all pages until End of Article is displayed
        var reachedEnd = false
        for (step in 1..15) {
            try {
                composeRule.onNodeWithTag("end_of_article_footer").assertIsDisplayed()
                reachedEnd = true
                break
            } catch (_: AssertionError) {
                composeRule.onNodeWithTag("kindle_paginated_reader").performTouchInput {
                    click(androidx.compose.ui.geometry.Offset(width * 0.9f, height * 0.5f))
                }
                composeRule.waitForIdle()
            }
        }
        assertTrue("Expected pagination to reach End of Article footer across all pages", reachedEnd)
        composeRule.onNodeWithText("End of Article").assertIsDisplayed()
        composeRule.onNodeWithText("Marker_8.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `multi-format syndication date parser normalizes RFC 822, RFC 1123, and ISO-8601 into UTC epoch millis`() {
        val expectedUtcMillis = 1794681000_000L // 2026-11-14T18:30:00Z
        assertEquals(expectedUtcMillis, RssFeedParser.parsePublishedEpochMillis("2026-11-14T18:30:00Z"))
        assertEquals(expectedUtcMillis, RssFeedParser.parsePublishedEpochMillis("2026-11-14T20:30:00+02:00"))
        assertEquals(expectedUtcMillis, RssFeedParser.parsePublishedEpochMillis("2026-11-14T18:30:00.000000Z"))
        assertEquals(expectedUtcMillis, RssFeedParser.parsePublishedEpochMillis("Sat, 14 Nov 2026 18:30:00 +0000"))
        assertEquals(expectedUtcMillis, RssFeedParser.parsePublishedEpochMillis("Sat, 14 Nov 2026 10:30:00 -0800"))
        assertEquals(expectedUtcMillis, RssFeedParser.parsePublishedEpochMillis("Sat, 14 Nov 2026 18:30:00 GMT"))
        assertEquals(expectedUtcMillis, RssFeedParser.parsePublishedEpochMillis("Sat, 14 Nov 2026 13:30:00 EST"))
        assertEquals(null, RssFeedParser.parsePublishedEpochMillis("not-a-valid-date"))
    }

    @Test
    fun `rss and atom feeds sort newest to oldest and assign lastBuildDate fallback for undated items`() {
        val fetchNow = 1794684600_000L // 2026-11-14T19:30:00Z
        val source = NewsSource(
            id = "test_world",
            name = "Global Wire",
            category = NewsCategory.WORLD,
            feedUrl = "https://example.org/rss.xml",
        )
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Global Wire</title>
                <lastBuildDate>Sat, 14 Nov 2026 19:00:00 +0000</lastBuildDate>
                <item>
                  <title>Older Morning Story</title>
                  <link>https://example.org/older</link>
                  <pubDate>Sat, 14 Nov 2026 16:30:00 +0000</pubDate>
                  <description>Older story body.</description>
                </item>
                <item>
                  <title>Undated Channel Fallback Story</title>
                  <link>https://example.org/undated</link>
                  <pubDate>invalid-date-string</pubDate>
                  <description>Undated story uses channel lastBuildDate fallback.</description>
                </item>
                <item>
                  <title>Newest ISO dcDate Story</title>
                  <link>https://example.org/newest</link>
                  <dc:date>2026-11-14T19:12:00Z</dc:date>
                  <description>Newest story body.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = RssFeedParser.parseFeed(xml, source, maxItems = 10, fetchTimeMillis = fetchNow)
        assertEquals(3, parsed.size)
        assertEquals("Newest ISO dcDate Story", parsed[0].headline)
        assertEquals("18m ago", parsed[0].timestamp)
        assertEquals("Undated Channel Fallback Story", parsed[1].headline)
        assertTrue(parsed[1].publishedAtEpochMillis in 1794682000_000L..1794683000_000L)
        assertEquals("Older Morning Story", parsed[2].headline)
        assertEquals("3h ago", parsed[2].timestamp)
        assertTrue(parsed.all { it.publishedAtEpochMillis in 1L until Long.MAX_VALUE })
    }

    @Test
    fun `dynamic relative timestamp formats just now, minutes, hours, and calendar dates`() {
        val now = 1794684600_000L // Nov 14, 2026 19:30:00 UTC
        val utc = java.util.TimeZone.getTimeZone("UTC")
        assertEquals("Just now", RssFeedParser.formatRelativeTimestamp(now - 30_000L, now, utc))
        assertEquals("18m ago", RssFeedParser.formatRelativeTimestamp(now - 18 * 60_000L, now, utc))
        assertEquals("3h ago", RssFeedParser.formatRelativeTimestamp(now - 3 * 3_600_000L, now, utc))
        assertEquals("Nov 13", RssFeedParser.formatRelativeTimestamp(now - 30 * 3_600_000L, now, utc))
    }

    @Test
    fun `aggregator merges, deduplicates canonical urls and headlines, and sorts descending across categories`() {
        val now = 1794684600_000L
        val olderTech = NewsArticle(
            id = "rss_tech_1",
            headline = "Foldable Display Hinges Drop Weight by Twenty Percent",
            subtitle = "Older syndicated copy",
            source = "Tech Feed A",
            timestamp = "stale",
            publishedAtEpochMillis = now - 2 * 3_600_000L,
            category = NewsCategory.TECH,
            readingTimeMinutes = 4,
            hasAudioBrief = false,
            audioDurationSeconds = 0,
            audioSummary = "",
            articleUrl = "https://www.techsite.com/foldable-hinge/?utm_source=rss",
            paragraphs = listOf("Body A"),
        )
        val duplicateTechNewer = olderTech.copy(
            id = "rss_tech_2",
            source = "Tech Feed B",
            publishedAtEpochMillis = now - 15 * 60_000L,
            articleUrl = "https://techsite.com/foldable-hinge",
            imageUrl = "https://techsite.com/hero.jpg",
        )
        val breakingTech = NewsArticle(
            id = "rss_tech_3",
            headline = "Quantum Silicon Wafer Ships Ahead of Schedule",
            subtitle = "Newest story",
            source = "Tech Feed C",
            timestamp = "stale",
            publishedAtEpochMillis = now - 60_000L,
            category = NewsCategory.TECH,
            readingTimeMinutes = 3,
            hasAudioBrief = false,
            audioDurationSeconds = 0,
            audioSummary = "",
            articleUrl = "https://techsite.com/quantum-wafer",
            paragraphs = listOf("Body B"),
        )
        val worldStory = breakingTech.copy(
            id = "rss_world_1",
            headline = "Alpine Hydro Interconnectors Reach Full Capacity",
            category = NewsCategory.WORLD,
            publishedAtEpochMillis = now - 45 * 60_000L,
            articleUrl = "https://worldsite.com/hydro",
        )

        val merged = ArticleFeedAggregator.mergeDeduplicateAndSort(
            articles = listOf(olderTech, worldStory, duplicateTechNewer, breakingTech),
            nowMillis = now,
        )
        assertEquals(3, merged.size)
        assertEquals("rss_tech_3", merged[0].id)
        assertEquals("Just now", merged[0].timestamp)
        assertEquals("rss_tech_2", merged[1].id)
        assertEquals("15m ago", merged[1].timestamp)
        assertEquals("https://techsite.com/hero.jpg", merged[1].imageUrl)
        assertEquals("rss_world_1", merged[2].id)
        assertEquals("45m ago", merged[2].timestamp)

        val techOnly = ArticleFeedAggregator.filterAndSort(merged, NewsCategory.TECH, "")
        assertEquals(listOf("rss_tech_3", "rss_tech_2"), techOnly.map { it.id })

        val repoArticles = MockNewsRepository.getArticles()
        assertTrue(
            repoArticles.zipWithNext().all { (a, b) -> a.publishedAtEpochMillis >= b.publishedAtEpochMillis }
        )
    }

    @Test
    fun `rss parser safely ignores html pages and recovers malformed xml feeds via jsoup fallback`() {
        val source = NewsSource(
            id = "src_broken_test",
            name = "Paywall Daily",
            category = NewsCategory.WORLD,
            feedUrl = "https://example.org/rss",
        )

        val htmlChallenge = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <link rel="stylesheet" href="/app.css">
              <title>Access Denied</title>
            </head>
            <body><p>Please enable JavaScript & cookies.</p></body>
            </html>
        """.trimIndent()
        val htmlParsed = RssFeedParser.parseFeed(htmlChallenge, source)
        assertTrue(htmlParsed.isEmpty())

        // Malformed XML: unescaped & and unclosed HTML tags directly inside <description> without CDATA
        val malformedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Tech & Science Wire</title>
                <item>
                  <title>Chips & Photonics Breakthrough</title>
                  <link>https://example.org/chips-photonics</link>
                  <pubDate>Sat, 14 Nov 2026 18:00:00 +0000</pubDate>
                  <description>Overview with unescaped & ampersand.<br>More engineering details.<img src="https://example.org/lead.jpg"></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val recovered = RssFeedParser.parseFeed(malformedXml, source)
        assertEquals(1, recovered.size)
        assertEquals("Chips & Photonics Breakthrough", recovered[0].headline)
        assertEquals("https://example.org/chips-photonics", recovered[0].articleUrl)
    }

    @Test
    fun `custom source serializer and deserializer handle malformed persisted entries safely`() {
        val corruptSet = setOf(
            "broken_entry_without_pipes",
            "|MissingId|TECH|https://example.com/rss",
            "id_blank_name|   |WORLD|https://example.com/rss",
            "custom_all|My Blog|ALL|https://myblog.dev/feed.xml",
            "custom_all|Duplicate Entry|TECH|https://myblog.dev/other.xml",
            "custom_ok|Valid Feed|SCIENCE|https://quantamagazine.org/feed/",
        )

        val parsed = ThemePreferencesRepository.deserializeCustomSourceEntries(corruptSet)
        assertEquals(2, parsed.size)
        val blog = parsed.first { it.id == "custom_all" }
        assertEquals(NewsCategory.ALL, blog.category)
        val valid = parsed.first { it.id == "custom_ok" }
        assertEquals(NewsCategory.SCIENCE, valid.category)
        assertEquals("https://quantamagazine.org/feed/", valid.feedUrl)
    }

    @Test
    fun `news view model safely normalizes and validates manual sources without crashing`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = NewsViewModel(app)

        // Schemeless domain should normalize to https:// cleanly
        vm.addManualSource("Custom\nWire|Feed", "example.com/rss.xml", NewsCategory.LOCAL)
        assertTrue(vm.uiState.value.snackbarMessage?.contains("Added Custom Wire Feed to Local") == true)

        // Invalid URL should show helpful message rather than crashing or throwing
        vm.addManualSource("Broken Feed", "http://[invalid-host", NewsCategory.WORLD)
        assertEquals("Enter a valid http/https feed URL", vm.uiState.value.snackbarMessage)

        // Toggling curated source should never throw
        vm.toggleSourceEnabled("src_reuters")
    }

    @Test
    fun `feed category detector auto detects local science world and returns null for ambiguous urls`() {
        val nasaRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>NASA Breaking News</title>
                <description>Latest physics, astronomy, and earth observatory updates.</description>
                <category>Science</category>
              </channel>
            </rss>
        """.trimIndent()
        val sciencePreview = FeedCategoryDetector.extractChannelPreview(
            rawBody = nasaRss,
            resolvedUrl = "https://www.nasa.gov/rss/dyn/breaking_news.rss",
        )
        assertTrue(sciencePreview.isValidFeed)
        assertEquals("NASA Breaking News", sciencePreview.channelTitle)
        assertEquals(NewsCategory.SCIENCE, sciencePreview.suggestedCategory)

        val localXml = """
            <rss version="2.0"><channel>
              <title>Metro City Dispatch</title>
              <description>Neighborhood council, downtown transit, and county updates</description>
            </channel></rss>
        """.trimIndent()
        val localPreview = FeedCategoryDetector.extractChannelPreview(
            rawBody = localXml,
            resolvedUrl = "https://metrocity.example/rss",
        )
        assertEquals(NewsCategory.LOCAL, localPreview.suggestedCategory)

        val (worldCat, _) = FeedCategoryDetector.suggestCategoryFromMetadata(
            feedUrl = "https://feeds.bbci.co.uk/news/world/rss.xml",
        )
        assertEquals(NewsCategory.WORLD, worldCat)

        val (ambiguousCat, _) = FeedCategoryDetector.suggestCategoryFromMetadata(
            feedUrl = "https://mygenericblog.org/feed.xml",
        )
        assertNull(ambiguousCat)
    }

    @Test
    fun `addFeed assigns exact selected or custom category and filters strictly without tech bleed`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = NewsViewModel(app)

        vm.addFeed(
            url = "https://cityhall.example/local.xml",
            title = "City Hall Wire",
            category = NewsCategory.LOCAL,
        )
        vm.addFeed(
            url = "https://markets.example/finance.xml",
            title = "Markets Daily",
            category = NewsCategory.ALL,
            customCategory = "Finance",
        )

        val state = vm.uiState.value
        val localSource = state.availableSources.firstOrNull { it.name == "City Hall Wire" }
        val financeSource = state.availableSources.firstOrNull { it.name == "Markets Daily" }
        assertTrue(localSource != null)
        assertEquals(NewsCategory.LOCAL, localSource!!.category)
        assertNull(localSource.cleanCustomCategory)

        assertTrue(financeSource != null)
        assertEquals("Finance", financeSource!!.cleanCustomCategory)

        val localArticle = MockNewsRepository.buildArticleForSource(localSource)
        val financeArticle = MockNewsRepository.buildArticleForSource(financeSource)

        val techFiltered = ArticleFeedAggregator.filterAndSort(
            articles = listOf(localArticle, financeArticle),
            category = NewsCategory.TECH,
            query = "",
        )
        assertTrue(techFiltered.isEmpty())

        val localFiltered = ArticleFeedAggregator.filterAndSort(
            articles = listOf(localArticle, financeArticle),
            category = NewsCategory.LOCAL,
            query = "",
        )
        assertEquals(listOf(localArticle.id), localFiltered.map { it.id })

        val financeFiltered = ArticleFeedAggregator.filterAndSort(
            articles = listOf(localArticle, financeArticle),
            category = NewsCategory.ALL,
            query = "",
            selectedCustomCategory = "Finance",
        )
        assertEquals(listOf(financeArticle.id), financeFiltered.map { it.id })

        // Reassigning category moves the source immediately
        vm.reassignSourceCategory(localSource.id, NewsCategory.SCIENCE, null)
        val movedSource = vm.uiState.value.availableSources.first { it.id == localSource.id }
        assertEquals(NewsCategory.SCIENCE, movedSource.category)
    }

    @Test
    fun `add feed dialog requires category selection before saving`() {
        var savedTuple: List<Any?>? = null

        composeRule.setContent {
            MaterialTheme {
                AddFeedDialog(
                    customCategories = listOf("Finance"),
                    onPreviewFeed = { url -> FeedChannelPreview(resolvedUrl = url) },
                    onCreateCustomCategory = {},
                    onSaveFeed = { url, title, cat, customCat ->
                        savedTuple = listOf(url, title, cat, customCat)
                    },
                    onDismiss = {},
                )
            }
        }

        // Save starts disabled when no category is selected
        composeRule.onNodeWithTag("save_manual_source_button").assertIsNotEnabled()

        // Ambiguous URL keeps save disabled until a category is picked
        composeRule.onNodeWithTag("manual_source_url_input")
            .performTextInput("https://myblog.org/feed.xml")
        composeRule.onNodeWithTag("manual_source_name_input")
            .performTextInput("My Blog")
        composeRule.onNodeWithTag("save_manual_source_button").assertIsNotEnabled()

        // User can select Local explicitly (never defaults to Tech) and save
        composeRule.onNodeWithTag("add_feed_category_builtin_LOCAL")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_manual_source_button").assertIsEnabled()
        composeRule.onNodeWithTag("save_manual_source_button").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("https://myblog.org/feed.xml", "My Blog", NewsCategory.LOCAL, null),
            savedTuple,
        )
    }

    @Test
    fun `add feed dialog smart auto detects science url and allows user override`() {
        var savedTuple: List<Any?>? = null

        composeRule.setContent {
            MaterialTheme {
                AddFeedDialog(
                    customCategories = listOf("Finance"),
                    onPreviewFeed = { url ->
                        FeedChannelPreview(
                            resolvedUrl = url,
                            channelTitle = "NASA Breaking News",
                            suggestedCategory = NewsCategory.SCIENCE,
                            detectionReason = "Matched \"nasa\" in feed details",
                            isValidFeed = true,
                        )
                    },
                    onCreateCustomCategory = {},
                    onSaveFeed = { url, title, cat, customCat ->
                        savedTuple = listOf(url, title, cat, customCat)
                    },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("manual_source_url_input")
            .performTextInput("https://www.nasa.gov/rss/dyn/breaking_news.rss")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auto_detected_category_banner")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("save_manual_source_button").assertIsEnabled()

        // Override auto-detected Science with World
        composeRule.onNodeWithTag("add_feed_category_builtin_WORLD")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("manual_source_name_input")
            .performScrollTo()
            .performTextReplacement("NASA Custom Title")
        composeRule.onNodeWithTag("save_manual_source_button").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("https://www.nasa.gov/rss/dyn/breaking_news.rss", "NASA Custom Title", NewsCategory.WORLD, null),
            savedTuple,
        )
    }

    @Test
    fun `manage feeds displays category badge and allows reassigning to custom category`() {
        var reassignedTuple: List<Any?>? = null

        val testSource = NewsSource(
            id = "src_test_local",
            name = "Bayfront Dispatch",
            category = NewsCategory.LOCAL,
            feedUrl = "https://bayfront.example/rss.xml",
            description = "Neighborhood news",
            isCustom = true,
        )

        composeRule.setContent {
            MaterialTheme {
                SourcesAndFeedsCard(
                    availableSources = listOf(testSource),
                    enabledSourceIds = setOf(testSource.id),
                    customCategories = listOf("Finance"),
                    onToggleSourceEnabled = {},
                    onPreviewFeed = { FeedChannelPreview(it) },
                    onAddManualSource = { _, _, _, _ -> },
                    onReassignSourceCategory = { id, cat, customCat ->
                        reassignedTuple = listOf(id, cat, customCat)
                    },
                    onCreateCustomCategory = {},
                    onRemoveCustomSource = {},
                )
            }
        }

        composeRule.onNodeWithTag("source_category_badge_src_test_local").assertIsDisplayed()
        composeRule.onNodeWithTag("source_category_badge_src_test_local").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reassign_category_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("reassign_category_custom_finance")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("confirm_reassign_category_button").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("src_test_local", NewsCategory.ALL, "Finance"),
            reassignedTuple,
        )
    }

    private fun buildSampleFeedArticles(count: Int): List<NewsArticle> {
        val base = MockNewsRepository.getArticles()
        return List(count) { idx ->
            val template = base[idx % base.size]
            template.copy(
                id = "feed_test_$idx",
                headline = "Story #${idx + 1}: ${template.headline}",
                publishedAtEpochMillis = 1_760_000_000_000L - idx * 60_000L,
            )
        }
    }

    @Test
    fun `quick scrollbar scrubs down long feed and updates saved scroll index`() {
        val sampleArticles = buildSampleFeedArticles(18)
        var lastSavedIndex = 0

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(360.dp, 640.dp)) {
                    NewsFeedPane(
                        articles = sampleArticles,
                        nowBriefArticles = emptyList(),
                        selectedCategory = NewsCategory.ALL,
                        searchQuery = "",
                        isSearchExpanded = false,
                        selectedArticleId = null,
                        highlightSelection = false,
                        bookmarkedIds = emptySet(),
                        offlineSavedIds = emptySet(),
                        themeSettings = ThemeSettings(enableNowBrief = false),
                        audioState = AudioPlaybackState(),
                        initialScrollIndex = 0,
                        initialScrollOffset = 0,
                        bottomContentPadding = 16.dp,
                        onSaveScrollPosition = { idx, _ -> lastSavedIndex = idx },
                        onSelectCategory = {},
                        onToggleSearch = {},
                        onSearchQueryChange = {},
                        onArticleClick = {},
                        onToggleBookmark = {},
                        onPlayArticleAudio = {},
                        onPlayBriefAudio = {},
                        onToggleBriefPlayPause = {},
                        onSaveAllBriefOffline = {},
                        onToggleBriefTopic = {},
                        applyStatusBarPadding = false,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_quick_scrollbar").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_quick_scrollbar").performTouchInput {
            swipeDown(
                startY = height * 0.10f,
                endY = height * 0.85f,
                durationMillis = 250L,
            )
        }
        composeRule.waitForIdle()

        assertTrue("Expected quick scrollbar scrub to advance beyond item 0, got $lastSavedIndex", lastSavedIndex > 0)
    }

    @Test
    fun `scrolling down feed reveals back to top button and clicking returns to top`() {
        val sampleArticles = buildSampleFeedArticles(18)
        var lastSavedIndex = 0
        var lastSavedOffset = 0

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(360.dp, 640.dp)) {
                    NewsFeedPane(
                        articles = sampleArticles,
                        nowBriefArticles = emptyList(),
                        selectedCategory = NewsCategory.ALL,
                        searchQuery = "",
                        isSearchExpanded = false,
                        selectedArticleId = null,
                        highlightSelection = false,
                        bookmarkedIds = emptySet(),
                        offlineSavedIds = emptySet(),
                        themeSettings = ThemeSettings(enableNowBrief = false),
                        audioState = AudioPlaybackState(),
                        initialScrollIndex = 0,
                        initialScrollOffset = 0,
                        bottomContentPadding = 16.dp,
                        onSaveScrollPosition = { idx, off ->
                            lastSavedIndex = idx
                            lastSavedOffset = off
                        },
                        onSelectCategory = {},
                        onToggleSearch = {},
                        onSearchQueryChange = {},
                        onArticleClick = {},
                        onToggleBookmark = {},
                        onPlayArticleAudio = {},
                        onPlayBriefAudio = {},
                        onToggleBriefPlayPause = {},
                        onSaveAllBriefOffline = {},
                        onToggleBriefTopic = {},
                        applyStatusBarPadding = false,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_scroll_to_top_button").assertDoesNotExist()

        composeRule.onNodeWithTag("news_feed_list").performScrollToIndex(10)
        composeRule.waitForIdle()

        assertTrue("Expected feed to scroll past item 0", lastSavedIndex > 0)
        composeRule.onNodeWithTag("feed_scroll_to_top_button").assertIsDisplayed()

        composeRule.onNodeWithTag("feed_scroll_to_top_button").performClick()
        composeRule.waitForIdle()

        assertEquals(0, lastSavedIndex)
        assertEquals(0, lastSavedOffset)
        composeRule.onNodeWithTag("feed_scroll_to_top_button").assertDoesNotExist()
    }

    @Test
    fun `quick scrollbar and back to top stay hidden when feed has single story`() {
        val singleArticle = buildSampleFeedArticles(1)

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(360.dp, 640.dp)) {
                    NewsFeedPane(
                        articles = singleArticle,
                        nowBriefArticles = emptyList(),
                        selectedCategory = NewsCategory.ALL,
                        searchQuery = "",
                        isSearchExpanded = false,
                        selectedArticleId = null,
                        highlightSelection = false,
                        bookmarkedIds = emptySet(),
                        offlineSavedIds = emptySet(),
                        themeSettings = ThemeSettings(enableNowBrief = false),
                        audioState = AudioPlaybackState(),
                        initialScrollIndex = 0,
                        initialScrollOffset = 0,
                        bottomContentPadding = 16.dp,
                        onSaveScrollPosition = { _, _ -> },
                        onSelectCategory = {},
                        onToggleSearch = {},
                        onSearchQueryChange = {},
                        onArticleClick = {},
                        onToggleBookmark = {},
                        onPlayArticleAudio = {},
                        onPlayBriefAudio = {},
                        onToggleBriefPlayPause = {},
                        onSaveAllBriefOffline = {},
                        onToggleBriefTopic = {},
                        applyStatusBarPadding = false,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_quick_scrollbar").assertDoesNotExist()
        composeRule.onNodeWithTag("feed_scroll_to_top_button").assertDoesNotExist()
    }

    @Test
    fun `floating navigation bar hides while scrolling feed and returns after scroll stops`() {
        val sampleArticles = buildSampleFeedArticles(18)

        composeRule.setContent {
            MaterialTheme {
                var isScrolling by remember { mutableStateOf(false) }
                var hideNav by remember { mutableStateOf(false) }

                LaunchedEffect(isScrolling) {
                    if (isScrolling) {
                        hideNav = true
                    } else {
                        kotlinx.coroutines.delay(480L)
                        hideNav = false
                    }
                }

                Box(modifier = Modifier.size(360.dp, 640.dp)) {
                    NewsFeedPane(
                        articles = sampleArticles,
                        nowBriefArticles = emptyList(),
                        selectedCategory = NewsCategory.ALL,
                        searchQuery = "",
                        isSearchExpanded = false,
                        selectedArticleId = null,
                        highlightSelection = false,
                        bookmarkedIds = emptySet(),
                        offlineSavedIds = emptySet(),
                        themeSettings = ThemeSettings(enableNowBrief = false),
                        audioState = AudioPlaybackState(),
                        initialScrollIndex = 0,
                        initialScrollOffset = 0,
                        bottomContentPadding = 94.dp,
                        onSaveScrollPosition = { _, _ -> },
                        onSelectCategory = {},
                        onToggleSearch = {},
                        onSearchQueryChange = {},
                        onArticleClick = {},
                        onToggleBookmark = {},
                        onPlayArticleAudio = {},
                        onPlayBriefAudio = {},
                        onToggleBriefPlayPause = {},
                        onSaveAllBriefOffline = {},
                        onToggleBriefTopic = {},
                        onScrollingChange = { isScrolling = it },
                        applyStatusBarPadding = false,
                    )

                    AnimatedVisibility(
                        visible = !hideNav,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        FloatingBottomNavBar(
                            activeDestination = AppDestination.FEED,
                            onSelectDestination = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_bottom_nav_bar").assertIsDisplayed()

        // Begin dragging the feed list in the upper half (clear of FloatingBottomNavBar) and hold pointer down
        composeRule.onNodeWithTag("news_feed_list").performTouchInput {
            down(Offset(width / 2f, height * 0.45f))
            moveBy(Offset(0f, -height * 0.30f))
        }
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("floating_bottom_nav_bar").assertDoesNotExist()

        // Release pointer and advance past settle delay + enter animation
        composeRule.onNodeWithTag("news_feed_list").performTouchInput {
            up()
        }
        composeRule.mainClock.advanceTimeBy(1200L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("floating_bottom_nav_bar").assertIsDisplayed()
    }
}
