#!/bin/bash
#
# DY解析器 - 一键编译脚本（支持 AAR 依赖）
#
# 依赖：aapt / zipalign / apksigner / javac / unzip / python3
#      + d8.jar 或 r8.jar（二选一，脚本会自动找，找不到就尝试下载）
# AAR 放 libs/*.aar，脚本会自动解包、合并资源、生成 R.java、打进 dex
#
# 常用环境变量：
#   ANDROID_JAR   默认 ./android.jar
#   KS_FILE       默认 ./debug.keystore
#   KS_PASS       默认 android
#   D8_JAR        手动指定 d8.jar / r8.jar 路径
#   NO_DOWNLOAD   设 1 则禁止联网下载 r8.jar
#   NO_SDCARD     设 1 则不复制到 /sdcard/Download
#
set -u
cd "$(dirname "$0")" || exit 1

ANDROID_JAR="${ANDROID_JAR:-./android.jar}"
KS_FILE="${KS_FILE:-./debug.keystore}"
KS_PASS="${KS_PASS:-android}"
PKG="com.re.douyinparse"

BUILD=build

# ---------- 0. 环境检查 ----------
echo '=== env check ==='
for t in aapt zipalign apksigner javac python3 unzip; do
  command -v "$t" >/dev/null 2>&1 || {
    echo "[ERR] 缺少命令：$t"
    echo "      Debian/Ubuntu: apt-get install -y android-sdk-build-tools android-sdk-platform-23 zipalign apksigner openjdk-17-jdk-headless"
    exit 1
  }
done
[ -f "$ANDROID_JAR" ] || { echo "[ERR] 缺 $ANDROID_JAR（Android SDK platform 的 android.jar）"; exit 2; }
echo "       tools ok"

# ---------- 0.1 找 dex 工具链（d8.jar / r8.jar）----------
# d8 和 r8 是同一份 jar 里两个入口，哪个能用就用哪个
DEX_RUNNER=""
probe_dex_jar(){
  local j="$1"
  [ -f "$j" ] || return 1
  if unzip -l "$j" 2>/dev/null | grep -q 'com/android/tools/r8/D8.class'; then
    DEX_RUNNER="$j"; return 0
  fi
  return 1
}

if [ -n "${D8_JAR:-}" ]; then
  probe_dex_jar "$D8_JAR" || { echo "[ERR] D8_JAR=$D8_JAR 里没有 com/android/tools/r8/D8.class"; exit 3; }
fi

