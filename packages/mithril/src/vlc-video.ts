// Typed wrapper for <vlc-video> (com.carlossweb.lynxvlcvideo.LynxUIVlcVideo,
// see /packages/android) — mithril-runtime's m() takes a bare string tag, so
// there's no JSX.IntrinsicElements-style registry to hang a .d.ts off; a
// small typed factory is what actually gets type-checking on call sites,
// the same way @lynx-js/types' VideoProps types the official <video> for
// React/JSX consumers.
//
// This is the "raw" concept, concept 1: a thin, functional binding with no
// opinion about UI. For a polished, ready-to-drop-in player component
// (controls, progress bar, track picker), see VlcVideoPlayer — currently a
// stub, pending.
import m from "mithril-runtime";

export interface VlcTrackInfo {
	id: number;
	name: string;
}

export interface VlcVideoProps {
	id?: string;
	class?: string;
	src?: string;
	loop?: boolean;
	volume?: number;
	muted?: boolean;
	speed?: number;
	"object-fit"?: "contain" | "cover" | "fill";
	mode?: "queue" | "direct" | "latest";
	"timeupdate-interval"?: number;
	/** libVLC-only: network read-ahead buffer, in ms (Media ":network-caching"). */
	"network-caching"?: number;
	onfirstframe?: (e: { detail: { duration: number } }) => void;
	onplaying?: () => void;
	onpaused?: () => void;
	onstopped?: () => void;
	onended?: () => void;
	onlooped?: () => void;
	onerror?: (e: { detail: { errorCode: number; errorMsg: string } }) => void;
	onbuffering?: (e: { detail: { buffering: number } }) => void;
	ontimeupdate?: (e: { detail: { current: number; duration: number } }) => void;
}

export function vlcVideo(attrs: VlcVideoProps) {
	return m("vlc-video", attrs);
}
