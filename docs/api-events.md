# Events

Same nine events as Lynx `<video>`. In mithril they are `onplaying`, `onerror`, … On the raw tag they are `bindplaying`, `binderror`, …. Times in `detail` are **seconds**.

| Event | `detail` | When |
|---|---|---|
| `firstframe` | `{ duration }` | First video output attached (libVLC has no pixel-exact “first frame”; this is `Vout`). |
| `playing` | — | Playback is running. |
| `paused` | — | User pause (not an internal stop). |
| `stopped` | — | After `stop`. |
| `timeupdate` | `{ current, duration }` | While playing, at least `timeupdate-interval` apart (default 0.33s). |
| `ended` | — | Item finished and `loop` is false. |
| `looped` | — | Item finished and `loop` is true (then it restarts). |
| `error` | `{ errorCode, errorMsg }` | Native error or `play` with no `src`. |
| `buffering` | `{ buffering }` | Buffered end, in seconds (0 for live / unknown duration). |

```js
vlcVideo({
  id: "player",
  src: url,
  onplaying: () => { status = "Playing"; },
  onstopped: () => { status = "Stopped"; },
  ontimeupdate: (e) => {
    const { current, duration } = e.detail;
  },
  onerror: (e) => {
    status = e.detail.errorMsg;
  },
});
```

## Honest gaps vs `<video>`

- `firstframe` is “video output attached”, not “this pixel was drawn”.
- `errorMsg` is always `"libVLC playback error"` for native failures. libVLC’s Java `EncounteredError` has no message. Use `adb logcat` with tag `VLC` while developing.

## Pitfalls

- `timeupdate` is omitted when duration is 0 (typical live window). Do not wait on it to know that a live stream is playing — use `playing`.
- `play` in `mode="queue"` does not `success` until `playing` or `error`. A stall has neither; call `stop` to unblock (see [playback mode](playback-mode.md)).
