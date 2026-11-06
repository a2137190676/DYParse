# -*- coding: utf-8 -*-
"""
TikTok 解析提速：服务器 / 本地竞速，谁先出结果用谁。

【现状（实测日志）】
  TT_REDIR 301 -> @wgh.1988/video/7702813303743024414   09:12:48
  TT_HTTP 200                                            09:13:00  ← 等了 12 秒
  TT_FILL ...                                            09:13:02  ← 又 2 秒
  TT_RESULT ok=true                                      09:13:02
  合计约 15 秒。瓶颈是本地 TtParser 要下载并解析整页 HTML（实测 481976 字节）。

  对比 X 只要 0.6 秒（fxtwitter API 直返 JSON）。

【现在的顺序】
  先试服务器 /api/tt（12s 连接 + 45s 读超时），失败才回退本地 TtParser。
  实测服务器经常失败（TT_SRV 没有 200），于是每次都白等一轮，
  再叠加本地 15 秒。

【改法】
  两条路并行开跑，谁先成功用谁：
    · 服务器通道成功 → 立刻出结果，本地线程结果丢弃
    · 服务器 8 秒内没回 → 本地结果到了直接用
    · 两边都失败 → 才报错
  这样最坏情况和现在一样，最好情况从 15 秒降到 2~3 秒。
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


rep('1-竞速',
    '''                // ---- 第一层：服务器 ----
                String srvR = parseTikTokByServer(url);
                if(srvR != null){
                    DypLog.i("RESULT_BEGIN\\n" + srvR + "\\nRESULT_END");
                    final String r = srvR;
                    h.post(new Runnable(){ public void run(){
                        o(r);
                        s("完成(TikTok·加速)");
                    }});''',
    '''                // ---- 服务器 / 本地竞速 ----
                //
                // 以前是「串行」：先等服务器（可能 12s 超时），失败再本地（又 15s）。
                // 实测服务器经常不返回 200，等于每条都白等一轮。
                //
                // 现在两个线程同时开：
                //   服务器成功 → 立刻出结果，标记已出，本地结果丢弃
                //   本地先成功 → 也用，不再等服务器
                final Object ttLock = new Object();
                final boolean[] ttDone = { false };
                final String[] ttBest = { null };
                final boolean[] useSrv = { false };

                // 线程 A：服务器
                new Thread(new Runnable(){ public void run(){
                    String r1 = null;
                    try{ r1 = parseTikTokByServer(url); }catch(Throwable t){
                        DypLog.i("TT_RACE_SRV_ERR " + t);
                    }
                    if(r1 != null){
                        synchronized(ttLock){
                            if(!ttDone[0]){
                                ttDone[0] = true;
                                ttBest[0] = r1;
                                useSrv[0] = true;
                            }
                        }
                        DypLog.i("TT_RACE_WIN srv");
                    }else{
                        DypLog.i("TT_RACE_SRV_NG 等本地");
                    }
                }}).start();

                // 线程 B：本地（并行跑，不等待）
                new Thread(new Runnable(){ public void run(){
                    String r2 = null;
                    try{
                        TtParser.TtVideo tv = TtParser.resolve(MainActivity.this, url);
                        if(tv != null && tv.ok() && tv.videoUrl != null && tv.videoUrl.length() > 0){
                            r2 = tv.videoUrl;
                        }
                        DypLog.i("TT_RACE_LOCAL ok=" + (r2 != null));
                    }catch(Throwable t){
                        DypLog.i("TT_RACE_LOCAL_ERR " + t);
                    }
                    if(r2 != null){
                        synchronized(ttLock){
                            if(!ttDone[0]){
                                ttDone[0] = true;
                                ttBest[0] = r2;
                                useSrv[0] = false;
                            }
                        }
                        DypLog.i("TT_RACE_WIN local");
                    }
                }}).start();

                // 汇总：轮询最多 40 秒，先到先用
                String srvR = null;
                for(int w = 0; w < 400; w++){
                    synchronized(ttLock){
                        if(ttDone[0]){ srvR = ttBest[0]; break; }
                    }
                    try{ Thread.sleep(100L); }catch(Throwable t){}
                }
                if(srvR != null) DypLog.i("TT_RACE_FINAL srv=" + useSrv[0]);
                if(srvR != null){
                    DypLog.i("RESULT_BEGIN\\n" + srvR + "\\nRESULT_END");
                    final String r = srvR;
                    h.post(new Runnable(){ public void run(){
                        o(r);
                        s("完成(TikTok)");
                    }});''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)