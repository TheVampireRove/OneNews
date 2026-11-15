package com.example.data

import com.example.R
import com.example.model.EventMetadata
import com.example.model.LocationMetadata
import com.example.model.LocalLocationPreset
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.NewsSource

import kotlin.math.abs

object MockNewsRepository {

    private val repositoryAnchorMillis: Long = System.currentTimeMillis()
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L

    fun getArticles(): List<NewsArticle> = articles.sortedByDescending { it.publishedAtEpochMillis }

    fun getArticleById(id: String): NewsArticle? = articles.find { it.id == id }

    fun getCuratedSources(): List<NewsSource> = curatedSources

    fun getLocalPresets(): List<LocalLocationPreset> = localPresets

    fun getLocalizedArticlesForArea(
        areaName: String,
        radiusMiles: Int,
        nowMillis: Long = repositoryAnchorMillis,
    ): List<NewsArticle> {
        val cleanArea = areaName.trim().ifBlank { "San Francisco, CA" }
        val matchingPreset = localPresets.find {
            it.displayName.equals(cleanArea, ignoreCase = true) ||
                it.cityShort.equals(cleanArea, ignoreCase = true)
        }
        val city = matchingPreset?.cityShort ?: cleanArea.substringBefore(",").trim().ifBlank { cleanArea }
        val transitCorridor = matchingPreset?.transitCorridor ?: "$city Metro Transit Corridor"
        val landmark = matchingPreset?.landmarkName ?: "$city Civic Plaza & Waterfront"
        val address = matchingPreset?.landmarkAddress ?: "Downtown $cleanArea"
        val lat = matchingPreset?.latitude ?: 37.7749
        val lon = matchingPreset?.longitude ?: -122.4194

        val ts06 = nowMillis - 45L * MINUTE_MS
        val ts10 = nowMillis - 2L * HOUR_MS
        val tsTransit = nowMillis - 3L * HOUR_MS

        return listOf(
            NewsArticle(
                id = "art_06",
                headline = "$city Waterfront Night Market Expands With Zero-Waste Solar Kiosks and Late Transit Loop",
                subtitle = "Forty-eight local kitchens and craft studios within $radiusMiles mi of $cleanArea join the weekend promenade.",
                source = "Bayfront Local",
                timestamp = RssFeedParser.formatRelativeTimestamp(ts06, nowMillis),
                publishedAtEpochMillis = ts06,
                category = NewsCategory.LOCAL,
                readingTimeMinutes = 3,
                hasAudioBrief = true,
                audioDurationSeconds = 85,
                audioSummary = "Local $city update: The $landmark twilight market opens 30 new solar-powered stalls tonight with 10-minute electric shuttle service.",
                imageResId = R.drawable.img_news_world,
                isFeaturedForBrief = true,
                locationMetadata = LocationMetadata(
                    venueName = landmark,
                    address = address,
                    latitude = lat,
                    longitude = lon,
                ),
                eventMetadata = EventMetadata(
                    title = "$city Twilight Night Market",
                    dateTimeLabel = "Tonight • 5:30 PM – 11:30 PM",
                    startTimeMillis = 1794706200000L,
                    endTimeMillis = 1794722400000L,
                    locationLabel = landmark,
                    description = "48 local culinary vendors, reusable steel dishware stations, and live neighborhood acoustics in $cleanArea.",
                ),
                paragraphs = listOf(
                    "Starting this weekend across $cleanArea, $landmark will stay illuminated until 11:30 PM as thirty new neighborhood kitchens join the expanded Zero-Waste Night Market.",
                    "Every vendor operates off modular solar battery carts and serves meals exclusively on deposit-free stainless trays collected at community wash hubs within a $radiusMiles-mile local radius.",
                    "To support evening visitors, $transitCorridor added a 10-minute electric shuttle loop connecting neighborhood stations directly to the waterfront plaza.",
                ),
            ),
            NewsArticle(
                id = "art_10",
                headline = "$city Conservatory Announces Free Rooftop Botanical Walks Every Saturday This Autumn",
                subtitle = "Native flora and nocturnal pollinator gardens open above the $city civic library terrace.",
                source = "Civic Chronicle",
                timestamp = RssFeedParser.formatRelativeTimestamp(ts10, nowMillis),
                publishedAtEpochMillis = ts10,
                category = NewsCategory.LOCAL,
                readingTimeMinutes = 3,
                hasAudioBrief = false,
                audioDurationSeconds = 0,
                audioSummary = "",
                imageResId = R.drawable.img_news_culture,
                locationMetadata = LocationMetadata(
                    venueName = "$city Civic Conservatory Terrace",
                    address = address,
                    latitude = lat + 0.004,
                    longitude = lon - 0.005,
                ),
                eventMetadata = EventMetadata(
                    title = "$city Saturday Rooftop Botanical Walk",
                    dateTimeLabel = "This Saturday • 10:30 AM",
                    startTimeMillis = 1794681000000L,
                    endTimeMillis = 1794686400000L,
                    locationLabel = "$city Civic Conservatory Terrace",
                    description = "Guided 45-minute rooftop walk exploring regional perennials in $cleanArea.",
                ),
                paragraphs = listOf(
                    "Perched six stories above downtown $city, a once-closed maintenance roof has blossomed into a 14,000-square-foot sanctuary of drought-resilient grasses and native pollinator beds.",
                    "Horticulturists serving the $cleanArea ($radiusMiles-mile metro zone) will lead free 45-minute walks every Saturday morning, sharing propagation tips for apartment balconies and urban courtyards.",
                ),
            ),
            NewsArticle(
                id = "art_local_transit",
                headline = "$transitCorridor Completes Weekend Signal Upgrade for Faster Cross-Town Service",
                subtitle = "Real-time platform arrival boards and quiet electric coaches launch across $cleanArea.",
                source = "Metro Transit Journal",
                timestamp = RssFeedParser.formatRelativeTimestamp(tsTransit, nowMillis),
                publishedAtEpochMillis = tsTransit,
                category = NewsCategory.LOCAL,
                readingTimeMinutes = 4,
                hasAudioBrief = true,
                audioDurationSeconds = 80,
                audioSummary = "Regional transit brief for $city: Headways tighten to 6 minutes during peak weekend hours following overnight positive-train-control cutover.",
                imageResId = R.drawable.img_news_world,
                locationMetadata = LocationMetadata(
                    venueName = "$city Central Transit Hub",
                    address = address,
                    latitude = lat - 0.003,
                    longitude = lon + 0.004,
                ),
                paragraphs = listOf(
                    "Commuters across $cleanArea saw six-minute peak headways go live Saturday morning after overnight crews completed software cutovers along the $transitCorridor.",
                    "Transport engineers say the modernized telemetry cuts platform dwell times by 22 percent across stops within $radiusMiles miles of the urban core, improving reliability for evening market and concert crowds.",
                ),
            ),
        )
    }

