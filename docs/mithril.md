# mithril-lynx helper

npm: `@carlos-sweb/lynx-vlc-video-mithril`. TypeScript only — no native code, no `postinstall`. You must still register the Android Behavior ([quick-start](quick-start.md)).

## Install

```bash
npm install @carlos-sweb/lynx-vlc-video-mithril
```

Peer: `mithril-runtime`.

| Side | Tool | Package |
|---|---|---|
| JS / Lynx bundle | npm | `@carlos-sweb/lynx-vlc-video-mithril` → `vlcVideo()` |
| Android host | Maven Central | `io.github.carlos-sweb:lynx-vlc-video:0.1.0` + `addBehaviors(…)` |

## `vlcVideo()`

Thin typed wrapper around `m("vlc-video", attrs)`. No chrome, no CSS.

```ts
import m from "mithril-runtime";
import { vlcVideo } from "@carlos-sweb/lynx-vlc-video-mithril";

export function view() {
  return m("view", [
    vlcVideo({
      id: "player",
      class: "Player",
      src: "https://example.com/live.m3u8",
      "object-fit": "contain",
      "network-caching": 1500,
      onplaying: () => {},
      onerror: (e) => console.log(e.detail.errorMsg),
    }),
  ]);
}
```

`VlcVideoProps` covers every public attribute and event. Methods use `lynx.createSelectorQuery()` — [methods](api-methods.md).

## `VlcVideoPlayer`

Not implemented. The export throws. Use `vlcVideo()` and your own controls.

Publishing this package: [publishing](publishing.md).
