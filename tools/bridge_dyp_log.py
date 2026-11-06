# -*- coding: utf-8 -*-
"""
【第 4 项】让所有功能都进调试日志文件。

【现状】
  全项目有 721 处 Log.i("DYP", ...) —— 功能日志其实写得很全，
  但它们只进 logcat（重启就没了、手机上看不到、用户也没法导出发给我们）。
  真正落文件的 DiagLog 只有 46 处。

  所以用户反馈「调试日志里没有我要的信息」，
  不是日志写得少，而是 721 条主力日志根本没进文件。

【改法】
  新增 DypLog 类，作为 Log.i("DYP", ...) 的替身：
      DypLog.i(msg)   → logcat + DiagLog 文件
      DypLog.w(msg)   → logcat + DiagLog 文件
      DypLog.e(msg)   → logcat + DiagLog 文件（同时进 crash.log）

  然后把 MainActivity 里所有  Log.i("DYP", X)  批量替换成  DypLog.i(X)。

  注意：
    · 不动 Log.e / Log.w / Log.d 的其他 tag（那些是系统或第三方库的）
    · 只替换 tag 严格等于 "DYP" 的
    · DypLog 内部用 DiagLog.i(...)，所以仍然受「调试日志开关」控制，
      关着的时候不写文件（避免无谓 IO），但 logcat 照常
    · 加一个「强制记录」模式：用户在设置里点「开始记录」后，
      即使开关关着也写文件，方便用户帮我们抓现场
"""
import io
import re

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# 统计一下要替换多少
pat = re.compile(r'Log\.i\(\s*"DYP"\s*,\s*')
before = len(pat.findall(s))
print('待替换 Log.i("DYP", ...):', before)

# 替换：Log.i("DYP", X)  ->  DypLog.i(X)
# 用循环逐个处理，能正确配对右括号（X 里可能有嵌套括号）
out = []
idx = 0
count = 0
while True:
    m = pat.search(s, idx)
    if not m:
        out.append(s[idx:])
        break
    out.append(s[idx:m.start()])
    # 从 X 的起点开始，找到与之配对的右括号
    j = m.end()
    depth = 1
    in_str = False
    esc = False
    while j < len(s):
        c = s[j]
        if in_str:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == '"':
                in_str = False
            j += 1
            continue
        if c == '"':
            in_str = True
        elif c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                break
        j += 1
    if j >= len(s):
        # 配对失败，原样保留
        out.append(s[m.start():m.end()])
        idx = m.end()
        continue
    expr = s[m.end():j]          # X
    out.append('DypLog.i(' + expr + ')')
    idx = j + 1                  # 跳过原来的 ')'
    count += 1

s2 = ''.join(out)
io.open(P, 'w', encoding='utf-8').write(s2)
print('已替换:', count)
print('剩余 Log.i("DYP"):', len(pat.findall(s2)))