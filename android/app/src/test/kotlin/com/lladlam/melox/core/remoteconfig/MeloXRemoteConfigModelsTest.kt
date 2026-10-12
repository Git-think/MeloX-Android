package com.lladlam.melox.core.remoteconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteConfigModelsTest {
    @Test
    fun unknownOptionalValuesFallBackWithoutExpandingRemoteAuthority() {
        val config = MeloXRemoteConfig.parse(
            """
            {
              "schemaVersion": 1,
              "configVersion": 8,
              "issuedAtEpochSeconds": 1,
              "minVersionCode": 10,
              "maxVersionCode": 20,
              "disabledCapabilities": ["qq_playback", "unknown_capability"],
              "strategies": {
                "netease_playback": "unknown",
                "qq_playback": "v2",
                "kugou_playback": "v1"
              },
              "crossProviderFallback": {
                "enabled": true,
                "order": ["unknown", "qq_music"],
                "disabledProviders": ["bilibili", "unknown"],
                "timeoutMs": 99999
              },
              "notice": null
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals(setOf("qq_playback"), config.disabledCapabilities)
        assertEquals("v1", config.strategies["netease_playback"])
        assertEquals("v1", config.strategies["qq_playback"])
        assertEquals(MeloXRemoteConfigDefaults.FallbackOrder, config.fallback.order)
        assertEquals(setOf("bilibili"), config.fallback.disabledProviders)
        assertEquals(10_000, config.fallback.timeoutMs)
        assertTrue(config.appliesTo(11))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSchema() {
        MeloXRemoteConfig.parse(
            """{"schemaVersion":2,"configVersion":1}""".toByteArray(),
        )
    }

    @Test
    fun textOverridesAreParsedPerLocale() {
        val config = MeloXRemoteConfig.parse(
            """
            {
              "schemaVersion": 1,
              "configVersion": 9,
              "issuedAtEpochSeconds": 1,
              "minVersionCode": 1,
              "maxVersionCode": 100,
              "disabledCapabilities": [],
              "strategies": {},
              "crossProviderFallback": {
                "enabled": true,
                "order": ["qq_music", "kugou", "bilibili"],
                "disabledProviders": [],
                "timeoutMs": 6000
              },
              "textOverrides": {
                "zh-CN": { "about.cat_title": "新的标题" },
                "default": { "about.cat_body": "fallback body" }
              }
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals("新的标题", config.textOverrides["zh-CN"]?.get("about.cat_title"))
        assertEquals("fallback body", config.textOverrides["default"]?.get("about.cat_body"))
    }

    @Test
    fun oversizedTextOverridesAreDropped() {
        val longValue = "x".repeat(MaxTextOverrideValueLength + 1)
        val longKey = "k".repeat(MaxTextOverrideKeyLength + 1)
        val parsed = parseTextOverrides(
            org.json.JSONObject(
                """
                {
                  "zh-CN": {
                    "ok": "fine",
                    "longkey$longKey": "v",
                    "toobig": "$longValue"
                  }
                }
                """.trimIndent(),
            ),
        )
        val locale = parsed["zh-CN"].orEmpty()
        assertEquals(setOf("ok"), locale.keys)
    }

    @Test
    fun missingTextOverridesYieldEmptyMap() {
        assertTrue(parseTextOverrides(null).isEmpty())
    }

    @Test
    fun scriptsAreParsedWithRevisionAndSource() {
        val scripts = parseScripts(
            org.json.JSONObject(
                """
                {
                  "search.keywords": {
                    "revision": 3,
                    "source": "function run(input){ return input; }"
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(setOf("search.keywords"), scripts.keys)
        assertEquals(3, scripts.getValue("search.keywords").revision)
        assertTrue(scripts.getValue("search.keywords").source.contains("function run"))
    }

    @Test
    fun oversizedAndExcessScriptsAreDropped() {
        val huge = "s".repeat(MaxHotScriptSourceLength + 1)
        val text = buildString {
            append("{")
            append("\"too.long\": {\"source\": \"").append(huge).append("\"},")
            repeat(MaxHotScripts + 2) { index ->
                append("\"hook.$index\": {\"source\": \"function run(i){ return i; }\"}")
                if (index <= MaxHotScripts) append(",")
            }
            append("}")
        }
        val scripts = parseScripts(org.json.JSONObject(text))

        assertTrue(scripts.size <= MaxHotScripts)
        assertTrue("too.long" !in scripts)
    }
}
