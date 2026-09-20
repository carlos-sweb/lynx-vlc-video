# lynx-vlc-video

A `<vlc-video>` native element for [Lynx](https://lynxjs.org)/Android, backed by [libVLC](https://www.videolan.org/vlc/libvlc.html). Same attributes, events, and UIMethods as Lynx `<video>`, plus `network-caching` and track selection.

Start here: **[docs/](docs/README.md)** (quick start, attributes, methods, events).

| Package | What it is | Distribute as |
|---|---|---|
| [`packages/android`](packages/android) | The element itself (`Behavior` / `LynxUI`). Framework-agnostic. | Maven Central: `io.github.carlos-sweb:lynx-vlc-video` |
| [`packages/mithril`](packages/mithril) | `vlcVideo()` typed helper for mithril-lynx. `VlcVideoPlayer` is a stub. | npm: `@carlos-sweb/lynx-vlc-video-mithril` |

MIT. Verified on Android 16 (Samsung SM-A075M).
