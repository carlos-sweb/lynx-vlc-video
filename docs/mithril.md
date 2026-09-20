# mithril-lynx

`/packages/mithril` is TypeScript only. It does not ship native code. The host must still register the Android Behavior ([quick start](quick-start.md)).

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

`VlcVideoProps` lists every public attribute and event. Methods are still `lynx.createSelectorQuery().select("#player").invoke({ method: "play" })` — see [methods](api-methods.md).

The package is not on npm yet. Depend on it by path from this repo, or copy `packages/mithril/src/vlc-video.ts` (`mithril-runtime` peer).

## `VlcVideoPlayer`

Not implemented. The export throws. Do not use it; use `vlcVideo()` and your own controls.
