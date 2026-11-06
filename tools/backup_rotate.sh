#!/data/data/com.termux/files/usr/bin/bash
# 备份轮转脚本：只保留最新两版
#   .bak2/snap.1  = 最近一次备份
#   .bak2/snap.2  = 上一次备份
#   每次执行：snap.2 删掉 → snap.1 改名 snap.2 → 新建 snap.1
set -u

cd /storage/emulated/0/MT2/mcp/DYParse || exit 1
B=.bak2
mkdir -p "$B"

# 1) 旧的 snap.2 直接删掉（只留两版）
if [ -d "$B/snap.2" ]; then
  rm -rf "$B/snap.2"
fi

# 2) snap.1 → snap.2（成为「上一版」）
if [ -d "$B/snap.1" ]; then
  mv "$B/snap.1" "$B/snap.2"
fi

# 3) 新建 snap.1，拷核心源码
mkdir -p "$B/snap.1/java/com/re/douyinparse" "$B/snap.1/res/layout"
cp java/com/re/douyinparse/*.java                          "$B/snap.1/java/com/re/douyinparse/" 2>/dev/null
cp res/layout/*.xml                                        "$B/snap.1/res/layout/"              2>/dev/null
cp AndroidManifest.xml                                     "$B/snap.1/"                         2>/dev/null
cp build.sh                                                "$B/snap.1/"                         2>/dev/null
cp version.json                                            "$B/snap.1/"                         2>/dev/null

# 4) 写入时间戳与文件数，方便确认哪一版
{
  echo "备份时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "java 文件数: $(ls -1 "$B/snap.1/java/com/re/douyinparse/" 2>/dev/null | wc -l)"
  echo "layout 文件数: $(ls -1 "$B/snap.1/res/layout/" 2>/dev/null | wc -l)"
  echo "MainActivity.java 大小: $(stat -c %s "$B/snap.1/java/com/re/douyinparse/MainActivity.java" 2>/dev/null) 字节"
  echo "BtDownloader.java 大小: $(stat -c %s "$B/snap.1/java/com/re/douyinparse/BtDownloader.java" 2>/dev/null) 字节"
} > "$B/snap.1/INFO.txt"

# 5) 清理所有旧的散装备份（.b10 / .final / .xx 之类），只保留 snap.1 与 snap.2
find "$B" -maxdepth 1 -type f -delete 2>/dev/null

echo '=== 备份完成 ==='
cat "$B/snap.1/INFO.txt"
echo '=== 当前备份目录 ==='
ls -la "$B"
echo '=== 占用空间 ==='
du -sh "$B"