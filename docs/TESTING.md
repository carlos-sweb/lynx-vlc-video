# Testing evidence

Everything below was run on a real device (Samsung SM-A075M, Android 16),
not a simulator/emulator — the whole point of this element is real-world
playback robustness, which doesn't show up in unit tests.

## 1. `<video>`'s real limits (the motivation)

- `org.lynxsdk.lynx:xelement-video` has no HLS support out of the box: a
  live `.m3u8` `src` throws
  `IllegalStateException: No suitable media source factory found for content
  type: 2` the moment `play()` is invoked — `media3-exoplayer-hls` isn't
  pulled in by default. Adding it fixes HLS specifically.
- Even with HLS fixed, real IPTV-style streams still fail against ExoPlayer
  unpredictably: some stall silently forever (no `bindplaying`, no
  `binderror`, ever), others eventually fire `binderror` with
  `"Source error"` well after the call returns.

## 2. `<vlc-video>` works where `<video>` doesn't — same stream, same moment

Both elements pointed at the same live Chilean channel
(`https://iptv.bitred.cl/mega/index.m3u8`, Mega Chile), tapped one after the
other:

```
<video>     (ExoPlayer): Error tras 3 intentos — sin respuesta tras 8s
<vlc-video> (libVLC):    Playing — 1:09 / 0:20   [real frames, chyron
                          text updating live: "PAGÓ FIANZA DE $5 MILLONES
                          Y DESAPARECIÓ"]
```

`adb logcat` for the same window confirms `<vlc-video>` genuinely decoding,
not just reporting success:

```
libvlc demux: Changing stream format Unknown -> TS
libvlc decoder: output: ... 1920x1080 ...
```

## 3. Two real native crashes, and their fixes

**`libvlc-all:3.3.10` on Android 16:**

```
dlopen failed: cannot locate symbol "__sfp_handle_exceptions" referenced by
".../libvlc.so"...
E VLC/LibVLC: Can't load vlcjni library: java.lang.UnsatisfiedLinkError: ...
I System.exit called, status: 1
```

Fix: use `libvlc-all:3.6.5` (newest version that doesn't also require
`compileSdk 36`).

**`libc++_shared.so` conflict** (after upgrading to 3.6.5, with
`packaging.jniLibs.pickFirsts` picking the wrong copy):

```
dlopen failed: cannot locate symbol
"_ZTTNSt6__ndk118basic_stringstreamIcNS_11char_traitsIcEENS_9allocatorIcEEEE"
referenced by ".../libvlc.so"...
```

Fix: exclude the OTHER native SDK's conflicting transitive dependency
outright instead of relying on `pickFirsts` — see ARCHITECTURE.md.

## 4. Stall/retry mechanism, verified end-to-end

The consuming app (not part of this repo) added a watchdog: if `play()`
produces neither `bindplaying` nor `binderror` within 8s, treat it as a
stall, `stop()` + retry, up to 3 attempts, then report a final status. Full
observed sequence against a since-dead CDN (`cdn1tlinkgo.tlink.cl`),
captured via `adb logcat`, one tap, fully isolated (confirmed no
cross-element interference by grepping for each element's own `id`):

```
play (11:40:00) -> 8s stall -> stop (11:40:08) -> 2s delay -> play (11:40:10)
-> 8s stall -> stop (11:40:18) -> 2s delay -> play (11:40:20)
-> 8s stall -> stop (11:40:28) -> 2s delay -> play (11:40:30)
-> 8s stall -> "Error tras 3 intentos — sin respuesta tras 8s"
```

Native side: with default `mode="queue"`, `stop` now preempts an in-flight
`play()` that never fired `bindplaying`/`binderror`. Before that, the host
retry's `stop()` was only queued and never reached libVLC.

Note: `<video>`'s own `src` connects eagerly on mount (ExoPlayer's
`prepare()` runs as soon as `setSrc` is called, not deferred until `play()`)
— so if you see BOTH a `<video>` and a `<vlc-video>` retrying and you only
tapped one, that's very likely this, not a bug: `<video>` started its own,
independent retry cycle just from being mounted with a `src` pointed at a
dead stream.

## 5. `object-fit` (contain / cover / fill)

`VideoHelper.updateVideoSurfaces()` in libvlc-all 3.6.5 swaps the frame's
width/height when the **Activity** is in portrait, assuming `VLCVideoLayout`
is fullscreen. An embedded landscape box (e.g. `100% × 220px`) then gets
contain computed against the swapped size — on the SM-A075M that was a
`412×232` surface inside a `630×412` box.

Fix: `MediaPlayer.setUseOrientationFromBounds(true)` so portrait is
`height > width` of the **element**, plus `updateVideoSurfaces()` after
layout and the first `Vout`. `contain` should touch at least one pair of
edges; `cover` fills and may crop; `fill` stretches.

## 6. Background / resume (black picture, audio still playing)

Leaving the app (Home, WhatsApp, mail) destroys the TextureView surface.
libVLC keeps decoding audio against a dead vout, so on return the box is
black. `LibVlcVideoPlayable` now `detachViews()` on activity `onStop` and
`attachViews()` on `onStart` (same pattern as VLC’s own Android apps).

## Reproducing this

This repo's `/packages/android` module builds and packages cleanly on its
own (`./gradlew :packages:android:assembleDebug`), but the on-device
scenarios above need a host app that registers the `Behavior` and renders
the tag — there is no app in this repo. The quickest way to reproduce: scaffold
any Lynx Android host, add this module + `libvlc-all:3.6.5` +
`lynx-processor` per `/packages/android/README.md`, register the Behavior,
and render `<vlc-video src="...">`.
