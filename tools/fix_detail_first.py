# -*- coding: utf-8 -*-
"""
「解析看详情」改成 detail 优先，不再一上来就整页解析。

【为什么】
  列表里点「解析看详情」→ parseProfileItem → loadForParse(iesdouyin 分享页)
  → WebView 打开整页 → 抖音风控页（probe=20|1|1）
  → 【解析失败 · 抖音风控】

  但同一个 aweme_id，用 detail 接口（带 uifid）是能 200 的：
      BATCH_DETAIL_REQ ok=true http=200 len=23525
  整页解析反而更容易被风控，因为它要加载完整页面 + 各种埋点请求。

【改法】
  parseProfileItem 里，抖音条目先走 detail：
    · detail 拿到真视频直链 → 直接入队，一次页面都不开
    · detail 判定是音乐作品 → 明确提示「这是音乐作品，没有视频」，不浪费风控
    · detail 失败 → 才退回原来的 loadForParse

  同时保留 pendingDetailTag 这条老链路的行为（详情失败时兜底）。
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


rep('1-详情走detail',
    '''        s("解析第 " + (idx + 1) + " 条...");
        loadForParse(target);
    }''',
    '''        // 【抖音单条详情：detail 优先，别一上来就整页解析】
        //
        // 整页解析要 WebView 加载完整页面（含埋点、播放器、推荐流），
        // 是风控命中率最高的路径；而 detail 只发一个 XHR，
        // 带上 uifid 之后实测 200。
        String did = (it.id != null) ? it.id.trim() : "";
        boolean isDy = (target.indexOf("douyin.com") >= 0
                || target.indexOf("iesdouyin.com") >= 0)
                && did.matches("\\\\d{15,20}");
        if(isDy){
            s("解析第 " + (idx + 1) + " 条（detail）...");
            DypLog.i("LIST_DETAIL_FIRST idx=" + idx + " id=" + did);
            final int fix = idx;
            final String fdid = did;
            final String ftarget = target;
            new Thread(new Runnable(){ public void run(){
                String ck = "";
                String ui = "";
                try{ ck = dyEffectiveCookie(); }catch(Throwable t){}
                try{ ui = dyCurUifid(); }catch(Throwable t){}
                if(ck == null) ck = "";
                if(ui == null) ui = "";
                DyDetail.Result r = null;
                try{ r = DyDetail.fetch(fdid, ck, 2, ui); }catch(Throwable t){
                    DypLog.i("LIST_DETAIL_ERR " + t);
                }
                final DyDetail.Result fr = r;
                h.post(new Runnable(){ public void run(){
                    String vurl = "";
                    boolean music = false;
                    if(fr != null && fr.ok && fr.body != null && fr.body.length() > 0){
                        try{
                            Object root = new JP(fr.body).parse();
                            if(root instanceof Map){
                                Map m = (Map) root;
                                Object ad = get(m, "aweme_detail");
                                if(ad instanceof Map){
                                    Map adM = (Map) ad;
                                    long at = parseLong(get(adM, "aweme_type"), 0);
                                    long mt = parseLong(get(adM, "media_type"), 0);
                                    Object vObj = get(adM, "video");
                                    if(vObj instanceof Map){
                                        Map vm = (Map) vObj;
                                        Object brs = get(vm, "bit_rate");
                                        String best = "";
                                        if(brs instanceof List){
                                            Map bm2 = null; long ba = -1;
                                            for(Object bo : (List) brs){
                                                if(!(bo instanceof Map)) continue;
                                                Map bmm = (Map) bo;
                                                Object bpa = get(bmm, "play_addr");
                                                if(!(bpa instanceof Map)) continue;
                                                long w = parseLong(get((Map) bpa, "width"), 0);
                                                long h2 = parseLong(get((Map) bpa, "height"), 0);
                                                if(w <= 0 || h2 <= 0) continue;
                                                if(w * h2 > ba){ ba = w * h2; bm2 = bmm; }
                                            }
                                            if(bm2 != null){
                                                Object bpa = get(bm2, "play_addr");
                                                if(bpa instanceof Map) best = firstUrl(get((Map) bpa, "url_list"));
                                            }
                                        }
                                        if(best.length() == 0){
                                            Object pa = get(vm, "play_addr");
                                            if(pa instanceof Map) best = firstUrl(get((Map) pa, "url_list"));
                                        }
                                        boolean noGears = !(brs instanceof List) || ((List) brs).isEmpty();
                                        if(at == 163 || mt == 43 || (noGears && isLikelyAudioUrl(best))){
                                            music = true;
                                            DypLog.i("LIST_DETAIL_MUSIC id=" + fdid
                                                    + " aweme_type=" + at + " media_type=" + mt);
                                        }else if(best.length() > 0 && !isLikelyAudioUrl(best)){
                                            vurl = best;
                                        }
                                    }
                                }
                            }
                        }catch(Throwable t){
                            DypLog.i("LIST_DETAIL_PARSE_ERR " + t);
                        }
                    }
                    if(vurl.length() > 0){
                        DypLog.i("LIST_DETAIL_OK id=" + fdid + " url="
                                + vurl.substring(0, Math.min(80, vurl.length())));
                        enqueueDownload(vurl, "视频" + (fix + 1), "mp4", "视频", "", "抖音", fdid);
                        s("已加入下载队列：第 " + (fix + 1) + " 条");
                        showHintToast("已加入下载队列");
                        return;
                    }
                    if(music){
                        s("第 " + (fix + 1) + " 条是抖音音乐作品（只有音频，没有视频）");
                        showHintToast("音乐作品，无视频流");
                        return;
                    }
                    // detail 也没戏 → 退回整页解析（最后手段）
                    DypLog.i("LIST_DETAIL_NG id=" + fdid + " 退回整页解析");
                    s("解析第 " + (fix + 1) + " 条...");
                    loadForParse(ftarget);
                }});
            }}).start();
            return;
        }
        s("解析第 " + (idx + 1) + " 条...");
        loadForParse(target);
    }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)