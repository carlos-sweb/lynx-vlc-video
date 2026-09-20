# Changelog

## Unreleased

- Shared one process-wide `LibVLC` (refcount) across `<vlc-video>` elements;
  each element still owns its own `MediaPlayer`.
- `mode="queue"`/`"latest"`: `stop` preempts an in-flight `play`/`pause` so
  a stalled IPTV `play()` can actually be stopped and retried.
- Map `volume` 0–1 to libVLC 0–100 (unity), not 0–200.
- Tear down the VLC event listener before releasing `MediaPlayer` so the
  native event thread cannot JNI a destroyed player.
- `network-caching` no longer replaces `player.media` while playing.
- `object-fit` (`contain` / `cover` / `fill`) sizes against the element box
  (`setUseOrientationFromBounds`), so `contain` no longer leaves a 16:9
  picture floating inside the black frame on portrait devices.
- Split consumer docs into short concept pages under `/docs` (quick start,
  Android host, attributes, methods, events, object-fit, playback mode,
  mithril). `SPEC.md` is replaced by those API pages.

- Initial `<vlc-video>` element (`/packages/android`): full parity with
  `<video>`'s attribute/event/UIMethod contract, plus libVLC-only
  `network-caching`, `getAudioTracks`/`setAudioTrack`,
  `getSubtitleTracks`/`setSubtitleTrack`.
- `vlcVideo()` typed mithril-lynx binding (`/packages/mithril`).
- `VlcVideoPlayer` (polished mithril player UI): stubbed, not implemented.
- Verified end-to-end on a real device: control playback (MDN CC0 sample),
  a live public traffic camera (NYSDOT), and a live Chilean TV channel
  (Mega) that fails against `<video>` and succeeds against `<vlc-video>` —
  see `/docs/TESTING.md`.
- Fixed two real native crashes along the way (`libvlc-all` version vs.
  device bionic ABI; `libc++_shared.so` conflict with `xelement-animax`) —
  documented in `/docs/ARCHITECTURE.md` and `/docs/TESTING.md` so they
  aren't rediscovered.
