package dev.kidepcode.chunkyfast.limiter

import dev.kidepcode.chunkyfast.config.LimiterSettings
import kotlin.math.max
import kotlin.math.min

class MsptLimiter(
    @Volatile private var s: LimiterSettings
) {
    private var factor = 1.0
    private var paused = false
    private var lastNs = System.nanoTime()

    fun update(settings: LimiterSettings) {
        s = settings
        factor = factor.coerceIn(s.minFactor, s.factorCap)
    }

    fun tick(mspt: Double): Result {
        val ss = s
        if (!ss.enabled) {
            paused = false
            factor = ss.factorCap
            lastNs = System.nanoTime()
            return Result(factor, false)
        }

        val now = System.nanoTime()
        val dt = ((now - lastNs).coerceAtLeast(0L)).toDouble() / 1_000_000_000.0
        lastNs = now

        if (!paused && mspt >= ss.hardPauseMspt) paused = true
        if (paused && mspt <= ss.resumeMspt) paused = false

        if (paused) {
            factor = max(ss.minFactor, min(ss.factorCap, factor))
            return Result(factor, true)
        }

        val target = ss.msptTarget
        if (mspt > target) {
            val over = (mspt / target) - 1.0
            val delta = min(ss.downPerSecond * dt, ss.downPerSecond * dt * (0.25 + over))
            factor -= delta
        } else {
            factor += ss.upPerSecond * dt
        }

        factor = factor.coerceIn(ss.minFactor, ss.factorCap)
        return Result(factor, false)
    }

    data class Result(
        val factor: Double,
        val paused: Boolean
    )
}