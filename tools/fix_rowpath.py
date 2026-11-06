# -*- coding: utf-8 -*-
"""给下载页每行填上「本地路径 / 是否磁力」，删除按钮才能定位到文件。"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# 1) 磁力行：记目录 + 标记 isBt
old = '''        dlRowAdd(rowKey, "【磁力】" + btDisplayName(magnet));
        dlRowUpdate(rowKey, -1, "获取种子信息…");'''
new = '''        dlRowAdd(rowKey, "【磁力】" + btDisplayName(magnet));
        dlRowUpdate(rowKey, -1, "获取种子信息…");
        dlRowPath.put(rowKey, saveDir.getAbsolutePath());   // 删除时用
        dlRowIsBt.put(rowKey, true);'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) 磁力行路径 OK')
else:
    print('MISS 1')

# 2) 开屏恢复的磁力行
old = '''                dlRowAdd(bk, "【磁力·未完成】" + btM.substring(0, Math.min(32, btM.length())));
                dlRowUpdate(bk, 0, "点「继续下载」接着下");
                dlRowResume.put(bk, null);
                btRowKey = Math.max(btRowKey, bk);'''
new = '''                dlRowAdd(bk, "【磁力·未完成】" + btM.substring(0, Math.min(32, btM.length())));
                dlRowUpdate(bk, 0, "点「继续下载」接着下");
                dlRowResume.put(bk, null);
                dlRowIsBt.put(bk, true);
                try{
                    // 未完成磁力的落盘目录（按 infohash 定位）
                    dlRowPath.put(bk, btSaveDir(btM).getAbsolutePath());
                }catch(Throwable t){}
                btRowKey = Math.max(btRowKey, bk);'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) 开屏磁力行路径 OK')
else:
    print('MISS 2')

# 3) 清空已完成 重建时的磁力行
old = '''                dlRowAdd(bk, "【磁力·未完成】" + btM.substring(0, Math.min(32, btM.length())));
                dlRowUpdate(bk, 0, "点「继续下载」接着下");
                dlRowResume.put(bk, null);
            }'''
new = '''                dlRowAdd(bk, "【磁力·未完成】" + btM.substring(0, Math.min(32, btM.length())));
                dlRowUpdate(bk, 0, "点「继续下载」接着下");
                dlRowResume.put(bk, null);
                dlRowIsBt.put(bk, true);
                try{
                    dlRowPath.put(bk, btSaveDir(btM).getAbsolutePath());
                }catch(Throwable t){}
            }'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('3) 清空已完成磁力行 OK')
else:
    print('MISS 3')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)