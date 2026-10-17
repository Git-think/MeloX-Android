package com.lladlam.melox.core.music

import com.lladlam.melox.core.provider.youtubemusic.YouTubeCursorPager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeCursorPagerTest {
    @Test
    fun serverBatchesAreBufferedWithoutLosingItemsBetweenUiPages() = runBlocking {
        val pager = YouTubeCursorPager<Int> { it.toString() }
        var calls = 0
        suspend fun load(page: Int) = pager.page("search", page, 3,
            first = { YouTubeCursorPager.Batch(listOf(1, 2, 3, 4, 5), "next") },
            next = { calls++; YouTubeCursorPager.Batch(listOf(6, 7), null) })
        assertEquals(listOf(1, 2, 3), load(1).items)
        assertEquals(listOf(4, 5, 6), load(2).items)
        val last = load(3)
        assertEquals(listOf(7), last.items)
        assertEquals(7L, last.total)
        assertFalse(last.hasMore)
        assertEquals(1, calls)
    }

    @Test
    fun emptyFilteredBatchesDoNotHideWritablePlaylistsOnLaterPages() = runBlocking {
        val pager = YouTubeCursorPager<String> { it }
        val result = pager.page("writable", 1, 10,
            first = { YouTubeCursorPager.Batch(emptyList(), "a") },
            next = { cursor -> if (cursor == "a") YouTubeCursorPager.Batch(emptyList(), "b")
                else YouTubeCursorPager.Batch(listOf("owned"), null) })
        assertEquals(listOf("owned"), result.items)
        assertFalse(result.hasMore)
    }

    @Test
    fun repeatedCursorsAndDuplicatePagesTerminate() = runBlocking {
        val pager = YouTubeCursorPager<Int> { it.toString() }
        var calls = 0
        val result = pager.page("library", 1, 10,
            first = { YouTubeCursorPager.Batch(listOf(1), "same") },
            next = { calls++; YouTubeCursorPager.Batch(listOf(1, 2), "same") })
        assertEquals(listOf(1, 2), result.items)
        assertFalse(result.hasMore)
        assertEquals(1, calls)
    }

    @Test
    fun failedContinuationCanBeRetriedWithoutLosingTheCursor() = runBlocking {
        val pager = YouTubeCursorPager<Int> { it.toString() }
        var fail = true
        suspend fun load() = pager.page("library", 2, 1,
            first = { YouTubeCursorPager.Batch(listOf(1), "next") },
            next = { if (fail) error("offline") else YouTubeCursorPager.Batch(listOf(2), null) })
        assertTrue(runCatching { load() }.isFailure)
        fail = false
        assertEquals(listOf(2), load().items)
    }

    @Test
    fun refreshAndAccountKeysDoNotReuseAnotherSessionsResults() = runBlocking {
        val pager = YouTubeCursorPager<String> { it }
        var value = "old"
        suspend fun load(key: String, page: Int) = pager.page(key, page, 1,
            first = { YouTubeCursorPager.Batch(listOf(value, "$value-2"), null) },
            next = { error("unexpected") })
        load("account-a", 1)
        value = "new"
        assertEquals(listOf("new-2"), load("account-b", 2).items)
        assertEquals(listOf("old-2"), load("account-a", 2).items)
        assertEquals(listOf("new"), load("account-a", 1).items)
    }
}
