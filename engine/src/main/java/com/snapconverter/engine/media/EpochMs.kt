package com.snapconverter.engine.media

object EpochMs {
    /** 2000-01-01 UTC. MP4 epoch 0 (1904) and Unix 0 (1970) are not real capture times. */
    const val MIN_PLAUSIBLE_MS = 946_684_800_000L

    /**
     * MediaStore mixes seconds and milliseconds depending on OEM/column.
     * Values that look like seconds since 1970 are scaled up.
     */
    fun normalize(raw: Long): Long {
        if (raw <= 0L) return raw
        return if (raw < 100_000_000_000L) raw * 1000L else raw
    }

    fun plausible(ms: Long?): Long? {
        if (ms == null) return null
        if (ms < MIN_PLAUSIBLE_MS) return null
        if (ms > System.currentTimeMillis() + 86_400_000L) return null
        return ms
    }
}
