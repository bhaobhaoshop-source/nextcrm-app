#!/usr/bin/env bash
# EstateDesk — Android build pipeline (no Gradle).
# Produces a signed, installable APK in dist/, signed by Google's official
# apksigner (v1+v2+v3), verified before it is published.
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
APKSIGNER="$ROOT/tools/apksigner.jar"

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
VERSION_NAME="1.0.1"
VERSION_CODE="3"
MIN_SDK="21"
TARGET_SDK="35"
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
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
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
  --min-api "$MIN_SDK" --release --output "$BUILD/dex" \
  "$BUILD/app.jar" "$KOTLIN_STDLIB"

echo "==> 6/6 Package + sign (official apksigner)"
"$PY" "$ROOT/build/package_apk.py" "$BUILD/unsigned.apk" "$BUILD/dex" "$BUILD/with_dex.apk"

mkdir -p "$KEYSTORE"
if [ ! -f "$KEYSTORE/release.pem" ]; then
  echo "==> Generating signing key (keystore/release.pem) — keep this file!"
  "$PY" "$ROOT/build/sign_apk.py" genkey "$KEYSTORE/release.pem" "$KEYSTORE/release-cert.der" "EstateDesk"
fi
if [ ! -f "$KEYSTORE/release.p12" ]; then
  "$PY" - "$KEYSTORE" <<'PYEOF'
import sys
from cryptography import x509
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.serialization import pkcs12
d = sys.argv[1]
key = serialization.load_pem_private_key(open(f"{d}/release.pem", "rb").read(), password=None)
cert = x509.load_der_x509_certificate(open(f"{d}/release-cert.der", "rb").read())
p12 = pkcs12.serialize_key_and_certificates(
    b"estatedesk", key, cert, None,
    serialization.BestAvailableEncryption(b"estatedesk"))
open(f"{d}/release.p12", "wb").write(p12)
PYEOF
fi

java -jar "$APKSIGNER" sign \
  --ks "$KEYSTORE/release.p12" --ks-pass pass:estatedesk --ks-key-alias estatedesk \
  --out "$BUILD/signed.apk" "$BUILD/with_dex.apk"

echo "==> Verify with the official apksigner (must print 'Verifies')"
java -jar "$APKSIGNER" verify --verbose "$BUILD/signed.apk" | head -8

# extra cross-checks with independent verifiers
"$PY" "$ROOT/build/verify_apk_v2.py" "$BUILD/signed.apk" >/dev/null || {
  echo "error: independent v2 verifier failed"; exit 1; }

cp "$BUILD/signed.apk" "$DIST/EstateDesk-$VERSION_NAME.apk"
echo ""
echo "================================================================"
echo " BUILD OK -> $DIST/EstateDesk-$VERSION_NAME.apk ($(du -h "$DIST/EstateDesk-$VERSION_NAME.apk" | cut -f1))"
echo " Install with: adb install -r dist/EstateDesk-$VERSION_NAME.apk"
echo "================================================================"