    fun buildArticleForSource(
        source: NewsSource,
        nowMillis: Long = repositoryAnchorMillis,
    ): NewsArticle {
        val headline = when {
            source.cleanCustomCategory != null -> "${source.name}: Top ${source.cleanCustomCategory} Briefing"
            source.category == NewsCategory.TECH -> "${source.name}: Next-Gen Foldable Software Stack Reaches Production Milestone"
            source.category == NewsCategory.WORLD -> "${source.name}: Cross-Border Clean Grid Corridors Expand Ahead of Winter"
            source.category == NewsCategory.LOCAL -> "${source.name}: Weekend Transit & Civic Plaza Schedule Announced"
            source.category == NewsCategory.SCIENCE -> "${source.name}: High-Resolution Earth Observation Constellation Calibrated"
            source.category == NewsCategory.CULTURE -> "${source.name}: Autumn Architectural & Sound Installations Open Citywide"
            else -> "${source.name}: Top Daily Briefing"
        }
        val cleanSubtitle = source.description.trim().takeIf { it.isNotBlank() }?.let {
            if (it.endsWith(".")) it else "$it."
        } ?: "Reporting and regional analysis across ${source.assignedCategoryLabel.lowercase()}."
        val safeArticleUrl = source.feedUrl.takeIf {
            it.startsWith("http", ignoreCase = true) && !it.contains(".example/")
        }
        val ageMinutes = 25L + (abs(source.id.hashCode()) % 180).toLong()
        val publishedMillis = nowMillis - ageMinutes * MINUTE_MS
        return NewsArticle(
            id = "src_art_${source.id}",
            headline = headline,
            subtitle = cleanSubtitle,
            source = source.name,
            timestamp = RssFeedParser.formatRelativeTimestamp(publishedMillis, nowMillis),
            publishedAtEpochMillis = publishedMillis,
            category = source.category,
            readingTimeMinutes = 4,
            hasAudioBrief = true,
            audioDurationSeconds = 85,
            audioSummary = "Latest dispatch from ${source.name} covering ${source.assignedCategoryLabel.lowercase()} developments.",
            imageResId = null,
            imageUrl = null,
            articleUrl = safeArticleUrl,
            paragraphs = listOf(
                "Correspondents at ${source.name} report accelerating investment in resilient regional infrastructure, adaptive consumer hardware, and high-contrast digital publishing tools.",
                "Engineers and policy analysts reviewed field telemetry released Friday, noting measurable gains in reliability during peak commuter and evening network loads.",
                "Regional teams expect initial rollouts to expand steadily through next quarter as open interoperability standards reach consumer devices and transit hubs.",
            ),
            isFullTextExtracted = true,
            customCategory = source.cleanCustomCategory,
        )
    }

