# -*- coding: utf-8 -*-
"""
回滚 TikTok 竞速方案，改回串行 + 加长超时。

【实测证据】
  失败那次：
      TT_REDIR_ERR java.net.SocketTimeoutException: Read timed out   ← 25s 读超时
      TT_REAL https://www.tiktok.com/t/ZPLmCJUWw/                    ← 短链没展开
      TT_RESULT err=拿不到作品ID ok=false
  成功那次：
      TT_REDIR 301 -> @wgh.1988/video/7702813303743024414  09:12:48
      TT_HTTP 200                                          09:13:00  ← 12 秒
      TT_RESULT ok=true                                    09:13:02

  → 根因是代理连 TikTok 慢/不稳，12 秒才回，25 秒超时就会挂。
  → 我的竞速方案让「服务器 + 本地」同时发两轮请求，
    代理链路更堵，反而更容易超时。属于帮倒忙，回滚。

【改法】
  1. 恢复串行：服务器 → 失败再本地
  2. 本地 followRedirect 的读超时放宽到 40 秒（原来 25 秒不够）
  3. 短链展开失败时，不再立刻放弃，改用「@i/video/{id}」也不行 ——
     因为拿不到 id。所以直接给出明确提示，让用户知道是网络问题
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


# 把竞速代码整段换回串行
START = '''                // ---- 服务器 / 本地竞速 ----'''
END = '''                // 汇总：轮询最多 40 秒，先到先用
                String srvR = null;
                for(int w = 0; w < 400; w++){
                    synchronized(ttLock){
                        if(ttDone[0]){ srvR = ttBest[0]; break; }
                    }
                    try{ Thread.sleep(100L); }catch(Throwable t){}
                }
                if(srvR != null) DypLog.i("TT_RACE_FINAL srv=" + useSrv[0]);
                if(srvR != null){'''

i1 = s.find(START)
i2 = s.find(END)
if i1 > 0 and i2 > i1:
    s = s[:i1] + '''                // ---- 第一层：服务器（串行，避免双份请求把代理链路堵死）----
                String srvR = null;
                try{ srvR = parseTikTokByServer(url); }catch(Throwable t){
                    DypLog.i("TT_SRV_THROW " + t);
                }
                if(srvR != null){''' + s[i2 + len(END):]
    ok.append('1-回滚竞速为串行')
else:
    miss.append('1-回滚竞速为串行')

io.open(P, 'w', encoding='utf-8').write(s)
print('MainActivity 成功:', ok)
print('MainActivity 失败:', miss)


# 2. 放宽 followRedirect 读超时
T = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/TtParser.java'
s2 = io.open(T, encoding='utf-8').read()
ok2, miss2 = [], []


def rep2(name, old, new):
    global s2
    if old in s2:
        s2 = s2.replace(old, new, 1)
        ok2.append(name)
    else:
        miss2.append(name)


rep2('2-放宽超时',
     '''        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);''',
     '''        // 实测：走代理时 TikTok 短链跳转经常要 12 秒以上，
        // 原来 25 秒读超时在链路抖动时会直接 SocketTimeoutException，
        // 短链展不开 → 「拿不到作品ID」。放宽到 40 秒。
        c.setConnectTimeout(20000);
        c.setReadTimeout(40000);''')

io.open(T, 'w', encoding='utf-8').write(s2)
print('TtParser 成功:', ok2)
print('TtParser 失败:', miss2)