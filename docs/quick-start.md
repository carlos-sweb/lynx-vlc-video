# Quick start

Install both sides of `<vlc-video>`, register the Behavior, render, then call `play`. Playback does **not** start on mount.

| Side | Package |
|---|---|
| JS (mithril-lynx) | `@carlos-sweb/lynx-vlc-video-mithril` (npm) |
| Android host | `io.github.carlos-sweb:lynx-vlc-video:0.1.0` (Maven Central) |

## 1. Install the JS helper

```bash
npm install @carlos-sweb/lynx-vlc-video-mithril
```

Peer: `mithril-runtime` (already in a mithril-lynx app). Skip this step if you render the raw `vlc-video` tag without the typed helper — you still need steps 2–5.

## 2. Add the Android library

In the host app `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.lynxsdk.lynx:lynx:4.1.0") // you already have this
    implementation("io.github.carlos-sweb:lynx-vlc-video:0.1.0")
}
```

`libvlc-all` comes in transitively. Local `include` of this repo: [android-host](android-host.md).

## 3. Register the Behavior

In `LynxViewBuilder` (next to however you register other elements):

```kotlin
import com.carlossweb.lynxvlcvideo.BehaviorGenerator as VlcVideoBehaviorGenerator

builder.addBehaviors(VlcVideoBehaviorGenerator.getBehaviors())
```

Without this line the tag mounts but never becomes a real player.

## 4. Render

Give the element an `id`, a network `src`, and a **CSS size** (e.g. `width: 100%; height: 220px`).

```ts
import m from "mithril-runtime";
import { vlcVideo } from "@carlos-sweb/lynx-vlc-video-mithril";

vlcVideo({
  id: "player",
  class: "Player",
  src: "https://example.com/live.m3u8",
  "object-fit": "contain",
  "network-caching": 1500,
  onplaying: () => {},
  onerror: (e) => console.log(e.detail.errorMsg),
});
```

Raw tag (any Lynx JS runtime):

```html
<vlc-video id="player" src="https://example.com/live.m3u8" object-fit="contain" />
```

## 5. Call `play`

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

## If something fails

| Symptom | Likely cause |
|---|---|
| Tag does nothing | Step 3 missing (`addBehaviors`) |
| `play` fails with missing source | No `src`, or empty string |
| Black box with no picture size | Element has no height in CSS |
| Native `dlopen` / `libc++` crash with `xelement` | Exclude `xelement-animax` — [android-host](android-host.md) |
| Audio after resume, black picture | Fixed in ≥ Android `0.1.0`; update the Maven dependency |

Next: [attributes](api-attributes.md), [methods](api-methods.md), [events](api-events.md), [object-fit](object-fit.md).