    private val localPresets: List<LocalLocationPreset> = listOf(
        LocalLocationPreset(
            id = "loc_sf",
            displayName = "San Francisco, CA",
            cityShort = "San Francisco",
            transitCorridor = "Embarcadero & Muni Metro",
            landmarkName = "Embarcadero Ferry Plaza Promenade",
            landmarkAddress = "1 Ferry Building, San Francisco, CA",
            latitude = 37.7955,
            longitude = -122.3937,
        ),
        LocalLocationPreset(
            id = "loc_seattle",
            displayName = "Seattle, WA",
            cityShort = "Seattle",
            transitCorridor = "Sound Transit Link Light Rail",
            landmarkName = "Pike Place & Waterfront Overlook",
            landmarkAddress = "85 Pike St, Seattle, WA",
            latitude = 47.6097,
            longitude = -122.3422,
        ),
        LocalLocationPreset(
            id = "loc_nyc",
            displayName = "New York, NY",
            cityShort = "New York",
            transitCorridor = "Hudson River Greenway & MTA",
            landmarkName = "Bryant Park & Midtown Cultural Commons",
            landmarkAddress = "42nd St & 6th Ave, New York, NY",
            latitude = 40.7536,
            longitude = -73.9832,
        ),
        LocalLocationPreset(
            id = "loc_austin",
            displayName = "Austin, TX",
            cityShort = "Austin",
            transitCorridor = "CapMetro Red Line",
            landmarkName = "Lady Bird Lake Boardwalk Pavilion",
            landmarkAddress = "1820 S Lakeshore Blvd, Austin, TX",
            latitude = 30.2500,
            longitude = -97.7370,
        ),
        LocalLocationPreset(
            id = "loc_chicago",
            displayName = "Chicago, IL",
            cityShort = "Chicago",
            transitCorridor = "CTA Loop & Riverwalk",
            landmarkName = "Millennium Park Civic Pavilion",
            landmarkAddress = "201 E Randolph St, Chicago, IL",
            latitude = 41.8826,
            longitude = -87.6226,
        ),
        LocalLocationPreset(
            id = "loc_london",
            displayName = "London, UK",
            cityShort = "London",
            transitCorridor = "Elizabeth Line & Thames Clipper",
            landmarkName = "Southbank Centre River Terrace",
            landmarkAddress = "Belvedere Rd, London SE1 8XX, UK",
            latitude = 51.5058,
            longitude = -0.1168,
        ),
        LocalLocationPreset(
            id = "loc_seoul",
            displayName = "Seoul, KR",
            cityShort = "Seoul",
            transitCorridor = "Cheonggyecheon & GTX-A",
            landmarkName = "Dongdaemun Design Plaza Commons",
            landmarkAddress = "281 Eulji-ro, Jung-gu, Seoul",
            latitude = 37.5665,
            longitude = 127.0092,
        ),
        LocalLocationPreset(
            id = "loc_tokyo",
            displayName = "Tokyo, JP",
            cityShort = "Tokyo",
            transitCorridor = "Yamanote & Marunouchi Corridor",
            landmarkName = "Marunouchi Brick Square Plaza",
            landmarkAddress = "2-6-1 Marunouchi, Chiyoda-ku, Tokyo",
            latitude = 35.6788,
            longitude = 139.7634,
        ),
    )

