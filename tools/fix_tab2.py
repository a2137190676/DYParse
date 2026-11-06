# -*- coding: utf-8 -*-
"""去掉剩余的自动跳列表：批量解析完成、磁力解析。"""
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


# 1) 批量解析完成：不跳
rep('批量完成不跳',
    '''                    // 把收集到的结果渲染到列表页，并自动切过去方便查看
                    renderBatchList();
                    saveBatchList();          // 落盘，退出重进可恢复
                    switchTab(1);''',
    '''                    // 渲染到列表页，但不自动切过去（用户要求只有主页/合集才跳）
                    renderBatchList();
                    saveBatchList();          // 落盘，退出重进可恢复''')

# 2) 磁力早期入列表：不跳
rep('磁力早期不跳',
    '''            h.post(new Runnable(){ public void run(){
                renderBatchList();
                switchTab(1);
            }});
        }catch(Throwable t){ Log.i("DYP", "BT_LIST_EARLY_ERR " + t); }''',
    '''            h.post(new Runnable(){ public void run(){
                renderBatchList();
            }});
        }catch(Throwable t){ Log.i("DYP", "BT_LIST_EARLY_ERR " + t); }''')

# 3) 磁力元数据入列表：不跳
rep('磁力元数据不跳',
    '''                        saveBatchList();
                        renderBatchList();
                        switchTab(1);
                        Log.i("DYP", "BT_LIST_ADDED " + listId + " hit=" + hit);''',
    '''                        saveBatchList();
                        renderBatchList();
                        Log.i("DYP", "BT_LIST_ADDED " + listId + " hit=" + hit);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)
print()
print('=== 剩余 switchTab(1) ===')
lines = s.split('\n')
for i, ln in enumerate(lines):
    if 'switchTab(1)' in ln:
        prev = lines[i - 1].strip() if i > 0 else ''
        print('行 %d: <<< %s' % (i + 1, prev[:70]))