if [ -z "$DEX_RUNNER" ]; then
  for c in \
    "${ANDROID_HOME:-}/build-tools"/*/lib/d8.jar \
    "${ANDROID_SDK_ROOT:-}/build-tools"/*/lib/d8.jar \
    /opt/android/android-*/lib/d8.jar \
    /opt/android-sdk/build-tools/*/lib/d8.jar \
    /usr/lib/android-sdk/build-tools/*/lib/d8.jar \
    ./build-tools/*/lib/d8.jar \
    ./tools/r8.jar ./tools/d8.jar ./r8.jar ./d8.jar \
  ; do
    [ -f "$c" ] || continue
    probe_dex_jar "$c" && { echo "       dex tool: $c"; break; }
  done
fi

if [ -z "$DEX_RUNNER" ] && [ "${NO_DOWNLOAD:-0}" != "1" ]; then
  echo '       d8/r8 未找到，尝试下载 r8.jar ...'
  mkdir -p tools
  for u in \
    https://maven.aliyun.com/repository/google/com/android/tools/r8/8.2.42/r8-8.2.42.jar \
    https://maven.aliyun.com/repository/public/com/android/tools/r8/8.2.42/r8-8.2.42.jar \
  ; do
    echo "       try $u"
    if curl -fsSL --max-time 120 -o tools/r8.jar "$u" 2>/dev/null && [ -s tools/r8.jar ]; then
      probe_dex_jar tools/r8.jar && { echo "       dex tool: tools/r8.jar (downloaded)"; break; }
      rm -f tools/r8.jar
    fi
  done
fi

[ -n "$DEX_RUNNER" ] || {
  echo '[ERR] 找不到 d8/r8。请任选一种：'
  echo '      1) apt-get install -y android-sdk-build-tools'
  echo '      2) 手动下载 r8.jar 放到 ./tools/r8.jar'
  echo '      3) D8_JAR=/path/to/d8.jar bash build.sh'
  exit 3
}

# ---------- 0.2 注入内置服务器凭据（secret_src/ 不进开源仓库）----------
# 默认构建（加密版）：真版覆盖空壳类（SrvSecret/SrvCert/SrvClient），编译带
#                     内置服务器 + TLS pin + HMAC 鉴权（含真实凭据混淆值）
# PLAIN=1 构建（未加密原包）：真版「结构 + 空值」覆盖（.empty 版），
#                     代码完整可逆向（含加密实现逻辑），但不含真实凭据值，
#                     供本地存档防源码丢失
SECRET_DIR="./secret_src"
if [ "${PLAIN:-0}" = "1" ]; then
  if [ -f "$SECRET_DIR/SrvSecret.java.empty" ] && [ -f "$SECRET_DIR/SrvCert.java.empty" ] && [ -f "$SECRET_DIR/SrvClient.java" ]; then
    cp "$SECRET_DIR/SrvSecret.java.empty" java/com/re/douyinparse/SrvSecret.java
    cp "$SECRET_DIR/SrvCert.java.empty"  java/com/re/douyinparse/SrvCert.java
    cp "$SECRET_DIR/SrvClient.java"       java/com/re/douyinparse/SrvClient.java
    echo "inject_srv: PLAIN build（原包：真源码结构 + 空凭据值，可逆向）"
  else
    echo "inject_srv: PLAIN build but secret_src incomplete, using stubs"
  fi
else
  if [ -d "$SECRET_DIR" ] && [ -f "$SECRET_DIR/SrvSecret.java" ] && [ -f "$SECRET_DIR/SrvCert.java" ] && [ -f "$SECRET_DIR/SrvClient.java" ]; then
    cp "$SECRET_DIR/SrvSecret.java" java/com/re/douyinparse/SrvSecret.java
    cp "$SECRET_DIR/SrvCert.java"  java/com/re/douyinparse/SrvCert.java
    cp "$SECRET_DIR/SrvClient.java" java/com/re/douyinparse/SrvClient.java
    echo "inject_srv: built-in server ON (secret_src found)"
  else
    echo "inject_srv: no secret_src, built-in server disabled (占位版编译)"
  fi
fi

python3 tools/inject_srv.py || { echo '[ERR] inject_srv 失败'; exit 4; }

# ---------- 1. 解包 AAR ----------
rm -rf "$BUILD/aars" "$BUILD/res-overlays" "$BUILD/gen" "$BUILD/obj" "$BUILD/dex" \
       "$BUILD/so" "$BUILD/lib.zip" "$BUILD/app-unsigned.apk" \
       "$BUILD/app-res.apk" "$BUILD/app-repacked.apk" "$BUILD/app-aligned.apk" \
       "$BUILD/DYParse.apk" "$BUILD/DYParse.apk.idsig"
mkdir -p "$BUILD/aars" "$BUILD/gen" "$BUILD/obj" "$BUILD/dex"

EXTRA_PKGS=""
if ls libs/*.aar >/dev/null 2>&1; then
  echo '=== unpack aar ==='
  for f in libs/*.aar; do
    n=$(basename "$f" .aar)
    mkdir -p "$BUILD/aars/$n"
    unzip -o -q "$f" -d "$BUILD/aars/$n" 2>/dev/null
  done

  # 包名推导 + 资源合并
  for d in "$BUILD"/aars/*/; do
    n=$(basename "$d")
    [ -f "$d/AndroidManifest.xml" ] || continue
    pkg=$(grep -o 'package="[^"]*"' "$d/AndroidManifest.xml" 2>/dev/null | head -1 | sed 's/package="//;s/"//')
    case "$n" in
      media3-ui)         [ -z "$pkg" ] && pkg="androidx.media3.ui" ;;
      media3-common)     [ -z "$pkg" ] && pkg="androidx.media3.common" ;;
      media3-exoplayer)  [ -z "$pkg" ] && pkg="androidx.media3.exoplayer" ;;
      media3-container)  [ -z "$pkg" ] && pkg="androidx.media3.container" ;;
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
      mkdir -p "$BUILD/res-overlays/$n"
      cp -r "$d/res"/. "$BUILD/res-overlays/$n"/ 2>/dev/null
    fi
  done
