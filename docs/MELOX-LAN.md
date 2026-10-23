# MeloX LAN Protocol v1

Status: draft, Android implementation in progress.

This document defines the local-network link used by MeloX apps to discover each
other and to control, cast, transfer, or synchronize playback. It is intentionally
small enough to reimplement on iOS, Windows, and Linux with only a TCP/UDP stack
and a JSON encoder.

## 1. Goals

- Discover other MeloX instances on the same LAN and expose this device's control
  port.
- Let one device control another (previous / next / play / pause / seek).
- **Cast**: the controlling device streams its own MeloX audio to the controlled
  device, which plays it like a speaker.
- **Transfer**: hand a track/queue to another device so that it resolves and plays
  the audio itself.
- **Together**: multiple devices play the same queue at roughly the same position;
  either side can issue transport commands.
- Work across Android, iOS, Windows, and Linux.

Non-goals for v1:

- Internet discovery, NAT traversal, or cloud relays.
- Capturing system audio from other apps. Cast taps only MeloX's own playback.
- Remote file browsing or arbitrary command execution.
- DRM-protected audio.

## 2. Ports and discovery

| Purpose | Transport | Default |
| --- | --- | --- |
| Discovery | UDP broadcast | `47830` |
| Control / events | TCP | `47831` (falls back to 47832–47840) |
| Cast audio | TCP | ephemeral, assigned per cast session |

The control port is configurable. A device that cannot bind the requested port
tries the next 9 ports and reports the one actually bound in announcements.

### 2.1 Discovery

All messages are UTF-8 JSON datagrams.

Request (broadcast to `255.255.255.255:47830`, optionally the same message to a
multicast group for networks that block broadcast):

```json
{
  "magic": "MELOX_LAN",
  "v": 1,
  "type": "discover",
  "deviceId": "8f3c…",
  "nonce": "b2f1…"
}
```

