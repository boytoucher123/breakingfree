package com.breakingfree.app.chaos

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class Region(val label: String) { AUSTRALIA("AUSTRALIA"), WEST("THE WEST"), WORLD("ELSEWHERE") }

enum class Category(val label: String, val terms: List<String>) {
    WAR("WAR", listOf("war", "warfare", "invasion", "invade", "invaded", "frontline", "front line", "airstrike",
        "airstrikes", "air strike", "missile", "missiles", "shelling", "bombardment", "ceasefire", "drone strike",
        "drone attack", "offensive", "occupation", "annex", "casualties", "killed in strike")),
    MILITARY("MILITARY", listOf("military", "army", "navy", "air force", "defence force", "defense force", "adf",
        "pentagon", "nato", "troops", "soldiers", "warship", "warships", "submarine", "aukus", "marines",
        "deployment", "deployed", "special forces", "conscription", "martial law", "defence minister",
        "defense secretary", "military base", "nuclear weapon", "nuclear weapons")),
    SHOOTING("SHOOTING", listOf("shooting", "shootings", "shot dead", "gunman", "gunmen", "gunfire", "opened fire",
        "shooter", "gunshot", "firearm", "firearms", "mass shooting", "shot and killed", "fatally shot")),
    TERROR("TERROR", listOf("terror", "terrorist", "terrorism", "extremist", "extremism", "bomb", "bombing",
        "explosion", "explosive", "hostage", "hostages", "radicalised", "radicalized", "jihadist", "assassination",
        "assassinated")),
    RIOT("RIOT", listOf("riot", "riots", "rioting", "rioters", "unrest", "civil unrest", "tear gas", "looting",
        "looters", "uprising", "curfew", "clashes", "clashed", "violent protest", "protesters clash", "insurrection",
        "mob", "water cannon", "pepper spray")),
    CRIME("CRIME", listOf("murder", "murdered", "murders", "homicide", "stabbing", "stabbed", "stabbings", "assault",
        "assaulted", "manslaughter", "robbery", "armed robbery", "carjacking", "home invasion", "kidnapping",
        "kidnapped", "abducted", "gang", "gangs", "cartel", "drug bust", "drug syndicate", "machete", "arson",
        "firebombed", "killed", "killing", "charged with", "crime", "crimes", "violence", "violent", "attack",
        "attacked", "manhunt", "fugitive", "trafficking")),
    CORRUPTION("CORRUPTION", listOf("corruption", "corrupt", "bribery", "bribe", "bribes", "embezzlement", "fraud",
        "kickback", "kickbacks", "money laundering", "icac", "anti-corruption", "integrity commission", "nacc",
        "misconduct", "cover-up", "coverup", "rort", "rorts", "scandal", "impeachment", "impeach", "indicted",
        "indictment", "abuse of power", "conflict of interest", "whistleblower", "lobbyist", "slush fund"));
}

private val AU_TERMS = listOf("australia", "australian", "australians", "aussie", "sydney", "melbourne", "brisbane",
    "perth", "adelaide", "hobart", "darwin", "canberra", "gold coast", "newcastle", "wollongong", "geelong",
    "townsville", "cairns", "nsw", "new south wales", "victoria", "victorian", "queensland", "tasmania", "tasmanian",
    "northern territory", "western australia", "south australia", "albanese", "dutton", "ley", "afp", "adf",
    "parliament house", "anzac", "alice springs", "toowoomba", "ballarat", "bendigo", "launceston")

private val WEST_TERMS = listOf("united states", "u.s.", "usa", "america", "american", "americans",
    "washington", "new york", "california", "texas", "florida", "chicago", "los angeles", "trump", "white house",
    "congress", "senate", "fbi", "uk", "britain", "british", "england", "london", "scotland", "wales", "ireland",
    "starmer", "westminster", "canada", "canadian", "toronto", "new zealand", "nz", "auckland", "wellington",
    "europe", "european", "eu", "france", "french", "paris", "germany", "german", "berlin", "italy", "spain",
    "netherlands", "belgium", "sweden", "norway", "denmark", "finland", "poland", "ukraine", "ukrainian", "kyiv",
    "nato", "greece", "portugal", "austria", "switzerland")

