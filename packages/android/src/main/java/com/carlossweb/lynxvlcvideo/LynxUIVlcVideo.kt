// Lynx-facing layer for <vlc-video> — adapted from xelement-video's real
// LynxUIVideo.kt (lynx-family/lynx, platform/android/lynx_xelement/
// lynx_xelement_video/.../LynxUIVideo.kt), which is itself written
// entirely against the player-agnostic LynxVideoPlayable interface, never
// against ExoPlayer directly. That means the play/pause/stop/seek
// UIMethod queueing (queue/direct/latest modes), error codes, and the 9
// bind* events below are copied verbatim in shape — only the tag name,
// the generator package, and which LynxVideoPlayable implementation
// createView() builds are different. Track selection and network-caching
// are new, libVLC-only additions on top of that parity surface.
package com.carlossweb.lynxvlcvideo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import com.lynx.react.bridge.Callback
import com.lynx.react.bridge.JavaOnlyMap
import com.lynx.react.bridge.ReadableMap
import com.lynx.react.bridge.ReadableType
import com.lynx.tasm.behavior.LynxBehavior
import com.lynx.tasm.behavior.LynxContext
import com.lynx.tasm.behavior.LynxGeneratorName
import com.lynx.tasm.behavior.LynxProp
import com.lynx.tasm.behavior.LynxUIMethod
import com.lynx.tasm.behavior.LynxUIMethodConstants
import com.lynx.tasm.behavior.ui.LynxUI
import com.lynx.tasm.event.LynxDetailEvent
import java.util.concurrent.ConcurrentLinkedQueue

