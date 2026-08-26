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

- No FFmpeg, no libx264/x265, no `Bitmap.compress`, no `createScaledBitmap` on the main path.
- Select codecs with `HardwareCodecSelector` + `MediaCodec.createByCodecName`.
- Video frames stay on Surface → OpenGL ES → encoder Surface.
- Missing hardware encoder = user-visible failure, never CPU fallback.
- V1 encode is Qualcomm-only; do not silently accept Google software codecs.

## Layout

- `:engine` — MediaCodec / EGL / policy
- `:app` — Compose UI
- `docs/ARCHITECTURE.md` — pipeline notes