private val WORLD_TERMS = listOf("gaza", "israel", "israeli", "palestinian", "hamas", "hezbollah", "lebanon", "iran",
    "iranian", "russia", "russian", "moscow", "putin", "china", "chinese", "beijing", "taiwan", "north korea",
    "south korea", "japan", "india", "pakistan", "afghanistan", "syria", "iraq", "yemen", "houthi", "sudan",
    "congo", "nigeria", "somalia", "ethiopia", "myanmar", "philippines", "indonesia", "mexico", "brazil",
    "venezuela", "colombia", "haiti", "africa", "middle east", "west bank", "saudi", "turkey")

private val SPORT_NOISE = listOf("afl", "nrl", "cricket", "footy", "grand final", "a-league", "premier league",
    "nba", "nfl", "tennis", "wicket", "goalkeeper", "striker", "test match", "rugby", "olympics", "match report",
    "in pictures", "quiz", "recipe")

data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val published: Long?,
    val summary: String,
    val region: Region,
    val categories: List<Category>,
    val intensity: Int,
)

private data class Feed(val url: String, val name: String, val hint: Region, val keepSummary: Boolean = true)

private fun googleNews(query: String, locale: String, country: String, hint: Region): Feed {
    val q = URLEncoder.encode("$query when:2d", "UTF-8")
    return Feed(
        "https://news.google.com/rss/search?q=$q&hl=$locale&gl=$country&ceid=$country:${locale.substringBefore('-')}",
        "Google News $country", hint, keepSummary = false,
    )
}

private const val CHAOS_QUERY =
    "(war OR shooting OR stabbing OR murder OR riot OR corruption OR military OR terror OR attack OR police)"

private val FEEDS = listOf(
    googleNews("australia $CHAOS_QUERY", "en-AU", "AU", Region.AUSTRALIA),
    googleNews("(NSW OR Victoria OR Queensland OR Sydney OR Melbourne) (crime OR police OR charged OR corruption)",
        "en-AU", "AU", Region.AUSTRALIA),
    Feed("https://www.abc.net.au/news/feed/51120/rss.xml", "ABC News", Region.AUSTRALIA),
    Feed("https://www.theguardian.com/australia-news/rss", "Guardian Australia", Region.AUSTRALIA),
    googleNews(CHAOS_QUERY, "en-US", "US", Region.WEST),
    googleNews(CHAOS_QUERY, "en-GB", "GB", Region.WEST),
    Feed("https://feeds.bbci.co.uk/news/uk/rss.xml", "BBC UK", Region.WEST),
    Feed("https://feeds.bbci.co.uk/news/world/rss.xml", "BBC World", Region.WORLD),
    Feed("https://www.theguardian.com/world/rss", "Guardian World", Region.WORLD),
)

private val wordCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
private fun termRegex(term: String): Regex = wordCache.getOrPut(term) {
    Regex("(?<![a-z0-9])" + Regex.escape(term.trim()) + "(?![a-z0-9])")
}

private fun matches(text: String, terms: List<String>): Int =
    terms.count { termRegex(it).containsMatchIn(text) }

object NewsEngine {
    private const val MAX_AGE_MS = 4L * 24 * 3600 * 1000

    suspend fun fetchAll(): List<NewsItem> = coroutineScope {
        val results = FEEDS.map { feed -> async(Dispatchers.IO) { runCatching { fetchFeed(feed) }.getOrNull() } }
            .awaitAll()
        if (results.all { it == null }) throw java.io.IOException("No signal. Every feed went dark.")
        rank(results.filterNotNull().flatten())
    }

    private fun rank(raw: List<NewsItem>): List<NewsItem> {
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        return raw
            .filter { it.published == null || now - it.published < MAX_AGE_MS }
            .sortedWith(compareBy<NewsItem> { it.region.ordinal }.thenByDescending { it.intensity })
            .distinctBy { it.link }
            .filter { seen.add(it.title.lowercase().replace(Regex("[^a-z0-9]"), "").take(60)) }
            .sortedWith(compareBy<NewsItem> { it.region.ordinal }.thenByDescending { it.published ?: 0L })
    }

