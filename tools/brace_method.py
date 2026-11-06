# -*- coding: utf-8 -*-
# 以「方法级 depth 应为 1」为基准：任何时刻 depth>1 且下一行是方法声明，说明上一方法没闭合
import io, re

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

def strip(s):
    q = False
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == '"' and (i == 0 or s[i-1] != '\\'):
            q = not q
            out.append(' ')
        elif c == '/' and i + 1 < len(s) and s[i+1] == '/' and not q:
            break
        elif q:
            out.append(' ')
        else:
            out.append(c)
        i += 1
    return ''.join(out)

method_re = re.compile(r'^\s{0,8}(public|private|protected|static|final|synchronized|void|String|int|long|boolean|double|float|Object|java\.|android\.|@Override|class|interface).*')

depth = 0
bad = []
for k, raw in enumerate(lines):
    s = strip(raw)
    before = depth
    depth += s.count('{') - s.count('}')
    # 判断：该行看起来像方法/字段声明，但进入时 depth != 1
    st = raw.strip()
    is_decl = (st.startswith('void ') or st.startswith('String ') or st.startswith('static ')
               or st.startswith('public ') or st.startswith('private ') or st.startswith('protected ')
               or st.startswith('@Override') or st.startswith('int ') or st.startswith('boolean ')
               or st.startswith('long ') or st.startswith('double ') or st.startswith('final '))
    if is_decl and before != 1 and k > 32:
        # 排除方法内部局部变量声明（缩进 >= 8）
        indent = len(raw) - len(raw.lstrip())
        if indent <= 6:
            bad.append((k + 1, before, st[:70]))

print('可疑位置（声明行但 depth != 1）：')
for b in bad[:20]:
    print('  行%d depth=%d  %s' % b)
print()
print('最终 depth =', depth)