# Quick start

Two steps: register `<vlc-video>` on the Android host, then render the tag and call `play`. Full Gradle notes are in [Android host](android-host.md).

## 1. Register on the host

In `LynxViewBuilder` (next to however you already register `<video>`):

```kotlin
import com.carlossweb.lynxvlcvideo.BehaviorGenerator as VlcVideoBehaviorGenerator

builder.addBehaviors(VlcVideoBehaviorGenerator.getBehaviors())
```

Add the Android library as a local module (it is not on Maven Central yet). If the host also depends on `xelement`, exclude `xelement-animax` — see [Android host](android-host.md).

## 2. Render and play

Give the element an `id`, a network `src`, and a size. Playback does **not** start on mount; call `play`.

```js
// mithril-lynx — see also docs/mithril.md
import { vlcVideo } from "@carlos-sweb/lynx-vlc-video-mithril";

vlcVideo({
  id: "player",
  class: "Player",
  src: "https://example.com/live.m3u8",
  "object-fit": "contain",
  "network-caching": 1500,
});
```

```js
lynx
  .createSelectorQuery()
  .select("#player")
  .invoke({
    method: "play",
    success: () => {},
    fail: (res) => console.log(res),
  })
  .exec();
```

Raw (any Lynx JS runtime) is the same tag:

```html
<vlc-video id="player" src="https://example.com/live.m3u8" object-fit="contain" />
```

## Pitfalls

- `src` is a **network URL only** — no bundled/local files.
- Changing `src` stops the previous item immediately.
- Give the element a CSS size (the demo uses `.Video { width: 100%; height: 220px; }`). Without a height the black box can collapse.
- Live IPTV often needs `network-caching` (milliseconds). `1500` is a reasonable first try.
- Leaving the app and coming back used to leave a black picture with audio still playing. The native layer re-attaches the video surface on activity start.
