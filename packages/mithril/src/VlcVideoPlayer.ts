// Concept 2, PENDING: a polished, ready-to-drop-in mithril-lynx player —
// play/pause/scrub bar, volume, buffering spinner, audio/subtitle track
// picker, all wired to the raw <vlc-video> element (vlc-video.ts) and styled
// out of the box, the way a real product would ship this (not a bare tag
// with no controls). Not built yet — this stub exists so the package's
// public shape (what a consumer will eventually import) is settled now,
// even though the implementation isn't.
//
// Planned props, sketched against what the raw element already exposes
// (see vlc-video.ts's VlcVideoProps and docs/api-attributes.md):
//   - src, loop, muted, autoplay-on-mount
//   - showControls / controlsTimeout (auto-hide)
//   - onTrackChange, initial audio/subtitle track selection
//   - a themeable style surface (see /docs, TODO)
import type { VlcVideoProps } from "./vlc-video";

export interface VlcVideoPlayerProps extends VlcVideoProps {
	// Reserved for the polished-UI pass — deliberately empty for now.
}

export function VlcVideoPlayer(_props: VlcVideoPlayerProps): never {
	throw new Error(
		"VlcVideoPlayer is not implemented yet — use vlcVideo() from './vlc-video' " +
			"directly and build your own controls, or track " +
			"https://github.com/carlos-sweb/lynx-vlc-video for this component.",
	);
}