    private val curatedSources: List<NewsSource> = listOf(
        // Tech
        NewsSource("src_verge", "The Verge", NewsCategory.TECH, "https://www.theverge.com/rss/index.xml", "Consumer hardware & digital culture", isDefaultEnabled = true),
        NewsSource("src_ars", "Ars Technica", NewsCategory.TECH, "https://feeds.arstechnica.com/arstechnica/index", "Deep-dive technology & computing", isDefaultEnabled = true),
        NewsSource("src_seoul_tech", "Seoul Tech Wire", NewsCategory.TECH, "https://seoultechwire.example/rss", "Semiconductors, foldables & silicon", isDefaultEnabled = true),
        NewsSource("src_pacific_design", "Pacific Design Review", NewsCategory.TECH, "https://pacificdesign.example/feed", "Hardware UX & industrial craft", isDefaultEnabled = true),
        NewsSource("src_volt_circuit", "Volt & Circuit", NewsCategory.TECH, "https://voltcircuit.example/rss", "Battery chemistry & EV engineering", isDefaultEnabled = true),
        NewsSource("src_wired", "Wired", NewsCategory.TECH, "https://www.wired.com/feed/rss", "Emerging tech, AI & security", isDefaultEnabled = false),
        NewsSource("src_techcrunch", "TechCrunch", NewsCategory.TECH, "https://techcrunch.com/feed/", "Startups, chips & mobile platforms", isDefaultEnabled = false),
        NewsSource("src_9to5google", "9to5Google", NewsCategory.TECH, "https://9to5google.com/feed/", "Android, Pixel & Fold ecosystem", isDefaultEnabled = false),
        NewsSource("src_mit_tr", "MIT Technology Review", NewsCategory.TECH, "https://www.technologyreview.com/feed/", "Research breakthroughs & computing", isDefaultEnabled = false),
        NewsSource("src_ieee", "IEEE Spectrum", NewsCategory.TECH, "https://spectrum.ieee.org/feeds/feed.rss", "Electrical engineering & robotics", isDefaultEnabled = false),
        // World
        NewsSource("src_npr", "NPR News", NewsCategory.WORLD, "https://feeds.npr.org/1001/rss.xml", "Public radio national & world dispatches", isDefaultEnabled = true),
        NewsSource("src_bbc", "BBC Global", NewsCategory.WORLD, "https://feeds.bbci.co.uk/news/world/rss.xml", "International reporting & analysis", isDefaultEnabled = true),
        NewsSource("src_global_dispatch", "Global Dispatch", NewsCategory.WORLD, "https://globaldispatch.example/rss", "International transit & climate policy", isDefaultEnabled = true),
        NewsSource("src_continental", "Continental Ledger", NewsCategory.WORLD, "https://continentalledger.example/feed", "European energy & macroeconomics", isDefaultEnabled = true),
        NewsSource("src_reuters", "Reuters World", NewsCategory.WORLD, "https://www.reuters.com/world/rss", "Breaking global wire dispatches", isDefaultEnabled = false),
        NewsSource("src_ap", "Associated Press (AP)", NewsCategory.WORLD, "https://apnews.com/hub/world-news.rss", "Fact-checked global wire reporting", isDefaultEnabled = false),
        NewsSource("src_economist", "The Economist", NewsCategory.WORLD, "https://www.economist.com/international/rss.xml", "Geopolitics, markets & policy", isDefaultEnabled = false),
        NewsSource("src_ft", "Financial Times", NewsCategory.WORLD, "https://www.ft.com/world?format=rss", "Global finance, trade & diplomacy", isDefaultEnabled = false),
        NewsSource("src_nikkei", "Nikkei Asia", NewsCategory.WORLD, "https://asia.nikkei.com/rss/feed/nar", "Asia-Pacific economies & supply chains", isDefaultEnabled = false),
        // Local
        NewsSource("src_bayfront", "Bayfront Local", NewsCategory.LOCAL, "https://bayfrontlocal.example/rss", "Neighborhood markets & city transit", isDefaultEnabled = true),
        NewsSource("src_civic", "Civic Chronicle", NewsCategory.LOCAL, "https://civicchronicle.example/feed", "Parks, libraries & community walks", isDefaultEnabled = true),
        NewsSource("src_metro", "Metro Transit Journal", NewsCategory.LOCAL, "https://metrotransit.example/rss", "Regional rail & waterfront corridors", isDefaultEnabled = true),
        NewsSource("src_citylab", "Bloomberg CityLab", NewsCategory.LOCAL, "https://www.bloomberg.com/citylab/rss", "Urban planning & civic transit", isDefaultEnabled = false),
        NewsSource("src_npr_local", "NPR Science & Cities", NewsCategory.LOCAL, "https://feeds.npr.org/1007/rss.xml", "Regional policy, schools & science", isDefaultEnabled = false),
        NewsSource("src_patch", "Neighborhood Patch", NewsCategory.LOCAL, "https://patch.example/local-feed.xml", "Hyperlocal events & street closures", isDefaultEnabled = false),
        NewsSource("src_downtown_biz", "Metro Business & Civic", NewsCategory.LOCAL, "https://metrobiz.example/rss", "Regional openings & housing policy", isDefaultEnabled = false),
        // Science
        NewsSource("src_horizon", "Horizon Science", NewsCategory.SCIENCE, "https://horizonscience.example/rss", "Astrophysics & planetary missions", isDefaultEnabled = true),
        NewsSource("src_polar", "Polar Meridian", NewsCategory.SCIENCE, "https://polarmeridian.example/feed", "Oceanography & cryosphere telemetry", isDefaultEnabled = true),
        NewsSource("src_space_weather", "Space Weather Bureau", NewsCategory.SCIENCE, "https://spaceweather.example/alerts.xml", "Geomagnetic & solar forecasts", isDefaultEnabled = true),
        NewsSource("src_quanta", "Quanta Magazine", NewsCategory.SCIENCE, "https://api.quantamagazine.org/feed/", "Mathematics, physics & biology", isDefaultEnabled = false),
        NewsSource("src_nature", "Nature Briefing", NewsCategory.SCIENCE, "https://www.nature.com/nature.rss", "Peer-reviewed breakthroughs daily", isDefaultEnabled = false),
        NewsSource("src_sciam", "Scientific American", NewsCategory.SCIENCE, "https://www.scientificamerican.com/feed/", "Earth science, health & space", isDefaultEnabled = false),
        NewsSource("src_nasa", "NASA Earth Observatory", NewsCategory.SCIENCE, "https://earthobservatory.nasa.gov/feeds/earth-observatory.rss", "Satellite imagery & climate telemetry", isDefaultEnabled = false),
        // Culture
        NewsSource("src_atelier", "Atelier Quarterly", NewsCategory.CULTURE, "https://atelierquarterly.example/rss", "Architecture & kinetic light art", isDefaultEnabled = true),
        NewsSource("src_sound_folio", "Sound & Folio", NewsCategory.CULTURE, "https://soundfolio.example/feed", "Analog acoustics & print craft", isDefaultEnabled = true),
        NewsSource("src_newyorker", "The New Yorker Arts", NewsCategory.CULTURE, "https://www.newyorker.com/feed/culture", "Essays, cinema & museum criticism", isDefaultEnabled = false),
        NewsSource("src_arch_digest", "Architectural Digest", NewsCategory.CULTURE, "https://www.architecturaldigest.com/feed/rss", "Spatial design & civic architecture", isDefaultEnabled = false),
        NewsSource("src_pitchfork", "Pitchfork", NewsCategory.CULTURE, "https://pitchfork.com/feed/feed-news/rss", "Independent music, vinyl & tours", isDefaultEnabled = false),
        NewsSource("src_atlantic_culture", "The Atlantic Culture", NewsCategory.CULTURE, "https://www.theatlantic.com/feed/channel/entertainment/", "Books, film & cultural essays", isDefaultEnabled = false),
    )

