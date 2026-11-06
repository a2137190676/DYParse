# -*- coding: utf-8 -*-
"""对比损坏文件与 Gitee 版中同名方法，找出被修改的方法"""
import io, re

B = '/storage/emulated/0/Download/MainActivity_broken_backup.java'
G = '/storage/emulated/0/Download/MainActivity.java'

def load(p):
    d = io.open(p, 'r', encoding='utf-8', errors='replace').read()
    return d.split('\n')

bl = load(B)
gl = load(G)

SIG = re.compile(r'^\s+(?:public |private |protected |static |final |synchronized )*(?:void|int|long|boolean|String|float|double|char|byte|short|Object|View|Bitmap|File|List|Map|JSONObject|JSONArray|Dialog|[A-Z][\w\.]*(?:<[^>]*>)?(?:\[\])*)\s+([A-Za-z_]\w*)\s*\(')

def body_end(lines, start):
    depth = 0; seen = False
    for i in range(start, len(lines)):
        s = re.sub(r'"(?:\\.|[^"\\])*"', '""', lines[i])
        s = re.sub(r"'(?:\\.|[^'\\])*'", "''", s)
        s = re.sub(r'//.*$', '', s)
        for ch in s:
            if ch == '{': depth += 1; seen = True
            elif ch == '}': depth -= 1
        if seen and depth <= 0:
            return i
    return len(lines) - 1

def collect(lines):
    m = {}
    i = 0
    while i < len(lines):
        mm = SIG.match(lines[i])
        if mm:
            name = mm.group(1)
            e = body_end(lines, i)
            key = re.sub(r'\s+', ' ', lines[i].strip())
            m.setdefault(key, []).append('\n'.join(lines[i:e+1]))
            i = e + 1
            continue
        i += 1
    return m

bm = collect(bl)
gm = collect(gl)

norm = lambda s: re.sub(r'\s+', ' ', s).strip()

changed = []
for key, bodies in bm.items():
    if key not in gm: continue
    b = norm(bodies[0])
    g = norm(gm[key][0])
    if b != g:
        changed.append((key, len(bodies[0].split('\n')), len(gm[key][0].split('\n'))))

print('=== 同名但被修改的方法: %d 个 ===' % len(changed))
for key, bl_, gl_ in changed:
    print('  %-70s 损坏%d行 / gitee%d行' % (key[:70], bl_, gl_))