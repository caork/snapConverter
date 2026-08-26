#!/usr/bin/env bash
# Cross-compile Netflix libvmaf for Android arm64-v8a and install the .a
# used by engine/src/main/cpp. Requires meson, ninja, NDK, xxd.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/ndk/27.1.12297006}"
LLVM="$NDK/toolchains/llvm/prebuilt/$(ls "$NDK/toolchains/llvm/prebuilt" | head -1)"
SRC="${TMPDIR:-/tmp}/vmaf-src/vmaf"
BUILD="${TMPDIR:-/tmp}/vmaf-android-build"
if [[ ! -d "$SRC/.git" ]]; then
  git clone --depth 1 --branch v3.0.0 https://github.com/Netflix/vmaf.git "$SRC"
fi
cat > /tmp/vmaf-android-cross.ini <<EOF
[binaries]
c = '$LLVM/bin/aarch64-linux-android29-clang'
cpp = '$LLVM/bin/aarch64-linux-android29-clang++'
ar = '$LLVM/bin/llvm-ar'
strip = '$LLVM/bin/llvm-strip'
pkg-config = 'false'
[built-in options]
c_args = ['-fPIC', '-O2', '-DANDROID']
cpp_args = ['-fPIC', '-O2', '-DANDROID']
c_link_args = ['-lm']
cpp_link_args = ['-lm', '-static-libstdc++']
[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
EOF
meson setup "$BUILD" "$SRC/libvmaf" --cross-file /tmp/vmaf-android-cross.ini \
  --buildtype release --default-library static \
  -Denable_tests=false -Denable_docs=false -Denable_avx512=false \
  -Denable_cuda=false -Dbuilt_in_models=true -Denable_float=false -Denable_asm=true \
  --reconfigure || meson setup "$BUILD" "$SRC/libvmaf" --cross-file /tmp/vmaf-android-cross.ini \
  --buildtype release --default-library static \
  -Denable_tests=false -Denable_docs=false -Denable_avx512=false \
  -Denable_cuda=false -Dbuilt_in_models=true -Denable_float=false -Denable_asm=true
ninja -C "$BUILD"
DEST="$ROOT/engine/src/main/cpp/vmaf/prebuilt/arm64-v8a"
mkdir -p "$DEST" "$ROOT/engine/src/main/cpp/vmaf/include/libvmaf"
cp "$BUILD/src/libvmaf.a" "$DEST/libvmaf.a"
cp "$SRC/libvmaf/include/libvmaf/"*.h "$ROOT/engine/src/main/cpp/vmaf/include/libvmaf/"
cp "$BUILD/include/libvmaf/version.h" "$ROOT/engine/src/main/cpp/vmaf/include/libvmaf/"
echo "installed $DEST/libvmaf.a"
