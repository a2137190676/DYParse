# -*- coding: utf-8 -*-
# 补 btCbRef 字段 + 在 runMagnetDownload 里赋值
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 字段
old_f = '''    /** 边下边播轮询线程是否还在跑 */
    volatile boolean btStreamWatch = false;'''
new_f = '''    /** 边下边播轮询线程是否还在跑 */
    volatile boolean btStreamWatch = false;
    /** 当前磁力任务的回调引用（给边下边播轮询线程用） */
    volatile BtDownloader.Cb btCbRef = null;'''
if old_f in d:
    d = d.replace(old_f, new_f, 1); n += 1; print('字段 OK')
else:
    print('MISS 字段')

# 2) 在 BtDownloader.start 前先把 cb 存到变量
old_s = '''        BtDownloader.start(magnet, dir, new BtDownloader.Cb(){'''
new_s = '''        BtDownloader.Cb _cb = new BtDownloader.Cb(){'''
if old_s in d:
    d = d.replace(old_s, new_s, 1); n += 1; print('改匿名类头 OK')
else:
    print('MISS 匿名类')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)

# 校验结尾
d2 = io.open(P, encoding='utf-8').read()
i = d2.find('BtDownloader.Cb _cb = new BtDownloader.Cb(){')
print('--- 尾部需要改成 }); 然后 start ---')
# 找这个匿名类的结尾 "        });"
j = d2.find('\n        });', i)
print(repr(d2[j-200:j+60]) if j > 0 else 'not found')