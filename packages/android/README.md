# `lynx-vlc-video` (raw, framework-agnostic)

The `<vlc-video>` native element itself — a `Behavior`/`LynxUI` registered
against Lynx's Element PAPI, the same layer `org.lynxsdk.lynx:xelement-video`
(`<video>`) is built on. **No framework-specific code lives here.** Once its
`Behavior` is registered in your Android host app, any JS runtime Lynx can
drive — mithril-lynx, ReactLynx, plain hyperscript — can render
`<vlc-video src="...">` and call its methods.

See [`/docs/SPEC.md`](../../docs/SPEC.md) for the full attribute/event/method
contract, and [`/docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) for how
it's built.

## Install

`build.gradle.kts` of your Android host app:

```kotlin
plugins {
    id("org.jetbrains.kotlin.kapt") // needed alongside your existing kotlin-android plugin
}

dependencies {
    implementation("org.lynxsdk.lynx:lynx:4.1.0") // you already have this for <video>/the core SDK

    // This module (once published — see "Publishing" below; until then,
    // include it as a local/composite build):
    implementation(project(":packages:android")) // or the published coordinates

    // The annotation processor that generates this module's Behavior
    // registration at YOUR build time (it's not baked into the module
    // itself — see ARCHITECTURE.md for why):
    kapt("org.lynxsdk.lynx:lynx-processor:4.1.0")
}
```

**If your host app also uses `xelement:4.1.0`** (for `<input>`/`<refresh>`
etc.), read [`/docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md)'s
"Native packaging" section before you build — there's a real
`libc++_shared.so` conflict between `xelement-animax` (pulled in
transitively) and `libvlc-all` that needs an explicit `exclude`, not just
`pickFirsts`.

## Register it

`MainActivity.kt` (or wherever you build your `LynxViewBuilder`), next to
however you already register `<video>`/`<input>`/etc.:

```kotlin
import com.carlossweb.lynxvlcvideo.BehaviorGenerator as VlcVideoBehaviorGenerator

// ...
builder.addBehaviors(VlcVideoBehaviorGenerator.getBehaviors())
```

## Use it

From any JS framework, once registered — this is the whole point of "raw":

```html
<vlc-video
  src="https://example.com/live.m3u8"
  object-fit="contain"
  network-caching="1500"
/>
```

```js
lynx.createSelectorQuery().select("#my-video").invoke({
  method: "play",
  success: () => {},
  fail: (res) => console.log(res),
}).exec();
```

For a typed, mithril-lynx-specific binding instead of raw `m("vlc-video",
...)` calls, see [`/packages/mithril`](../mithril).

## Build / verify locally

```bash
./gradlew :packages:android:assembleDebug
```

This only proves the module compiles and packages — it can't prove
on-device playback by itself. See [`/docs/TESTING.md`](../../docs/TESTING.md)
for the real-device evidence this was actually validated against, including
two native crashes and their fixes (read that before you file a "doesn't
build on a real device" issue — it might already be documented there).

## Publishing

Not yet published to Maven Central / a Maven repo. Until then, consume this
as a local project or composite build (`includeBuild`) from your host app's
`settings.gradle.kts`.
