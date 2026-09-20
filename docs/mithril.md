# mithril-lynx

npm package: `@carlos-sweb/lynx-vlc-video-mithril`. TypeScript only — no native code and **no `postinstall`**. The host must still register the Android Behavior ([quick start](quick-start.md), [Android host](android-host.md)).

## Install

```bash
npm install @carlos-sweb/lynx-vlc-video-mithril
```

Peer: `mithril-runtime` (your mithril-lynx app already has it).

Until the package is on the registry, use a path/workspace dependency on this repo’s `packages/mithril`.

## What the end user installs (two sides)

| Side | Tool | Package |
|---|---|---|
| JS / Lynx bundle | npm | `@carlos-sweb/lynx-vlc-video-mithril` → `vlcVideo()` |
| Android host | Gradle / Maven Central | `io.github.carlos-sweb:lynx-vlc-video:0.1.0` + `addBehaviors(BehaviorGenerator…)` |

npm cannot drop a `.aar` into an Android project. That is why there is no `postinstall` Gradle hook — it would be brittle and surprising. Document the host steps instead.

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

## `VlcVideoPlayer`

Not implemented. The export throws. Do not use it; use `vlcVideo()` and your own controls.

## Publish (maintainers)

From `packages/mithril`:

```bash
npm run build
npm publish --access public
```

`prepublishOnly` runs the build. Publish the Android library separately (Maven / `include` project) — see [android-host](android-host.md).
