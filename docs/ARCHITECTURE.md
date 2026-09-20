# Architecture

## Two layers, same split Lynx's own `<video>` uses

`<vlc-video>` copies the exact architecture of `org.lynxsdk.lynx:xelement-video`
(confirmed by reading its real source in
[`lynx-family/lynx`](https://github.com/lynx-family/lynx/tree/main/platform/android/lynx_xelement/lynx_xelement_video)):

```
LynxUIVlcVideo          <-- player-agnostic: UIMethod queueing (queue/direct/
  (LynxUI subclass)         latest), error codes, event dispatch, timeupdate
       |                    polling. Copied in shape from xelement-video's
       | implements         own LynxUIVideo.kt — this part never changes
       v                    no matter which player backs it.
LynxVideoPlayable       <-- the interface that separates "Lynx" from "the
  (interface)               real player". A local copy of xelement-video's
       ^                    own internal interface (not published standalone).
       | implements
       |
LibVlcVideoPlayable     <-- the ONLY libVLC-specific file. Everything below
  (this repo's own          this line is swappable for a different engine
   real code)                without touching LynxUIVlcVideo at all.
       |
       | acquire/release
       v
SharedLibVlc            <-- one process-wide LibVLC (refcount). Each
                            element still owns its own MediaPlayer.
```

This is why adding track selection and `network-caching` only touched two
files (`LynxVideoPlayable`'s interface + `LibVlcVideoPlayable`'s
implementation, plus a couple of new `@LynxUIMethod`s on `LynxUIVlcVideo`) —
the queueing/event machinery didn't need to change.

## Registration: annotations, not hand-written boilerplate

`LynxUIVlcVideo` is annotated, not manually wired:

```kotlin
@LynxGeneratorName(packageName = "com.carlossweb.lynxvlcvideo")
@LynxBehavior(tagName = ["vlc-video"], isCreateAsync = false)
open class LynxUIVlcVideo(...) : LynxUI<View>(...), LynxVideoPlayable.Callback {
    @LynxProp(name = "src") fun setSrc(src: String?) { ... }
    @LynxUIMethod fun play(params: ReadableMap?, callback: Callback?) { ... }
}
```

`org.lynxsdk.lynx:lynx-processor` (confirmed published standalone on Maven
Central — this is the same `kapt project(':LynxProcessor')` xelement-video
itself uses internally) generates `BehaviorGenerator`, `$$PropsSetter`, and
`$$MethodInvoker` at build time from these annotations. Register it in the
host app exactly like `xelement-video`'s own:

```kotlin
import com.carlossweb.lynxvlcvideo.BehaviorGenerator as VlcVideoBehaviorGenerator
// ...
builder.addBehaviors(VlcVideoBehaviorGenerator.getBehaviors())
```

## Event mapping (libVLC → Lynx)

| libVLC `MediaPlayer.Event` | Lynx callback | Note |
|---|---|---|
| `Vout` (first one) | `onFirstFrame` | Best-effort — no native "first frame" event, see [events](api-events.md). |
| `Buffering` (0–100%) | `onBuffering` | Converted to ms via `duration * pct/100`; 0 for live/unknown duration. |
| `Playing` | `onPlaying` | Suppressed once per loop restart (`suppressNextPlayingForLoop`) so looping doesn't re-fire `bindplaying`. |
| `Paused` | `onPaused` | Suppressed during an internal (non-user) stop, same pattern. |
| `EndReached` | `onEnded` / `onLooped` + manual restart | libVLC has no native repeat mode used here — looping is done by hand, re-building the `Media` and calling `play()` again, matching how the ExoPlayer-backed `LynxVideoView` also does this manually. |
| `EncounteredError` | `onError` | No code/message available from libVLC's Java API — see [events](api-events.md). |
| `TimeChanged` | `onTimeUpdate` | |

Volume (`Int` 0–100, 100 = 0 dB unity) and `object-fit` (`MediaPlayer.ScaleType`)
are converted/mapped at the `LibVlcVideoPlayable` boundary — the spec's 0–1
`volume` maps `1.0` to 100 so it matches `<video>`, not libVLC's 200% boost.
`object-fit` uses ScaleType against the **element** box
(`setUseOrientationFromBounds(true)`), not the Activity portrait flag —
otherwise VideoHelper swaps width/height in portrait and `contain` undersizes
an embedded landscape view.

## Native packaging: two real failure modes, both solved

Building this against a real, modern Android device surfaced two genuine
native-linking problems — neither is a Kotlin/Lynx issue, both are
libVLC/Gradle packaging issues, documented here so they aren't rediscovered:

1. **`libvlc-all:3.3.10` dlopen-fails on Android 16**:
   `cannot locate symbol "__sfp_handle_exceptions"` — that release was built
   against an older NDK/bionic. `3.7.x`+ fixes it but requires `compileSdk
   36` (needs a newer AGP than 8.5.2 supports). **`3.6.5`** is the newest
   version confirmed to both fix the crash and stay on `compileSdk 34`.

2. **`libc++_shared.so` conflict**: if the host app also pulls in another
   native SDK that bundles its own `libc++_shared.so` (e.g. Lynx's
   `animax-sdk`, via `xelement-animax`, itself pulled transitively by
   `xelement`), Gradle's `packaging.jniLibs.pickFirsts` can silently pick
   the *wrong* copy — producing a **different** `UnsatisfiedLinkError`
   (a libc++ symbol, not a bionic one) that looks like a new bug but is the
   same class of problem. Excluding the other source's transitive artifact
   outright (`exclude(group = ..., module = "xelement-animax")`) is more
   reliable than gambling on `pickFirsts` — see the host app example in
   `/packages/android`'s own dependency notes and `/docs/TESTING.md` for
   the exact crash logs of both.

## What's deliberately NOT in `/packages/android`

No app, no `MainActivity`, no `AndroidManifest` beyond the empty one a
library module needs. This package is the raw element only — see
`/docs/TESTING.md` for how it was exercised against a real host app during
development (that test app isn't part of this repo).
