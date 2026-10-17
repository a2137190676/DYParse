#!/bin/bash
#
# \u4e0b\u8f7d\u7f16\u8bd1\u6240\u9700\u7684\u4f9d\u8d56\u5e93\u5230 libs/
# \u4f9d\u8d56\uff1aMedia3 (ExoPlayer) + Guava + Kotlin stdlib + androidx collection
#
set -u
cd "$(dirname "$0")" || exit 1
mkdir -p libs

DL="https://dl.google.com/dl/android/maven2"
M2="https://repo1.maven.org/maven2"

MEDIA3_VER="1.4.1"

# Media3 \u7cfb\u5217
for a in media3-common media3-exoplayer media3-container media3-datasource media3-decoder media3-extractor; do
  out="libs/$a.aar"
  [ -f "$out" ] && { echo "skip $a"; continue; }
  echo "get $a..."
  curl -sSL -o "$out" "$DL/androidx/media3/$a/$MEDIA3_VER/$a-$MEDIA3_VER.aar" \
    -w "  %{http_code} %{size_download}\n"
done

# Guava
[ -f libs/guava.jar ] || curl -sSL -o libs/guava.jar \
  "$M2/com/google/guava/guava/32.1.3-android/guava-32.1.3-android.jar" \
  -w "guava %{http_code}\n"

# Kotlin stdlib
[ -f libs/kotlin-stdlib.jar ] || curl -sSL -o libs/kotlin-stdlib.jar \
  "$M2/org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar" \
  -w "kotlin %{http_code}\n"

# androidx collection (\u542b LruCache)
[ -f libs/collection-jvm.jar ] || curl -sSL -o libs/collection-jvm.jar \
  "$DL/androidx/collection/collection-jvm/1.4.0/collection-jvm-1.4.0.jar" \
  -w "collection %{http_code}\n"

# androidx annotation
[ -f libs/annotation.jar ] || curl -sSL -o libs/annotation.jar \
  "$DL/androidx/annotation/annotation-jvm/1.7.1/annotation-jvm-1.7.1.jar" \
  -w "annotation %{http_code}\n"

echo
echo "=== done ==="
ls -lh libs/
