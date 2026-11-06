# -*- coding: utf-8 -*-
# 更稳的做法：dlRowUpdate / dlRowClear 内部判断，silent 的 key 直接忽略
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 加 silentKeys 集合
old = '''    /** 下载列表：新建一行（key 用任务序号） */
    void dlRowAdd(final int key, String fileName){'''
new = '''    /** silent 任务的 key：这些不参与下载列表渲染 */
    final java.util.Set<Integer> dlSilentKeys =
            java.util.Collections.synchronizedSet(new java.util.HashSet<Integer>());

    /** 下载列表：新建一行（key 用任务序号） */
    void dlRowAdd(final int key, String fileName){
        if (dlSilentKeys.contains(key)) return;   // silent 任务不建行'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('silentKeys OK')
else:
    print('MISS silentKeys')

# 2) 在 runOneTask 里把 silent 的 key 注册进集合
old2 = '''        final boolean silent = task.silent;
        final int rowKey = idx;
        if (!silent) {
            dlRowAdd(rowKey, name);
            dlRowUpdate(rowKey, 0, "连接中…");
        }'''
new2 = '''        final boolean silent = task.silent;
        final int rowKey = idx;
        if (silent) {
            dlSilentKeys.add(rowKey);   // 标记：所有 dlRow* 调用都会自动忽略
        } else {
            dlRowAdd(rowKey, name);
            dlRowUpdate(rowKey, 0, "连接中…");
        }'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('注册 silent OK')
else:
    print('MISS 注册')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)

# 看看 dlRowUpdate / dlRowClear / dlRowRename 的签名
for fn in ['void dlRowUpdate', 'void dlRowClear', 'void dlRowRename', 'void dlRefreshHead']:
    i = d.find(fn)
    print('---', fn, '在', d[:i].count(chr(10))+1 if i>0 else 'MISS')