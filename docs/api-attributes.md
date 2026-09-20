# Attributes

Set these as Lynx props on `<vlc-video>`. In mithril they are keys of `vlcVideo({ … })`. Types match `VlcVideoProps` in `/packages/mithril/src/vlc-video.ts`.

| Attribute | Type | Default | Effect |
|---|---|---|---|
| `src` | `string` | — | Network URL. **No** bundled/local files. Changing `src` stops the previous item immediately. |
| `loop` | `boolean` | `false` | Replay from the start when the item ends. |
| `volume` | `number` 0–1 | `1.0` | Linear gain. `1.0` is unity (libVLC 100), not a 200% boost. |
| `muted` | `boolean` | `false` | Independent of `volume`. Unmuting restores the last volume. |
| `speed` | `number` 0.1–2.0 | `1.0` | Playback rate. |
| `object-fit` | `"contain"` \| `"cover"` \| `"fill"` | `"contain"` | How the picture sits in the box. See [object-fit](object-fit.md). |
| `mode` | `"queue"` \| `"direct"` \| `"latest"` | `"queue"` | How `play`/`pause`/`stop`/`seek` queue. See [playback mode](playback-mode.md). |
| `timeupdate-interval` | `number` (seconds) | `0.33` | Minimum gap between `timeupdate` events. |
| `network-caching` | `number` (ms) | `0` (off) | **libVLC-only.** Network read-ahead. Applied on the next Media build (mount / `src` change / loop), not while a stream is already playing. |

```js
vlcVideo({
  id: "player",
  src: "https://example.com/live.m3u8",
  volume: 1,
  muted: false,
  "object-fit": "contain",
  "network-caching": 1500,
});
```

## Pitfalls

- `src` is required before `play` succeeds (`fail` → `"missing video source"`).
- `network-caching` on a live update while playing is stored and used on the **next** rebuild, so the current item keeps going.
- `volume` is 0–1. Values are clamped.
