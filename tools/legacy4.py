# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# dlRowUpdate 开头加判断
old = '''    void dlRowUpdate(final int key, final int progress, final String meta){'''
new = '''    void dlRowUpdate(final int key, final int progress, final String meta){
        if (dlSilentKeys.contains(key)) {
            // silent（单个下载）不进列表：只更新解析页进度条
            if (progress >= 0) progSet(progress / 1000f, meta);
            return;
        }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('dlRowUpdate OK')
else:
    print('MISS dlRowUpdate')

# dlRowRename 加判断
old2 = '''    void dlRowRename(final int key, final String fileName){'''
new2 = '''    void dlRowRename(final int key, final String fileName){
        if (dlSilentKeys.contains(key)) return;'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('dlRowRename OK')
else:
    print('MISS dlRowRename')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)