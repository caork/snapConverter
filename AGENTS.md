# AGENTS.md — SnapConverter

This file is the source of truth for humans and coding agents working in this repository.

## What this project is

SnapConverter is a **hardware-first Android media converter**.

The product constraint is not “compress a file”. The product constraint is:

> Decode and encode on dedicated video codec silicon. Resize / rotate / crop / color convert on the GPU. The CPU schedules work; it does not run the codec.

On Snapdragon devices the intended path is:

```text
storage
  → Qualcomm hardware decoder (MediaCodec)
  → Surface
  → Adreno (OpenGL ES)
  → encoder input Surface
  → Qualcomm hardware encoder (MediaCodec)
  → MediaMuxer
  → storage
```

Snapdragon video encode/decode runs on a **dedicated video processing unit / video codec block**, not on Kryo CPU cores and not on the Adreno 3D GPU. Adreno is the right place for image geometry and color work between decoder and encoder.

## Non-negotiable constraints

These are product rules, not style preferences. A change that violates them is incorrect even if it “works”.

1. **Do not make FFmpeg the core pipeline.** No JNI `libavcodec` / `libx264` / `libx265` / `libvpx` encode path. FFmpeg must not be added as a Gradle dependency.
2. **Do not run software codecs.** Reject `c2.android.*`, `OMX.google.*`, `isSoftwareOnly() == true`. Never call `MediaCodec.createEncoderByType()` and assume the result is hardware.
3. **Do not put decoded frames on the CPU as the main path.** No `Bitmap` / `ByteArray` / `YUV` round-trip per video frame. The path is decoder Surface → GL texture → encoder Surface.
4. **Do not use `Bitmap.compress()` as the image encode path.**
5. **Do not use `Bitmap.createScaledBitmap()` as the image resize path.**
6. **Do not silently fall back to CPU encode.** If the required hardware encoder is missing, fail and tell the user. JPEG without an exposed hardware encoder must refuse, not encode in software.
7. **Do not hard-code a SoC marketing name as capability.** Enumerate `MediaCodecList` at runtime. Capability-driven, not “Snapdragon 8 Gen 3 therefore AV1 encode”.
8. **Do not put `c2.qti.hevc.encoder` string literals in UI or policy code.** Codec names live in `HardwareCodecSelector` / vendor policy. Business code asks for “hardware HEVC encoder, Qualcomm preferred”.
9. **V1 encode is Qualcomm-only.** Other vendors get a clear “unsupported device” error until a `MediaTekCodecPolicy` / `ExynosCodecPolicy` exists. Decoder may accept any hardware decoder, but still reject software.
10. **Internal docs, comments, commit messages: English.** User-facing UI may be localized.

## Target stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| minSdk | 29 (Android 10) — `isHardwareAccelerated` / `isVendor` / `isSoftwareOnly` |
| Vendor extensions | API 31+ via `MediaCodec.getSupportedVendorParameters()` |
| compileSdk / targetSdk | Latest stable installed in CI (currently 36) |
| File parse | `MediaExtractor` |
| Decode / encode | `MediaCodec.createByCodecName(...)` after enumeration |
| GPU | EGL + OpenGL ES 3.x, `GL_OES_EGL_image_external` |
| Mux | `MediaMuxer` (MP4) |
| Photos | `androidx.heifwriter.HeifWriter` with hardware-only preference; JPEG only if a hardware JPEG encoder is enumerable |
| Snapdragon extras | Qualcomm vendor keys (`vendor.qti-ext-*`), probed at runtime |
| Media3 Transformer | Optional research / future helper. Not the V1 core path. |
| Tests | JVM unit tests for pure policy/name logic; instrumented tests later for MediaCodec |

## Architecture

Three abstractions. Keep them separate.

```text
HardwareCodecSelector
        │
        ├── selectDecoder()
        ├── selectEncoder()
        ├── requireHardware()
        └── preferQualcomm()

GpuFrameProcessor
        │
        ├── scale()
        ├── rotate()
        ├── crop()
        └── toneMap()          (V2)

CompressionPolicy
        │
        ├── QualityMode
        ├── TargetSizeMode
        ├── TargetBitrateMode
        └── LosslessRemuxMode
```

Modules:

- `:engine` — codec selection, EGL/GPU, video transcoder, image encoder, policy. No Compose.
- `:app` — Compose UI, SAF/MediaStore, ViewModel. Depends on `:engine`.

Package root: `com.snapconverter`.

### Video pipeline (required)

