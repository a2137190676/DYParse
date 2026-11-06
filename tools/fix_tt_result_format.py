# -*- coding: utf-8 -*-
"""
修：TikTok 单独解析「没画面没声音」。

【根因：是我上一版竞速改坏的】
  我的竞速线程 B 做了：
      TtParser.resolve(...) → r2 = tv.playUrl   ← 裸 URL
      h.post → o(r2)

  但正常路径不是这样，原代码是：
      String r = buildTtResult(v);              ← 组装成带【视频直链】/【封面】的结果文本
      o(r);

  o() 收到的是「结果文本」，解析器负责把里面的【视频直链】抽出来喂播放器。
  我直接塞一个裸 URL，o() 抽不到任何字段，
  于是：作品ID 有（另一个通道填的），播放器空白，00:00/00:00。

【改法】
  竞速的两个通道都改成产出「最终结果文本」：
    · 服务器通道：parseTikTokByServer 本来就返回结果文本 ✔
    · 本地通道：调 buildTtResult(tv) 拿到结果文本
  这样无论谁赢，喂给 o() 的都是同一种格式。
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


rep('1-本地通道用buildTtResult',
    '''                // 线程 B：本地（并行跑，不等待）
                new Thread(new Runnable(){ public void run(){
                    String r2 = null;
                    try{
                        TtParser.TtVideo tv = TtParser.resolve(MainActivity.this, url);
                        if(tv != null && tv.ok() && tv.playUrl != null && tv.playUrl.length() > 0){
                            r2 = tv.playUrl;
                        }
                        DypLog.i("TT_RACE_LOCAL ok=" + (r2 != null));
                    }catch(Throwable t){
                        DypLog.i("TT_RACE_LOCAL_ERR " + t);
                    }''',
    '''                // 线程 B：本地（并行跑，不等待）
                //
                // 【关键】必须走 buildTtResult 组装成「结果文本」，
                // 不能直接给 o() 塞 playUrl 裸地址 ——
                // o() 是解析结果渲染入口，靠文本里的【视频直链】字段喂播放器。
                // 塞裸 URL 的结果就是：作品ID 有、播放器黑屏、00:00/00:00。
                new Thread(new Runnable(){ public void run(){
                    String r2 = null;
                    try{
                        TtParser.TtVideo tv = TtParser.resolve(MainActivity.this, url);
                        if(tv != null && tv.ok() && tv.playUrl != null && tv.playUrl.length() > 0){
                            r2 = buildTtResult(tv);
                            if(r2 == null || r2.length() == 0) r2 = null;
                        }
                        DypLog.i("TT_RACE_LOCAL ok=" + (r2 != null)
                                + " playUrlLen=" + (tv == null ? 0 : (tv.playUrl == null ? 0 : tv.playUrl.length())));
                    }catch(Throwable t){
                        DypLog.i("TT_RACE_LOCAL_ERR " + t);
                    }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)