# SnapConverter

Hardware-first Android media converter for Snapdragon devices.

Decode and encode on the **video codec block**. Resize, rotate, and crop on **Adreno**. The CPU is the scheduler, not the codec.

```text
storage → Qualcomm HW decoder → Surface → OpenGL ES → encoder Surface → Qualcomm HW encoder → MP4 / HEIC
```

This is **not** an FFmpeg wrapper. It does not ship libx264, libx265, or libjpeg-turbo as the encode path.

## Why this shape

Android already exposes vendor video silicon through `MediaCodec` (`c2.qti.*` / `OMX.qcom.*`). Qualcomm documents MediaCodec vendor extensions (ROI, LTR, encoder statistics, QP) for Snapdragon. Putting frames into `Bitmap` / `ByteArray` throws that path away: YUV→RGB→resize→RGB→YUV on the CPU.

The correct path is the one Android documents for `MediaCodec.createInputSurface()`: render with OpenGL ES (or another hardware API) directly into the encoder.

Photos follow the same rule. `Bitmap.compress(JPEG, …)` is not hardware encode. V1’s still-image path is **HEIC via `HeifWriter` + a hardware HEVC encoder**. JPEG is offered only when a hardware JPEG encoder is actually enumerable; otherwise the app refuses instead of silently using the CPU.

## V1 feature set

| | Input | Output |
| --- | --- | --- |
| Video | MP4, MOV | H.265 / H.264 MP4 |
| Image | JPEG, PNG, WebP, HEIC | HEIC (HW), JPEG (HW only) |

- Resolution: original, 2160p, 1440p, 1080p, 720p
- Frame rate: original, 60, 30, 24
- Modes: quality 0–100, target bitrate, target file size
- Hardware: **Qualcomm MediaCodec only** (no software fallback)
- GPU: OpenGL ES 3.x
- Audio: copied into the MP4, not re-encoded

AV1 / AVIF, HDR, ROI, and other-vendor SoCs are V2.

## Architecture

```text
                    Android App (Kotlin + Compose)
                                │
                        CompressionEngine
                     ┌──────────┴──────────┐
               Video Engine           Image Engine
                     │                     │
              MediaExtractor         ImageDecoder
                     │                     │
           Qualcomm HW decoder      GPU resize/crop
                     │                     │
              SurfaceTexture          Surface / YUV
                     │                     │
               Adreno GLES          HEIC  /  JPEG HW
                     │
          Encoder input Surface
                     │
           Qualcomm HW encoder
                     │
                 MediaMuxer
```

Three internal abstractions:

1. **`HardwareCodecSelector`** — enumerate `MediaCodecList`, require hardware + vendor, prefer Qualcomm, never `createEncoderByType()` as the source of truth.
2. **`GpuFrameProcessor`** — scale / rotate / crop on GLES, no per-frame Bitmap.
3. **`CompressionPolicy`** — map UI quality / target size / target bitrate to mime, resolution, fps, bitrate mode, QP.

Details: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`AGENTS.md`](AGENTS.md).

## Requirements

- Android 10 (API 29)+. Vendor extension probe uses API 31+.
- A Snapdragon device that exposes Qualcomm hardware codecs. Pixel / Exynos / Dimensity will currently fail closed with a capability error (by design).
- Android Studio or command-line SDK (`compileSdk` 36).

## Build

```bash
git clone https://github.com/caork/snapConverter.git
cd snapConverter
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :engine:test :app:assembleDebug
```

Install on a device:

```bash
./gradlew :app:installDebug
```

## Encoder selection

```text
encoder
AND hardware accelerated
AND vendor
AND NOT software-only
AND name contains qti or qcom
AND NOT c2.android.* / OMX.google.*
→ MediaCodec.createByCodecName(name)
```

Typical Snapdragon names: `c2.qti.hevc.encoder`, `c2.qti.avc.encoder`, older `OMX.qcom.video.encoder.*`.

## Quality slider

The UI number `0..100` is not written straight into `MediaFormat.KEY_QUALITY`. Policy turns it into resolution, fps, bitrate or CQ, GOP, and optional vendor QP keys. If the encoder does not support CQ, SnapConverter uses VBR and a bitrate model.

## Status

This repository is the initial public implementation: project layout, hardware codec selector, GLES Surface pipeline, HEIC hardware still path, Compose UI, and capability probe. Treat on-device transcode as **early**. Device-specific Qualcomm behavior (CQ quality scale, vendor keys, HEVC still encode) must be verified on hardware.

## License

Apache License 2.0. See [LICENSE](LICENSE).

Snapdragon, Adreno, and Qualcomm are trademarks of their owners. This project is not affiliated with Qualcomm.
