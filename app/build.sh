#!/usr/bin/env bash
# Build FireReader without Gradle/Android Studio.
# Requirements: bash, curl, unzip, zip, JDK 11+ (javac, keytool).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${SDK:-$PWD/.sdk}"
BT="$SDK/build-tools"           # build-tools 34 (aapt2, d8, zipalign, apksigner)
PLAT="$SDK/platform"            # platform android-27 (android.jar)
LAMBDA="$SDK/build-tools-30/core-lambda-stubs.jar"

fetch() { # url dest-dir
  local tmp; tmp="$(mktemp -d)"; curl -fsSL "$1" -o "$tmp/x.zip"; unzip -q "$tmp/x.zip" -d "$tmp/u"
  mkdir -p "$2"; cp -r "$tmp"/u/*/. "$2"/; rm -rf "$tmp"; }

[ -x "$BT/aapt2" ]        || fetch https://dl.google.com/android/repository/build-tools_r34-linux.zip "$BT"
[ -f "$PLAT/android.jar" ] || fetch https://dl.google.com/android/repository/platform-27_r03.zip "$PLAT"
[ -f "$LAMBDA" ]          || fetch https://dl.google.com/android/repository/build-tools_r30.0.3-linux.zip "$SDK/build-tools-30"

OUT=build; rm -rf "$OUT"; mkdir -p "$OUT/classes"
"$BT/aapt2" link -o "$OUT/base.apk" --manifest AndroidManifest.xml -I "$PLAT/android.jar"
javac -encoding UTF-8 -nowarn -source 8 -target 8 -bootclasspath "$PLAT/android.jar:$LAMBDA" \
      -d "$OUT/classes" $(find src -name '*.java')
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 21 --lib "$PLAT/android.jar" \
      --output "$OUT" $(find "$OUT/classes" -name '*.class')
cp "$OUT/base.apk" "$OUT/unsigned.apk"; (cd "$OUT" && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# Your own signing key, created on first build and never committed (see .gitignore).
KS="${KEYSTORE:-$PWD/firereader.keystore}"
PASS="${KEYSTORE_PASS:-changeit}"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$PASS" \
      -alias firereader -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=FireReader"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" --out "$OUT/FireReader.apk" "$OUT/aligned.apk"
echo "OK -> $OUT/FireReader.apk"
