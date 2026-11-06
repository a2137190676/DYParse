# -*- coding: utf-8 -*-
# onCreate 里启动时恢复队列
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

# 在 applyTheme(isDarkMode()); 之后加恢复
old = '''        applyTheme(isDarkMode());
        // 启动自动检测更新（静默，仅首次启动）'''
new = '''        applyTheme(isDarkMode());

        // 恢复上次没下完的任务（退出软件后不丢，接着下）
        h.postDelayed(new Runnable(){ public void run(){
            restoreDlTasks();
        }}, 800);

        // 启动自动检测更新（静默，仅首次启动）'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('onCreate 恢复 OK')
else:
    print('MISS')
    i = d.find('applyTheme(isDarkMode());')
    print(repr(d[i-100:i+200]) if i > 0 else 'not found')