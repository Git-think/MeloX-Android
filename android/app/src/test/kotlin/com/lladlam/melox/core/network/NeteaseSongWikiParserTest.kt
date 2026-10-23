package com.lladlam.melox.core.network

import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseSongWikiParserTest {
    private val client = NeteaseMusicOperationsClient(cookieProvider = { "" }, httpClient = OkHttpClient())

    @Test
    fun `parses creatives and direct block resources`() {
        val response = JSONObject(
            """
            {
              "code": 200,
              "data": {
                "blocks": [
                  {
                    "code": "SONG_PLAY_ABOUT_SONG_BASIC",
                    "uiElement": { "mainTitle": { "title": "基本信息" } },
                    "creatives": [
                      {
                        "uiElement": { "mainTitle": { "title": "曲风" } },
                        "resources": [
                          {
                            "uiElement": {
                              "mainTitle": { "title": "流行" },
                              "descriptions": [{ "description": "华语流行" }],
                              "subTitles": [{ "title": "2020" }]
                            }
                          }
                        ]
                      }
                    ]
                  },
                  {
                    "code": "SONG_PLAY_ABOUT_MUSIC_MEMORY",
                    "resources": [
                      {
                        "resourceType": "FIRST_LISTEN",
                        "extensionInfo": { "musicFirstListen": { "date": "2021-01-02" } }
                      },
                      {
                        "resourceType": "TOTAL_PLAY",
                        "extensionInfo": { "musicTotalPlay": { "playCount": 123 } }
                      },
                      { "uiElement": { "mainTitle": { "title": "回忆坐标" } } }
                    ]
                  },
                  {
                    "code": "SONG_PLAY_ABOUT_SIMILAR_SONG",
                    "creatives": [
                      { "resources": [{ "uiElement": { "mainTitle": { "title": "相似歌" } } }] }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val sections = client.parseSongWikiSections(response)

        assertEquals(listOf("基本信息", "音乐记忆", "相似歌曲"), sections.map { it.title })
        assertEquals(
            listOf("曲风", "流行", "华语流行", "2020"),
            sections[0].lines,
        )
        assertEquals(
            listOf("第一次听：2021-01-02", "累计播放：123 次", "回忆坐标"),
            sections[1].lines,
        )
        assertEquals(listOf("相似歌"), sections[2].lines)
    }

    @Test
    fun `skips empty blocks and tolerates missing data`() {
        assertTrue(client.parseSongWikiSections(JSONObject("{}")).isEmpty())
        val response = JSONObject(
            """{"data":{"blocks":[{"code":"UNKNOWN"},{"code":"UNKNOWN","resources":[]}]}}""",
        )
        assertTrue(client.parseSongWikiSections(response).isEmpty())
    }
}
