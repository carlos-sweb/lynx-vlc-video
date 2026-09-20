// Local copy of xelement-video's own LynxVideoPlayable interface
// (com.lynx.xelement.video.LynxVideoPlayable, lynx-family/lynx) — not
// published standalone, so LynxUIVlcVideo is written against this copy
// instead. Extended with the track-selection and network-caching surface
// that xelement-video's ExoPlayer-backed implementation doesn't expose.
package com.carlossweb.lynxvlcvideo

interface LynxVideoPlayable {
    fun setSrc(src: String?)
    fun setLoop(loop: Boolean)
    fun setVolume(volume: Float)
    fun setMuted(muted: Boolean)
    fun setSpeed(speed: Float)
    fun setObjectFit(objectFit: String?)
    fun setNetworkCaching(caching: Int)

    fun play()
    fun pause()
    fun stop()
    fun seek(positionMs: Long)

    fun getDuration(): Long
    fun getCurrentPosition(): Long
    fun isPlaying(): Boolean

    fun getAudioTracks(): List<TrackInfo>
    fun setAudioTrack(id: Int): Boolean
    fun getSubtitleTracks(): List<TrackInfo>
    fun setSubtitleTrack(id: Int): Boolean

    fun setCallback(callback: Callback?)
    fun release()

    data class TrackInfo(val id: Int, val name: String)

    interface Callback {
        fun onFirstFrame(durationMs: Long)
        fun onPlaying()
        fun onPaused()
        fun onStopped()
        fun onTimeUpdate(currentMs: Long, durationMs: Long)
        fun onEnded()
        fun onLooped()
        fun onError(errorCode: Int, errorMsg: String)
        fun onBuffering(bufferedMs: Long)
    }
}
