# CLAUDE.md

SnapConverter is a hardware-first Android media converter. **Read `AGENTS.md` before editing.** That file is the source of truth for architecture and constraints.

## Commands

```bash
# SDK path (gitignored)
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :engine:test
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

On this machine the SDK is often at `/opt/homebrew/share/android-commandlinetools`.

## Guardrails (short)

- No FFmpeg, no libx264/x265, no `createScaledBitmap` on the main path.
- `Bitmap.compress` only for JPEG via `ImageEngine.encodeJpegOnCpu` — the one declared CPU path, labelled 「CPU 编码」 in the UI. HEIC / AVIF / video stay hardware-only.
- Select codecs with `HardwareCodecSelector` + `MediaCodec.createByCodecName`.
- Video frames stay on Surface → OpenGL ES → encoder Surface.
- Missing hardware encoder = user-visible failure; the labelled JPEG path is the only CPU encode and never rescues a failed hardware encode.
- V1 encode is Qualcomm-only; do not silently accept Google software codecs.

## Layout

- `:engine` — MediaCodec / EGL / policy, audit (`MediaAudit`)
- `:app` — Compose UI (workbench + scan/batch), MediaStore output
- `docs/ARCHITECTURE.md` — pipeline notes
