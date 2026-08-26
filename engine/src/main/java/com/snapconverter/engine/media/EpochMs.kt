package com.snapconverter.engine.media

object EpochMs {
    /**
     * MediaStore mixes seconds and milliseconds depending on OEM/column.
     * Values that look like seconds since 1970 are scaled up.
     */
    fun normalize(raw: Long): Long {
        if (raw <= 0L) return raw
        return if (raw < 100_000_000_000L) raw * 1000L else raw
    }
}
