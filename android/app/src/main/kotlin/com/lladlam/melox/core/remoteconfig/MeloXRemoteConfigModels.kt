package com.lladlam.melox.core.remoteconfig

import org.json.JSONObject

data class MeloXRemoteFallbackConfig(
    val enabled: Boolean,
    val order: List<String>,
    val disabledProviders: Set<String>,
    val timeoutMs: Int,
)

data class MeloXRemoteNotice(
    val id: String,
    val level: String,
    val title: String,
    val message: String,
    val frequency: String,
)

/**
 * A signed, sandboxed behaviour hook. The source must define `run(input)` and
 * return a JSON-serialisable value; see [com.lladlam.melox.core.remoteconfig.MeloXHotScripts].
 */
data class MeloXRemoteScript(
    val name: String,
    val revision: Int,
    val source: String,
)

data class MeloXRemoteConfig(
    val schemaVersion: Int,
    val configVersion: Int,
    val issuedAtEpochSeconds: Long,
    val minVersionCode: Int,
    val maxVersionCode: Int,
    val disabledCapabilities: Set<String>,
    val strategies: Map<String, String>,
    val fallback: MeloXRemoteFallbackConfig,
    val notice: MeloXRemoteNotice?,
    /**
     * Signed UI text overrides, `localeTag -> (key -> text)`. Only applied while
     * the config is a verified remote one, so the payload signature is the only
     * authority for what text a build will show.
     */
    val textOverrides: Map<String, Map<String, String>>,
    val scripts: Map<String, MeloXRemoteScript>,
) {
    fun appliesTo(versionCode: Int): Boolean = versionCode in minVersionCode..maxVersionCode

    companion object {
        fun parse(payload: ByteArray): MeloXRemoteConfig {
            val root = JSONObject(payload.toString(Charsets.UTF_8))
            require(root.getInt("schemaVersion") == 1) { "Unsupported remote config schema" }
            val configVersion = root.getInt("configVersion")
            require(configVersion > 0) { "Invalid remote config version" }
            val minVersionCode = root.getInt("minVersionCode")
            val maxVersionCode = root.getInt("maxVersionCode")
            require(minVersionCode > 0 && maxVersionCode >= minVersionCode) { "Invalid app version range" }

            val disabledCapabilities = root.getJSONArray("disabledCapabilities").strings()
                .filterTo(linkedSetOf()) { it in MeloXRemoteConfigDefaults.AllowedCapabilities }
            val strategyJson = root.getJSONObject("strategies")
            val strategies = MeloXRemoteConfigDefaults.Strategies.mapValues { (name, default) ->
                strategyJson.optString(name).takeIf { it == "v1" } ?: default
            }
            val fallbackJson = root.getJSONObject("crossProviderFallback")
            val order = fallbackJson.getJSONArray("order").strings()
                .filter { it in MeloXRemoteConfigDefaults.AllowedProviders }
                .distinct()
                .takeIf { it.size == MeloXRemoteConfigDefaults.AllowedProviders.size }
                ?: MeloXRemoteConfigDefaults.FallbackOrder
            val disabledProviders = fallbackJson.getJSONArray("disabledProviders").strings()
                .filterTo(linkedSetOf()) { it in MeloXRemoteConfigDefaults.AllowedProviders }
            val timeoutMs = fallbackJson.getInt("timeoutMs").coerceIn(3_000, 10_000)
            val noticeJson = root.optJSONObject("notice")
            val notice = noticeJson?.let {
                MeloXRemoteNotice(
                    id = it.getString("id").take(80),
                    level = it.getString("level").takeIf { value -> value in setOf("info", "warning", "outage") }
                        ?: "info",
                    title = it.getString("title").take(60),
                    message = it.getString("message").take(300),
                    frequency = it.getString("frequency").takeIf { value -> value in setOf("once", "daily") }
                        ?: "once",
                ).takeIf { value -> value.id.isNotBlank() && value.title.isNotBlank() && value.message.isNotBlank() }
            }
            return MeloXRemoteConfig(
                schemaVersion = 1,
                configVersion = configVersion,
                issuedAtEpochSeconds = root.getLong("issuedAtEpochSeconds"),
                minVersionCode = minVersionCode,
                maxVersionCode = maxVersionCode,
                disabledCapabilities = disabledCapabilities,
                strategies = strategies,
                fallback = MeloXRemoteFallbackConfig(
                    enabled = fallbackJson.getBoolean("enabled"),
                    order = order,
                    disabledProviders = disabledProviders,
                    timeoutMs = timeoutMs,
                ),
                notice = notice,
                textOverrides = parseTextOverrides(root.optJSONObject("textOverrides")),
                scripts = parseScripts(root.optJSONObject("scripts")),
            )
        }
    }
}