    private fun fetchFeed(feed: Feed): List<NewsItem> {
        val conn = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) BreakingFree/1.0")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
        }
        try {
            if (conn.responseCode !in 200..299) return emptyList()
            return conn.inputStream.use { parse(it, feed) }
        } finally {
            conn.disconnect()
        }
    }

    private class Raw {
        var title = ""; var link = ""; var date = ""; var description = ""; var source = ""
    }

    private fun parse(input: InputStream, feed: Feed): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val out = ArrayList<NewsItem>()
        var current: Raw? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    if (name == "item" || name == "entry") {
                        current = Raw()
                    } else if (current != null) {
                        when (name) {
                            "title" -> current.title = parser.safeText()
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null) {
                                    if (current.link.isEmpty()) current.link = href
                                } else current.link = parser.safeText()
                            }
                            "pubdate", "published", "updated", "dc:date" ->
                                if (current.date.isEmpty()) current.date = parser.safeText()
                            "description", "summary" -> current.description = parser.safeText()
                            "source" -> current.source = parser.safeText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    if ((name == "item" || name == "entry") && current != null) {
                        classify(current, feed)?.let(out::add)
                        current = null
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun XmlPullParser.safeText(): String = try {
        nextText().trim()
    } catch (e: Exception) {
        ""
    }

    private fun classify(raw: Raw, feed: Feed): NewsItem? {
        var title = clean(raw.title)
        var source = raw.source.ifBlank { feed.name }
        // Google News titles end with " - Publisher"
        if (!feed.keepSummary) {
            val dash = title.lastIndexOf(" - ")
            if (dash > 20) {
                if (raw.source.isBlank()) source = title.substring(dash + 3)
                title = title.substring(0, dash)
            }
        }
        if (title.isBlank() || raw.link.isBlank()) return null
        val summary = if (feed.keepSummary) clean(raw.description).take(220) else ""
        val text = " ${title.lowercase()} ${summary.lowercase()} "

        val cats = Category.entries
            .map { it to matches(text, it.terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        if (cats.isEmpty()) return null
        val hits = cats.sumOf { it.second }
        val noise = matches(text, SPORT_NOISE)
        if (noise > 0 && hits < 3) return null
        // A lone soft word ("attack", "killed", "violence") isn't chaos enough on its own.
        if (cats.size == 1 && cats[0].first == Category.CRIME && hits == 1 &&
            Regex("attack|killed|killing|violence|violent|crime").containsMatchIn(text) &&
            !Regex("police|charged|arrest").containsMatchIn(text)
        ) return null

        val region = when {
            matches(text, AU_TERMS) > 0 -> Region.AUSTRALIA
            matches(text, WEST_TERMS) > 0 -> Region.WEST
            matches(text, WORLD_TERMS) > 0 -> Region.WORLD
            else -> feed.hint
        }
        return NewsItem(
            title = title,
            link = raw.link.trim(),
            source = source,
            published = parseDate(raw.date),
            summary = summary,
            region = region,
            categories = cats.map { it.first }.take(3),
            intensity = hits,
        )
    }

    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('￼', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun parseDate(s: String): Long? {
        if (s.isBlank()) return null
        return runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching {
                ZonedDateTime.parse(s.replace(" EST", " -0500").replace(" EDT", " -0400")
                    .replace(" AEST", " +1000").replace(" AEDT", " +1100").replace(" UTC", " GMT"),
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }
            .getOrNull()
    }

    // ---- offline cache ----

    suspend fun saveCache(file: File, items: List<NewsItem>, fetchedAt: Long) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        items.forEach { n ->
            arr.put(JSONObject().apply {
                put("t", n.title); put("l", n.link); put("s", n.source); put("p", n.published ?: -1L)
                put("m", n.summary); put("r", n.region.name); put("i", n.intensity)
                put("c", JSONArray(n.categories.map { it.name }))
            })
        }
        runCatching { file.writeText(JSONObject().put("at", fetchedAt).put("items", arr).toString()) }
    }

    suspend fun loadCache(file: File): Pair<Long, List<NewsItem>>? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("items")
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val cats = o.getJSONArray("c")
                NewsItem(
                    title = o.getString("t"), link = o.getString("l"), source = o.getString("s"),
                    published = o.getLong("p").takeIf { it > 0 }, summary = o.optString("m"),
                    region = Region.valueOf(o.getString("r")),
                    categories = (0 until cats.length()).mapNotNull { j ->
                        runCatching { Category.valueOf(cats.getString(j)) }.getOrNull()
                    },
                    intensity = o.optInt("i", 1),
                )
            }
            root.getLong("at") to list
        }.getOrNull()
    }
}
