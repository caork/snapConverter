package com.snapconverter.engine.policy

import com.snapconverter.engine.codec.MimeTypes
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Library audit: decides whether a file the user already owns is larger than it
 * needs to be.
 *
 * The reference is not a magic number — it is the same bitrate model the
 * encoder would use if it re-encoded that exact file ([QualityStrategy]). So
 * "this video is 4.3× too big" means "a hardware HEVC encode of it at quality
 * 70 would need a quarter of the bits", which is a claim the app can then go
 * and honour. Images use the same idea in bytes-per-pixel.
 *
 * Pure Kotlin on purpose: the scan reads its facts from MediaStore columns, so
 * the whole decision is testable on the JVM and costs no file IO.
 */
enum class AuditVerdict { OK, HIGH, VERY_HIGH }

/** Why a file was flagged; the UI explains itself with this. */
enum class AuditReason { NONE, BITRATE, DENSITY, DIMENSION }

/**
 * How eager the audit is. Scales both the overshoot thresholds and the floors
 * that keep small files out of the list.
 */
enum class AuditSensitivity(
    val thresholdScale: Double,
    val floorScale: Double,
    /** Long edge above which a still is considered oversized for a phone. */
    val maxLongEdge: Int,
) {
    RELAXED(1.5, 2.0, 6000),
    STANDARD(1.0, 1.0, 4096),
    STRICT(0.7, 0.4, 3200),
    ;

    /** The preset expanded into the numbers the audit actually applies. */
    fun tuning(): AuditTuning = AuditTuning(
        videoHigh = MediaAudit.VIDEO_HIGH * thresholdScale,
        videoVeryHigh = MediaAudit.VIDEO_VERY_HIGH * thresholdScale,
        stillHigh = MediaAudit.STILL_HIGH * thresholdScale,
        stillVeryHigh = MediaAudit.STILL_VERY_HIGH * thresholdScale,
        stillTargetBpp = MediaAudit.STILL_TARGET_BPP,
        maxLongEdge = maxLongEdge,
        minVideoBytes = (MediaAudit.VIDEO_MIN_BYTES * floorScale).toLong(),
        minVideoMs = MediaAudit.VIDEO_MIN_MS,
        minVideoSaving = (MediaAudit.VIDEO_MIN_SAVING * floorScale).toLong(),
        minStillBytes = (MediaAudit.STILL_MIN_BYTES * floorScale).toLong(),
        minStillSaving = (MediaAudit.STILL_MIN_SAVING * floorScale).toLong(),
    )
}

/**
 * Every number the audit applies, so the advanced sheet can expose them
 * individually instead of only offering three presets. [AuditSensitivity]
 * builds one of these; the UI may then change any single field, which is what
 * "自定义" means on that screen.
 */
data class AuditTuning(
    /** Bitrate multiples over the model at which a video is flagged. */
    val videoHigh: Double = MediaAudit.VIDEO_HIGH,
    val videoVeryHigh: Double = MediaAudit.VIDEO_VERY_HIGH,
    /** Density multiples over [stillTargetBpp] at which a still is flagged. */
    val stillHigh: Double = MediaAudit.STILL_HIGH,
    val stillVeryHigh: Double = MediaAudit.STILL_VERY_HIGH,
    /** Bytes per pixel a converted still is expected to need. */
    val stillTargetBpp: Double = MediaAudit.STILL_TARGET_BPP,
    /** Long edge above which a still is oversized, and the estimate's cap. */
    val maxLongEdge: Int = 4096,
    val minVideoBytes: Long = MediaAudit.VIDEO_MIN_BYTES,
    val minVideoMs: Long = MediaAudit.VIDEO_MIN_MS,
    val minVideoSaving: Long = MediaAudit.VIDEO_MIN_SAVING,
    val minStillBytes: Long = MediaAudit.STILL_MIN_BYTES,
    val minStillSaving: Long = MediaAudit.STILL_MIN_SAVING,
    /**
     * RAW negatives and animated stills are excluded by default. Turning this
     * off judges them like any other still, which will flag every DNG in the
     * library — the caller has to mean it.
     */
    val skipUnjudgedStills: Boolean = true,
)

