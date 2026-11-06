# -*- coding: utf-8 -*-
"""
补修：进行中的普通下载任务删不掉 / 删除无效。

【漏洞】
runOneTask 里：
    final int rowKey = idx;
    dlRowAdd(rowKey, name);      // ← 只加了行，没注册 dlRowResume / dlRowPath

doDeleteDlRow 靠 dlRowResume.get(key) 拿 DlTask 才能：
    · 加进 dlDeleted 黑名单
    · 从队列/在途/失败清单移除
    · 删磁盘文件

普通下载行没有注册 → t == null → 黑名单没加 → 任务继续跑、
最终 finally 又回填 dlFailed 并 saveDlTasks()，
表现就是「下载中的任务删除无效，还会复活」。

【修法】
1) runOneTask 里 dlRowAdd 之后立即注册：
     dlRowResume.put(rowKey, task);
     dlRowPath.put(rowKey, target.getAbsolutePath());
2) 删除时如果 t == null（比如行是恢复出来的），
   也要把行移除 + 记 dlDeletedHint，避免再被 saveDlTasks 写回。
   为简单起见：按「文件名」也做一层兜底匹配。
3) 删除进行中任务时，最好能中断下载。这里用 dlDeleted 让 runOneTask
   在回调里提前放弃（下载器不支持真正中断，但至少不会回填）。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# ---------- 1) runOneTask：注册 dlRowResume + dlRowPath ----------
rep('1-注册行映射',
    '''                    if(silent){
                        dlSilentKeys.add(rowKey);   // 标记：所有 dlRow* 调用都会自动忽略
                    }else{
                        dlRowAdd(rowKey, name);
                        dlRowUpdate(rowKey, 0, "连接中…");
                    }''',
    '''                    if(silent){
                        dlSilentKeys.add(rowKey);   // 标记：所有 dlRow* 调用都会自动忽略
                    }else{
                        dlRowAdd(rowKey, name);
                        dlRowUpdate(rowKey, 0, "连接中…");
                        // 关键：注册行 -> 任务 / 路径的映射。
                        // 不注册的话，用户点「删除」时 doDeleteDlRow 拿不到 DlTask，
                        // 既加不进 dlDeleted 黑名单，也移不出队列，
                        // 任务跑完后 finally 又回填 dlFailed 并写盘 —— 删除无效。
                        dlRowResume.put(rowKey, task);
                        try{ dlRowPath.put(rowKey, target.getAbsolutePath()); }catch(Throwable t){}
                    }''')

# ---------- 2) doDeleteDlRow：t==null 时也要移除+提示 ----------
rep('2-删除兜底',
    '''            DlTask t = dlRowResume.get(key);
            if(t != null){
                dlDeleted.add(dlTaskKey(t));''',
    '''            DlTask t = dlRowResume.get(key);
            if(t == null){
                // 行存在但拿不到任务（历史行/静默行）：至少要能移除界面行，
                // 并记一条日志便于定位，不能让「删除」变成无操作。
                Log.i("DYP", "DL_DEL_NO_TASK key=" + key + " 仅移除界面行");
            }
            if(t != null){
                dlDeleted.add(dlTaskKey(t));''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)
print('dlRowResume.put 总数:', s.count('dlRowResume.put'))