fi

mkdir -p "$BUILD/res-overlays/app"
cp -r res/. "$BUILD/res-overlays/app/"

# aar 里的 classes.jar + libs/下的普通 jar（如 guava）
JARS=$(find "$BUILD/aars" -name 'classes.jar' 2>/dev/null | tr '\n' ':')
if ls libs/*.jar >/dev/null 2>&1; then
  for jf in libs/*.jar; do JARS="$JARS$jf:"; done
fi

# ---------- 2. aapt 生成 R.java（含 AAR 包名） ----------
RES_ARGS="-S $BUILD/res-overlays/app"
for ov in "$BUILD"/res-overlays/*/; do
  [ "$(basename "$ov")" = "app" ] && continue
  RES_ARGS="$RES_ARGS -S ${ov%/}"
done
echo '=== aapt gen R.java ==='
aapt package -f -m -J "$BUILD/gen" -M AndroidManifest.xml $RES_ARGS --auto-add-overlay \
  $EXTRA_PKGS -I "$ANDROID_JAR" || exit 10

# ---------- 3. javac ----------
# 源文件自动收集：java/ 下所有 .java，以后新增文件不用改脚本
echo '=== javac ==='
CP_ARGS=""
[ -n "$JARS" ] && CP_ARGS="-cp $JARS"
GEN_R=$(find "$BUILD/gen" -name 'R.java' | tr '\n' ' ')
SRC=$(find java -name '*.java' | tr '\n' ' ')
echo "       R files: $GEN_R"
echo "       src: $(echo "$SRC" | wc -w) files"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" $CP_ARGS -d "$BUILD/obj" \
  $GEN_R $SRC 2>&1 | grep -v 'warning:' | grep -v 'Note:' || true

ls "$BUILD"/obj/com/re/douyinparse/*.class >/dev/null 2>&1 || { echo '[ERR] javac 失败'; exit 11; }

# ---------- 4. dex（d8）----------
echo '=== d8 ==='
D8_INPUTS=$(find "$BUILD/obj" -name '*.class')
for j in $(echo "$JARS" | tr ':' ' '); do [ -n "$j" ] && [ -f "$j" ] && D8_INPUTS="$D8_INPUTS $j"; done
java -cp "$DEX_RUNNER" com.android.tools.r8.D8 --min-api 21 --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" $D8_INPUTS 2>&1 | grep -vi 'warning in\|was not found' | tail -6
[ -f "$BUILD/dex/classes.dex" ] || { echo '[ERR] d8 没产出 classes.dex'; exit 20; }

# ---------- 5. aapt 打包资源 ----------
echo '=== aapt package ==='
aapt package -f -M AndroidManifest.xml $RES_ARGS --auto-add-overlay -I "$ANDROID_JAR" \
  -F "$BUILD/app-res.apk" || exit 30

# ---------- 5.5 收集 native so ----------
echo '=== collect jni libs ==='
if [ -d "$BUILD/aars" ]; then
  for d in "$BUILD"/aars/*/; do
    n=$(basename "$d")
    [ -d "$d/jni" ] || continue
    # ffmpeg-kit 的 so 名字带架构后缀，需重命名
    if [ "$n" = "ffmpeg-kit-audio" ]; then
      for abi_dir in "$d/jni"/*/; do
        abi=$(basename "$abi_dir")
        [ -d "$BUILD/so/$abi" ] || mkdir -p "$BUILD/so/$abi"
        for so in "$abi_dir"*.so; do
          [ -f "$so" ] || continue
          b=$(basename "$so")
          tgt=$(echo "$b" | sed -E 's/_(neon|armv7a_neon|armv7a|x86_64|x86|arm64_v8a|arm64)\.so$/.so/')
          cp "$so" "$BUILD/so/$abi/$tgt"
          echo "       $abi/$tgt"
        done
      done
    else
      cp -r "$d/jni"/* "$BUILD/so/" 2>/dev/null
    fi
  done
fi

# ---------- 6. 合包 + 规范压缩 ----------
# 关键点：
#   classes.dex / resources.arsc / lib/**/*.so 必须是 STORED（不压缩），
#   否则 zipalign 无法给 so 做 4 字节对齐，apksigner v2 之后安装也可能告警。
#   这里统一个 python 步骤：读 aapt 打好的 res apk + dex + so，重写一个新 zip。
echo '=== merge apk ==='
python3 - "$BUILD" <<'PY'
import os, sys, zipfile