/** Everything the audit needs, all of it available from a MediaStore row. */
data class MediaFact(
    val kind: MediaKind,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val mime: String = "",
)

data class AuditResult(
    val verdict: AuditVerdict,
    val reason: AuditReason,
    /** Measured bitrate; 0 for stills. */
    val bitrateBps: Long,
    /** Measured bytes per pixel; 0 for video. */
    val bytesPerPixel: Double,
    /** What the model says this geometry needs; 0 for stills. */
    val referenceBps: Long,
    /** measured / reference. 1.0 means "already about right". */
    val overshoot: Double,
    val estimatedBytes: Long,
    val savingBytes: Long,
) {
    val flagged: Boolean get() = verdict != AuditVerdict.OK
}

object MediaAudit {

    /** The quality the audit assumes when it judges a file. */
    const val AUDIT_QUALITY = 70

    /** Bytes per pixel a hardware HEIC still needs at [AUDIT_QUALITY]-ish. */
    const val STILL_TARGET_BPP = 0.12

    const val VIDEO_HIGH = 1.6
    const val VIDEO_VERY_HIGH = 2.6
    const val STILL_HIGH = 1.8
    const val STILL_VERY_HIGH = 3.0

    const val VIDEO_MIN_BYTES = 12L * 1024 * 1024
    const val VIDEO_MIN_MS = 3_000L
    const val VIDEO_MIN_SAVING = 4L * 1024 * 1024
    const val STILL_MIN_BYTES = 1_500L * 1024
    const val STILL_MIN_SAVING = 600L * 1024

    fun audit(
        fact: MediaFact,
        sensitivity: AuditSensitivity = AuditSensitivity.STANDARD,
        quality: Int = AUDIT_QUALITY,
    ): AuditResult = audit(fact, sensitivity.tuning(), quality)

    fun audit(
        fact: MediaFact,
        tuning: AuditTuning,
        quality: Int = AUDIT_QUALITY,
    ): AuditResult = when (fact.kind) {
        MediaKind.VIDEO -> auditVideo(fact, tuning, quality)
        MediaKind.IMAGE -> auditStill(fact, tuning)
    }

    /**
     * What this file would take after a conversion at [quality], capped to
     * [maxLongEdge]. The scan uses it to judge; the batch screen uses it again
     * with the user's own settings, so both numbers come from one model.
     */
    fun estimateOutputBytes(
        fact: MediaFact,
        quality: Int = AUDIT_QUALITY,
        maxLongEdge: Int = Int.MAX_VALUE,
        stillBpp: Double = STILL_TARGET_BPP,
    ): Long {
        val (w, h) = cappedSize(fact.width, fact.height, maxLongEdge)
        if (w <= 0 || h <= 0) return 0L
        return when (fact.kind) {
            MediaKind.VIDEO -> {
                if (fact.durationMs <= 0L) return 0L
                val bps = referenceBitrate(w, h, quality)
                (bps.toDouble() * fact.durationMs / 8_000.0).roundToLong()
            }
            MediaKind.IMAGE -> (w.toLong() * h * stillBpp).roundToLong()
        }
    }

    private fun estimateStillBytes(fact: MediaFact, tuning: AuditTuning): Long =
        estimateOutputBytes(
            fact = fact,
            quality = AUDIT_QUALITY,
            maxLongEdge = tuning.maxLongEdge,
            stillBpp = tuning.stillTargetBpp,
        )

    /** Bits per second a hardware HEVC encode of this geometry should need. */
    fun referenceBitrate(width: Int, height: Int, quality: Int = AUDIT_QUALITY): Int =
        QualityStrategy.scaleBitrateForPixels(
            bitrate1080p = QualityStrategy.interpolate(quality).bitrate1080pHevcBps,
            width = width,
            height = height,
            mime = MimeTypes.HEVC,
        )

    /** Output geometry after capping the long edge, aspect preserved. */
    fun cappedSize(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val longEdge = max(width, height)
        if (longEdge <= maxLongEdge) return width to height
        val scale = maxLongEdge.toDouble() / longEdge
        val w = (width * scale).roundToLong().toInt().coerceAtLeast(2)
        val h = (height * scale).roundToLong().toInt().coerceAtLeast(2)
        return w to h
    }

