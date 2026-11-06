# -*- coding: utf-8 -*-
# 367 行多了一个 }（应为 ifB 关在 365、ifA 关在 366，try 由 368 的 }catch 闭合）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 找 "}catch(Throwable t){ Log.i(\"DYP\", \"YT_CK_GRAB_ERR" 所在行
ci = None
for k in range(350, 390):
    if 'YT_CK_GRAB_ERR' in lines[k] and 'catch' in lines[k]:
        ci = k
        break

print('catch 行:', ci + 1 if ci else 'MISS')
if ci:
    # 它前一行若是孤立 "}"，删掉
    if lines[ci - 1].strip() == '}':
        del lines[ci - 1]
        print('删除多余 } OK')
    else:
        print('前一行不是孤立 }:', repr(lines[ci - 1]))

io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('done')