Announce (unicast back to the sender's source address/port):

```json
{
  "magic": "MELOX_LAN",
  "v": 1,
  "type": "announce",
  "deviceId": "5d20…",
  "name": "Pixel 9 · MeloX",
  "platform": "android",
  "model": "Pixel 9",
  "appVersion": "0.6.5",
  "port": 47831,
  "pinRequired": true,
  "trusted": false,
  "capabilities": ["control", "cast", "transfer", "together"],
  "state": {
    "playing": false,
    "title": "…",
    "artist": "…",
    "positionMs": 0,
    "durationMs": 0,
    "trackId": "netease:123",
    "hasQueue": true
  },
  "nonce": "b2f1…"
}
```

`trusted` is relative to the receiver as seen by that peer: it is `true` only if
the announcing device already trusts the requester. Devices may also send
unsolicited announcements every 5 seconds while discovery is enabled.

Discovery must be optional in settings. When disabled, the device neither listens
for discovery requests nor announces itself, and the control server is closed.

## 3. Control connection

TCP, one JSON object per line (`\n` terminated), UTF-8. Maximum line length is
256 KiB. The server closes a connection after 15 seconds without a message.

### 3.1 Handshake

The client sends a `hello` first:

```json
{
  "type": "hello",
  "v": 1,
  "deviceId": "8f3c…",
  "deviceName": "MacBook · MeloX",
  "platform": "windows",
  "appVersion": "0.6.5",
  "pin": "0427"
}
```

`pin` is omitted when the client believes it is trusted. The server replies:

```json
{"type":"auth_ok","trusted":true,"sessionId":"…"}
```

or

```json
{"type":"auth_required","pinRequired":true}
{"type":"auth_failed","reason":"pin","attemptsLeft":2}
```

Rules:

- The server generates a stable 4-digit PIN, displayed in the LAN settings UI.
- A device is trusted when its `deviceId` was added to the server's **common
  devices** list. Trusted connections never require a PIN.
- Otherwise the first successful PIN authenticates this connection only. The
  receiver may offer “add to common devices”; only that explicit action makes
  future connections PIN-free.
- Five failed PINs close the connection. A server may rate-limit repeated
  failures per source address.
- A trusted device is identified by its persisted `deviceId`; this is a
  convenience/trust feature, not a cryptographic identity. Do not expose the LAN
  server to untrusted networks.
- A peer cannot add itself to the trusted list over the network. Trust is changed
  only by the local user in Settings → LAN playback → Recently connected.

### 3.2 Requests and responses

Every request carries an `id`; responses echo it.

```json
{"id":"1","type":"request","action":"control","command":"next"}
```

```json
{"id":"1","type":"response","ok":true}
```

Supported actions:

| Action | Fields | Meaning |
| --- | --- | --- |
| `state` | — | Return current playback state |
| `control` | `command`: `play`, `pause`, `toggle`, `next`, `previous`, `seek`, `volume`; `positionMs` / `volume` | Control the target's playback |
| `transfer` | `track`, optional `queue`, `positionMs` | Target resolves and plays the track |
| `cast.start` | — | Target pauses and prepares an audio receiver; response contains cast port and format |
| `cast.stop` | — | Stop the cast receiver |
| `together.start` | `queue`, `index`, `positionMs`, `playing` | Target joins as a synchronized player |
| `together.stop` | — | Leave the session |
| `ping` | `t0` | Clock probe; response echoes `t0` and returns `serverTimeMs` |

`control` composes as follows:

- `next` / `previous` use the target's current queue.
- `toggle` flips play/pause.
- `seek` requires `positionMs`.
- `volume` is 0–100 and may be ignored by platforms without app-level volume.

### 3.3 Events

The server pushes state changes and session events to authenticated clients:

```json
{"type":"event","event":"state","state":{…}}
{"type":"event","event":"cast.started","port":51234,"format":{…}}
{"type":"event","event":"cast.stopped"}
{"type":"event","event":"together.state","queue":[…],"index":2,"positionMs":53120,"playing":true,"serverTimeMs":…}
```

A state event is sent on every relevant player change and at least once per
second while at least one client is connected. Clients must tolerate unknown
event names and fields.

### 3.4 Playback state object

```json
{
  "playing": true,
  "title": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "artworkUrl": "https://…",
  "positionMs": 31000,
  "durationMs": 269000,
  "volume": 100,
  "trackId": "netease:186016",
  "source": "netease",
  "index": 4,
  "queueLength": 42,
  "canNext": true,
  "canPrevious": true
}
```

Track object used by transfer/together:

```json
{
  "source": "netease",
  "id": "186016",
  "title": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "artworkUrl": "https://…",
  "durationMs": 269000
}
```

For non-NetEase sources, `source` is the provider id (`qq`, `kugou`, `youtube`,
`local`, …) and `id` is the provider track id. Receivers that cannot resolve a
source should return `ok:false, error:"unsupported_source"`.

## 4. Cast audio

### 4.1 Handshake

Controller (the optional `format` lets the receiver allocate the correct
`AudioTrack`; when omitted the default is 48 kHz stereo):

```json
{"id":"c1","type":"request","action":"cast.start",
 "format":{"codec":"pcm_s16le","sampleRate":48000,"channels":2}}
```

Controlled device:

1. Pauses its own playback (to avoid echo).
2. Opens an ephemeral TCP `ServerSocket` bound to the LAN interface.
3. Replies:

```json
{
  "id":"c1","type":"response","ok":true,
  "castPort":51234,
  "format":{"codec":"pcm_s16le","sampleRate":48000,"channels":2}
}
```

The controller then connects to `controlledIp:castPort` and streams.

### 4.2 Packet format

Each audio packet:

- 4-byte big-endian unsigned payload length (`N`).
- `N` bytes of interleaved little-endian signed 16-bit PCM.
- No header, no timestamp; the TCP stream is continuous.

Recommended packet size: 20 ms of audio (e.g. 3840 bytes for 48 kHz stereo),
maximum 64 KiB. The receiver should buffer ~150–250 ms and discard the oldest
audio if it falls too far behind. The stream ends when the sender closes the TCP
connection or sends `cast.stop` on the control channel.

The controller taps only its own playback pipeline. Android uses a Media3
`AudioProcessor`; desktop uses the PCM written to the system audio line. If the
sample format changes, the controller stops and starts a new cast session.

### 4.3 Why PCM

PCM avoids shipping an encoder/decoder in v1 and keeps the protocol implementable
on every platform. Bandwidth is ~1.5 Mbit/s at 48 kHz stereo, acceptable on a
LAN. A future version may negotiate Opus/AAC.

## 5. Transfer

```json
{
  "id":"t1","type":"request","action":"transfer",
  "track":{"source":"netease","id":"186016","title":"晴天","artist":"周杰伦","album":"叶惠美","durationMs":269000},
  "queue":[ … ],
  "positionMs": 0
}
```

The receiver resolves the track with its own account/source adapters. If the
track cannot be resolved, it replies `ok:false` with `error` and leaves its
playback unchanged. On success it replaces its queue with `queue` when provided,
starts at the matching index, and pauses the sender (the sender decides; the
protocol does not mandate it).

## 6. Together

### 6.1 Start

The initiator (host) sends its queue snapshot and state:

```json
{"id":"g1","type":"request","action":"together.start",
 "queue":[ … ], "index":2, "positionMs":42000, "playing":true}
```

The receiver applies the queue, seeks to `positionMs`, and replies with its
state. The host remains authoritative for queue changes. The control connection used
for `together.start` stays open and is bidirectional: the joining device may send
`control` requests back over the same socket, and the initiator applies them and
emits `together.state` events over that socket. This avoids a reverse PIN prompt
while still letting both sides control playback.

### 6.3 Guest without an account or VIP: temporary audio relay

A host MAY include a `relay` object when starting Together:

```json
{"id":"g1","type":"request","action":"together.start",
 "queue":[…],"index":2,"positionMs":42000,"playing":true,
 "relay":{"baseUrl":"http://192.168.1.20:51234","token":"9f4c…"}}
```

When `relay` is present, the joining device builds its queue from the same tracks
but uses `GET {baseUrl}/audio?token=…&songId=…` as the media URI instead of
resolving the source with its own account. The host proxies the audio using its
own signed-in account (or a local download) and returns normal HTTP `Range`
responses.

Token rules:

- The guest never receives the host's NetEase cookie or any account credential.
- The token is random, bound to the guest's source IP and to the song ids in the
  shared queue.
- It expires **10 seconds after issue if it has not been used**. The first valid
  audio request extends it for the session (the host may cap it at six hours).
- The host revokes it when Together stops, when the control connection drops, or
  when the user ends the session.
- A token is rejected if the requesting IP differs, the song is not in the
  session queue, or the token has expired/been revoked.

The proxy is host-local and never exposed to the internet. A future version may
replace PCM/HTTP proxying with Opus and an authenticated media session.

### 6.2 Clock sync

Clients estimate the host clock offset with `ping`:

```
t0 = client monotonic send time
t1 = host receive time (serverTimeMs)
t2 = host send time
t3 = client receive time
offset ≈ ((t1 - t0) + (t2 - t3)) / 2
```

Before each correction the guest computes:

```
target = hostPositionMs + (clientNow - hostServerTime) + offset
```

If `|target - localPosition| > 400 ms`, seek. Otherwise adjust playback rate only
if the platform supports it; v1 implementations may simply seek. This tolerates
audible drift under half a second without extra DSP.

## 7. Security and robustness

- Bind the control server to the LAN only. Do not expose it on public interfaces
  when a VPN or cellular interface is present if the platform allows choosing.
- PIN authentication is required for new peers. Limit failed attempts.
- Trusted devices are explicitly added in settings; there is no implicit trust
  from being on the same Wi-Fi.
- Reject malformed/oversized JSON, unknown actions, and non-JSON lines.
- Never execute received paths, URLs, or shell commands. Transfer only accepts
  known source ids; cast only accepts raw PCM.
- Cap concurrent clients (e.g. 8) and cast sessions (1).
- Discovery is optional and off-switchable; when off, close both UDP and TCP.

## 8. Platform notes

- **Android**: control server and discovery live in the playback service so they
  survive screen-off. Cast sender taps Media3 decoded PCM; cast receiver uses
  `AudioTrack` with `USAGE_MEDIA`. Requires only `INTERNET` for the link and
  `BLUETOOTH_CONNECT` for local output names; no MediaProjection is needed.
- **Windows/Linux**: both use the shared `melox` Kotlin module. Use `ServerSocket`,
  `DatagramSocket`, and `SourceDataLine`; reuse the PCM already written to Java
  Sound. Firewall prompts must be documented.
- **iOS**: Network.framework `NWListener`/`NWBrowser` or BSD sockets; AVAudioEngine
  tap for cast; `AVAudioPlayerNode` for the receiver.
- All platforms must show their own device name, control port, PIN, and the
  common-device list in settings.

## 9. Versioning

`v` is 1 for this document. Unknown fields must be ignored. A newer major version
may be advertised in `capabilities`; v1 peers should show the device as
“unsupported protocol” rather than silently failing.

## 10. Future extensions (not v1)

- Remote queue editing and playlist transfer.
- Opus/AAC negotiation and adaptive bitrate.
- Device suggestions / handoff (Xiaomi-style flow) using the same discovery and
  transfer primitives.
- Optional account-based trust on top of the PIN/trusted-device layer.
