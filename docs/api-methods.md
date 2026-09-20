# Methods

Call methods with `lynx.createSelectorQuery()` on the element’s `id`. `play`, `pause`, `stop`, and `seek` honor [`mode`](playback-mode.md). Track methods do **not** — they run immediately.

```js
lynx
  .createSelectorQuery()
  .select("#player")
  .invoke({
    method: "play",
    success: (res) => {},
    fail: (res) => console.log(res.code, res.data),
  })
  .exec();
```

## Playback

| Method | Params | When `success` fires |
|---|---|---|
| `play` | — | After `playing` (or immediately if already playing). |
| `pause` | — | After `paused` (or immediately if already paused). |
| `stop` | — | After `stopped`. In `queue`/`latest`, `stop` **cancels** an in-flight `play`. |
| `seek` | `{ position }` seconds | Immediately if `position` is in range. |

`seek` `position` is **seconds** (not ms). It must be ≥ 0. If duration is known (`duration > 0`), `position` must be ≤ duration. Live streams with duration `0` skip that upper bound.

```js
.invoke({
  method: "seek",
  params: { position: 12.5 },
  success: () => {},
  fail: (res) => {},
})
```

On `fail`, the callback payload includes `success: false`, `msg`, and `errorCode`. Typical messages: `"missing video source"`, `"missing position param"`, `"position out of range"`, `"video view is not ready"`, `"request canceled by stop"`.

## Tracks (libVLC-only)

| Method | Params | Result |
|---|---|---|
| `getAudioTracks` | — | `{ success: true, tracks: [{ id, name }] }` |
| `setAudioTrack` | `{ id }` | `{ success }` or `fail` if `id` is missing / unknown |
| `getSubtitleTracks` | — | `{ success: true, tracks: [{ id, name }] }` |
| `setSubtitleTrack` | `{ id }` | `{ success }`. Pass `id: -1` to disable subtitles. |

```js
.invoke({
  method: "setSubtitleTrack",
  params: { id: -1 },
  success: () => {},
  fail: (res) => {},
})
```

## Pitfalls

- Nothing autoplays. Mount with `src`, then `play`.
- `pause` on a live IPTV stream may be a no-op in libVLC; `stop` always reaches the player.
- Track ids come from `get*Tracks`. Do not hard-code them across streams.
