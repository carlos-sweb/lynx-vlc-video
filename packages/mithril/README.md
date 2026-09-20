# `@carlos-sweb/lynx-vlc-video-mithril`

Typed [mithril-lynx](https://github.com/carlos-sweb/mithril-lynx) bindings
for `<vlc-video>`. This package ships **two concepts**:

1. **`vlcVideo()`** — a thin, functional, typed wrapper around
   `m("vlc-video", attrs)`. No opinions, no styling, no controls — you build
   the UI. **Done, working.**
2. **`VlcVideoPlayer`** — a polished, ready-to-drop-in player component
   (controls, scrub bar, volume, buffering spinner, track picker), styled
   out of the box. **Pending — currently a stub that throws.** The
   raw element's contract is already settled (see `/docs/SPEC.md`), so this
   is purely a UI-building task, not a design-unknown.

Requires the native `<vlc-video>` element registered on the host app first —
see [`/packages/android`](../android). This package has no native code of
its own; it's pure TypeScript over `m()`.

## Install

Not yet published to npm. For now, depend on this package by path/workspace
reference from within this repo, or copy `src/vlc-video.ts` directly — it
has no dependency beyond `mithril-runtime` (peer).

## Usage (concept 1, available now)

```ts
import m from "mithril-runtime";
import { vlcVideo } from "@carlos-sweb/lynx-vlc-video-mithril";

let status = "Ready";

export function view() {
  return m("view", [
    vlcVideo({
      id: "player",
      src: "https://example.com/live.m3u8",
      "object-fit": "contain",
      "network-caching": 1500,
      onplaying: () => { status = "Playing"; },
      onerror: (e) => { status = `Error: ${e.detail.errorMsg}`; },
    }),
    m("text", status),
  ]);
}
```

`VlcVideoProps` is fully typed — every attribute and event from
[`/docs/SPEC.md`](../../docs/SPEC.md), including the libVLC-only
`network-caching` and the track-selection UIMethods (called the normal way,
via `lynx.createSelectorQuery()`, same as `play`/`pause`/`stop`).

## Usage (concept 2, pending)

```ts
import { VlcVideoPlayer } from "@carlos-sweb/lynx-vlc-video-mithril";

// Not implemented yet — throws. Tracked in this repo; see VlcVideoPlayer.ts
// for the planned prop surface.
VlcVideoPlayer({ src: "..." });
```

## Build

```bash
npm install
npm run build   # tsc -b, emits dist/
```

## Why a stub instead of nothing

`VlcVideoPlayer`'s planned prop surface is sketched in `src/VlcVideoPlayer.ts`
now, before the implementation, so the package's public shape (what a
consumer will eventually import and how) is settled early — the polished-UI
work that's left is genuinely a UI/design pass, not an API-design one.
