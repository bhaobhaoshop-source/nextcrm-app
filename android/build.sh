#!/usr/bin/env bash
# EstateDesk — Android build pipeline (no Gradle).
# Produces a signed, installable APK in dist/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
SRC="$APP/src/main"
BUILD="$ROOT/build/out"
DIST="$ROOT/dist"
KEYSTORE="$ROOT/keystore"

# ---- toolchain ---------------------------------------------------------
TC="${TOOLCHAIN_DIR:-/opt/toolchain}"
PY="${PYTHON:-$TC/venv/bin/python}"
[ -x "$PY" ] || PY="$(command -v python3 || true)"
JAVA_HOME="${JAVA_HOME:-$("$PY" -c 'import jdk4py; print(jdk4py.JAVA_HOME)' 2>/dev/null || true)}"
KOTLINC="$TC/kotlinc/bin/kotlinc"
AAPT2="$TC/aapt2"
D8JAR="$TC/r8repo/buildtools/d8-master.jar"
ANDROID_JAR="$TC/sdk/android-35/android.jar"
KOTLIN_STDLIB="$TC/kotlinc/lib/kotlin-stdlib.jar"

if [ ! -x "$AAPT2" ]; then AAPT2="$(command -v aapt2 || true)"; fi
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "error: JDK not found. Run tools/setup_toolchain.sh first (needs pip + npm)."
  exit 1
fi
for f in "$KOTLINC" "$AAPT2" "$D8JAR" "$ANDROID_JAR" "$KOTLIN_STDLIB"; do
  if [ ! -e "$f" ]; then
    echo "error: missing toolchain file: $f"
    echo "Run: sudo bash $ROOT/tools/setup_toolchain.sh"
    exit 1
  fi
done

export PATH="$JAVA_HOME/bin:$PATH"
VERSION_NAME="1.0.0"
VERSION_CODE="1"
PKG="com.estatedesk.crm"

echo "==> Clean"
rm -rf "$BUILD"; mkdir -p "$BUILD"/{flat,gen,classes,dex} "$DIST"

echo "==> 1/6 Compile resources (aapt2)"
"$AAPT2" compile --dir "$SRC/res" -o "$BUILD/flat"

echo "==> 2/6 Link APK + generate R (aapt2)"
FLATS=$(find "$BUILD/flat" -name '*.flat' | tr '\n' ' ')
"$AAPT2" link \
  -o "$BUILD/unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$SRC/AndroidManifest.xml" \
  -R $FLATS \
  --java "$BUILD/gen" \
  --min-sdk-version 26 --target-sdk-version 35 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo "==> 3/6 Convert R.java -> R.kt (no javac needed)"
PKG_PATH="${PKG//./\/}"
"$PY" "$ROOT/build/r_java_to_kt.py" "$BUILD/gen/$PKG_PATH/R.java" "$BUILD/gen/R.kt"

echo "==> 4/6 Compile Kotlin"
find "$SRC/kotlin" -name '*.kt' | sort > "$BUILD/sources.txt"
echo "$BUILD/gen/R.kt" >> "$BUILD/sources.txt"
"$KOTLINC" -nowarn -jvm-target 1.8 \
  -classpath "$ANDROID_JAR:$KOTLIN_STDLIB" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt"

echo "==> 5/6 Dex (d8)"
"$PY" "$ROOT/build/jar_dir.py" "$BUILD/classes" "$BUILD/app.jar"
mkdir -p "$BUILD/dex"
java -cp "$D8JAR" com.android.tools.r8.D8 \
  --min-api 26 --release --output "$BUILD/dex" \
  "$BUILD/app.jar" "$KOTLIN_STDLIB"

echo "==> 6/6 Package + sign"
"$PY" "$ROOT/build/package_apk.py" "$BUILD/unsigned.apk" "$BUILD/dex" "$BUILD/with_dex.apk"

mkdir -p "$KEYSTORE"
if [ ! -f "$KEYSTORE/release.pem" ]; then
  echo "==> Generating signing key (keystore/release.pem)"
  "$PY" "$ROOT/build/sign_apk.py" genkey "$KEYSTORE/release.pem" "$KEYSTORE/release-cert.der" "EstateDesk"
fi
"$PY" "$ROOT/build/sign_apk.py" sign "$BUILD/with_dex.apk" \
  "$KEYSTORE/release.pem" "$KEYSTORE/release-cert.der" \
  "$BUILD/signed.apk" v1v2
"$PY" "$ROOT/build/sign_apk.py" verify "$BUILD/signed.apk"

cp "$BUILD/signed.apk" "$DIST/EstateDesk-$VERSION_NAME.apk"
echo ""
echo "================================================================"
echo " BUILD OK -> $DIST/EstateDesk-$VERSION_NAME.apk ($(du -h "$DIST/EstateDesk-$VERSION_NAME.apk" | cut -f1))"
echo " Install with: adb install -r dist/EstateDesk-$VERSION_NAME.apk"
echo "================================================================"
