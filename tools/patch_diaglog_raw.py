# -*- coding: utf-8 -*-
"""
给 DiagLog 补 DypLog 需要的两个东西：
  · appCtx 提升为 public（DypLog 要读 prefs）
  · LEVEL_I / LEVEL_W / LEVEL_D 提升为 public
  · 新增 raw(level, mod, msg)：跳过开关检查，直接写文件

为什么 raw 要跳过开关：
  调用方（DypLog）已经自己判断过 shouldWrite() 了，
  如果 raw 里再判一次 isDebugOn()，「强制记录」模式就失效了。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/DiagLog.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# 1) appCtx 改 public
rep('1-appCtx',
    '''    static Context appCtx;''',
    '''    /** 应用上下文。public 是因为 DypLog 要读「强制记录」开关 */
    public static Context appCtx;''')

# 2) 级别常量改 public
rep('2-级别常量',
    '''    static final int LEVEL_D = 0;   // 调试细节
    static final int LEVEL_I = 1;   // 正常流程
    static final int LEVEL_W = 2;   // 异常但可继续
    static final int LEVEL_E = 3;   // 失败''',
    '''    public static final int LEVEL_D = 0;   // 调试细节
    public static final int LEVEL_I = 1;   // 正常流程
    public static final int LEVEL_W = 2;   // 异常但可继续
    public static final int LEVEL_E = 3;   // 失败''')

# 3) 新增 raw()
rep('3-raw方法',
    '''    /** 警告，只在开关打开时记录 */''',
    '''    /** 原样写入，不再检查开关。
     *
     *  DypLog 已经在外层判断过 shouldWrite()（含「强制记录」模式），
     *  这里如果重复判断 isDebugOn()，「强制记录」就失效了。
     */
    public static void raw(int level, String mod, String msg){
        write(debugFile(), level, mod, msg);
    }

    /** 警告，只在开关打开时记录 */''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)