package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.data.repository.PostChange
import es.aria_on_the_planet.quasar.data.streaming.StreamChannel
import es.aria_on_the_planet.quasar.data.streaming.StreamEvent
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class TimelineItemTest {
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private val booster = User("2", "bob", "", "https://e", "", "", emptyList())
    private fun post(id: Int, text: String = "") = Post(id.toString(), user, text, Instant.EPOCH, 0, 0, 0, emptyList(), statusId = id.toString())
    /** [id] のブースト (中身は [statusId])。 */
    private fun boost(id: Int, statusId: Int) = post(statusId).copy(id = id.toString(), boostedBy = booster)

    private fun timeline(vararg ids: Int) = ids.map { TimelineItem.PostItem(post(it)) }
    private fun gap(maxId: Int, sinceId: Int) = TimelineItem.Gap(maxId.toString(), sinceId.toString())

    /** 一覧を「ID」と「gap(max..since)」の文字列にする。 */
    private fun describe(items: List<TimelineItem>) = items.map {
        when (it) {
            is TimelineItem.PostItem -> it.post.id
            is TimelineItem.Gap -> "gap(${it.maxId}..${it.sinceId})"
        }
    }

    // --- 新しい側への追加 -----------------------------------------------------------------------

    @Test
    fun streamUpdateGoesToTop() {
        assertEquals(listOf("30", "20", "10"), describe(mergeNewerPosts(timeline(20, 10), listOf(post(30)))))
    }

    @Test
    fun ignoresAlreadyKnownPosts() {
        val current = timeline(20, 10)
        assertSame(current, mergeNewerPosts(current, emptyList()))
        assertEquals(listOf("20", "10"), describe(mergeNewerPosts(current, listOf(post(20)))))
    }

    @Test
    fun backfillArrivingAfterStreamKeepsIdOrder() {
        var list = mergeNewerPosts(timeline(20, 10), listOf(post(50)))
        list = mergeNewerPosts(list, listOf(post(40), post(30)))
        assertEquals(listOf("50", "40", "30", "20", "10"), describe(list))
    }

    @Test
    fun comparesIdsNumerically() {
        assertEquals(listOf("100", "99"), describe(mergeNewerPosts(timeline(99), listOf(post(100)))))
    }

    @Test
    fun newPostsSkipOverGapsCorrectly() {
        // gap の上下どちらにも正しく入る (新しいものは gap より上)
        val list = timeline(50) + gap(50, 20) + timeline(20, 10)
        assertEquals(listOf("60", "50", "gap(50..20)", "20", "10"), describe(mergeNewerPosts(list, listOf(post(60)))))
    }

    // --- gap の作成と埋め込み --------------------------------------------------------------------

    @Test
    fun fullBackfillPageInsertsGapBetweenNewAndOld() {
        val result = mergeNewerPostsWithGap(timeline(20, 10), listOf(post(90), post(80), post(70)), sinceId = "20")
        assertEquals(listOf("90", "80", "70", "gap(70..20)", "20", "10"), describe(result))
    }

    @Test
    fun noGapWhenPageOverlapsExisting() {
        // ストリームで既に先頭に 90 が入っていて、補完ページも 20 まで繋がっている
        val result = mergeNewerPostsWithGap(timeline(90, 20, 10), listOf(post(90), post(50), post(20)), sinceId = "20")
        assertEquals(listOf("90", "50", "20", "10"), describe(result))
    }

    @Test
    fun streamedPostsAboveBackfillPageStayOnTop() {
        // 再接続直後にストリームで 95 が届いた後、補完ページ (90..70) が 1 ページ分埋まって返る
        val afterStream = mergeNewerPosts(timeline(20, 10), listOf(post(95)))
        // since_id は補完前の最新 (20)。一覧の今の最新 (95) ではない
        val result = mergeNewerPostsWithGap(afterStream, listOf(post(90), post(80), post(70)), sinceId = "20")
        assertEquals(listOf("95", "90", "80", "70", "gap(70..20)", "20", "10"), describe(result))
    }

    @Test
    fun fillingGapWithFullPageMovesGapDown() {
        val g = gap(70, 20)
        val list = timeline(90, 70) + g + timeline(20, 10)
        val result = fillGap(list, g, listOf(post(60), post(50)), pageLimit = 2)
        assertEquals(listOf("90", "70", "60", "50", "gap(50..20)", "20", "10"), describe(result))
    }

    @Test
    fun fillingGapWithPartialPageClosesIt() {
        val g = gap(70, 20)
        val list = timeline(90, 70) + g + timeline(20, 10)
        val result = fillGap(list, g, listOf(post(40)), pageLimit = 2)
        assertEquals(listOf("90", "70", "40", "20", "10"), describe(result))
    }

    @Test
    fun fillingGapWithEmptyPageClosesIt() {
        val g = gap(70, 20)
        val result = fillGap(timeline(70) + g + timeline(20), g, emptyList(), pageLimit = 40)
        assertEquals(listOf("70", "20"), describe(result))
    }

    @Test
    fun fillingGapIgnoresDuplicatesAndUnknownGap() {
        val g = gap(70, 20)
        val list = timeline(70) + g + timeline(20)
        // 20 は既にある (区間の端) → 無視
        assertEquals(listOf("70", "30", "20"), describe(fillGap(list, g, listOf(post(30), post(20)), pageLimit = 40)))
        assertSame(list, fillGap(list, gap(1, 0), listOf(post(30)), pageLimit = 40))
    }

    @Test
    fun gapLoadingFlagIsToggled() {
        val g = gap(70, 20)
        val loading = setGapLoading(timeline(70) + g + timeline(20), g, loading = true)
        assertEquals(true, (loading[1] as TimelineItem.Gap).isLoading)
        // 読み込み中の gap でもキーは同じなので埋められる
        assertEquals(listOf("70", "30", "20"), describe(fillGap(loading, g.copy(isLoading = true), listOf(post(30)), 40)))
    }

    // --- 削除・編集 -----------------------------------------------------------------------------

    @Test
    fun removesByPostIdOrBoostIdKeepingGaps() {
        val list = timeline(30) + listOf(TimelineItem.PostItem(boost(25, statusId = 5))) + gap(25, 20) + timeline(20)
        assertEquals(listOf("25", "gap(25..20)", "20"), describe(removePost(list, "30")))
        assertEquals(listOf("30", "gap(25..20)", "20"), describe(removePost(list, "25")))
        assertEquals(listOf("30", "gap(25..20)", "20"), describe(removePost(list, "5")))
        assertSame(list, removePost(list, "999"))
    }

    @Test
    fun replacesEditedPostKeepingBoostWrapper() {
        val list = timeline(30) + listOf(TimelineItem.PostItem(boost(25, statusId = 5)))
        val result = replaceEditedPost(list, post(5, text = "edited")).posts
        assertEquals("edited", result[1].text)
        assertEquals("25", result[1].id)
        assertEquals("bob", result[1].boostedBy?.screenName)
        assertSame(list, replaceEditedPost(list, post(999)))
    }

    @Test
    fun appliesOwnEditAndDeleteToPlainPostLists() {
        val posts = listOf(post(30), boost(25, statusId = 5), post(5))
        val edited = posts.withPostChange(PostChange.Edited(post(5, text = "edited")))
        assertEquals(listOf("", "edited", "edited"), edited.map { it.text })
        assertEquals("25", edited[1].id)
        // 削除はブーストごと除く
        assertEquals(listOf("30"), posts.withPostChange(PostChange.Deleted("5")).map { it.id })
    }

    // --- 最大件数 -------------------------------------------------------------------------------

    @Test
    fun trimsOldestPostsBeyondMax() {
        assertEquals(listOf("50", "40", "30"), describe(trimTimeline(timeline(50, 40, 30, 20, 10), maxPosts = 3)))
        val within = timeline(20, 10)
        assertSame(within, trimTimeline(within, maxPosts = 3))
    }

    @Test
    fun trimDropsGapsLeftAtTheEndButCountsOnlyPosts() {
        // gap は件数に数えない
        val list = timeline(90) + gap(90, 20) + timeline(20, 10)
        assertEquals(listOf("90", "gap(90..20)", "20", "10"), describe(trimTimeline(list, maxPosts = 3)))
        // 切った結果末尾に gap が残るなら捨てる
        assertEquals(listOf("90"), describe(trimTimeline(list, maxPosts = 1)))
    }

    @Test
    fun appendOlderPostsSkipsKnown() {
        assertEquals(listOf("30", "20", "10"), describe(appendOlderPosts(timeline(30, 20), listOf(post(20), post(10)))))
    }

    @Test
    fun newestAndOldestIgnoreGaps() {
        val list = timeline(99) + gap(99, 5) + timeline(100, 5)
        assertEquals("100", newestPostId(list))
        assertEquals("5", oldestPostId(list))
        assertNull(newestPostId(emptyList()))
    }

    // --- ストリーム・補完の適用 -----------------------------------------------------------------

    @Test
    fun streamEventsApplyByKind() {
        val list = timeline(20, 10)
        assertEquals(listOf("30", "20", "10"), describe(applyStreamEvent(list, StreamEvent.Update(StreamChannel.User, post(30)), isTarget = true)))
        assertSame(list, applyStreamEvent(list, StreamEvent.Update(StreamChannel.Local, post(30)), isTarget = false))
        assertEquals(listOf("10"), describe(applyStreamEvent(list, StreamEvent.Delete("20"), isTarget = false)))
        assertEquals("e", applyStreamEvent(list, StreamEvent.StatusUpdate(post(20, "e")), isTarget = false).posts[0].text)
        assertSame(list, applyStreamEvent(list, StreamEvent.Connected, isTarget = true))
    }

    @Test
    fun newerPageInsertsGapOnlyWhenFull() {
        assertEquals(listOf("40", "30", "20"), describe(applyNewerPage(timeline(20), "20", listOf(post(40), post(30)), pageLimit = 3)))
        assertEquals(
            listOf("50", "40", "30", "gap(30..20)", "20"),
            describe(applyNewerPage(timeline(20), "20", listOf(post(50), post(40), post(30)), pageLimit = 3)),
        )
    }

    // --- キャッシュで先に出した一覧への最初のページ ------------------------------------------------

    @Test
    fun refreshedPageReplacesItsRangeAndKeepsOlderCached() {
        // 25 は新着、20 は消された、10 以前はページの範囲外なので残す
        val merged = mergeRefreshedPosts(timeline(30, 20, 15, 10, 5), listOf(post(30, "edited"), post(25), post(15)), pageLimit = 3)
        assertEquals(listOf("30", "25", "15", "10", "5"), describe(merged!!))
        assertEquals("edited", (merged[0] as TimelineItem.PostItem).post.text)
    }

    @Test
    fun refreshedPageNotReachingCachedIsNotMerged() {
        // 間に未取得の区間がありうる
        assertNull(mergeRefreshedPosts(timeline(20, 10), listOf(post(50), post(40), post(30)), pageLimit = 3))
    }

    @Test
    fun shortRefreshedPageIsWholeList() {
        // 3 件に満たない = それで全部。キャッシュの 10 は消されている
        assertNull(mergeRefreshedPosts(timeline(30, 20, 10), listOf(post(30), post(20)), pageLimit = 3))
    }

    @Test
    fun refreshedPageWithoutCacheIsNotMerged() {
        assertNull(mergeRefreshedPosts(emptyList(), listOf(post(30), post(20)), pageLimit = 2))
    }

    @Test
    fun refreshedPageKeepsBoostKeys() {
        val merged = mergeRefreshedPosts(listOf(TimelineItem.PostItem(boost(40, 7)), *timeline(30).toTypedArray()), listOf(boost(40, 7), post(30)), pageLimit = 2)
        assertEquals(listOf("40", "30"), describe(merged!!))
    }
}
