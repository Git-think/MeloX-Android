package com.lladlam.melox.core.provider.bilibili

import com.lladlam.melox.core.music.model.*
import com.lladlam.melox.core.music.provider.loadAllPlaylistTracks
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BilibiliFavoritesTest {
    private val folder = MusicPlaylistSummary(MusicResourceId(MusicSource.Bilibili, "999"), "音乐")
    private val session = BilibiliSession("SESSDATA=test; bili_jct=csrf; DedeUserID=123", "test", "csrf", "123", "", "")
    private fun provider(reply: (Request) -> JSONObject): BilibiliProvider {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply(chain.request()).toString().toResponseBody()).build()
        }.build()
        return BilibiliProvider({ session }, client)
    }
    private fun ok(data: JSONObject) = JSONObject().put("code", 0).put("data", data)
    private fun media(index: Int, parts: Int = 1) = JSONObject().put("bvid", "BVtest$index").put("id", index)
        .put("title", "歌曲$index").put("type", 2).put("page", parts).put("duration", 180)
        .put("upper", JSONObject().put("name", "UP 主")).put("ugc", JSONObject().put("first_cid", index + 100L))

    @Test fun collectedFoldersKeepFullMediaIdAndSeasonsHaveSeparateIdentity() = runBlocking {
        val provider = provider { request ->
            when (request.url.encodedPath) {
                "/x/v3/fav/folder/created/list-all" -> ok(JSONObject().put("list", JSONArray()))
                "/x/v3/fav/folder/collected/list" -> ok(JSONObject().put("list", JSONArray()
                    .put(JSONObject().put("id", 999).put("fid", 9).put("title", "他人收藏夹").put("type", 11).put("cover", "https://example.com/a.jpg"))
                    .put(JSONObject().put("id", 999).put("mid", 456).put("title", "订阅合集").put("type", 21).put("cover", "https://example.com/b.jpg"))))
                else -> throw AssertionError("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val playlists = provider.userPlaylists(1, 100).items
        assertEquals(listOf("999", "season:456:999"), playlists.map { it.id.value })
    }

    @Test fun standardFolderUsesFullIdAndExistingCidWithoutPerVideoRequests() = runBlocking {
        var calls = 0
        val provider = provider { request ->
            calls++
            assertEquals("/x/v3/fav/resource/list", request.url.encodedPath)
            assertEquals("999", request.url.queryParameter("media_id"))
            assertEquals("20", request.url.queryParameter("ps"))
            ok(JSONObject().put("info", JSONObject().put("media_count", 4000))
                .put("medias", JSONArray().put(media(1)).put(media(2))).put("has_more", true))
        }
        val detail = provider.playlistDetail(folder, 1, 200)
        assertEquals(2, detail.tracks.size)
        assertEquals(true, detail.hasMore)
        assertEquals(180000L, detail.tracks.first().durationMs)
        assertEquals("UP 主", detail.tracks.first().artistText)
        assertEquals(1, calls)
    }

    @Test fun oneDeletedVideoDoesNotDiscardTheRestOfThePage() = runBlocking {
        val provider = provider { request ->
            when (request.url.encodedPath) {
                "/x/v3/fav/resource/list" -> ok(JSONObject().put("info", JSONObject().put("media_count", 2))
                    .put("medias", JSONArray().put(media(1)).put(media(2).removeCid())).put("has_more", false))
                "/x/web-interface/view" -> JSONObject().put("code", -404).put("message", "视频不存在")
                else -> throw AssertionError("Unexpected endpoint")
            }
        }
        val detail = provider.playlistDetail(folder, 1, 20)
        assertEquals(listOf("BVtest1:101"), detail.tracks.map { it.id.value })
        assertEquals(false, detail.hasMore)
    }

    @Test fun seasonUsesSeasonEndpointRatherThanFavoriteResourceEndpoint() = runBlocking {
        val provider = provider { request ->
            assertEquals("/x/polymer/web-space/seasons_archives_list", request.url.encodedPath)
            assertEquals("456", request.url.queryParameter("mid"))
            assertEquals("999", request.url.queryParameter("season_id"))
            ok(JSONObject().put("page", JSONObject().put("total", 1)).put("archives", JSONArray().put(media(1))))
        }
        val detail = provider.playlistDetail(folder.copy(id = MusicResourceId(MusicSource.Bilibili, "season:456:999")), 1, 20)
        assertEquals(1, detail.tracks.size)
        assertEquals(false, detail.hasMore)
    }

    @Test fun multipartsDoNotStopResourcePagingEarly() = runBlocking {
        val pages = mutableListOf<Int>()
        val provider = provider { request ->
            when (request.url.encodedPath) {
                "/x/v3/fav/resource/list" -> {
                    val page = request.url.queryParameter("pn")!!.toInt()
                    pages += page
                    ok(JSONObject().put("info", JSONObject().put("media_count", 2))
                        .put("medias", JSONArray().put(media(page, if (page == 1) 2 else 1))).put("has_more", page == 1))
                }
                "/x/web-interface/view" -> ok(JSONObject().put("aid", 1).put("pages", JSONArray()
                    .put(JSONObject().put("cid", 101).put("part", "上").put("page", 1))
                    .put(JSONObject().put("cid", 102).put("part", "下").put("page", 2))))
                else -> throw AssertionError("Unexpected endpoint")
            }
        }
        val detail = provider.loadAllPlaylistTracks(folder)
        assertEquals(listOf(1, 2), pages)
        assertEquals(3, detail.tracks.size)
    }

    private fun JSONObject.removeCid(): JSONObject = apply { remove("ugc"); remove("cid") }
}
