package com.snapconverter.engine

/**
 * Thrown when the requested operation cannot be done on hardware codecs.
 * Callers must surface this to the user. Never catch and fall back to CPU encode.
 */
open class HardwareOnlyException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class QualcommEncoderRequiredException(mime: String, found: List<String>) :
    HardwareOnlyException(
        "No Qualcomm hardware encoder for $mime. " +
            "V1 refuses software and non-Qualcomm encoders. Found: " +
            found.ifEmpty { listOf("<none>") }.joinToString(),
    )

class HardwareEncoderRequiredException(mime: String) :
    HardwareOnlyException("No hardware encoder exposed for $mime on this device.")

class HardwareDecoderRequiredException(mime: String) :
    HardwareOnlyException("No hardware decoder exposed for $mime on this device.")

class JpegHardwareUnavailableException :
    HardwareOnlyException(
        "This device does not expose a hardware JPEG encoder to third-party apps. " +
            "Choose HEIC, or do not encode JPEG.",
    )

class SoftwareCodecRejectedException(codecName: String) :
    HardwareOnlyException("Rejected software / AOSP codec: $codecName")
