# -*- coding: utf-8 -*-
"""
修：主页解析会把「批量解析」子页签的内容清空。

【根因】
  enterProfileMode() 里有这么一段：
      if(!batchCollectMode){
          synchronized(batchCollected){ batchCollected.clear(); }
          // 同步删掉落盘文件，否则重启后旧批量结果又冒出来
          try{ File f = batchListFile(); if(f.exists()) f.delete(); }catch(...){}
      }
  → 进主页模式时直接把「批量解析」的内存池 + 落盘文件全删了。

  四个数据池本来就是独立的：
      itemsFromBatch(0) / itemsFromSingle(1) / itemsFromProfile(2) / itemsFromMix(3)
  各自有 list_pools.tsv 的池号隔离，页签也各自对应。
  真正多出来的破坏动作就是上面这段 clear + delete。

【为什么当初这么写】
  batchCollected 是「当前要渲染进列表页的那份数据」，
  主页解析要重新收集，所以想先清干净，避免旧结果混进来。
  但它清的是 batchCollected（批量解析的池），不是 profileCollected（主页的池）。

【改法】
  进主页模式只清「主页自己」的临时收集区：
      profileCollected.clear()   ← 本来就有
      profileSeen.clear()        ← 本来就有
  不再碰 batchCollected，也不删 batch_list.tsv。
  itemsFromBatch 保持原样，批量解析子页签的内容就保住了。
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


rep('1-不再清批量池',
    '''        // 主页/合集解析：先自动清空列表（批量模式下不清，否则会把自己收集的结果清掉）
        if(!batchCollectMode){
            synchronized(batchCollected){ batchCollected.clear(); }
            // 同步删掉落盘文件，否则重启后旧批量结果又冒出来
            try{ java.io.File f = batchListFile(); if(f != null && f.exists()) f.delete(); }catch(Throwable t){}
        }''',
    '''        // 【不要清「批量解析」的池】
        //
        // 以前这里会 batchCollected.clear() + 删 batch_list.tsv，
        // 于是只要解析一次主页，「批量解析」子页签的内容就被清空。
        //
        // 四个子页签的数据池本来就是独立的：
        //     itemsFromBatch / itemsFromSingle / itemsFromProfile / itemsFromMix
        // 主页解析只该动自己的那份（profileCollected / profileSeen，下面已有），
        // 不应该伸手去清别人的。
        //
        // 如果担心旧的主页结果混进来：profileCollected 和 profileSeen 已经在
        // 上面 clear 了；itemsFromProfile 会在这次解析完成时被 LIST_POOL_SAVE 覆盖。
        DypLog.i("PROFILE_KEEP_POOLS 保留批量/单条池不清空");''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)