# -*- coding: utf-8 -*-
# 轮询 90 秒 -> 5 分钟；间隔 3 秒 -> 2 秒（扫码后能更快捕获）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''            while(dyWatchRun && sec < 90){
                try{ Thread.sleep(3000); }catch(Throwable t){ break; }
                sec += 3;'''

new = '''            // 扫码登录经常要 1~2 分钟，90 秒太短；拉到 5 分钟
            while(dyWatchRun && sec < 300){
                try{ Thread.sleep(2000); }catch(Throwable t){ break; }
                sec += 2;'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('轮询延长 OK')
else:
    print('MISS')