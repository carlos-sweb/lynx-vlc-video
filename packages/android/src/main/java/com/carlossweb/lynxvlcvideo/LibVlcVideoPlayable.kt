// LynxVideoPlayable implemented against libVLC instead of ExoPlayer — the
// only file in this package that's actually libVLC-specific (mirrors
// xelement-video's own split: LynxUIVideo.kt/LynxVideoPlayable.kt stay
// player-agnostic, LynxVideoView.kt is the one ExoPlayer-specific file).
//
// API facts below were confirmed against real libVLC-Android source and a
// real device (see /docs/TESTING.md), not assumed:
// - org.videolan.android:libvlc-all:3.3.10 dlopen-fails on a recent Android
//   (16) device: "cannot locate symbol '__sfp_handle_exceptions'" — built
//   against an older NDK/bionic. 3.7.x+ builds but requires compileSdk 36.
//   3.6.5 is the newest version confirmed working with compileSdk 34 — see
//   docs/TESTING.md for the exact crash logs of both failure modes.
// - libvlc-all's own libc++_shared.so conflicts with other native deps that
//   bundle a different build of it (e.g. Lynx's animax-sdk, if present) —
//   packaging.jniLibs.pickFirsts can silently pick the WRONG copy and
//   produce a different, equally confusing UnsatisfiedLinkError. Excluding
//   the other source outright is more reliable than pickFirsts — see this
//   module's own consumer proguard/packaging notes in the host app's
//   build.gradle.
// - MediaPlayer.Event has no "first frame rendered" event like ExoPlayer's
//   onRenderedFirstFrame() — Vout (video output attached) is the closest
//   available signal, so bindfirstframe fires there as a best-effort
//   equivalent, not a pixel-exact match.
// - EncounteredError carries no error code/message in the Java bindings
//   (unlike ExoPlayer's PlaybackException) — errorMsg is necessarily
//   generic here.
// - Buffering reports a 0-100 percentage, not a timeline position — the
//   spec's "buffered end position in seconds" is derived by multiplying
//   against the known duration (0 for a live stream with unknown length,
//   same case already handled for IPTV against <video>).
// - Volume is an Int 0-200, not a Float 0-1 — converted at the boundary.
// - videoScale (MediaPlayer.ScaleType) already covers object-fit natively
//   (SURFACE_BEST_FIT/SURFACE_FIT_SCREEN/SURFACE_FILL) — no manual aspect
//   ratio math needed. KNOWN ISSUE: on device, SURFACE_BEST_FIT ("contain")
//   renders visibly smaller than the container instead of scaling up to
//   fill it (see docs/TESTING.md) — not yet root-caused, likely a
//   VLCVideoLayout measure/layout timing quirk rather than the ScaleType
//   choice itself.
package com.carlossweb.lynxvlcvideo

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcVideoPlayable(context: Context) : LynxVideoPlayable {

    private val videoLayout = VLCVideoLayout(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val libVLC = LibVLC(context, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
    private val player = MediaPlayer(libVLC)

    @Volatile private var callback: LynxVideoPlayable.Callback? = null
    private var currentSrc: String? = null
    private var loop = false
    private var volume = 1.0f
    private var muted = false
    private var networkCachingMs = 0
    private var hasFiredFirstFrame = false
    private var isExplicitlyStopped = false
    private var suppressNextPlayingForLoop = false
    private var suppressNextPaused = false
    @Volatile private var isReleased = false
    @Volatile private var cachedDurationMs = 0L
    @Volatile private var cachedCurrentPositionMs = 0L
    @Volatile private var cachedIsPlaying = false

    init {
        player.attachViews(videoLayout, null, true, false)
        runOnMainThread { applyObjectFit("contain") }
        player.setEventListener(::onVlcEvent)
    }

    private fun isOnMainThread() = Looper.myLooper() == mainHandler.looper

    private fun runOnMainThread(allowAfterRelease: Boolean = false, action: () -> Unit) {
        if (!allowAfterRelease && isReleased) return
        if (isOnMainThread()) {
            if (allowAfterRelease || !isReleased) action()
            return
        }
        mainHandler.post { if (allowAfterRelease || !isReleased) action() }
    }

    private fun notifyCallback(action: LynxVideoPlayable.Callback.() -> Unit) {
        runOnMainThread { callback?.action() }
    }

    private fun refreshPlaybackSnapshot() {
        cachedDurationMs = player.length.coerceAtLeast(0)
        cachedCurrentPositionMs = player.time.coerceAtLeast(0)
        cachedIsPlaying = player.isPlaying
    }

    private fun <T> readPlayerState(cachedValue: T, read: () -> T): T =
        if (!isReleased && isOnMainThread()) read() else cachedValue

    private fun onVlcEvent(event: MediaPlayer.Event) {
        if (isReleased) return
        when (event.type) {
            MediaPlayer.Event.Vout -> {
                refreshPlaybackSnapshot()
                if (!hasFiredFirstFrame && event.voutCount > 0) {
                    hasFiredFirstFrame = true
                    val durationMs = cachedDurationMs
                    notifyCallback { onFirstFrame(durationMs) }
                }
            }
            MediaPlayer.Event.Buffering -> {
                refreshPlaybackSnapshot()
                val durationMs = cachedDurationMs
                val bufferedMs = if (durationMs > 0) (durationMs * (event.buffering / 100f)).toLong() else 0L
                notifyCallback { onBuffering(bufferedMs) }
            }
            MediaPlayer.Event.Playing -> {
                refreshPlaybackSnapshot()
                if (suppressNextPlayingForLoop) {
                    suppressNextPlayingForLoop = false
                } else {
                    notifyCallback { onPlaying() }
                }
            }
            MediaPlayer.Event.Paused -> {
                refreshPlaybackSnapshot()
                if (suppressNextPaused) {
                    suppressNextPaused = false
                } else if (!isExplicitlyStopped) {
                    notifyCallback { onPaused() }
                }
            }
            MediaPlayer.Event.EndReached -> {
                refreshPlaybackSnapshot()
                if (loop) {
                    notifyCallback { onLooped() }
                    suppressNextPlayingForLoop = true
                    suppressNextPaused = true
                    runOnMainThread { restartForLoop() }
                } else {
                    notifyCallback { onEnded() }
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                notifyCallback { onError(-1, "libVLC playback error") }
            }
            MediaPlayer.Event.TimeChanged -> {
                refreshPlaybackSnapshot()
                val current = cachedCurrentPositionMs
                val durationMs = cachedDurationMs
                notifyCallback { onTimeUpdate(current, durationMs) }
            }
        }
    }

    private fun restartForLoop() {
        // EndReached leaves libVLC's own Media detached — re-set it from the
        // stored src rather than assuming replay() is safe to call directly.
        currentSrc?.let { setMediaFrom(it) }
        player.play()
    }

    fun getPlayerView(): View = videoLayout

    private fun buildMedia(src: String): Media {
        val media = Media(libVLC, Uri.parse(src))
        if (networkCachingMs > 0) {
            media.addOption(":network-caching=$networkCachingMs")
        }
        return media
    }

    private fun setMediaFrom(src: String) {
        val media = buildMedia(src)
        player.media = media
        media.release()
    }

    override fun setSrc(src: String?) {
        runOnMainThread {
            currentSrc = src
            hasFiredFirstFrame = false
            isExplicitlyStopped = false
            suppressNextPlayingForLoop = false
            player.stop()
            cachedCurrentPositionMs = 0
            if (src.isNullOrEmpty()) {
                player.media = null
                refreshPlaybackSnapshot()
                return@runOnMainThread
            }
            setMediaFrom(src)
            refreshPlaybackSnapshot()
        }
    }

    override fun setLoop(loop: Boolean) {
        runOnMainThread { this.loop = loop }
    }

    override fun setVolume(volume: Float) {
        runOnMainThread {
            this.volume = volume.coerceIn(0f, 1f)
            applyVolume()
        }
    }

    override fun setMuted(muted: Boolean) {
        runOnMainThread {
            this.muted = muted
            applyVolume()
        }
    }

    private fun applyVolume() {
        val effective = if (muted) 0f else volume
        player.volume = (effective * 200).toInt().coerceIn(0, 200)
    }

    override fun setSpeed(speed: Float) {
        runOnMainThread { player.rate = speed.coerceIn(0.1f, 2.0f) }
    }

    override fun setObjectFit(objectFit: String?) {
        runOnMainThread { applyObjectFit(objectFit) }
    }

    private fun applyObjectFit(objectFit: String?) {
        player.videoScale = when (objectFit) {
            "cover" -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            "fill" -> MediaPlayer.ScaleType.SURFACE_FILL
            else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
    }

    override fun setNetworkCaching(caching: Int) {
        runOnMainThread {
            networkCachingMs = caching.coerceAtLeast(0)
            // Only takes effect on the next Media — matches how xelement-video's
            // own props apply "from the next relevant operation", not
            // retroactively to an already-buffering connection.
            currentSrc?.let { setMediaFrom(it) }
        }
    }

    override fun play() {
        runOnMainThread {
            isExplicitlyStopped = false
            if (player.media == null && !currentSrc.isNullOrEmpty()) {
                setMediaFrom(currentSrc!!)
            }
            player.play()
            refreshPlaybackSnapshot()
        }
    }

    override fun pause() {
        runOnMainThread {
            player.pause()
            refreshPlaybackSnapshot()
        }
    }

    override fun stop() {
        runOnMainThread {
            isExplicitlyStopped = true
            suppressNextPaused = suppressNextPaused || player.isPlaying
            player.stop()
            cachedCurrentPositionMs = 0
            refreshPlaybackSnapshot()
            notifyCallback { onStopped() }
        }
    }

    override fun seek(positionMs: Long) {
        runOnMainThread {
            player.time = positionMs.coerceAtLeast(0)
            cachedCurrentPositionMs = positionMs
        }
    }

    override fun getDuration(): Long = readPlayerState(cachedDurationMs) { player.length.coerceAtLeast(0) }

    override fun getCurrentPosition(): Long =
        readPlayerState(cachedCurrentPositionMs) { player.time.coerceAtLeast(0) }

    override fun isPlaying(): Boolean = readPlayerState(cachedIsPlaying) { player.isPlaying }

    override fun getAudioTracks(): List<LynxVideoPlayable.TrackInfo> =
        readPlayerState(emptyList()) {
            player.audioTracks?.map { LynxVideoPlayable.TrackInfo(it.id, it.name) } ?: emptyList()
        }

    override fun setAudioTrack(id: Int): Boolean = readPlayerState(false) { player.setAudioTrack(id) }

    override fun getSubtitleTracks(): List<LynxVideoPlayable.TrackInfo> =
        readPlayerState(emptyList()) {
            player.spuTracks?.map { LynxVideoPlayable.TrackInfo(it.id, it.name) } ?: emptyList()
        }

    override fun setSubtitleTrack(id: Int): Boolean = readPlayerState(false) { player.setSpuTrack(id) }

    override fun setCallback(callback: LynxVideoPlayable.Callback?) {
        this.callback = callback
    }

    override fun release() {
        isReleased = true
        mainHandler.removeCallbacksAndMessages(null)
        runOnMainThread(allowAfterRelease = true) {
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            libVLC.release()
        }
    }
}
