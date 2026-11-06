# -*- coding: utf-8 -*-
# runOneTask 支持 silent：单个下载不建列表行，只更新解析页进度条
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 建行改为条件建
old = '''        // 下载列表：建行并标记「连接中」
        final int rowKey = idx;
        dlRowAdd(rowKey, name);
        dlRowUpdate(rowKey, 0, "连接中…");'''
new = '''        // 单个下载(silent)不进「下载」页，只在解析页进度条显示
        final boolean silent = task.silent;
        final int rowKey = idx;
        if (!silent) {
            dlRowAdd(rowKey, name);
            dlRowUpdate(rowKey, 0, "连接中…");
        }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('建行条件化 OK')
else:
    print('MISS 建行')

# 2) dlRowUpdate 全部包一层：silent 时替换成解析页进度
#    找一个集中包装的办法：给 dlRowUpdate 加个静默判断不行（不知道 key 对应哪条）
#    所以改成：silent 时调用一个专用于解析页的更新
cnt = d.count('dlRowUpdate(rowKey,')
print('dlRowUpdate(rowKey, 出现', cnt, '次')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)