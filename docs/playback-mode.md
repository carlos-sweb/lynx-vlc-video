# Playback mode

`mode` controls how `play`, `pause`, `stop`, and `seek` run when another call is still in flight. Track methods ignore `mode`. Default is `"queue"` (same idea as Lynx `<video>`).

| `mode` | Behavior |
|---|---|
| `queue` | Calls wait in line. `play`/`pause`/`stop` stay “in flight” until their event (`playing` / `paused` / `stopped`). |
| `latest` | If busy, keep only the newest pending call. |
| `direct` | Run immediately; do not wait for the matching event to `success`. |

```js
vlcVideo({
  id: "player",
  src: url,
  mode: "queue",
});
```

## Stalled `play` and `stop`

A live URL can stall: no `playing`, no `error`. In `queue` that used to pin the queue forever, so a later `stop` never reached libVLC.

`stop` is a hard reset: if a `play` (or `pause`) is waiting, it is finished with `"request canceled by stop"` and `stop` runs now. A host watchdog of “8s then `stop` then `play`” works with the default `mode`.

## Pitfalls

- Default `queue` is what you want for button UIs (play then pause in order).
- Use `direct` only if you are sure you will not stack calls.
- `pause` does **not** preempt a hung `play` (`pause` no-ops when `isPlaying` is still false). Use `stop`.
