#!/system/bin/sh
# 可靠清空 DYParse 输入框：全选删除 → 用 uiautomator 读回验证，最多 10 轮。
#
# 为什么不能用「按 120 次 DEL」：
#   1) 光标不一定在末尾，DEL 只删光标前的字符；
#   2) 粘贴内容可能含换行，DEL 行为不一致；
#   3) 之前就是没验证，上一个链接残留拼到新链接后面，
#      video-crack 收到脏 content 直接返回 code=-2「不支持的内容」。
#
# 用法： sh tools/clear_box.sh  → 输出 CLEARED round=N 或 CLEAR_FAILED

BOX_X=632
BOX_Y=884
HINT='粘贴分享链接或口令（支持一行一个，批量解析）'

read_box() {
  uiautomator dump /sdcard/_clr.xml >/dev/null 2>&1
  grep -o '<node[^>]*com.re.douyinparse:id/input[^>]*>' /sdcard/_clr.xml 2>/dev/null \
    | head -1 | sed -n 's/.*text="\([^"]*\)".*/\1/p'
}

i=0
while [ $i -lt 10 ]; do
  i=$((i+1))
  input tap $BOX_X $BOX_Y
  sleep 0.4
  input keyevent KEYCODE_MOVE_END
  sleep 0.2
  # 全选后删除：先 Ctrl+A（KEYCODE_A=29），再一次 DEL 清掉整段
  input keycombination 113 29 >/dev/null 2>&1 || true
  sleep 0.3
  input keyevent KEYCODE_DEL
  sleep 0.3
  # 兜底：末尾再删 40 个字符
  input keyevent KEYCODE_MOVE_END
  k=0
  while [ $k -lt 40 ]; do
    input keyevent KEYCODE_DEL
    k=$((k+1))
  done
  sleep 0.5

  txt=$(read_box)
  if [ -z "$txt" ] || [ "$txt" = "$HINT" ]; then
    echo "CLEARED round=$i"
    exit 0
  fi
  echo "round=$i still_len=${#txt} head=$(echo "$txt" | cut -c1-24)"
done

echo "CLEAR_FAILED"
exit 1