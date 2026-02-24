package dev.kidepcode.chunkyfast.util

object Format {
    fun formatDurationMs(ms: Long): String {
        var s = ms / 1000L
        val h = s / 3600L
        s %= 3600L
        val m = s / 60L
        s %= 60L
        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun formatEtaSeconds(sec: Long): String {
        val s = if (sec < 0) 0 else sec
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        val ss = s % 60L
        return when {
            h > 0 -> "${h}h ${m}m ${ss}s"
            m > 0 -> "${m}m ${ss}s"
            else -> "${ss}s"
        }
    }
}