# -*- coding: utf-8 -*-
"""修：开屏恢复列表时，不该跳到列表页，也不该在解析页显示「主页解析完成」。
   根因：showProfileList() 末尾无条件 s("主页解析完成…") + switchTab(1)，
        而开屏 loadBatchList() → renderBatchList() → showProfileList() 也会走到这里。
   修法：加 restoringList 标志，恢复期间只渲染、不切页签、不写状态栏。
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# 1) 新标志位
old = '''    /** 当前磁力/种子任务在「列表」页的条目 id（拿到元数据后按它更新标题） */
    volatile String btListItemId = "";'''
new = '''    /** 当前磁力/种子任务在「列表」页的条目 id（拿到元数据后按它更新标题） */
    volatile String btListItemId = "";

    /** true = 正在做「开屏恢复列表」：只渲染，不切页签、不写状态栏 */
    volatile boolean restoringList = false;'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) restoringList 字段 OK')
else:
    print('MISS 1')

# 2) loadBatchList 期间置标志
old = '''    void loadBatchList(){
        try{
            if(!batchKeepOn()) return;
            java.io.File f = batchListFile();
            if(f == null || !f.exists()) return;'''
new = '''    void loadBatchList(){
        try{
            if(!batchKeepOn()) return;
            java.io.File f = batchListFile();
            if(f == null || !f.exists()) return;
            restoringList = true;   // 恢复期间不切页签、不写状态栏'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) loadBatchList 置标志 OK')
else:
    print('MISS 2')

# 3) loadBatchList 结束清标志（正常出口 + 异常出口都要清）
old = '''            Log.i("DYP", "BATCH_LOAD n=" + tmp.size());
            if(!tmp.isEmpty()){
                renderBatchList();
            }
        }catch(Throwable t){
            Log.i("DYP", "BATCH_LOAD_ERR " + t);
        }
    }'''
new = '''            Log.i("DYP", "BATCH_LOAD n=" + tmp.size());
            if(!tmp.isEmpty()){
                renderBatchList();
            }
        }catch(Throwable t){
            Log.i("DYP", "BATCH_LOAD_ERR " + t);
        }finally{
            restoringList = false;   // 恢复结束，恢复正常行为
        }
    }'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('3) loadBatchList finally 清标志 OK')
else:
    print('MISS 3')

# 4) showProfileList 末尾：恢复期间不切页签、不写状态栏
old = '''        s("主页解析完成 · " + n + " 条 · 已切到列表页");
        switchTab(1);
    }'''
new = '''        if(restoringList){
            // 开屏恢复：只把内容渲染出来，不切页签、不写「解析完成」，
            // 否则用户一打开 App 就被拽到列表页，解析页还挂着上一轮的状态文字。
            Log.i("DYP", "LIST_RESTORE_RENDER n=" + n + " (不切页签)");
        }else{
            s("主页解析完成 · " + n + " 条 · 已切到列表页");
            switchTab(1);
        }
    }'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('4) showProfileList 恢复期不跳转 OK')
else:
    print('MISS 4')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)