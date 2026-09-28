#!/bin/bash
# Manual APK build for Dhikra (no Gradle: the sandbox hijacks Java loopback sockets,
# which breaks the Gradle daemon protocol; aapt2/kotlinc/d8/apksigner need no sockets).
set -e
export JAVA_HOME=~/jdk17
export PATH=$JAVA_HOME/bin:$PATH
SDK=~/android-sdk
BT=$SDK/build-tools/34.0.0
ANDROID_JAR=$SDK/platforms/android-34/android.jar
KOTLINC=~/kotlin-compiler/bin/kotlinc
STDLIB=~/kotlin-compiler/lib/kotlin-stdlib.jar
SRC=~/workspace/ayah-widget-app/app/src/main
OUT=/tmp/apkbuild/out
rm -rf "$OUT" && mkdir -p "$OUT"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/compiled.zip"

echo "== aapt2 link =="
"$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$SRC/AndroidManifest.xml" \
  -A "$SRC/assets" \
  --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  "$OUT/compiled.zip"

echo "== javac (R.java) =="
mkdir -p "$OUT/classes"
find "$OUT/gen" -name '*.java' > "$OUT/rjava.txt"
javac -cp "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/rjava.txt"

echo "== kotlinc =="
find "$SRC/java" -name '*.kt' > "$OUT/kt.txt"
"$KOTLINC" -cp "$ANDROID_JAR:$OUT/classes:$STDLIB" -d "$OUT/classes" \
  -jvm-target 17 @"$OUT/kt.txt"

echo "== jar classes =="
"$JAVA_HOME/bin/jar" -cf "$OUT/classes.jar" -C "$OUT/classes" .

echo "== d8 =="
mkdir -p "$OUT/dex"
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --release \
  --output "$OUT/dex" "$OUT/classes.jar" "$STDLIB"

echo "== package classes.dex =="
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && "$BT/aapt" add "$OUT/unsigned.apk" classes.dex)

echo "== zipalign =="
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== keystore =="
if [ ! -f /tmp/debug.keystore ]; then
  keytool -genkeypair -keystore /tmp/debug.keystore -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10950 -dname "CN=Android Debug,O=Android,C=US"
fi

echo "== apksigner =="
"$BT/apksigner" sign --ks /tmp/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --out "$OUT/dhikra.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/dhikra.apk" && echo VERIFY_OK
ls -la "$OUT/dhikra.apk"
