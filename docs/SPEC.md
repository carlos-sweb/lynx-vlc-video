# `<vlc-video>` element spec

This is the framework-agnostic contract `<vlc-video>` exposes once its
`Behavior` is registered on the host app (see [`/packages/android`](../packages/android)).
Any Lynx JS runtime — mithril-lynx, ReactLynx, plain hyperscript — can render
this tag and call these methods; nothing here is mithril-specific.

It intentionally mirrors [Lynx's own `<video>` spec](https://github.com/lynx-family/lynx/blob/main/platform/specs/lynx_video_element_spec.md)
(`org.lynxsdk.lynx:xelement-video`) — same attributes, same events, same
UIMethods — plus the libVLC-only additions at the bottom. A `<video>` user
can switch to `<vlc-video>` by changing the tag name alone; everything else
keeps working the same way.

## Why this exists

`<video>` is backed by ExoPlayer/Media3, which needs a matching extension
module per format (HLS needs `media3-exoplayer-hls` added explicitly — it
isn't there by default) and, even with that added, some real-world streams
still fail unpredictably against it (confirmed on-device — see
[`/docs/TESTING.md`](TESTING.md)). `<vlc-video>` is backed by libVLC, whose
demuxers cover a much broader set of containers/codecs/protocols (HLS, RTSP,
RTMP, MMS, plain files, …) without per-format modules. Confirmed on-device:
a live Chilean TV channel that reproducibly failed against `<video>` played
correctly against `<vlc-video>`, same URL, same moment — see TESTING.md.

## Attributes

| Attribute | Type | Default | |
|---|---|---|---|
| `src` | `string` | — | Online network URL. Only network sources — no bundled/local files. After `src` changes, the previous playback stops immediately. |
| `loop` | `boolean` | `false` | Loop playback. |
| `volume` | `number` (0–1) | `1.0` | |
| `muted` | `boolean` | `false` | Independent of `volume` — unmuting restores the previous volume. |
| `speed` | `number` (0.1–2.0) | `1.0` | Playback rate. |
| `object-fit` | `"contain" \| "cover" \| "fill"` | `"contain"` | Video scaling strategy. |
| `mode` | `"queue" \| "direct" \| "latest"` | `"queue"` | How `play`/`pause`/`stop`/`seek` UIMethod calls queue against each other — see xelement-video's own spec for the exact semantics, copied verbatim here. |
| `timeupdate-interval` | `number` (seconds) | `0.33` | Minimum dispatch interval for `bindtimeupdate`. |
| `network-caching` | `number` (ms) | `0` (off) | **libVLC-only.** Network read-ahead buffer (`Media`'s `:network-caching` option) — the knob that actually matters against a flaky/high-latency source. |

## UIMethods

`play`, `pause`, `stop`, `seek({ position })` — identical contract to
`<video>`, including the queueing modes and error codes.

**libVLC-only additions:**

| Method | Params | Result |
|---|---|---|
| `getAudioTracks` | — | `{ success, tracks: [{ id, name }] }` |
| `setAudioTrack` | `{ id }` | `{ success }` (or `OPERATION_ERROR` if `id` doesn't match a track) |
| `getSubtitleTracks` | — | `{ success, tracks: [{ id, name }] }` |
| `setSubtitleTrack` | `{ id }` | `{ success }` — pass `id: -1` to disable subtitles. |

These are **not** queued through the `mode` machinery — they're treated as
instantaneous queries/switches, not playback-state transitions.

## Events

`bindfirstframe`, `bindplaying`, `bindpaused`, `bindstopped`,
`bindtimeupdate`, `bindended`, `bindlooped`, `binderror`, `bindbuffering` —
same 9 events, same `detail` shapes, as `<video>`.

### Known deviations from the ExoPlayer-backed `<video>`

libVLC's Java API doesn't have a 1:1 equivalent for two things ExoPlayer
exposes directly — these are honestly-documented approximations, not bugs:

- **`bindfirstframe`**: libVLC has no "first frame rendered" event. This
  fires on the first `Vout` (video output attached) event instead — close,
  not a pixel-exact match.
- **`binderror`'s `errorMsg`**: libVLC's `EncounteredError` event carries no
  code or message in the Java bindings (unlike ExoPlayer's
  `PlaybackException`). `errorMsg` is necessarily generic
  (`"libVLC playback error"`); check `adb logcat` (tag `VLC`) for the real
  detail during development.

## What's NOT part of this spec (yet)

A polished player UI (controls, scrub bar, track picker) is a separate,
higher-level concern — see [`/packages/mithril`](../packages/mithril)'s
`VlcVideoPlayer` (currently a stub, pending).
