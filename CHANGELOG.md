# Changelog

## Unreleased

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
