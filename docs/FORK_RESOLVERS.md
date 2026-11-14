# Fork resolver integration

This fork is rebuilt from upstream. Official source APIs, quality selection,
playback service, decoders, lyrics and plugin isolation remain upstream code.
The fork adds configured APIs only at the existing final third-party fallback
stage. It does not replace official playback with third-party URLs in advance.

## Configuration

Enable the third-party music-source consent switch under **Music Services**,
then configure and enable the services you want to use:

| Service | Endpoint | Supported source IDs |
| --- | --- | --- |
| Meting | Complete API URL, e.g. `https://host/api.php` or `https://host/meting/` | NetEase, QQ Music, Kugou |
| CHKSZ | Server root or `/api`, plus the personal API Key | NetEase, QQ Music, Kugou |
| UNM | HTTP `/match?id=…&server=…` API, not an ordinary UNM proxy port | NetEase only |

Meting sends `server`, `type=url`, `id`, `br` and `level` to the exact configured
endpoint. Optional `auth` is a service-specific query token, not an official
music-account cookie or a server signing secret. CHKSZ preserves its existing
`163_music`, `qq_music` and `kugou_music` request contracts. UNM accepts an API
server implementing the `/match` contract; the upstream UNM proxy alone does
not expose that API.

Configuration uses the previous fork's local preference names, preserving saved
addresses, API keys and explicit enabled flags. Nothing is uploaded to remote
configuration. A new installation has no enabled service by default.

The **Test resolver** action prefills NetEase song ID `22676167` (DJ OKAWARI,
“Flower Dance”). It was selected from an actual Meting search and its audio
headers were checked; service availability can still change. The ID remains
editable, and only the selected service is requested. A successful result means
an audio URL was returned, **not** that the URL was downloaded, decoded or played
successfully. Do not infer on-device
playback quality, battery use or temperature from a cloud build or this test.

Failed tests display the HTTP status and the service response as selectable plain
text. Empty responses are explicit, non-text bodies are not rendered, and long
responses are truncated to at most 4,096 characters. Configured credentials are
redacted, and response bodies are never written to playback logs. Explicit tests
make fresh requests rather than reusing playback caches or service cooldowns.

## Playback boundary

1. Official current source (upstream behaviour).
2. Upstream's strict built-in cross-provider fallback, when enabled.
3. Configured Meting → CHKSZ → UNM → installed LX scripts.

The existing membership-only preference still limits step 3 to membership or
rights failures. Without that restriction, any terminal unavailable result may
reach step 3. Network/authentication/cancellation failures are not treated as
proof that a track has no source.

Two small adapters fix missing paths into that existing stage:

- With third-party consent enabled, built-in providers that throw a documented
  terminal no-source exception are normalized to the existing playback result
  types. With consent disabled, the upstream exception behaviour is preserved.
  Other exceptions always propagate.
- A legacy NetEase URI without title metadata can still use its numeric song ID
  for a configured API; matching stages remain free to reject missing metadata.

Plugin sources never enter built-in/LX resolvers. No imported LX scripts means
no LX runtime or matching request is started.

## Resource and network bounds

- Use upstream's cookie-free, isolated HTTP transport, not its retrying official
  API client. Connect/read/whole-call timeouts are 3/5/8 seconds with connection
  retries disabled.
- Coalesce identical concurrent requests. Positive results last at most two
  minutes; negative results last 30 seconds. Network, authorization, rate-limit
  and server failures temporarily cool down the affected service.
- Limit text responses to 64 KiB before JSON parsing. Only explicit audio URL
  fields in known containers are accepted; artwork and error-page links are not
  used as audio.
- A direct/redirected audio response is closed after its headers are inspected.
  A resolver must never download a whole song into a string.
- Cache identities hash local settings rather than exposing raw credentials;
  diagnostic messages contain service names/statuses, not API keys or full URLs.

## Release compatibility and validation

The fork keeps its current signing secrets, package ID and workflows. A
universal ARM APK remains enabled because the existing release/update workflows
publish a fixed asset name. Update/download endpoints point to the fork so
installations cannot accidentally download an upstream-signed replacement.

Tests cover protocol URLs and source identity, absent configuration, JSON URL
filtering, bounded bodies, direct/redirected audio, cache expiry, cooldown,
concurrent requests, fallback ordering, and exception classification. Run Android
compilation and these tests in GitHub Actions; no local Android SDK/build is
required for this rewrite.

## Upstream compatibility

The third-party result model and the LX-to-result mapper retain upstream's
`sourceQuality` and `actualQuality` fields. Fork API expiration is computed in
`ConfiguredResolverSource.kt`, not added to the upstream model. The installed-LX
guard wraps the upstream mapper without rebuilding or losing its result fields;
it checks the script cache identity on every resolution.

This keeps the finite API cache and empty-script guard while reducing overlapping
edits when upstream changes LX quality negotiation. Remaining integration points
are merged normally; there is no blanket “ours” merge strategy or automatic
discarding of upstream playback changes.

The built-in fallback adapter runs inside upstream's bounded provider resolution.
Provider timeouts and cancellation still propagate as failures, never as a
terminal no-source result that could start third-party fallback requests.