```text
MediaExtractor
  → Qualcomm HW decoder (Surface output)
  → SurfaceTexture / samplerExternalOES
  → Adreno shaders (resize / rotate / crop)
  → MediaCodec.createInputSurface()
  → Qualcomm HW encoder (c2.qti.* / OMX.qcom.*)
  → MediaMuxer
  → MP4
```

Audio in V1 is **passthrough** (copy samples into the muxer). Do not re-encode audio unless a later version explicitly adds a hardware audio encoder path.

### Image pipeline (required)

```text
ImageDecoder
  → HardwareBuffer / Surface when possible
  → OpenGL ES texture
  → Adreno resize / crop / rotate
  → HEIC via HeifWriter SURFACE + hardware HEVC still encoder
     or JPEG via an enumerated hardware JPEG encoder
```

If JPEG hardware is not exposed as a third-party `MediaCodec`, the UI must disable JPEG and say so. No silent `Bitmap.CompressFormat.JPEG`.

### Encoder selection (required)

Never trust `createEncoderByType("video/hevc")`.

Required filter for V1 video encode:

```text
isEncoder
AND mime matches
AND isHardwareAccelerated
AND isVendor
AND NOT isSoftwareOnly
AND name looks like Qualcomm (qti / qcom)
AND NOT c2.android.* / OMX.google.*
```

Then `MediaCodec.createByCodecName(candidate.name)`.

Known Qualcomm names (examples, not an allow-list to hard-code as the only possibilities):

- `c2.qti.avc.encoder` / `c2.qti.hevc.encoder` / `c2.qti.av1.encoder` (if present)
- `OMX.qcom.video.encoder.avc` / `OMX.qcom.video.encoder.hevc`

AV1 encode: show in UI only when a hardware AV1 encoder actually enumerates. Hide otherwise. No software AV1.

### Quality is a policy, not a single integer

The UI slider `0..100` is `AppQuality`. It must be mapped by `CompressionPolicy` into a bundle:

- output mime (HEVC default, AVC fallback if HEVC HW missing — still Qualcomm HW)
- resolution cap
- frame rate cap
- bitrate or CQ quality
- I-frame interval
- optional QP bounds
- optional vendor keys on API 31+

Prefer CQ (`BITRATE_MODE_CQ` + `KEY_QUALITY`) when the encoder reports it. Otherwise VBR + a bitrate model. `KEY_QUALITY` is vendor-specific; never assume `quality=70` is comparable across devices.

Target-size mode:

```text
videoBitrate ≈ (targetFileSizeBits - estimatedAudioBits - containerOverhead) / durationSeconds
```

## V1 scope (in)

- Video in: MP4 / MOV via `MediaExtractor`
- Video out: H.264 / H.265 MP4
- Image in: JPEG / PNG / WebP / HEIC
- Image out: HEIC (hardware), JPEG only if HW encoder is public
- Resolution: original / 2160p / 1440p / 1080p / 720p
- FPS: original / 60 / 30 / 24
- Modes: quality 0–100, target bitrate, target file size
- Device capability screen from live `MediaCodecList`
- OpenGL ES 3.x frame processor
- Qualcomm vendor parameter probe (API 31+)

## V2 scope (out until explicitly requested)

- AV1 / AVIF hardware
- HDR preserve and HDR→SDR tone map
- ROI encoding, LTR, encoder statistics-driven bitrate
- Content-aware bitrate, VMAF/SSIM
- Batch queue
- MediaTek / Exynos vendor policies
- Media3 Transformer as an alternate engine

## Build

JDK 17+. Android SDK with `platforms;android-36` (or the compileSdk in `gradle/libs.versions.toml`).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :engine:test
./gradlew :app:assembleDebug
```

`local.properties` is gitignored. Do not commit machine SDK paths.

## Code rules

- Prefer small, named types over boolean bags.
- GL resources must be created and destroyed on the same thread as the EGL context.
- Always `release()` `MediaCodec`, `MediaExtractor`, `MediaMuxer`, `Surface`, `SurfaceTexture`, EGL in `finally`.
- Probe vendor extensions with `getSupportedVendorParameters()` before setting `vendor.qti-ext-*`.
- Keep UI free of `MediaFormat` key strings.
- When adding a vendor, add a policy object. Do not scatter `if (name.contains("qti"))` through the transcoder.

## What “done” means for a pipeline change

A pipeline change is not done because it compiles. It is done when:

1. Encoder/decoder names are chosen by `HardwareCodecSelector`, not by `createEncoderByType`.
2. Frames do not land in `Bitmap`/`ByteArray` on the hot path.
3. Missing hardware fails loudly.
4. `:engine:test` still passes.
5. `:app:assembleDebug` still passes.
