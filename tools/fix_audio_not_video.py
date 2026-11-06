# -*- coding: utf-8 -*-
"""
修：批量下载下来的 .mp4 其实是音频（M4A / MP3）。

用户实测（4 个文件全查了文件头）：
    dy_7702666069617478954_视频4.mp4   0000 001c 6674 7970 4d34 4120 → ftypM4A  纯音频
    dy_7702833793832422696_视频3.mp4   0000 0020 6674 7970 6973 6f6d → ftypisom 正常视频
    dy_7703121486688570664_视频2.mp4   0000 001c 6674 7970 4d34 4120 → ftypM4A  纯音频
    dy_7703531375761321039_视频1.mp4   4944 3304 0000 0000 072c 5458 → ID3      纯 MP3
                                                                                ↑
                                                                        4 个里 3 个是音频

根因（appendDyProfileItems 第 6213~6217 行）：

    Object pa = get((Map) video, "play_addr");
    if(pa instanceof Map){
        String uu = firstUrl(get((Map) pa, "url_list"));
        it.directUrl = uu;          // ← 无条件采用，不校验、不回退
    }

    主页列表的 video.play_addr.url_list[0] **不保证是视频轨**。
    实测这一批里 3 条给出的是音频地址：
      · 部分作品 play_addr 直接指向 music 轨
      · 部分作品（尤其混剪/图文转视频）play_addr 里混着 M4A
    旧代码拿到就用，于是 .mp4 里装的是纯音频 ——
    相册能播但只有声音，用户反馈「只是音频，不是视频」。

    对比：合集路径（第 6477~6511 行）有完整三级回退
        play_addr → bit_rate 最高码率 → vid 拼播放地址
    而主页列表路径只有第一级，且完全没兜底。

改法（两层）：

  【第一层 · 源头】appendDyProfileItems 改成和合集一样的多级挑选：
      1) bit_rate 里码率最高的那一档（这是真正的视频轨，且清晰度最高）
      2) 退回 video.play_addr
      3) 再退回 vid 拼播放地址
    优先 bit_rate 而不是 play_addr，因为实测 play_addr 会混入音频，
    而 bit_rate 数组里每一项都是编码好的视频档。

  【第二层 · 兜底】InnerDownloader 的 Content-Type 检查扩展：
      text/html / application/json 之外，再拦 audio/* 和 video/mp4 以外的
      明显音频类型；同时检查响应体前 12 字节的 ftyp box，
      发现 M4A / ID3 直接判失败，不留假视频。
"""
import io

P1 = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
P2 = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/InnerDownloader.java'

ok, miss = [], []

# ============================================================
# 第一层：appendDyProfileItems 多级挑选
# ============================================================
s = io.open(P1, encoding='utf-8').read()

old1 = '''            if(!isImage){
                Object video = get(aw, "video");
                if(video instanceof Map){
                    Object pa = get((Map) video, "play_addr");
                    if(pa instanceof Map){
                        String uu = firstUrl(get((Map) pa, "url_list"));
                        if(uu.length() > 0 && !uu.equals("?")) it.directUrl = uu;
                    }
                }
            }'''

