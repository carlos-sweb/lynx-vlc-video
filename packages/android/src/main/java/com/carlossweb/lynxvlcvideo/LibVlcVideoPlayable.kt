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
// - Volume is an Int 0-100 at unity (100 = 0 dB). Values above 100 amplify;
//   the spec's 0-1 range maps to 0-100 so volume=1.0 matches <video>.
// - videoScale (MediaPlayer.ScaleType) maps object-fit (BEST_FIT / FIT_SCREEN
//   / FILL). VideoHelper.updateVideoSurfaces() swaps width/height when the
//   Activity is in portrait — that assumes a fullscreen VLCVideoLayout.
//   Embedded boxes are often landscape inside a portrait Activity, so we
//   call setUseOrientationFromBounds(true) and size against the element.
package com.carlossweb.lynxvlcvideo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcVideoPlayable(context: Context) : LynxVideoPlayable {

    private val videoLayout = VLCVideoLayout(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val playerLock = Any()
    private val libVLC = SharedLibVlc.acquire(context)
    private var player: MediaPlayer? = MediaPlayer(libVLC)

    @Volatile private var callback: LynxVideoPlayable.Callback? = null
    private var currentSrc: String? = null
    private var loop = false
    private var volume = 1.0f
    private var muted = false
    private var networkCachingMs = 0
    private var objectFit: String = "contain"
    private var hasFiredFirstFrame = false
    private var isExplicitlyStopped = false
    private var suppressNextPlayingForLoop = false
    private var suppressNextPaused = false
    @Volatile private var isReleased = false
    @Volatile private var cachedDurationMs = 0L
    @Volatile private var cachedCurrentPositionMs = 0L
    @Volatile private var cachedIsPlaying = false
    private var viewsDetachedForBackground = false
    private val hostActivity: Activity? = context.findActivity()

    // TextureView is destroyed when the activity is no longer visible (user
    // opened WhatsApp, etc.). Audio keeps playing; vout points at a dead
    // surface → black picture. VLC's own apps detach on stop / attach on start.
    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {
            if (activity === hostActivity) handleHostStarted()
        }
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {
            if (activity === hostActivity) handleHostStopped()
        }
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val w = right - left
        val h = bottom - top
        val oldW = oldRight - oldLeft
        val oldH = oldBottom - oldTop
        if (w > 0 && h > 0 && (w != oldW || h != oldH)) {
            applyObjectFit(objectFit)
        }
    }

    init {
        attachVideoViews()
        player?.setEventListener(::onVlcEvent)
        videoLayout.addOnLayoutChangeListener(layoutListener)
        hostActivity?.application?.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        runOnMainThread {
            applyObjectFit(objectFit)
            applyVolume()
        }
    }

    private fun attachVideoViews() {
        val p = player ?: return
        p.setUseOrientationFromBounds(java.lang.Boolean.TRUE)
        p.attachViews(videoLayout, null, true, false)
        viewsDetachedForBackground = false
    }

    private fun handleHostStopped() {
        if (isReleased || viewsDetachedForBackground) return
        val p = player ?: return
        p.detachViews()
        viewsDetachedForBackground = true
    }

    private fun handleHostStarted() {
        if (isReleased || !viewsDetachedForBackground) return
        // TextureView only has a SurfaceTexture after the window is focused
        // (onResume). Binding in onStart leaves a live MediaPlayer with a
        // dead vout — audio, black picture.
        videoLayout.post {
            if (isReleased || !viewsDetachedForBackground) return@post
            attachVideoViews()
            applyObjectFit(objectFit)
        }
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
        val p = player
        if (isReleased || p == null) return
        cachedDurationMs = p.length.coerceAtLeast(0)
        cachedCurrentPositionMs = p.time.coerceAtLeast(0)
        cachedIsPlaying = p.isPlaying
    }

    private fun <T> readPlayerState(cachedValue: T, read: (MediaPlayer) -> T): T {
        val p = player
        return if (!isReleased && p != null && isOnMainThread()) read(p) else cachedValue
    }

    private fun onVlcEvent(event: MediaPlayer.Event) {
        synchronized(playerLock) {
            if (isReleased || player == null) return
            when (event.type) {
                MediaPlayer.Event.Vout -> {
                    refreshPlaybackSnapshot()
                    if (isExplicitlyStopped) return
                    if (!hasFiredFirstFrame && event.voutCount > 0) {
                        hasFiredFirstFrame = true
                        val durationMs = cachedDurationMs
                        notifyCallback { onFirstFrame(durationMs) }
                        runOnMainThread { applyObjectFit(objectFit) }
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
                    if (isExplicitlyStopped) return
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
    }

    private fun restartForLoop() {
        // EndReached leaves libVLC's own Media detached — re-set it from the
        // stored src rather than assuming replay() is safe to call directly.
        val p = player ?: return
        currentSrc?.let { setMediaFrom(it) }
        p.play()
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
        val p = player ?: return
        val media = buildMedia(src)
        p.media = media
        media.release()
    }

    override fun setSrc(src: String?) {
        runOnMainThread {
            val p = player ?: return@runOnMainThread
            currentSrc = src
            hasFiredFirstFrame = false
            isExplicitlyStopped = false
            suppressNextPlayingForLoop = false
            p.stop()
            cachedCurrentPositionMs = 0
            if (src.isNullOrEmpty()) {
                p.media = null
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
        val p = player ?: return
        val effective = if (muted) 0f else volume
        p.volume = (effective * 100).toInt().coerceIn(0, 100)
    }

    override fun setSpeed(speed: Float) {
        runOnMainThread { player?.rate = speed.coerceIn(0.1f, 2.0f) }
    }

    override fun setObjectFit(objectFit: String?) {
        runOnMainThread { applyObjectFit(objectFit) }
    }

    private fun applyObjectFit(objectFit: String?) {
        val p = player ?: return
        this.objectFit = objectFit ?: "contain"
        p.videoScale = when (this.objectFit) {
            "cover" -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            "fill" -> MediaPlayer.ScaleType.SURFACE_FILL
            else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
        p.updateVideoSurfaces()
    }

    override fun setNetworkCaching(caching: Int) {
        runOnMainThread {
            networkCachingMs = caching.coerceAtLeast(0)
            // Applied on the next Media rebuild (setSrc / play / loop). Rebuild
            // now only when idle so a late mount prop still takes effect before
            // the first play(); never replace player.media while playing.
            val src = currentSrc
            val p = player
            if (src != null && p != null && !p.isPlaying) {
                setMediaFrom(src)
            }
        }
    }

    override fun play() {
        runOnMainThread {
            val p = player ?: return@runOnMainThread
            isExplicitlyStopped = false
            if (p.media == null && !currentSrc.isNullOrEmpty()) {
                setMediaFrom(currentSrc!!)
            }
            p.play()
            refreshPlaybackSnapshot()
        }
    }

    override fun pause() {
        runOnMainThread {
            player?.pause()
            refreshPlaybackSnapshot()
        }
    }

    override fun stop() {
        runOnMainThread {
            val p = player ?: return@runOnMainThread
            isExplicitlyStopped = true
            suppressNextPaused = suppressNextPaused || p.isPlaying
            p.stop()
            cachedCurrentPositionMs = 0
            refreshPlaybackSnapshot()
            notifyCallback { onStopped() }
        }
    }

    override fun seek(positionMs: Long) {
        runOnMainThread {
            val p = player ?: return@runOnMainThread
            p.time = positionMs.coerceAtLeast(0)
            cachedCurrentPositionMs = positionMs
        }
    }

    override fun getDuration(): Long = readPlayerState(cachedDurationMs) { it.length.coerceAtLeast(0) }

    override fun getCurrentPosition(): Long =
        readPlayerState(cachedCurrentPositionMs) { it.time.coerceAtLeast(0) }

    override fun isPlaying(): Boolean = readPlayerState(cachedIsPlaying) { it.isPlaying }

    override fun getAudioTracks(): List<LynxVideoPlayable.TrackInfo> =
        readPlayerState(emptyList()) { p ->
            p.audioTracks?.map { LynxVideoPlayable.TrackInfo(it.id, it.name) } ?: emptyList()
        }

    override fun setAudioTrack(id: Int): Boolean = readPlayerState(false) { it.setAudioTrack(id) }

    override fun getSubtitleTracks(): List<LynxVideoPlayable.TrackInfo> =
        readPlayerState(emptyList()) { p ->
            p.spuTracks?.map { LynxVideoPlayable.TrackInfo(it.id, it.name) } ?: emptyList()
        }

    override fun setSubtitleTrack(id: Int): Boolean = readPlayerState(false) { it.setSpuTrack(id) }

    override fun setCallback(callback: LynxVideoPlayable.Callback?) {
        this.callback = callback
    }

    override fun release() {
        isReleased = true
        hostActivity?.application?.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
        mainHandler.removeCallbacksAndMessages(null)
        videoLayout.removeOnLayoutChangeListener(layoutListener)
        runOnMainThread(allowAfterRelease = true) {
            synchronized(playerLock) {
                val p = player ?: return@synchronized
                p.setEventListener(null)
                p.stop()
                p.detachViews()
                p.release()
                player = null
                SharedLibVlc.release()
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
