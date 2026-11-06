# -*- coding: utf-8 -*-
"""
修：批量下载的 detail 通道被风控
    BATCH_DETAIL_FAIL err=BLOCKED:Blocked by ArgusSecurityPlugin Uifid Not Found

根因：
    我写的 fetchDetailForBatch() 是自己临时拼的简易版：
      · 走 WebView 里 fetch('/aweme/v1/web/aweme/detail/?...')
      · 只带了一堆 device/browser 参数
      · **没有 uifid，没有 msToken，没有 x-secsdk-web-signature**
    抖音对这个接口校验很严，缺 uifid 直接 403：
      Blocked by ArgusSecurityPlugin Uifid Not Found
    （DyDetail.java 第 19 行注释里早就写了这一条）

    项目里本来就有正确的实现：DyDetail.fetch(awemeId, cookie, maxTries)
      · 补随机 msToken
      · 带 uifid（从 cookie 里取）
      · 算 x-secsdk-web-signature
      · 自带重试

改法：
    fetchDetailForBatch 直接调 DyDetail.fetch()，
    在后台线程跑，拿到 body 后解析 aweme_detail.video.bit_rate，
    挑分辨率最高的那档 play_addr，交给原来的入队逻辑。
    完全不用 WebView，也就不受页面风控影响。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# 找到整段 fetchDetailForBatch 并替换
start = s.find('    /** 批量用：按作品 id 请求 detail，拿到真视频地址后入队 */')
if start < 0:
    print('FAIL 找不到 fetchDetailForBatch')
    raise SystemExit(1)

end_marker = '    /** JS 回传的批量 detail 结果 */'
end = s.find(end_marker, start)
if end < 0:
    print('FAIL 找不到结尾')
    raise SystemExit(1)

new_fn = '''    /** 批量用：按作品 id 请求 detail，拿到真视频地址后入队。
     *
     *  走 DyDetail.fetch() —— 原生 HTTP，带 msToken + uifid + secsdk 签名。
     *
     *  为什么不用 WebView 里 fetch：
     *    之前那版只带 device/browser 参数，没 uifid 没签名，
     *    抖音直接回 403：
     *        Blocked by ArgusSecurityPlugin Uifid Not Found
     *    （DyDetail 第 19 行注释已写明这个坑）
     *    而且走 WebView 还受页面风控状态影响，更不稳。
     *
     *  原生请求 + 完整签名是目前最稳的一条。 */
    void fetchDetailForBatch(final String awemeId){
        new Thread(new Runnable(){ public void run(){
            try{
                // cookie：优先本地保存的完整登录态
                String ck = "";
                try{ ck = CookieStore.header(MainActivity.this, CookieStore.PLATFORM_DY); }
                catch(Throwable t){}
                if(ck == null) ck = "";
                DyDetail.Result r = DyDetail.fetch(awemeId, ck, 2);
                Log.i("DYP", "BATCH_DETAIL_REQ id=" + awemeId + " " + r);
                if(r == null || !r.ok || r.body == null || r.body.length() == 0){
                    Log.i("DYP", "BATCH_DETAIL_FAIL id=" + awemeId
                            + " err=" + (r == null ? "null" : r.err));
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                Object root = new JP(r.body).parse();
                if(!(root instanceof Map)){
                    Log.i("DYP", "BATCH_DETAIL_NOT_MAP");
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                Object ad = get((Map) root, "aweme_detail");
                if(!(ad instanceof Map)){
                    Log.i("DYP", "BATCH_DETAIL_NO_AWEME");
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                Map adM = (Map) ad;
                Object vObj = get(adM, "video");
                if(!(vObj instanceof Map)){
                    Log.i("DYP", "BATCH_DETAIL_NO_VIDEO id=" + awemeId);
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                Map vm = (Map) vObj;
                String best = "";
                // 1) bit_rate 里分辨率最高的那一档（真视频流）
                try{
                    List brs = (List) get(vm, "bit_rate");
                    if(brs != null){
                        Map bestMap = null; long bestArea = -1;
                        for(int b = 0; b < brs.size(); b++){
                            Object bo = brs.get(b);
                            if(!(bo instanceof Map)) continue;
                            Map bm = (Map) bo;
                            Object bpa = get(bm, "play_addr");
                            if(!(bpa instanceof Map)) continue;
                            long w = parseLong(get((Map) bpa, "width"), 0);
                            long h2 = parseLong(get((Map) bpa, "height"), 0);
                            if(w <= 0 || h2 <= 0) continue;
                            long area = w * h2;
                            if(area > bestArea){ bestArea = area; bestMap = bm; }
                        }
                        if(bestMap != null){
                            Object bpa = get(bestMap, "play_addr");
                            if(bpa instanceof Map){
                                String uu = firstUrl(get((Map) bpa, "url_list"));
                                if(uu.length() > 0 && !uu.equals("?")) best = uu;
                            }
                        }
                        Log.i("DYP", "BATCH_DETAIL_GEARS n=" + brs.size()
                                + " best=" + (best.length() > 0 ? "yes" : "no"));
                    }
                }catch(Throwable t){}
                // 2) 退回顶层 play_addr
                if(best.length() == 0){
                    Object pa = get(vm, "play_addr");
                    if(pa instanceof Map){
                        String uu = firstUrl(get((Map) pa, "url_list"));
                        if(uu.length() > 0 && !uu.equals("?")) best = uu;
                    }
                }
                // 3) 退回 download_addr
                if(best.length() == 0){
                    Object da = get(vm, "download_addr");
                    if(da instanceof Map){
                        String uu = firstUrl(get((Map) da, "url_list"));
                        if(uu.length() > 0 && !uu.equals("?")) best = uu;
                    }
                }
                if(best.length() == 0){
                    Log.i("DYP", "BATCH_DETAIL_NO_URL id=" + awemeId);
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                if(isLikelyAudioUrl(best)){
                    Log.i("DYP", "BATCH_DETAIL_AUDIO id=" + awemeId + " url="
                            + best.substring(0, Math.min(80, best.length())));
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                Log.i("DYP", "BATCH_DETAIL_OK id=" + awemeId + " url="
                        + best.substring(0, Math.min(100, best.length())));
                batchDetailWant = false;
                final String vv = best;
                final String sid = awemeId;
                h.post(new Runnable(){ public void run(){
                    String tag = "视频" + (batchDlIndex + 1);
                    Log.i("DYP", "BATCH_DL_ENQ_DETAIL tag=" + tag + " plat=抖音");
                    enqueueDownload(vv, tag, "mp4", "视频", "", "抖音", sid);
                    batchDlOk++;
                    advanceBatchAfterDetail(true);
                }});
            }catch(Throwable t){
                Log.i("DYP", "BATCH_DETAIL_ERR " + t);
                batchDetailWant = false;
                h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
            }
        }}).start();
    }

'''

s = s[:start] + new_fn + s[end:]
io.open(P, 'w', encoding='utf-8').write(s)
print('OK fetchDetailForBatch 已改为 DyDetail.fetch')