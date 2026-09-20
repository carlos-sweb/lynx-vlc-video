# lynx-vlc-video

A `<vlc-video>` native element for [Lynx](https://lynxjs.org)/Android,
backed by [libVLC](https://www.videolan.org/vlc/libvlc.html) instead of
ExoPlayer/Media3 — built as a more robust alternative to the official
`<video>` (`org.lynxsdk.lynx:xelement-video`), not a replacement for it.

**Why:** confirmed on a real device, `<video>` has real limits — no HLS
support without manually adding `media3-exoplayer-hls`, and even with that
fixed, some real-world streams still fail against ExoPlayer unpredictably.
libVLC's demuxers cover a much wider set of formats/protocols/codecs
natively. Full evidence, including a live Chilean TV channel that failed
against `<video>` and played correctly against `<vlc-video>` — same URL,
same moment — in [`/docs/TESTING.md`](docs/TESTING.md).

## Two packages, two concepts

| Package | What it is | Status |
|---|---|---|
| [`/packages/android`](packages/android) | The raw `<vlc-video>` element itself — a Lynx `Behavior`/`LynxUI`, **framework-agnostic**: works from mithril-lynx, ReactLynx, or any other JS layer Lynx can drive, with zero framework-specific code in this package. | **Done, tested on-device.** |
| [`/packages/mithril`](packages/mithril) | Typed [mithril-lynx](https://github.com/carlos-sweb/mithril-lynx) bindings. Ships `vlcVideo()` (a thin typed wrapper, done) and `VlcVideoPlayer` (a polished, ready-to-use player UI — controls, scrub bar, track picker). | `vlcVideo()` **done**. `VlcVideoPlayer` **pending** (stub only — see that package's README). |

## Quick start

1. Register the element on your Android host app — see
   [`/packages/android`'s README](packages/android/README.md).
2. Render it:
   - Raw: `m("vlc-video", { src: "...", "object-fit": "contain" })`
   - Typed (mithril): `vlcVideo({ src: "...", "object-fit": "contain" })` —
     see [`/packages/mithril`'s README](packages/mithril/README.md).

Full attribute/event/method contract: [`/docs/SPEC.md`](docs/SPEC.md).

## Docs

- [`/docs/SPEC.md`](docs/SPEC.md) — the element's contract (attributes,
  events, UIMethods), framework-agnostic.
- [`/docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — the two-layer design
  (mirrors `xelement-video`'s own split), the libVLC event-mapping table,
  and the two real native packaging issues this hit (and how they were
  fixed) along the way.
- [`/docs/TESTING.md`](docs/TESTING.md) — real-device evidence: what
  `<video>` can't do, what `<vlc-video>` does instead, and one still-open
  known issue (`object-fit: contain` sizing).

## Status

Functional and verified end-to-end on a real device (Android 16). Not yet
published to Maven Central or npm — see each package's README for how to
consume it locally in the meantime. `VlcVideoPlayer` (the polished mithril
UI) is the one open, tracked piece of work.

## License

MIT. Architecturally modeled on `xelement-video` from
[`lynx-family/lynx`](https://github.com/lynx-family/lynx) (Apache 2.0) — see
ARCHITECTURE.md for exactly what's copied in shape vs. original; no source
from that repo is vendored here.
