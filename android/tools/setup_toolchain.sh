#!/usr/bin/env bash
# EstateDesk toolchain setup (no Gradle, no Google SDK downloads needed).
#
# This sandbox-friendly pipeline assembles the entire Android build
# toolchain from npm / PyPI / GitHub source archives:
#   - JDK 17            : PyPI package `jdk4py` (bundled JDK, no network fetch at build time)
#   - Kotlin compiler   : npm package `kotlin-compiler` (JetBrains distribution)
#   - aapt2             : npm package `aaptjs3` (bundled Linux binary)
#   - d8 (dexer)        : LineageOS mirror of AOSP prebuilts/r8 (via codeload.github.com)
#   - android.jar (35)  : Sable/android-platforms mirror of the official SDK platform jars
#
# Usage: sudo bash tools/setup_toolchain.sh [DEST]   (default DEST=/opt/toolchain)
set -euo pipefail

DEST="${1:-/opt/toolchain}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> Installing into $DEST"
mkdir -p "$DEST"
if [ "$(id -u)" != "0" ]; then
  echo "note: not root; assuming $DEST is writable"
fi

echo "==> 1/5 JDK (jdk4py)"
python3 -m venv "$DEST/venv"
"$DEST/venv/bin/pip" install --quiet jdk4py==17.0.9.2 cryptography

echo "==> 2/5 Kotlin compiler (npm)"
cd "$WORK"
npm pack kotlin-compiler@2.4.10 --silent >/dev/null
mkdir -p "$DEST/kotlinc"
tar xzf kotlin-compiler-2.4.10.tgz -C "$DEST/kotlinc" --strip-components=1

echo "==> 3/5 aapt2 (npm)"
npm pack aaptjs3@2.0.2 --silent >/dev/null
tar xzf aaptjs3-2.0.2.tgz --strip-components=1 -C "$WORK" package/bin/x64/linux/aapt2 2>/dev/null || \
  (tar xzf aaptjs3-2.0.2.tgz && cp "$WORK/package/bin/x64/linux/aapt2" "$DEST/aapt2")
[ -f "$DEST/aapt2" ] || cp "$WORK/package/bin/x64/linux/aapt2" "$DEST/aapt2"
chmod +x "$DEST/aapt2"

echo "==> 4/5 d8 dexer (LineageOS prebuilts/r8 via codeload)"
curl -sL -o "$WORK/r8.tgz" https://codeload.github.com/LineageOS/android_prebuilts_r8/tar.gz/refs/heads/lineage-17.1
mkdir -p "$DEST/r8repo"
tar xzf "$WORK/r8.tgz" -C "$DEST/r8repo" --strip-components=1

echo "==> 5/5 android.jar API 35 (Sable/android-platforms via codeload)"
curl -sL -o "$WORK/sable.tgz" https://codeload.github.com/Sable/android-platforms/tar.gz/refs/heads/master
mkdir -p "$DEST/sdk"
tar xzf "$WORK/sable.tgz" -C "$DEST/sdk" --strip-components=1 \
  android-platforms-master/android-35 2>/dev/null || true
[ -f "$DEST/sdk/android-35/android.jar" ] || {
  echo "error: android-35/android.jar extraction failed"
  exit 1
}

echo ""
echo "Toolchain ready at $DEST"
echo "Now build the app with:  bash android/build.sh"
