#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/classes build/gen
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I $SDK -F build/unsigned.apk
javac -source 8 -target 8 -bootclasspath $SDK -classpath $SDK -d build/classes $(find src build/gen -name '*.java') 2>&1 | grep -v "warning" || true
dalvik-exchange --dex --output=build/classes.dex build/classes
(cd build && cp classes.dex classes.dex.tmp && mv classes.dex.tmp classes.dex && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias refocus -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Refocus" >/dev/null 2>&1
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out build/Refocus.apk build/aligned.apk
apksigner verify build/Refocus.apk && echo OK