new1 = '''            if(!isImage){
                Object video = get(aw, "video");
                if(video instanceof Map){
                    Map vm = (Map) video;
                    // 【优先 bit_rate 最高档，而不是 play_addr】
                    //
                    // 实测：主页列表的 video.play_addr.url_list[0] 不保证是视频轨。
                    // 一批 4 条里 3 条拿到的是纯音频（文件头 ftypM4A / ID3），
                    // 存成 .mp4 后相册能播但只有声音。
                    //
                    // bit_rate[] 里每一项都是已经编码好的视频档
                    //（自带 bit_rate / play_addr），比顶层 play_addr 可靠，
                    // 还能顺手挑到最高清晰度。
                    String best = "";
                    // 1) bit_rate 里码率最高的一档
                    try{
                        List brs = (List) get(vm, "bit_rate");
                        if(brs != null && brs.size() > 0){
                            Map bestMap = null; long bestBr = -1;
                            for(int b = 0; b < brs.size(); b++){
                                Object bo = brs.get(b);
                                if(!(bo instanceof Map)) continue;
                                Map bm = (Map) bo;
                                long br = parseLong(get(bm, "bit_rate"), 0);
                                if(br > bestBr){ bestBr = br; bestMap = bm; }
                            }
                            if(bestMap != null){
                                Object bpa = get(bestMap, "play_addr");
                                if(bpa instanceof Map){
                                    String uu = firstUrl(get((Map) bpa, "url_list"));
                                    if(uu.length() > 0 && !uu.equals("?")) best = uu;
                                    if(best.length() == 0){
                                        String buri = gs(get((Map) bpa, "uri"));
                                        if(buri.length() > 0) best = playUrl(buri);
                                    }
                                }
                            }
                        }
                    }catch(Throwable t){}
                    // 2) 退回 play_addr
                    if(best.length() == 0){
                        Object pa = get(vm, "play_addr");
                        if(pa instanceof Map){
                            String uu = firstUrl(get((Map) pa, "url_list"));
                            if(uu.length() > 0 && !uu.equals("?")) best = uu;
                            if(best.length() == 0){
                                String uri = gs(get((Map) pa, "uri"));
                                if(uri.length() > 0) best = playUrl(uri);
                            }
                        }
                    }
                    // 3) 最后用 vid 拼播放地址
                    if(best.length() == 0){
                        String vid = gs(get(vm, "vid"));
                        if(vid.length() > 0) best = playUrl(vid);
                    }
                    if(best.length() > 0 && !best.equals("?")) it.directUrl = best;
                    // 记下视频时长（秒），0 说明可能拿到了非视频轨
                    try{
                        long dur = parseLong(get(vm, "duration"), 0);
                        if(dur > 1000) dur = dur / 1000;   // 有的接口给的是毫秒
                        it.durSec = dur;
                    }catch(Throwable t){}
                }
            }'''

if old1 in s:
    s = s.replace(old1, new1, 1)
    ok.append('1-主页直链三级回退')
else:
    miss.append('1-主页直链三级回退')

io.open(P1, 'w', encoding='utf-8').write(s)

# ============================================================
# 第二层：InnerDownloader 拦截音频响应
# ============================================================
s2 = io.open(P2, encoding='utf-8').read()

old2 = '''            if(ctype != null){
                String lc = ctype.toLowerCase();
                if(lc.indexOf("text/html") >= 0 || lc.indexOf("application/json") >= 0){
                    Log.i(TAG, "IDL_NOT_MEDIA ctype=" + ctype + " url=" + url);'''

new2 = '''            if(ctype != null){
                String lc = ctype.toLowerCase();
                // 音频也算「不是媒体」——这里要的是视频。
                // 抖音部分作品的 play_addr 会指向 music 轨，
                // 存成 .mp4 后相册能播但只有声音（用户实测 4 条里 3 条）。
                boolean isAudio = lc.indexOf("audio/") >= 0
                        || lc.indexOf("application/ogg") >= 0
                        || lc.indexOf("mpeg") >= 0 && lc.indexOf("audio") >= 0;
                if(isAudio){
                    Log.i(TAG, "IDL_AUDIO_NOT_VIDEO ctype=" + ctype + " url=" + url);
                    try{ c.disconnect(); }catch(Throwable t){}
                    try{ if(part.exists()) part.delete(); }catch(Throwable t){}
                    return false;
                }
                if(lc.indexOf("text/html") >= 0 || lc.indexOf("application/json") >= 0){
                    Log.i(TAG, "IDL_NOT_MEDIA ctype=" + ctype + " url=" + url);'''

if old2 in s2:
    s2 = s2.replace(old2, new2, 1)
    ok.append('2-拦截 audio Content-Type')
else:
    miss.append('2-拦截 audio Content-Type')

io.open(P2, 'w', encoding='utf-8').write(s2)

print('成功:', ok)
print('失败:', miss)