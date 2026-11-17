package io.github.nodyssey.data

import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import kotlinx.coroutines.flow.first

/** A new thread whose title holds one of the reader's 提醒关键词. */
data class KeywordHit(
    val postId: Long,
    val title: String,
    /** The keyword it matched, as stored — lower-cased. */
    val keyword: String,
)

/** A followed thread that has gained [newReplies] since the reader was last told about it. */
data class TrackedThreadUpdate(
    val postId: Long,
    val title: String,
    val newReplies: Int,
)

/**
 * 提醒关键词's one decision, kept apart from any request so it can be checked on its own.
 *
 * [baseline] is the newest thread id a previous run already looked at. Null means this is the first
 * run, which only records where "new" starts: notifying on it would announce a whole page of threads
 * that were posted before the reader added a single word.
 */
fun keywordHits(
    baseline: Long?,
    latest: List<PostSummary>,
    keywords: List<String>,
): Pair<List<KeywordHit>, Long?> =
    alertRuleHits(baseline, latest, keywords.map { TitleAlertRule(it, false) }, emptyList(), emptySet())

/** Pure Pulse Radar match: category, exclusion and include rules are applied before a hit is emitted. */
fun alertRuleHits(
    baseline: Long?,
    latest: List<PostSummary>,
    rules: List<TitleAlertRule>,
    excludedWords: List<String>,
    categorySlugs: Set<String>,
): Pair<List<KeywordHit>, Long?> {
    val newest = latest.maxOfOrNull { it.postId }
    val nextBaseline = listOfNotNull(baseline, newest).maxOrNull()
    if (baseline == null) return emptyList<KeywordHit>() to nextBaseline
    val regexRules = rules.filter(TitleAlertRule::isRegex).mapNotNull { rule ->
        runCatching { rule to Regex(rule.text, RegexOption.IGNORE_CASE) }.getOrNull()
    }
    val plainRules = rules.filterNot(TitleAlertRule::isRegex)
    val hits = latest
        .filter { it.postId > baseline && !it.isBlocked }
        .filter { categorySlugs.isEmpty() || it.categorySlug in categorySlugs }
        .filterNot { post -> excludedWords.any { post.title.contains(it, ignoreCase = true) } }
        .mapNotNull { post ->
            val rule = plainRules.firstOrNull { post.title.contains(it.text, ignoreCase = true) }
                ?: regexRules.firstOrNull { (_, regex) -> regex.containsMatchIn(post.title) }?.first
            rule?.let { KeywordHit(post.postId, post.title, it.text) }
        }
        .sortedBy { it.postId }
    return hits to nextBaseline
}

/**
 * 追踪新回复's one decision: which followed threads show more replies on the list than the reader
 * has been told about.
 *
 * A negative [TrackedThread.lastKnownCount] is "followed before the count was known" — the
 * first sighting sets it without a notification, the same rule [keywordHits] has for its first run.
 * A thread absent from [listed] says nothing either way and is left as it was.
 */
fun trackedThreadUpdates(
    tracked: List<TrackedThread>,
    listed: List<PostSummary>,
): List<Pair<TrackedThread, Int>> {
    val counts = listed.associate { it.postId to it.commentCount }
    return tracked.mapNotNull { thread ->
        val count = counts[thread.postId] ?: return@mapNotNull null
        if (count > thread.lastKnownCount) thread to count else null
    }
}

/**
 * The background poll's own half: reads the lists, decides, records how far it got, and returns what
 * is worth a notification. Posting them is the platform's business.
 *
 * Neither check needs an account — the lists are public — so this runs signed out as well.
 * [loadList] is the site's thread list as the network hands it over, never the Room cache: a stale
 * cached page would make an old thread look new.
 */
class BackgroundAlertChecker(
    private val loadList: suspend (sort: FeedSort, page: Int) -> List<PostSummary>,
    private val keywords: TitleKeywordStore,
    private val tracked: TrackedThreadStore,
    private val settings: SettingsRepository,
    private val loadCategoryList: suspend (categorySlug: String, sort: FeedSort, page: Int) -> List<PostSummary> = { _, sort, page -> loadList(sort, page) },
) {
    /** One request per selected board, or one mixed-feed request when no board filter is set. */
    suspend fun checkKeywords(): List<KeywordHit> {
        val rules = keywords.currentAlertRules()
        if (rules.isEmpty()) return emptyList()
        val filters = settings.settings.first()
        val latest = if (filters.alertCategorySlugs.isEmpty()) {
            loadList(FeedSort.POST_TIME, 1)
        } else {
            filters.alertCategorySlugs.flatMap { slug -> loadCategoryList(slug, FeedSort.POST_TIME, 1) }
        }.distinctBy(PostSummary::postId)
        val (hits, baseline) = alertRuleHits(
            settings.keywordAlertBaseline(),
            latest,
            rules,
            filters.alertExcludedWords,
            filters.alertCategorySlugs.toSet(),
        )
        baseline?.let { settings.setKeywordAlertBaseline(it) }
        return hits
    }

    /**
     * Two requests — the first two pages sorted by latest reply, which is where a thread that just
     * gained one sits — and none while nothing is followed.
     */
    suspend fun checkTrackedThreads(): List<TrackedThreadUpdate> {
        val following = tracked.all()
        if (following.isEmpty()) return emptyList()
        val listed = (1..TRACKED_PAGES).flatMap { page -> loadList(FeedSort.LAST_REPLY, page) }
        val followedIds = following.mapTo(HashSet()) { it.postId }
        listed.filter { it.postId in followedIds }.forEach { tracked.fillSnapshot(it) }
        return trackedThreadUpdates(following, listed).mapNotNull { (thread, count) ->
            tracked.advance(thread.postId, count)
            // First sighting: recorded, not announced.
            if (thread.lastKnownCount < 0) return@mapNotNull null
            TrackedThreadUpdate(thread.postId, thread.title, count - thread.lastKnownCount)
        }
    }

    companion object {
        const val TRACKED_PAGES = 2
    }
}
