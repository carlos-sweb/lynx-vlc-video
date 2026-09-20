# lynx-vlc-video

A `<vlc-video>` native element for [Lynx](https://lynxjs.org)/Android, backed by [libVLC](https://www.videolan.org/vlc/libvlc.html). Same attributes, events, and UIMethods as Lynx `<video>`, plus `network-caching` and track selection.

Start here: **[docs/](docs/README.md)** (quick start, attributes, methods, events).

| Package | What it is |
|---|---|
| [`packages/android`](packages/android) | The element itself (`Behavior` / `LynxUI`). Framework-agnostic. |
| [`packages/mithril`](packages/mithril) | `vlcVideo()` typed helper for mithril-lynx. `VlcVideoPlayer` is a stub. |

Not on Maven Central / npm yet. MIT. Verified on Android 16 (Samsung SM-A075M).