build = sys.argv[1]
src_apk = os.path.join(build, 'app-res.apk')
dex     = os.path.join(build, 'dex', 'classes.dex')
so_root = os.path.join(build, 'so')
dst_apk = os.path.join(build, 'app-unsigned.apk')

# 必须 STORED 的：native so、classes.dex、resources.arsc
def must_store(name):
    return (name.startswith('lib/') and name.endswith('.so')) \
        or name == 'classes.dex' or name == 'resources.arsc'

zin = zipfile.ZipFile(src_apk, 'r')
zout = zipfile.ZipFile(dst_apk, 'w', zipfile.ZIP_DEFLATED)

def add(name, data):
    zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_STORED if must_store(name) else zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    zout.writestr(zi, data)

written = 0
for it in zin.infolist():
    if it.is_dir():
        continue
    add(it.filename, zin.read(it.filename))
    written += 1
zin.close()

# classes.dex
with open(dex, 'rb') as f:
    add('classes.dex', f.read())
print('       + classes.dex %d bytes' % os.path.getsize(dex))

# lib/**/*.so
so_count = 0
if os.path.isdir(so_root):
    for dp, dn, fn in os.walk(so_root):
        for f in sorted(fn):
            if not f.endswith('.so'):
                continue
            p = os.path.join(dp, f)
            arc = 'lib/' + os.path.relpath(p, so_root).replace(os.sep, '/')
            with open(p, 'rb') as fh:
                add(arc, fh.read())
            so_count += 1
            print('       + %s' % arc)

zout.close()
print('       entries: %d res + 1 dex + %d so -> %s (%d bytes)'
      % (written, so_count, dst_apk, os.path.getsize(dst_apk)))

# 自检：确认 so 是 STORED
z = zipfile.ZipFile(dst_apk)
bad = [i.filename for i in z.infolist()
       if i.filename.endswith('.so') and i.compress_type != zipfile.ZIP_STORED]
z.close()
if bad:
    print('[ERR] 以下 so 未按 STORED 写入：' + ', '.join(bad))
    sys.exit(41)
PY
[ "$?" -eq 0 ] || exit 41

# ---------- 7. 对齐 ----------
echo '=== zipalign ==='
zipalign -f 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk" || exit 40
zipalign -c -v 4 "$BUILD/app-aligned.apk" >/dev/null 2>&1 \
  && echo '       align ok' \
  || { echo '[ERR] zipalign 校验失败'; exit 42; }

# ---------- 8. 签名 ----------
echo '=== sign ==='
[ -f "$KS_FILE" ] || { echo "[ERR] 缺 keystore：$KS_FILE"; exit 51; }
apksigner sign --ks "$KS_FILE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$BUILD/DYParse.apk" "$BUILD/app-aligned.apk" || exit 50
apksigner verify --verbose "$BUILD/DYParse.apk" | head -8
apksigner verify "$BUILD/DYParse.apk" && echo SIGN_OK || exit 52

