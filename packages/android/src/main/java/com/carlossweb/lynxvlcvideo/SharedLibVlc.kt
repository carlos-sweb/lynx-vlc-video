package com.carlossweb.lynxvlcvideo

import android.content.Context
import org.videolan.libvlc.LibVLC

/**
 * One process-wide libvlc instance, refcounted across <vlc-video> elements.
 * Each element still owns its own MediaPlayer.
 */
internal object SharedLibVlc {
    private val lock = Any()
    private var instance: LibVLC? = null
    private var refCount = 0

    private val OPTIONS = arrayListOf("--no-drop-late-frames", "--no-skip-frames")

    fun acquire(context: Context): LibVLC = synchronized(lock) {
        val existing = instance
        if (existing != null) {
            refCount++
            return existing
        }
        val created = LibVLC(context.applicationContext ?: context, OPTIONS)
        instance = created
        refCount = 1
        created
    }

    fun release() = synchronized(lock) {
        if (refCount <= 0) return
        refCount--
        if (refCount == 0) {
            instance?.release()
            instance = null
        }
    }
}
