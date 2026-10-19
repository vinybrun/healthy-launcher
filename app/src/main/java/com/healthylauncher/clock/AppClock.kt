package com.healthylauncher.clock

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

interface AppClock {
    fun elapsedRealtime(): Long
    fun wallTime(): Long
    val offsetMs: Long
}

class SystemAppClock : AppClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun wallTime(): Long = System.currentTimeMillis()
    override val offsetMs: Long = 0L
}

/** Offset stays at zero unless a debug build advances it. Alarm triggers subtract [offsetMs]. */
class AdjustableClock(private val real: AppClock = SystemAppClock()) : AppClock {
    private val extra = AtomicLong(0L)

    override fun elapsedRealtime(): Long = real.elapsedRealtime() + extra.get()
    override fun wallTime(): Long = real.wallTime() + extra.get()
    override val offsetMs: Long get() = extra.get()

    fun advance(ms: Long) {
        extra.addAndGet(ms)
    }
}
