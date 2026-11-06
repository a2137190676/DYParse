# -*- coding: utf-8 -*-
"""精简版：只改 DownloadService 的 5 处，用行号定位更稳。"""
import io
import re

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/DownloadService.java'
s = io.open(P, encoding='utf-8').read()

# 1) 在 lastNotifyAt 后加 foregroundStarted
s = s.replace(
    '    long lastNotifyAt = 0;',
    '    long lastNotifyAt = 0;\n'
    '    /** 是否已经进入前台（防止重复 startForegroundService 触发 5 秒超时崩溃） */\n'
    '    static volatile boolean foregroundStarted = false;', 1)

# 2) onCreate 里 super.onCreate(); 之后立刻 startForeground 兜底
s = s.replace(
    '    @Override public void onCreate(){\n        super.onCreate();\n        inst = this;',
    '    @Override public void onCreate(){\n        super.onCreate();\n        inst = this;\n'
    '        // 双保险：onCreate 就进前台，不等 onStartCommand 派发（主线程忙时会被延迟）\n'
    '        try{\n'
    '            startForeground(NOTIFY_ID, buildNotification(lastTitle, lastText, lastPct, true));\n'
    '            foregroundStarted = true;\n'
    '        }catch(Throwable te){ Log.i("DYP", "DLS_FG_CREATE_ERR " + te); }', 1)

# 3) onStartCommand 包 try-catch + 标记
s = s.replace(
    '        startForeground(NOTIFY_ID, buildNotification(lastTitle, lastText, lastPct, true));\n'
    '        // START_STICKY',
    '        try{\n'
    '            startForeground(NOTIFY_ID, buildNotification(lastTitle, lastText, lastPct, true));\n'
    '            foregroundStarted = true;\n'
    '        }catch(Throwable te){ Log.i("DYP", "DLS_FG_CMD_ERR " + te); }\n'
    '        // START_STICKY', 1)

# 4) markStart 里：已启动则跳过
s = s.replace(
    '    static void markStart(Context c){\n        lastRunning = true;\n        try{',
    '    static void markStart(Context c){\n        lastRunning = true;\n'
    '        // 服务已在前台：绝不重复 startForegroundService。\n'
    '        // 重复拉起会重置系统 5 秒计时，主线程忙时必然超时崩溃。\n'
    '        if(foregroundStarted && inst != null) return;\n'
    '        try{', 1)

# 5) onDestroy 复位
s = s.replace(
    '    @Override public void onDestroy(){\n        if(inst == this) inst = null;',
    '    @Override public void onDestroy(){\n        if(inst == this) inst = null;\n        foregroundStarted = false;', 1)

# 加 Log import（若没有）
if 'import android.util.Log;' not in s:
    s = s.replace('import android.os.PowerManager;', 'import android.os.PowerManager;\nimport android.util.Log;', 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('foregroundStarted:', s.count('foregroundStarted'))
print('DLS_FG_CREATE_ERR:', s.count('DLS_FG_CREATE_ERR'))
print('import Log:', 'import android.util.Log;' in s)