@LynxGeneratorName(packageName = "com.carlossweb.lynxvlcvideo")
@LynxBehavior(tagName = ["vlc-video"], isCreateAsync = false)
open class LynxUIVlcVideo(context: LynxContext, params: Any?) :
    LynxUI<View>(context, params), LynxVideoPlayable.Callback {

    constructor(context: LynxContext) : this(context, null)

    companion object {
        private const val DEFAULT_TIME_UPDATE_INTERVAL_SEC = 0.33f
        private const val MAX_SAFE_SEEK_POSITION_SEC = Long.MAX_VALUE / 1000.0
    }

    private var mPlayable: LynxVideoPlayable? = null
    private var mMode: String = "queue"
    private var mSrc: String? = null
    private var mTimeUpdateIntervalSec: Float = DEFAULT_TIME_UPDATE_INTERVAL_SEC
    private var mDurationMs: Long = 0
    private var mHasPlaybackStarted = false
    private var mHasPlaybackEnded = false
    private var mIsTimeUpdateActive = false

    private val mHandler = Handler(Looper.getMainLooper())
    private val mTimeUpdateRunnable = object : Runnable {
        override fun run() {
            mHandler.removeCallbacks(this)
            if (!shouldPollTimeUpdate()) return
            dispatchTimeUpdate()
            if (shouldPollTimeUpdate()) mHandler.postDelayed(this, timeUpdateDelayMs())
        }
    }

    private data class UIMethodRequest(
        val method: String,
        val params: ReadableMap,
        val callback: Callback?
    )

    private sealed class RequestExecutionResult {
        object WaitForEvent : RequestExecutionResult()
        data class Finished(
            val code: Int,
            val msg: String? = null,
            val shouldDispatchErrorEvent: Boolean = false
        ) : RequestExecutionResult()
    }

    private val mPendingQueue = ConcurrentLinkedQueue<UIMethodRequest>()
    private var mLatestPending: UIMethodRequest? = null
    private var mIsExecuting = false
    private var mCurrentRequest: UIMethodRequest? = null

    override fun createView(context: Context): View {
        val videoView = LibVlcVideoPlayable(context)
        videoView.setCallback(this)
        mPlayable = videoView
        return videoView.getPlayerView()
    }

    override fun destroy() {
        cancelPendingSerialRequests("request canceled by destroy")
        mHandler.removeCallbacksAndMessages(null)
        stopTimeUpdatePolling()
        mPlayable?.release()
        mPlayable = null
        super.destroy()
    }

    // --- LynxProps ---

    @LynxProp(name = "src")
    fun setSrc(src: String?) {
        mSrc = src
        mIsTimeUpdateActive = false
        mHasPlaybackStarted = false
        mHasPlaybackEnded = false
        stopTimeUpdatePolling()
        cancelPendingSerialRequests("request canceled by src change")
        mPlayable?.setSrc(src)
    }

    @LynxProp(name = "loop", defaultBoolean = false)
    fun setLoop(loop: Boolean) {
        mPlayable?.setLoop(loop)
    }

    @LynxProp(name = "volume", defaultFloat = 1.0f)
    fun setVolume(volume: Float) {
        mPlayable?.setVolume(volume.coerceIn(0f, 1f))
    }

    @LynxProp(name = "muted", defaultBoolean = false)
    fun setMuted(muted: Boolean) {
        mPlayable?.setMuted(muted)
    }

    @LynxProp(name = "speed", defaultFloat = 1.0f)
    fun setSpeed(speed: Float) {
        mPlayable?.setSpeed(speed.coerceIn(0.1f, 2.0f))
    }

    @LynxProp(name = "object-fit")
    fun setObjectFit(objectFit: String?) {
        mPlayable?.setObjectFit(objectFit)
    }

    @LynxProp(name = "mode")
    fun setMode(mode: String?) {
        mMode = mode ?: "queue"
    }

    @LynxProp(name = "timeupdate-interval", defaultFloat = 0.33f)
    fun setTimeUpdateInterval(interval: Float) {
        if (interval > 0f) mTimeUpdateIntervalSec = interval
    }

    // libVLC-only addition: network read-ahead buffer, in ms — most useful
    // exactly for the unstable IPTV-style sources that motivated this
    // element (Media.addOption(":network-caching=<ms>"), applied on the
    // next Media build inside LibVlcVideoPlayable).
    @LynxProp(name = "network-caching", defaultInt = 0)
    fun setNetworkCaching(cachingMs: Int) {
        mPlayable?.setNetworkCaching(cachingMs)
    }

    // --- LynxUIMethods: play/pause/stop/seek (queued, parity with <video>) ---

    @LynxUIMethod
    fun play(params: ReadableMap?, callback: Callback?) {
        dispatchRequest("play", params, callback)
    }

    @LynxUIMethod
    fun pause(params: ReadableMap?, callback: Callback?) {
        dispatchRequest("pause", params, callback)
    }

    @LynxUIMethod
    fun stop(params: ReadableMap?, callback: Callback?) {
        dispatchRequest("stop", params, callback)
    }

    @LynxUIMethod
    fun seek(params: ReadableMap?, callback: Callback?) {
        dispatchRequest("seek", params, callback)
    }

    // --- LynxUIMethods: track selection (libVLC-only, not queued — these
    // are instantaneous queries/switches, not playback-state transitions) ---

    @LynxUIMethod
    fun getAudioTracks(params: ReadableMap?, callback: Callback?) {
        val tracks = mPlayable?.getAudioTracks() ?: emptyList()
        callback?.invoke(LynxUIMethodConstants.SUCCESS, buildTrackListResponse(tracks))
    }

    @LynxUIMethod
    fun setAudioTrack(params: ReadableMap?, callback: Callback?) {
        applyTrackSelection(params, callback) { id -> mPlayable?.setAudioTrack(id) ?: false }
    }

    @LynxUIMethod
    fun getSubtitleTracks(params: ReadableMap?, callback: Callback?) {
        val tracks = mPlayable?.getSubtitleTracks() ?: emptyList()
        callback?.invoke(LynxUIMethodConstants.SUCCESS, buildTrackListResponse(tracks))
    }

    @LynxUIMethod
    fun setSubtitleTrack(params: ReadableMap?, callback: Callback?) {
        applyTrackSelection(params, callback) { id -> mPlayable?.setSubtitleTrack(id) ?: false }
    }

    private fun applyTrackSelection(params: ReadableMap?, callback: Callback?, apply: (Int) -> Boolean) {
        val id = params?.let { if (it.hasKey("id") && isNumericParam(it, "id")) it.getDouble("id").toInt() else null }
        if (id == null) {
            callback?.invoke(LynxUIMethodConstants.PARAM_INVALID, buildFinishResponse(LynxUIMethodConstants.PARAM_INVALID, "missing id param"))
            return
        }
        val ok = apply(id)
        val code = if (ok) LynxUIMethodConstants.SUCCESS else LynxUIMethodConstants.OPERATION_ERROR
        callback?.invoke(code, buildFinishResponse(code, if (ok) null else "track not found"))
    }

    private fun buildTrackListResponse(tracks: List<LynxVideoPlayable.TrackInfo>): JavaOnlyMap {
        val response = JavaOnlyMap()
        response["success"] = true
        val list = com.lynx.react.bridge.JavaOnlyArray()
        tracks.forEach { track ->
            val entry = JavaOnlyMap()
            entry["id"] = track.id
            entry["name"] = track.name
            list.pushMap(entry)
        }
        response["tracks"] = list
        return response
    }

    // --- UIMethod scheduling (identical to xelement-video's own model) ---

    private fun dispatchRequest(method: String, params: ReadableMap?, callback: Callback?) {
        dispatchRequest(UIMethodRequest(method, params ?: JavaOnlyMap(), callback))
    }

    private fun dispatchRequest(request: UIMethodRequest) {
        // stop is a hard reset: do not queue it behind a WaitForEvent play()
        // that may never fire (stalled IPTV). The host retry in TESTING.md
        // depends on stop actually reaching libVLC.
        if (mMode != "direct" && request.method == "stop" && mIsExecuting) {
            cancelPendingSerialRequests("request canceled by stop")
        }
        when (mMode) {
            "direct" -> executeDirectRequest(request)
            "latest" -> {
                if (mIsExecuting) mLatestPending = request else executeRequest(request)
            }
            else -> {
                if (mIsExecuting) mPendingQueue.offer(request) else executeRequest(request)
            }
        }
    }

    private fun executeRequest(request: UIMethodRequest) {
        mIsExecuting = true
        mCurrentRequest = request
        when (val result = performRequest(request)) {
            is RequestExecutionResult.Finished -> finishRequest(result)
            RequestExecutionResult.WaitForEvent -> {}
        }
    }

    private fun executeDirectRequest(request: UIMethodRequest) {
        when (val result = performRequest(request)) {
            is RequestExecutionResult.Finished -> {
                invokeRequestCallback(request, result.code, result.msg)
                dispatchErrorEventIfNeeded(result)
            }
            RequestExecutionResult.WaitForEvent -> {
                invokeRequestCallback(request, LynxUIMethodConstants.SUCCESS)
            }
        }
    }

    private fun performRequest(request: UIMethodRequest): RequestExecutionResult =
        when (request.method) {
            "play" -> doPlay()
            "pause" -> doPause()
            "stop" -> doStop()
            "seek" -> doSeek(request)
            else -> RequestExecutionResult.Finished(LynxUIMethodConstants.METHOD_NOT_FOUND, "method not found")
        }

    private fun doPlay(): RequestExecutionResult {
        val playable = mPlayable ?: return RequestExecutionResult.Finished(LynxUIMethodConstants.INVALID_STATE_ERROR, "video view is not ready")
        if (playable.isPlaying()) {
            activateTimeUpdatePolling()
            return RequestExecutionResult.Finished(LynxUIMethodConstants.SUCCESS)
        }
        if (mSrc.isNullOrEmpty()) {
            return RequestExecutionResult.Finished(LynxUIMethodConstants.OPERATION_ERROR, "missing video source", shouldDispatchErrorEvent = true)
        }
        playable.play()
        return RequestExecutionResult.WaitForEvent
    }

    private fun doPause(): RequestExecutionResult {
        val playable = mPlayable ?: return RequestExecutionResult.Finished(LynxUIMethodConstants.INVALID_STATE_ERROR, "video view is not ready")
        if (!playable.isPlaying()) return RequestExecutionResult.Finished(LynxUIMethodConstants.SUCCESS)
        playable.pause()
        return RequestExecutionResult.WaitForEvent
    }

    private fun doStop(): RequestExecutionResult {
        val playable = mPlayable ?: return RequestExecutionResult.Finished(LynxUIMethodConstants.INVALID_STATE_ERROR, "video view is not ready")
        playable.stop()
        return RequestExecutionResult.WaitForEvent
    }

    private fun doSeek(request: UIMethodRequest): RequestExecutionResult {
        val playable = mPlayable ?: return RequestExecutionResult.Finished(LynxUIMethodConstants.INVALID_STATE_ERROR, "video view is not ready")
        if (!request.params.hasKey("position")) {
            return RequestExecutionResult.Finished(LynxUIMethodConstants.PARAM_INVALID, "missing position param")
        }
        if (!isNumericParam(request.params, "position")) {
            return RequestExecutionResult.Finished(LynxUIMethodConstants.PARAM_INVALID, "position param must be a number")
        }
        val positionSec = request.params.getDouble("position")
        val durationSec = playable.getDuration() / 1000.0
        if (positionSec.isNaN() || positionSec.isInfinite() || positionSec < 0 ||
            positionSec > MAX_SAFE_SEEK_POSITION_SEC || (durationSec > 0 && positionSec > durationSec)
        ) {
            return RequestExecutionResult.Finished(LynxUIMethodConstants.PARAM_INVALID, "position out of range")
        }
        playable.seek((positionSec * 1000.0).toLong())
        return RequestExecutionResult.Finished(LynxUIMethodConstants.SUCCESS)
    }

    private fun isNumericParam(params: ReadableMap, name: String): Boolean =
        when (params.getType(name)) {
            ReadableType.Int, ReadableType.Long, ReadableType.Number -> true
            else -> false
        }

    private fun finishRequest(code: Int, msg: String? = null) {
        mCurrentRequest?.let { invokeRequestCallback(it, code, msg) }
        mCurrentRequest = null
        mIsExecuting = false
        when (mMode) {
            "latest" -> mLatestPending?.let { mLatestPending = null; executeRequest(it) }
            "direct" -> {}
            else -> mPendingQueue.poll()?.let { executeRequest(it) }
        }
    }

    private fun cancelPendingSerialRequests(msg: String) {
        val currentRequest = mCurrentRequest
        val latestRequest = mLatestPending
        val pendingRequests = mutableListOf<UIMethodRequest>()
        while (true) {
            val pendingRequest = mPendingQueue.poll() ?: break
            pendingRequests.add(pendingRequest)
        }
        mCurrentRequest = null
        mLatestPending = null
        mIsExecuting = false
        currentRequest?.let { invokeRequestCallback(it, LynxUIMethodConstants.OPERATION_ERROR, msg) }
        pendingRequests.forEach { invokeRequestCallback(it, LynxUIMethodConstants.OPERATION_ERROR, msg) }
        latestRequest?.let { invokeRequestCallback(it, LynxUIMethodConstants.OPERATION_ERROR, msg) }
    }

    private fun finishRequest(result: RequestExecutionResult.Finished) {
        finishRequest(result.code, result.msg)
        dispatchErrorEventIfNeeded(result)
    }

    private fun invokeRequestCallback(request: UIMethodRequest, code: Int, msg: String? = null) {
        request.callback?.invoke(code, buildFinishResponse(code, msg))
    }

    private fun dispatchErrorEventIfNeeded(result: RequestExecutionResult.Finished) {
        if (result.shouldDispatchErrorEvent) {
            dispatchErrorEvent(result.code, result.msg ?: defaultErrorMessage(result.code))
        }
    }

    private fun buildFinishResponse(code: Int, msg: String?): JavaOnlyMap {
        val response = JavaOnlyMap()
        val success = code == LynxUIMethodConstants.SUCCESS
        response["success"] = success
        if (!success) {
            response["msg"] = msg ?: defaultErrorMessage(code)
            response["errorCode"] = code
        }
        return response
    }

    private fun defaultErrorMessage(code: Int): String =
        when (code) {
            LynxUIMethodConstants.METHOD_NOT_FOUND -> "method not found"
            LynxUIMethodConstants.PARAM_INVALID -> "parameter invalid"
            LynxUIMethodConstants.INVALID_STATE_ERROR -> "invalid state"
            LynxUIMethodConstants.OPERATION_ERROR -> "operation error"
            else -> "request failed"
        }

    // --- LynxVideoPlayable.Callback ---

    override fun onFirstFrame(durationMs: Long) {
        mDurationMs = durationMs
        val event = LynxDetailEvent(sign, "firstframe")
        event.addDetail("duration", durationMs / 1000f)
        mContext?.eventEmitter?.sendCustomEvent(event)
    }

    override fun onPlaying() {
        activateTimeUpdatePolling()
        val requestToFinish = mCurrentRequest?.takeIf { it.method == "play" }
        mContext?.eventEmitter?.sendCustomEvent(LynxDetailEvent(sign, "playing"))
        finishRequestAfterEvent(requestToFinish)
    }

    override fun onPaused() {
        dispatchTimeUpdate()
        mIsTimeUpdateActive = false
        val requestToFinish = mCurrentRequest?.takeIf { it.method == "pause" }
        mContext?.eventEmitter?.sendCustomEvent(LynxDetailEvent(sign, "paused"))
        finishRequestAfterEvent(requestToFinish)
    }

    override fun onStopped() {
        dispatchTimeUpdate()
        mIsTimeUpdateActive = false
        mHasPlaybackStarted = false
        mHasPlaybackEnded = false
        val requestToFinish = mCurrentRequest?.takeIf { it.method == "stop" }
        mContext?.eventEmitter?.sendCustomEvent(LynxDetailEvent(sign, "stopped"))
        finishRequestAfterEvent(requestToFinish)
    }

    private fun finishRequestAfterEvent(request: UIMethodRequest?) {
        if (request == null) return
        mHandler.post { if (mCurrentRequest === request) finishRequest(LynxUIMethodConstants.SUCCESS) }
    }

    override fun onTimeUpdate(currentMs: Long, durationMs: Long) {
        // Handled by polling, same as xelement-video's own LynxUIVideo.
    }

    override fun onEnded() {
        dispatchTimeUpdate()
        mIsTimeUpdateActive = false
        mHasPlaybackEnded = true
        stopTimeUpdatePolling()
        mContext?.eventEmitter?.sendCustomEvent(LynxDetailEvent(sign, "ended"))
    }

    override fun onLooped() {
        mContext?.eventEmitter?.sendCustomEvent(LynxDetailEvent(sign, "looped"))
    }

    override fun onError(errorCode: Int, errorMsg: String) {
        mIsTimeUpdateActive = false
        stopTimeUpdatePolling()
        if (mCurrentRequest != null) {
            finishRequest(LynxUIMethodConstants.OPERATION_ERROR, errorMsg)
        }
        dispatchErrorEvent(errorCode, errorMsg)
    }

    private fun dispatchErrorEvent(errorCode: Int, errorMsg: String) {
        val event = LynxDetailEvent(sign, "error")
        event.addDetail("errorCode", errorCode)
        event.addDetail("errorMsg", errorMsg)
        mContext?.eventEmitter?.sendCustomEvent(event)
    }

    override fun onBuffering(bufferedMs: Long) {
        val event = LynxDetailEvent(sign, "buffering")
        event.addDetail("buffering", bufferedMs / 1000f)
        mContext?.eventEmitter?.sendCustomEvent(event)
    }

    // --- TimeUpdate Polling (identical to xelement-video's own model) ---

    private fun activateTimeUpdatePolling() {
        mIsTimeUpdateActive = true
        mHasPlaybackStarted = true
        mHasPlaybackEnded = false
        startTimeUpdatePolling()
    }

    private fun startTimeUpdatePolling() {
        stopTimeUpdatePolling()
        if (shouldPollTimeUpdate()) mHandler.postDelayed(mTimeUpdateRunnable, timeUpdateDelayMs())
    }

    private fun stopTimeUpdatePolling() {
        mHandler.removeCallbacks(mTimeUpdateRunnable)
    }

    private fun dispatchTimeUpdate() {
        val playable = mPlayable ?: return
        if (!shouldPollTimeUpdate()) return
        val current = playable.getCurrentPosition()
        val duration = playable.getDuration()
        if (duration > 0) {
            val event = LynxDetailEvent(sign, "timeupdate")
            event.addDetail("current", current / 1000f)
            event.addDetail("duration", duration / 1000f)
            mContext?.eventEmitter?.sendCustomEvent(event)
        }
    }

    private fun timeUpdateDelayMs(): Long =
        (normalizeTimeUpdateIntervalSec(mTimeUpdateIntervalSec) * 1000).toLong().coerceAtLeast(1L)

    private fun shouldPollTimeUpdate(): Boolean =
        mPlayable != null && mIsTimeUpdateActive && mHasPlaybackStarted && !mHasPlaybackEnded

    private fun normalizeTimeUpdateIntervalSec(interval: Float): Float =
        if (interval > 0f) interval else DEFAULT_TIME_UPDATE_INTERVAL_SEC
}