enum class MeloXRemoteConfigSource { BuiltIn, VerifiedRemote, VersionInapplicable }

data class MeloXRemoteConfigStatus(
    val source: MeloXRemoteConfigSource = MeloXRemoteConfigSource.BuiltIn,
    val config: MeloXRemoteConfig = MeloXRemoteConfigDefaults.Config,
    val keyId: String? = null,
    val lastCheckedAtEpochMs: Long = 0L,
    val lastUpdatedAtEpochMs: Long = 0L,
    val error: String? = null,
    val refreshing: Boolean = false,
)

private fun org.json.JSONArray.strings(): List<String> = buildList {
    for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
}

/**
 * Parses the signed `textOverrides` block defensively.
 *
 * The block is only allowed to replace *display text*, so it is bounded hard:
 * at most [MaxTextOverrideEntries] keys across locales, each locale tag, key and
 * value length-capped. Anything oversized is dropped rather than trusted, and a
 * malformed block simply yields no overrides instead of failing the config.
 */
internal fun parseTextOverrides(json: JSONObject?): Map<String, Map<String, String>> {
    if (json == null) return emptyMap()
    val result = LinkedHashMap<String, Map<String, String>>()
    var remaining = MaxTextOverrideEntries
    for (locale in json.keys()) {
        if (remaining <= 0) break
        val tag = locale.trim().take(MaxTextOverrideLocaleLength)
        if (tag.isBlank()) continue
        val entries = json.optJSONObject(locale) ?: continue
        val parsed = LinkedHashMap<String, String>()
        for (key in entries.keys()) {
            if (remaining <= 0) break
            val cleanKey = key.trim()
            if (cleanKey.isBlank() || cleanKey.length > MaxTextOverrideKeyLength) continue
            val value = entries.optString(key)
            if (value.isBlank() || value.length > MaxTextOverrideValueLength) continue
            parsed[cleanKey] = value
            remaining--
        }
        if (parsed.isNotEmpty()) result[tag] = parsed
    }
    return result
}

internal const val MaxTextOverrideEntries = 256
internal const val MaxTextOverrideLocaleLength = 24
internal const val MaxTextOverrideKeyLength = 120
internal const val MaxTextOverrideValueLength = 400

/**
 * Parses the signed `scripts` block defensively.
 *
 * Scripts run in a memory-limited JS sandbox, but the config still only gets a
 * fixed budget: a handful of hooks, each length-capped, with a hard total so a
 * compromised or oversized payload cannot turn into a large download.
 */
internal fun parseScripts(json: JSONObject?): Map<String, MeloXRemoteScript> {
    if (json == null) return emptyMap()
    val result = LinkedHashMap<String, MeloXRemoteScript>()
    var total = 0
    for (name in json.keys()) {
        if (result.size >= MaxHotScripts) break
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > MaxHotScriptNameLength) continue
        val entry = json.optJSONObject(name) ?: continue
        val source = entry.optString("source")
        if (source.isBlank() || source.length > MaxHotScriptSourceLength) continue
        if (total + source.length > MaxHotScriptTotalLength) break
        total += source.length
        result[cleanName] = MeloXRemoteScript(
            name = cleanName,
            revision = entry.optInt("revision", 1).coerceAtLeast(1),
            source = source,
        )
    }
    return result
}

internal const val MaxHotScripts = 8
internal const val MaxHotScriptNameLength = 64
internal const val MaxHotScriptSourceLength = 16_384
internal const val MaxHotScriptTotalLength = 32_768
