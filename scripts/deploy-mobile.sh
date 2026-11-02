#!/usr/bin/env bash
# 手机端 Debug 包一键部署：打包 -> 嫁接 Python 资源 -> 对齐签名 -> 装机
set -e

ROOT=/c/Users/Smile/Desktop/iBox
BT="C:/Users/Smile/AppData/Local/Android/Sdk/build-tools/36.0.0"
ADB="/c/Users/Smile/AppData/Local/Android/Sdk/platform-tools/adb.exe"
JDK="C:/Users/Smile/.jdks/jdk-25.0.5.0.2+1"

cd "$ROOT"

JAVA_HOME="$JDK" ANDROID_HOME="C:/Users/Smile/AppData/Local/Android/Sdk" \
  ./gradlew :app:assembleMobileArm64_v8aDebug --console=plain \
  -x :chaquo:installArm64_v8aDebugPythonRequirements | grep -E "BUILD|FAILED" || true

python build/graft.py
"$BT/zipalign.exe" -f -p 4 build/ibox_grafted_unsigned.apk build/ibox_grafted_aligned.apk
JAVA_HOME="$JDK" "$BT/apksigner.bat" sign \
  --ks "C:/Users/Smile/.android/debug.keystore" \
  --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey \
  build/ibox_grafted_aligned.apk 2>/dev/null

export MSYS_NO_PATHCONV=1
"$ADB" start-server >/dev/null 2>&1
"$ADB" -s emulator-5554 install -r "C:/Users/Smile/Desktop/iBox/build/ibox_grafted_aligned.apk"
"$ADB" -s emulator-5554 shell am force-stop com.media.ibox
"$ADB" -s emulator-5554 shell monkey -p com.media.ibox -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo DEPLOYED
