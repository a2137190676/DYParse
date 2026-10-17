#!/bin/bash
#
# DY解析器 - 一键编译脚本（支持 AAR 依赖）
#
# 依赖：aapt / d8 / zipalign / apksigner / javac / unzip
# AAR 放 libs/*.aar，脚本会自动解包、合并资源、生成 R.java、打进 dex
#
set -u
cd "$(dirname "$0")" || exit 1

ANDROID_JAR="${ANDROID_JAR:-./android.jar}"
KS_FILE="${KS_FILE:-./debug.keystore}"
KS_PASS="${KS_PASS:-android}"

[ -f "$ANDROID_JAR" ] || { echo "[ERR] 缺 $ANDROID_JAR"; exit 2; }

if [ -z "${D8_JAR:-}" ]; then
  for c in "${ANDROID_HOME:-}/build-tools"/*/lib/d8.jar /opt/android/android-*/lib/d8.jar \
           /usr/lib/android-sdk/build-tools/*/lib/d8.jar; do
    [ -f "$c" ] && D8_JAR="$c" && break
  done
fi
[ -n "${D8_JAR:-}" ] && [ -f "$D8_JAR" ] || { echo "[ERR] 找不到 d8.jar"; exit 3; }

PKG="com.re.douyinparse"

# ---------- 1. 解包 AAR ----------
rm -rf build/aars build/merged-res build/res-overlays build/gen build/obj build/dex
mkdir -p build/aars build/merged-res build/gen build/obj build/dex

EXTRA_PKGS=""
if ls libs/*.aar >/dev/null 2>&1; then
  echo '=== unpack aar ==='
  for f in libs/*.aar; do
    n=$(basename "$f" .aar)
    mkdir -p "build/aars/$n"
    unzip -o -q "$f" -d "build/aars/$n" 2>/dev/null
  done

  # 包名推导 + 资源合并
  for d in build/aars/*/; do
    n=$(basename "$d")
    [ -f "$d/AndroidManifest.xml" ] || continue
    pkg=$(grep -o 'package="[^"]*"' "$d/AndroidManifest.xml" 2>/dev/null | head -1 | sed 's/package="//;s/"//')
    case "$n" in
      media3-ui)         [ -z "$pkg" ] && pkg="androidx.media3.ui" ;;
      media3-common)     [ -z "$pkg" ] && pkg="androidx.media3.common" ;;
      media3-exoplayer)  [ -z "$pkg" ] && pkg="androidx.media3.exoplayer" ;;
      media3-container)   [ -z "$pkg" ] && pkg="androidx.media3.container" ;;
      media3-datasource) [ -z "$pkg" ] && pkg="androidx.media3.datasource" ;;
      media3-decoder)    [ -z "$pkg" ] && pkg="androidx.media3.decoder" ;;
      media3-extractor)  [ -z "$pkg" ] && pkg="androidx.media3.extractor" ;;
      core)              [ -z "$pkg" ] && pkg="androidx.core" ;;
      recyclerview-*)    [ -z "$pkg" ] && pkg="androidx.recyclerview" ;;
      lifecycle-runtime-*)[ -z "$pkg" ] && pkg="androidx.lifecycle" ;;
      versionedparcelable-*)[ -z "$pkg" ] && pkg="androidx.versionedparcelable" ;;
      interpolator-*)    [ -z "$pkg" ] && pkg="androidx.interpolator" ;;
    esac
    if [ -n "$pkg" ]; then
      EXTRA_PKGS="$EXTRA_PKGS --extra-packages $pkg"
      echo "       pkg: $pkg"
    fi
    # 用 aapt 的 overlay 机制处理同名文件冲突：先拷到独立目录
    if [ -d "$d/res" ]; then
      mkdir -p "build/res-overlays/$n"
      cp -r "$d/res"/. "build/res-overlays/$n"/ 2>/dev/null
    fi
  done
fi

mkdir -p build/res-overlays/app
cp -r res/. build/res-overlays/app/

# aar 里的 classes.jar + libs/下的普通 jar（如 guava）
JARS=$(find build/aars -name 'classes.jar' 2>/dev/null | tr '\n' ':')
if ls libs/*.jar >/dev/null 2>&1; then
  for jf in libs/*.jar; do JARS="$JARS$jf:"; done
fi

# ---------- 2. aapt 生成 R.java（含 AAR 包名） ----------
RES_ARGS="-S build/res-overlays/app"
for ov in build/res-overlays/*/; do
  [ "$(basename "$ov")" = "app" ] && continue
  RES_ARGS="$RES_ARGS -S ${ov%/}"
done
echo '=== aapt gen R.java ==='
aapt package -f -m -J build/gen -M AndroidManifest.xml $RES_ARGS --auto-add-overlay \
  $EXTRA_PKGS -I "$ANDROID_JAR" || exit 10

# ---------- 3. javac ----------
echo '=== javac ==='
CP_ARGS=""
[ -n "$JARS" ] && CP_ARGS="-cp $JARS"
GEN_R=$(find build/gen -name 'R.java' | tr '\n' ' ')
echo "       R files: $GEN_R"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" $CP_ARGS -d build/obj \
  $GEN_R java/com/re/douyinparse/MainActivity.java java/com/re/douyinparse/PlayerActivity.java \
  2>&1 | grep -v 'warning:' | grep -v 'Note:' || true

ls build/obj/com/re/douyinparse/*.class >/dev/null 2>&1 || { echo '[ERR] javac 失败'; exit 11; }

# ---------- 4. d8 ----------
echo '=== d8 ==='
D8_INPUTS=$(find build/obj -name '*.class')
for j in $(echo "$JARS" | tr ':' ' '); do [ -n "$j" ] && D8_INPUTS="$D8_INPUTS $j"; done
java -cp "$D8_JAR" com.android.tools.r8.D8 --min-api 21 --lib "$ANDROID_JAR" \
  --output build/dex $D8_INPUTS 2>&1 | tail -6 || exit 20

# ---------- 5. 打包 ----------
echo '=== aapt package ==='
aapt package -f -M AndroidManifest.xml $RES_ARGS --auto-add-overlay -I "$ANDROID_JAR" \
  -F build/app-unsigned.apk || exit 30
( cd build/dex && aapt add ../app-unsigned.apk classes.dex ) || exit 31

zipalign -f 4 build/app-unsigned.apk build/app-aligned.apk || exit 40

apksigner sign --ks "$KS_FILE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out build/DYParse.apk build/app-aligned.apk || exit 50
apksigner verify build/DYParse.apk && echo SIGN_OK

md5sum build/DYParse.apk
ls -lh build/DYParse.apk

[ -d /sdcard/Download ] && cp build/DYParse.apk /sdcard/Download/DYParse.apk && echo COPIED_TO_SDCARD
exit 0