    private fun auditVideo(
        fact: MediaFact,
        tuning: AuditTuning,
        quality: Int,
    ): AuditResult {
        val floorBytes = tuning.minVideoBytes
        val minSaving = tuning.minVideoSaving
        if (fact.width <= 0 || fact.height <= 0 ||
            fact.durationMs < tuning.minVideoMs ||
            fact.sizeBytes < floorBytes
        ) {
            return ok()
        }
        val bitrate = (fact.sizeBytes * 8_000.0 / fact.durationMs).roundToLong()
        val reference = referenceBitrate(fact.width, fact.height, quality).toLong()
        val overshoot = bitrate.toDouble() / reference
        val estimated = estimateOutputBytes(fact, quality, tuning.maxLongEdge)
        val saving = fact.sizeBytes - estimated
        val high = tuning.videoHigh
        val veryHigh = tuning.videoVeryHigh
        val verdict = when {
            saving < minSaving -> AuditVerdict.OK
            overshoot >= veryHigh -> AuditVerdict.VERY_HIGH
            overshoot >= high -> AuditVerdict.HIGH
            else -> AuditVerdict.OK
        }
        return AuditResult(
            verdict = verdict,
            reason = if (verdict == AuditVerdict.OK) AuditReason.NONE else AuditReason.BITRATE,
            bitrateBps = bitrate,
            bytesPerPixel = 0.0,
            referenceBps = reference,
            overshoot = overshoot,
            estimatedBytes = estimated,
            savingBytes = saving.coerceAtLeast(0L),
        )
    }

    /**
     * Stills the audit refuses to judge. A RAW negative is large because that
     * is what the format is for, and re-encoding it to a lossy still would
     * throw the sensor data away; an animated GIF would lose its animation.
     * Neither is an oversized photo, so neither belongs in the list — however
     * many bytes per pixel it spends.
     */
    private val UNJUDGED_STILL_SUBTYPES = listOf(
        "dng", "raw", "arw", "nef", "cr2", "cr3", "crw", "raf",
        "orf", "rw2", "srw", "sr2", "pef", "x3f", "gif",
    )

    private fun judgeableStill(mime: String): Boolean {
        if (mime.isBlank()) return true
        val subtype = mime.substringAfter('/').lowercase()
        return UNJUDGED_STILL_SUBTYPES.none { subtype.contains(it) }
    }

    private fun auditStill(fact: MediaFact, tuning: AuditTuning): AuditResult {
        if (tuning.skipUnjudgedStills && !judgeableStill(fact.mime)) return ok()
        val floorBytes = tuning.minStillBytes
        val minSaving = tuning.minStillSaving
        val pixels = fact.width.toLong() * fact.height
        if (pixels <= 0L || fact.sizeBytes < floorBytes) return ok()
        val bpp = fact.sizeBytes.toDouble() / pixels
        val overshoot = bpp / tuning.stillTargetBpp
        val estimated = estimateStillBytes(fact, tuning)
        val saving = fact.sizeBytes - estimated
        val oversizedGeometry = max(fact.width, fact.height) > tuning.maxLongEdge
        val high = tuning.stillHigh
        val veryHigh = tuning.stillVeryHigh
        val verdict = when {
            saving < minSaving -> AuditVerdict.OK
            overshoot >= veryHigh -> AuditVerdict.VERY_HIGH
            overshoot >= high || oversizedGeometry -> AuditVerdict.HIGH
            else -> AuditVerdict.OK
        }
        val reason = when {
            verdict == AuditVerdict.OK -> AuditReason.NONE
            overshoot >= high -> AuditReason.DENSITY
            else -> AuditReason.DIMENSION
        }
        return AuditResult(
            verdict = verdict,
            reason = reason,
            bitrateBps = 0L,
            bytesPerPixel = bpp,
            referenceBps = 0L,
            overshoot = overshoot,
            estimatedBytes = estimated,
            savingBytes = saving.coerceAtLeast(0L),
        )
    }

    private fun ok() = AuditResult(
        verdict = AuditVerdict.OK,
        reason = AuditReason.NONE,
        bitrateBps = 0L,
        bytesPerPixel = 0.0,
        referenceBps = 0L,
        overshoot = 0.0,
        estimatedBytes = 0L,
        savingBytes = 0L,
    )
}