# ---------- 9. 收尾 ----------
# 双包分流：
#   正常构建（默认）= 加密版：含凭据混淆，上传 Gitee / 手机安装 / 对外分享
#   PLAIN=1 构建 = 未加密原包：空壳类（无凭据），存本地 releases/plain/ 防源码丢失，
#                  jadx 逆向即完整源码（无混淆、无内置服务器）
ARCHIVE_ROOT="releases"
mkdir -p "$ARCHIVE_ROOT/encrypted" "$ARCHIVE_ROOT/plain"
VER_NAME="$(grep -o 'android:versionName="[^"]*"' AndroidManifest.xml | head -1 | sed 's/.*="//;s/"//')"
[ -n "$VER_NAME" ] || VER_NAME="unknown"
if [ "${PLAIN:-0}" = "1" ]; then
  PLAIN_NAME="DYParse-v${VER_NAME}-plain.apk"
  cp "$BUILD/DYParse.apk" "$ARCHIVE_ROOT/plain/$PLAIN_NAME"
  echo "PLAIN_ARCHIVED: $ARCHIVE_ROOT/plain/$PLAIN_NAME（未加密原包，防源码丢失）"
else
  ENC_NAME="DYParse-v${VER_NAME}.apk"
  cp "$BUILD/DYParse.apk" "$ARCHIVE_ROOT/encrypted/$ENC_NAME"
  echo "ENC_ARCHIVED: $ARCHIVE_ROOT/encrypted/$ENC_NAME（加密版，发布/安装用）"
fi
echo
md5sum "$BUILD/DYParse.apk"
ls -lh "$BUILD/DYParse.apk"
echo "       icon: $(aapt dump badging "$BUILD/DYParse.apk" 2>/dev/null | grep -m1 '^application:' | sed "s/.*icon='//;s/'.*//")"

if [ "${NO_SDCARD:-0}" != "1" ] && [ -d /sdcard/Download ]; then
  cp "$BUILD/DYParse.apk" /sdcard/Download/DYParse.apk && echo COPIED_TO_SDCARD
fi

# 清掉中间产物，只留最终 apk
rm -f "$BUILD/app-res.apk" "$BUILD/app-unsigned.apk" "$BUILD/app-repacked.apk" \
      "$BUILD/app-aligned.apk" "$BUILD/lib.zip"

# 恢复 SrvConfig.java 为占位符（真实地址只留在编译产物里，不进源码/仓库）
python3 - "$BUILD" <<'PY'
import os, sys
src = os.path.join("java", "com", "re", "douyinparse", "SrvConfig.java")
with open(src, encoding="utf-8") as f:
    code = f.read()
mark = '"<INJECT_SERVER>"'
i = code.find('public static final String DEFAULT_SERVER =')
if i >= 0:
    j = code.find(';', i)
    code = code[:i] + 'public static final String DEFAULT_SERVER = ' + mark + code[j:]
with open(src, "w", encoding="utf-8") as f:
    f.write(code)
print("       SrvConfig.java restored to placeholder")
PY

# 恢复 SrvSecret/SrvCert/SrvClient 为空壳占位版（真版/.empty 版只存在于 secret_src/，防误提交）
# 两种构建模式都恢复：java/ 目录始终只有空壳，真值永不落地
python3 - "$BUILD" <<'PY'
import os
for name in ("SrvSecret", "SrvCert", "SrvClient"):
    dst = os.path.join("java", "com", "re", "douyinparse", name + ".java")
    stub = os.path.join("secret_src", name + ".java.stub")
    if os.path.isfile(stub):
        with open(stub, encoding="utf-8") as f:
            data = f.read()
        with open(dst, "w", encoding="utf-8") as f:
            f.write(data)
        print("       %s.java restored to stub" % name)
PY
exit 0