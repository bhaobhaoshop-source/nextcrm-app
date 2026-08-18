# Build tools (vendored)

- **apksigner.jar** — Google's official APK signer/verifier from the Android
  SDK build-tools (Apache License 2.0, AOSP). Used as the primary signer in
  `build.sh`; produces Android-guaranteed v1/v2/v3 signatures. Source:
  https://android.googlesource.com/platform/tools/apksig/
- The `build/*.py` scripts implement the same formats (v1 JAR + v2) in pure
  Python and remain as a documented fallback.

Generated at build time (git-ignored): `keystore/release.p12` (PKCS12 built
from `release.pem` + `release-cert.der`).
