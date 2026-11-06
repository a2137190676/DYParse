# -*- coding: utf-8 -*-
"""按行号给 pendingAutoDl 入队处加直链校验"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 定位：if(pendingAutoDlTag.length() > 0){ 后的 if(!fail && curPlay != null && curPlay.length() > 0){
target = None
for i, ln in enumerate(lines):
    if 'if(pendingAutoDlTag.length() > 0){' in ln:
        # 往后找 3 行内的 if(!fail && curPlay
        for j in range(i, min(i + 6, len(lines))):
            if 'if(!fail && curPlay != null && curPlay.length() > 0){' in lines[j]:
                target = j
                break
        if target:
            break

if target is None:
    print('未找到目标行')
    raise SystemExit(1)

print('命中行 %d: %s' % (target + 1, lines[target].strip()))

# 替换为带直链校验的版本
old_line = lines[target]
indent = old_line[:len(old_line) - len(old_line.lstrip())]
lines[target] = (
    indent + 'if(!fail && curPlay != null && curPlay.length() > 0\n'
    + indent + '        && rejectNonMediaUrl(curPlay).length() == 0){'
)

# 在 final String dl = curPlay; 之后插入日志
for k in range(target, min(target + 8, len(lines))):
    if 'final String dl = curPlay;' in lines[k]:
        ind2 = lines[k][:len(lines[k]) - len(lines[k].lstrip())]
        lines.insert(k + 1, ind2 + 'Log.i("DYP", "BATCH_DL_ENQ url=" + dl);')
        print('已在第 %d 行后插入日志' % (k + 1))
        break

io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('完成')