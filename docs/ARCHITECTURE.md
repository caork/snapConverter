# Architecture

## Goal

Keep pixels in hardware from decode to encode.

```text
decoder Surface
    → GL_TEXTURE_EXTERNAL_OES
    → GLES shader (scale / rotate / crop / later tone map)
    → encoder input Surface  (MediaCodec.createInputSurface)
    → hardware encoder
    → MediaMuxer
```

If a change introduces a `ByteBuffer` of YUV/RGB for every video frame, it has left the design.

## Modules

| Module | Responsibility |
| --- | --- |
| `:engine` | Codec probe/select, EGL, transcode, still encode, policy |
| `:app` | Compose, document picker, MediaStore output, user-visible errors |

`:engine` must not depend on Compose. `:app` must not call `MediaCodec.createEncoderByType`.

## Codec selection

`HardwareCodecSelector` walks `MediaCodecList(REGULAR_CODECS)`.

Reject:

- `isSoftwareOnly == true`
- `isHardwareAccelerated == false` when hardware is required
- names starting with `c2.android.` or `OMX.google.`

V1 encode additionally requires Qualcomm (`qti` / `qcom` in the codec name, `isVendor == true`).

Always finish with `MediaCodec.createByCodecName`.

On API 31+, after creating a codec, call `getSupportedVendorParameters()` and only then set `vendor.qti-ext-*` keys from `QtiVendorParameters`.

## Video transcoder

`SurfaceTranscoder` owns one worker thread (EGL context is thread-affine):

1. `MediaExtractor` selects video + optional audio.
2. Decoder is configured with a `Surface` from `SurfaceTexture`.
3. Encoder is configured with `COLOR_FormatSurface` and `createInputSurface()`.
4. EGL window surface is bound to the encoder input Surface (`EGL_RECORDABLE_ANDROID`).
5. Each released decoder buffer with `render=true` becomes an OES texture, is drawn, then `eglPresentationTimeANDROID` + `eglSwapBuffers`.
6. Encoder output buffers go to `MediaMuxer`.
7. Audio samples are copied (passthrough).

Frame-rate caps drop frames by presentation timestamp; duration is preserved.

## Image path

1. `ImageDecoder` reads bounds and decodes into a GL-uploadable source (HardwareBuffer when the allocator allows it).
2. `GpuFrameProcessor` draws into the still-encoder Surface at the target size.
3. HEIC: `HeifWriter` `INPUT_MODE_SURFACE` + `EncoderPreference.HARDWARE_ENCODER_ONLY` (and CQ when requested).
4. JPEG: only if `HardwareCodecSelector` finds a hardware `image/jpeg` (or vendor JPEG) encoder. Otherwise the engine throws `JpegHardwareUnavailableException`.

## Policy

`CompressionPolicy` converts a `CompressionRequest` into a `VideoEncodePlan` / `ImageEncodePlan`.

Modes:

- **Quality** — prefer CQ; else VBR bitrate interpolated from a 1080p HEVC table and scaled by pixel count.
- **Target size** — `BitrateEstimator` from file size, duration, audio bitrate, container overhead.
- **Target bitrate** — used as-is after clamping to encoder capabilities.
- **Lossless remux** — reserved; not a re-encode. V1 UI may hide it.

`KEY_QUALITY` values are vendor-specific. Do not treat `70` as a portable unit.

## Adding another SoC vendor

1. Add a `VendorFamily` entry if needed.
2. Teach `CodecName` how to classify that vendor’s codec names.
3. Add a policy that maps AppQuality → that vendor’s CQ/QP/bitrate.
4. Switch V1’s `requireQualcomm` off only behind an explicit product decision.

Do not sprinkle vendor strings through `SurfaceTranscoder`.
