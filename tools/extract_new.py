# -*- coding: utf-8 -*-
"""提取损坏文件中 Gitee 版没有的方法（整块，含方法体）"""
import io, re

B = '/storage/emulated/0/Download/MainActivity_broken_backup.java'
G = '/storage/emulated/0/Download/MainActivity.java'
OUT = '/storage/emulated/0/MT2/mcp/DYParse/tools/new_methods.txt'

bd = io.open(B, 'r', encoding='utf-8', errors='replace').read()
gd = io.open(G, 'r', encoding='utf-8', errors='replace').read()
bl = bd.split('\n')

def sig_names(text):
    return set(re.findall(r'[A-Za-z_]\w*\s*\(', text))

# 方法起始行：行首有修饰符/返回类型且以 ( 结尾或含 ( 
SIG = re.compile(r'^\s+(?:public|private|protected|static|final|synchronized|abstract|\s)*(?:void|int|long|boolean|String|float|double|char|byte|short|Object|View|Bitmap|File|List|Map|JSONObject|JSONArray|Dialog|[A-Z][\w\.]*(?:<[^>]*>)?(?:\[\])*)\s+([A-Za-z_]\w*)\s*\(')

# 用括号配平找方法体结束
def block_end(start):
    depth = 0
    seen = False
    for i in range(start, len(bl)):
        s = bl[i]
        # 粗略去字符串
        s2 = re.sub(r'"(?:\\.|[^"\\])*"', '""', s)
        s2 = re.sub(r"'(?:\\.|[^'\\])*'", "''", s2)
        s2 = re.sub(r'//.*$', '', s2)
        for ch in s2:
            if ch == '{':
                depth += 1
                seen = True
            elif ch == '}':
                depth -= 1
        if seen and depth <= 0:
            return i
    return -1

out = []
i = 0
while i < len(bl):
    m = SIG.match(bl[i])
    if m:
        name = m.group(1)
        # Gitee 里有同名方法？用 name + 完整签名判断
        sig = bl[i].strip()
        key = re.sub(r'\s+', ' ', sig)
        g_has = key in re.sub(r'\s+', ' ', gd)
        if not g_has:
            e = block_end(i)
            if e > i:
                out.append((i + 1, e + 1, name, '\n'.join(bl[i:e+1])))
                i = e + 1
                continue
    i += 1

with io.open(OUT, 'w', encoding='utf-8') as f:
    f.write('// 损坏文件中独有（本轮新增）的方法，共 %d 个\n\n' % len(out))
    for s, e, name, code in out:
        f.write('// ===== %s  (原第 %d~%d 行) =====\n' % (name, s, e))
        f.write(code + '\n\n')

print('提取 %d 个方法 -> %s' % (len(out), OUT))
for s, e, name, code in out:
    print('  %-32s %d~%d (%d行)' % (name, s, e, e - s + 1))