    private val articles: List<NewsArticle> = listOf(
        NewsArticle(
            id = "art_01",
            headline = "Exynos Quantum Grid Goes Live",
            subtitle = "Sub-2nm neural fabric reaches commercial fab readiness ahead of Q1 flagship cycle.",
            source = "Seoul Tech Wire",
            timestamp = "12m ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 12L * MINUTE_MS,
            category = NewsCategory.TECH,
            readingTimeMinutes = 3,
            hasAudioBrief = true,
            audioDurationSeconds = 95,
            audioSummary = "Breaking tech update: Exynos Quantum Grid has entered commercial production, delivering 34 percent higher NPU efficiency and cooler sustained thermals for foldable devices.",
            imageResId = R.drawable.img_news_tech,
            isBreaking = true,
            isFeaturedForBrief = true,
            eventMetadata = EventMetadata(
                title = "Exynos Developer Architecture Briefing",
                dateTimeLabel = "Nov 18, 2026 • 10:00 AM",
                startTimeMillis = 1795024800000L,
                endTimeMillis = 1795028400000L,
                locationLabel = "Global Livestream / Seoul Hall B",
                description = "Live deep-dive on neural scheduler upgrades, ray-traced UI shaders, and on-device voice synthesis.",
            ),
            paragraphs = listOf(
                "Engineers in Hwaseong flipped the switch on the first commercial Exynos Quantum Grid wafer line early Friday morning, marking a decisive leap in mobile heterogeneous compute density.",
                "Rather than scaling clock speeds aggressively, the new architecture splits matrix workloads across low-voltage photonic interconnects embedded directly above the SRAM cache layer.",
                "Early thermal telemetry from dual-battery foldable prototypes suggests sustained 60fps generative audio processing with under 1.8 watts of package draw—meaning thinner hinge assemblies and longer uninterrupted reading sessions.",
                "Developer documentation and SDK profiling tools will debut during next week's architecture keynote, giving mobile creators access to deterministic frame pacing across cover and unfolded screens.",
            ),
        ),
        NewsArticle(
            id = "art_02",
            headline = "How Foldable Form Factors and Micro-Hinge Acoustics Are Reshaping Long-Form Mobile Journalism Across East Asia",
            subtitle = "Publishers are redesigning column grids, tabletop audio controls, and thumb-zone ergonomics for dual-posture readers.",
            source = "Pacific Design Review",
            timestamp = "41m ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 41L * MINUTE_MS,
            category = NewsCategory.TECH,
            readingTimeMinutes = 6,
            hasAudioBrief = true,
            audioDurationSeconds = 135,
            audioSummary = "In-depth report: East Asian newsrooms are adopting crease-aware layouts and one-handed thumb decks as foldable readership surges 68 percent year over year.",
            imageResId = R.drawable.img_news_tech,
            isFeaturedForBrief = true,
            locationMetadata = LocationMetadata(
                venueName = "Dongdaemun Design Plaza Innovation Hall",
                address = "281 Eulji-ro, Jung-gu, Seoul",
                latitude = 37.5665,
                longitude = 127.0092,
            ),
            eventMetadata = EventMetadata(
                title = "Mobile Typography & Foldable UX Showcase",
                dateTimeLabel = "Nov 21, 2026 • 2:00 PM",
                startTimeMillis = 1795298400000L,
                endTimeMillis = 1795305600000L,
                locationLabel = "Dongdaemun Design Plaza, Seoul",
                description = "Hands-on exhibition featuring adaptive multi-pane newsrooms and OLED ink-saving dark typography.",
            ),
            paragraphs = listOf(
                "For more than a decade, digital newsrooms treated smartphones as narrow vertical tubes—stacking headlines, ad slots, and share bars into an endless single-thumb scroll.",
                "That assumption is rapidly fracturing as book-style foldables become primary reading hardware for commuters in Seoul, Tokyo, and Singapore. Analytics from four major dailies show unfolded sessions lasting nearly three times longer than cover-screen skims.",
                "Editorial designers are responding with asymmetric viewing-versus-interaction splits: generous negative space and bold display typography occupy the upper third of the display, while interactive filters, bookmarking, and audio scrubbers stay anchored near the bottom palm zone.",
                "Just as transformative is tabletop Flex posture. Readers resting a half-opened device on a café table expect headlines and photography to sit cleanly above the hinge crease, turning the lower half into a tactile control console free of accidental palm touches.",
                "Visitors can test several prototype newsroom builds next Saturday at the Dongdaemun Design Plaza Innovation Hall, where hardware acoustic teams will also demonstrate zero-gap titanium hinge dampening.",
            ),
        ),
        NewsArticle(
            id = "art_03",
            headline = "High-Speed Coastal Rail Corridors Cut Regional Transit Emissions by 41% in Pilot Year",
            subtitle = "Electrified waterfront viaducts and dynamic headway scheduling drew 19 million intercity passengers.",
            source = "Global Dispatch",
            timestamp = "1h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 1L * HOUR_MS,
            category = NewsCategory.WORLD,
            readingTimeMinutes = 5,
            hasAudioBrief = true,
            audioDurationSeconds = 110,
            audioSummary = "World infrastructure brief: Coastal electric rail corridors achieved a 41 percent drop in regional transport emissions while cutting intercity travel times in half.",
            imageResId = R.drawable.img_news_world,
            isFeaturedForBrief = true,
            paragraphs = listOf(
                "A consortium of port cities released its first annual audit of the Pacific-Nordic Coastal Rail Initiative on Friday, confirming a 41 percent reduction in short-haul corridor carbon emissions compared with baseline highway and regional flight traffic.",
                "Crucially, ridership surged not solely from top-end speed, but from seamless last-mile ferry and light-rail transfers synchronized down to 90-second platform windows.",
                "Nighttime freight pods share the same regenerative braking substations, feeding surplus kinetic energy back into municipal waterfront microgrids during peak evening demand.",
                "Urban planners from twelve additional coastal metros are already drafting compatible signaling standards ahead of next spring's infrastructure summit.",
            ),
        ),
        NewsArticle(
            id = "art_04",
            headline = "Orbital Telescope Captures First Direct Atmospheric Signature of Temperate Exoplanet K2-419b",
            subtitle = "Spectrographs reveal layered water vapor clouds and methane equilibrium 118 light-years away.",
            source = "Horizon Science",
            timestamp = "2h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 2L * HOUR_MS,
            category = NewsCategory.SCIENCE,
            readingTimeMinutes = 5,
            hasAudioBrief = true,
            audioDurationSeconds = 120,
            audioSummary = "Science brief: Orbital infrared interferometry has mapped persistent cloud bands and water vapor signatures on temperate super-Earth K2-419b.",
            imageResId = R.drawable.img_news_science,
            isFeaturedForBrief = true,
            eventMetadata = EventMetadata(
                title = "Exoplanet Spectroscopy Public Planetarium Briefing",
                dateTimeLabel = "Nov 15, 2026 • 7:30 PM",
                startTimeMillis = 1794799800000L,
                endTimeMillis = 1794805200000L,
                locationLabel = "Morrison Planetarium Dome",
                description = "Lead astrophysicists walk through the infrared spectral graphs of K2-419b.",
            ),
            paragraphs = listOf(
                "Astrophysicists operating the Cryogenic Orbital Array announced the clearest direct transmission spectrum ever recorded from a temperate rocky-ice world outside our solar system.",
                "Located 118 light-years away in the constellation Leo, K2-419b orbits within the conservative habitable zone of an unusually quiet K-dwarf star, sparing the planet's upper atmosphere from violent flare stripping.",
                "By subtracting stellar noise across forty-two consecutive transit orbits, the team isolated distinct absorption bands pointing to high-altitude water vapor and trace nitrogen chemistry.",
                "While researchers caution against premature biosignature claims, the instrument's signal-to-noise clarity validates a new generation of starshade coronagraphs scheduled for launch in 2028.",
            ),
        ),
        NewsArticle(
            id = "art_05",
            headline = "Bioluminescent Architecture: Inside the Twilight Light Biennale Transforming Industrial Piers",
            subtitle = "Sixteen kinetic light sculptures convert tidal motion into synchronized nocturnal color fields.",
            source = "Atelier Quarterly",
            timestamp = "3h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 3L * HOUR_MS,
            category = NewsCategory.CULTURE,
            readingTimeMinutes = 4,
            hasAudioBrief = true,
            audioDurationSeconds = 100,
            audioSummary = "Culture highlight: Sixteen waterfront installations fuse concrete arches with tide-powered optical ribbons at this year's Twilight Light Biennale.",
            imageResId = R.drawable.img_news_culture,
            isFeaturedForBrief = true,
            locationMetadata = LocationMetadata(
                venueName = "Pier 27 Contemporary Arts Pavilion",
                address = "Pier 27 The Embarcadero, San Francisco, CA",
                latitude = 37.8065,
                longitude = -122.4018,
            ),
            eventMetadata = EventMetadata(
                title = "Twilight Light Biennale Opening Night Walk",
                dateTimeLabel = "Nov 14, 2026 • 6:00 PM",
                startTimeMillis = 1794708000000L,
                endTimeMillis = 1794718800000L,
                locationLabel = "Pier 27 Contemporary Arts Pavilion",
                description = "Curator-led evening tour across all sixteen tide-driven optical galleries.",
            ),
            paragraphs = listOf(
                "Beneath vaulted brutalist concrete arches along the waterfront, strands of low-power electroluminescent fiber ripple in real time with the bay's incoming tide.",
                "Curated by architect Min-ji Kwon and sound designer Mateo Alvarez, the Twilight Light Biennale turns decommissioned maritime warehouses into acoustic and chromatic instruments.",
                "Rather than drawing from municipal power lines, submerged turbines beneath the pier generate 100 percent of the exhibition's electricity, dimming and brightening organically as ferry wakes pass nearby.",
                "Admission remains free every evening through December, with guided acoustic walks departing hourly from the main pavilion atrium.",
            ),
        ),
        NewsArticle(
            id = "art_06",
            headline = "Downtown Waterfront Night Market Expands With Zero-Waste Solar Kiosks and Late Transit Loop",
            subtitle = "Forty-eight local kitchens and craft studios join the Friday–Sunday twilight promenade.",
            source = "Bayfront Local",
            timestamp = "3h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - (3L * HOUR_MS + 10L * MINUTE_MS),
            category = NewsCategory.LOCAL,
            readingTimeMinutes = 3,
            hasAudioBrief = false,
            audioDurationSeconds = 0,
            audioSummary = "",
            imageResId = R.drawable.img_news_world,
            locationMetadata = LocationMetadata(
                venueName = "Embarcadero Ferry Plaza Promenade",
                address = "1 Ferry Building, San Francisco, CA",
                latitude = 37.7955,
                longitude = -122.3937,
            ),
            eventMetadata = EventMetadata(
                title = "Waterfront Twilight Night Market",
                dateTimeLabel = "Nov 14, 2026 • 5:30 PM",
                startTimeMillis = 1794706200000L,
                endTimeMillis = 1794722400000L,
                locationLabel = "Embarcadero Ferry Plaza",
                description = "48 local culinary vendors, reusable steel dishware stations, and live acoustic sets.",
            ),
            paragraphs = listOf(
                "Starting this weekend, the Embarcadero Ferry Plaza will stay illuminated until 11:30 PM as thirty new culinary kiosks join the expanded Zero-Waste Night Market.",
                "Every vendor operates off modular solar battery carts and serves meals exclusively on deposit-free stainless trays collected at neighborhood wash hubs.",
                "To support evening visitors, municipal transit authorities added a 10-minute electric shuttle loop connecting the promenade directly to regional rail stations.",
            ),
        ),
        NewsArticle(
            id = "art_07",
            headline = "Solid-State Silicon Anode Cells Reach 80% Charge in Nine Minutes During Winter Fleet Trials",
            subtitle = "Sub-zero endurance testing in northern Scandinavia eliminates cold-weather range cliff.",
            source = "Volt & Circuit",
            timestamp = "4h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 4L * HOUR_MS,
            category = NewsCategory.TECH,
            readingTimeMinutes = 4,
            hasAudioBrief = true,
            audioDurationSeconds = 105,
            audioSummary = "Tech update: New solid-state silicon anode packs survived minus 25 Celsius fleet trials with negligible dendrite formation and 9-minute fast charging.",
            imageResId = R.drawable.img_news_tech,
            paragraphs = listOf(
                "Battery engineers have long wrestled with a stubborn tradeoff: silicon anodes store far more lithium than graphite, yet swell dramatically during rapid sub-zero charging.",
                "A porous ceramic-scaffold architecture tested across 120 delivery vans in northern Lapland appears to have cracked the thermal bottleneck, retaining 96 percent of rated capacity after 1,400 fast-charge cycles.",
                "Consumer electronics divisions are closely watching the chemistry for ultra-thin foldable batteries, where rigid graphite layers currently restrict hinge-side volume.",
            ),
        ),
        NewsArticle(
            id = "art_08",
            headline = "Nordic Grid Interconnectors Balance Offshore Wind Surpluses Across Three Time Zones",
            subtitle = "Subsea HVDC cables route excess North Sea power to alpine pumped-hydro reservoirs in milliseconds.",
            source = "Continental Ledger",
            timestamp = "5h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 5L * HOUR_MS,
            category = NewsCategory.WORLD,
            readingTimeMinutes = 5,
            hasAudioBrief = false,
            audioDurationSeconds = 0,
            audioSummary = "",
            imageResId = R.drawable.img_news_world,
            paragraphs = listOf(
                "When North Sea gale gusts pushed offshore wind generation to a record 38 gigawatts Tuesday night, operators didn't curtail a single turbine.",
                "Instead, automated converter stations routed the surplus instantaneously through a newly commissioned 740-kilometer subsea HVDC link into Norwegian and Alpine pumped-storage caverns.",
                "Energy economists note that continental wholesale price volatility fell by nearly half during the first autumn quarter of synchronized grid operation.",
            ),
        ),
        NewsArticle(
            id = "art_09",
            headline = "Deep-Sea Autonomous Gliders Map Thermal Vents Beneath Antarctic Shelf Ice",
            subtitle = "Acoustic beacons guide buoyancy-driven drones through 900-meter dark water cavities.",
            source = "Polar Meridian",
            timestamp = "6h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 6L * HOUR_MS,
            category = NewsCategory.SCIENCE,
            readingTimeMinutes = 6,
            hasAudioBrief = true,
            audioDurationSeconds = 125,
            audioSummary = "Science dispatch: Buoyancy-driven autonomous gliders completed a four-month sub-ice survey, mapping previously unseen warm currents beneath Antarctic ice shelves.",
            imageResId = R.drawable.img_news_science,
            paragraphs = listOf(
                "Operating entirely without satellite GPS beneath nearly a kilometer of solid glacial ice, three torpedo-shaped autonomous gliders resurfaced this week carrying high-resolution bathymetry maps.",
                "Powered by subtle buoyancy shifts rather than noisy propellers, the gliders drifted along deep thermal plumes for 114 days while navigating off moored acoustic transponders.",
                "Climate modelers say the newly charted seafloor ridges help explain why adjacent glaciers melt at sharply different rates despite sharing identical surface air temperatures.",
            ),
        ),
        NewsArticle(
            id = "art_10",
            headline = "City Conservatory Announces Free Rooftop Botanical Walks Every Saturday This Autumn",
            subtitle = "Native coastal flora and nocturnal pollinator gardens open above the civic library terrace.",
            source = "Civic Chronicle",
            timestamp = "7h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 7L * HOUR_MS,
            category = NewsCategory.LOCAL,
            readingTimeMinutes = 2,
            hasAudioBrief = false,
            audioDurationSeconds = 0,
            audioSummary = "",
            imageResId = R.drawable.img_news_culture,
            locationMetadata = LocationMetadata(
                venueName = "Skyline Civic Conservatory Terrace",
                address = "100 Larkin St, San Francisco, CA",
                latitude = 37.7792,
                longitude = -122.4158,
            ),
            eventMetadata = EventMetadata(
                title = "Saturday Rooftop Botanical Walk",
                dateTimeLabel = "Nov 14, 2026 • 10:30 AM",
                startTimeMillis = 1794681000000L,
                endTimeMillis = 1794686400000L,
                locationLabel = "Skyline Civic Conservatory Terrace",
                description = "Guided 45-minute rooftop walk exploring drought-resilient coastal perennials.",
            ),
            paragraphs = listOf(
                "Perched six stories above Civic Center, a once-closed maintenance roof has blossomed into a 14,000-square-foot sanctuary of fog-catching grasses and native sage.",
                "Botanists will lead free 45-minute walks every Saturday morning, sharing propagation tips for apartment balconies and urban courtyards.",
            ),
        ),
        NewsArticle(
            id = "art_11",
            headline = "Vinyl Mastering Engineers Return to All-Analog Lathes as Physical Audio Sales Hit 18-Year High",
            subtitle = "Independent pressing plants add precision optical groove monitoring to eliminate warps.",
            source = "Sound & Folio",
            timestamp = "8h ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 8L * HOUR_MS,
            category = NewsCategory.CULTURE,
            readingTimeMinutes = 4,
            hasAudioBrief = true,
            audioDurationSeconds = 115,
            audioSummary = "Culture story: Boutique mastering studios are restoring vintage cutting lathes with modern optical feedback sensors as listeners seek tactile album rituals.",
            imageResId = R.drawable.img_news_culture,
            paragraphs = listOf(
                "Inside a sunlit brick loft on the city's eastern edge, mastering engineer Kenji Takahashi adjusts a sapphire stylus under a stereo microscope before lowering it onto a fresh lacquer disc.",
                "Demand for tactile, intentional listening sessions has pushed physical album sales to their highest mark since 2008, prompting engineers to combine 1970s Neumann cutting lathes with non-contact laser pitch computers.",
                "Listeners say putting on an album—much like unfolding a morning paper—restores focus in an era of fragmented notifications.",
            ),
        ),
        NewsArticle(
            id = "art_12",
            headline = "Solar Flare Alert: Auroras Visible Tonight",
            subtitle = "Coronal mass ejection reaches upper magnetosphere around 9:15 PM local time.",
            source = "Space Weather Bureau",
            timestamp = "19m ago",
            publishedAtEpochMillis = repositoryAnchorMillis - 19L * MINUTE_MS,
            category = NewsCategory.SCIENCE,
            readingTimeMinutes = 2,
            hasAudioBrief = true,
            audioDurationSeconds = 75,
            audioSummary = "Breaking skywatch alert: A G3 geomagnetic storm peaks tonight after 9 PM, offering rare mid-latitude aurora visibility away from city streetlights.",
            imageResId = R.drawable.img_news_science,
            isBreaking = true,
            isFeaturedForBrief = true,
            eventMetadata = EventMetadata(
                title = "Geomagnetic Aurora Viewing Window",
                dateTimeLabel = "Tonight • 9:15 PM – 11:45 PM",
                startTimeMillis = 1794633300000L,
                endTimeMillis = 1794642300000L,
                locationLabel = "North-Facing Dark Sky Overlooks",
                description = "Peak G3 aurora oval expansion expected along northern horizons.",
            ),
            paragraphs = listOf(
                "Solar forecasters upgraded tonight's geomagnetic outlook to a strong G3 watch after satellite magnetometers clocked solar wind speeds exceeding 720 kilometers per second.",
                "Observers with clear northern horizons should step outside between 9:15 PM and midnight, allowing ten minutes for night vision to adapt away from porch lights.",
            ),
        ),
    )
}
