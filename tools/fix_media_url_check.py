# -*- coding: utf-8 -*-
"""
修「批量下载下来的是 HTML 页面（32KB，相册显示 00:00）」。

证据：
    dy_7703121486688570664_视频1.mp4  32,517 字节
    head → <!doctype html><html>...argus-csp-token...
    这是抖音反爬验证页 HTML，不是视频。

原因：
    批量下载把「作品页 URL」当成「视频直链」写进了下载队列。
    下载器老老实实 GET 这个页面，服务器回 200 + HTML，
    于是文件写着 .mp4 后缀，内容是网页 —— 相册当然显示 00:00。

要修两件事：
  【1】入队前校验：直链必须是媒体 CDN 域（douyinpic/amemv/video 等），
       页面 URL（www.douyin.com/video/123、iesdouyin、v.douyin.com 短链）
       一律拒绝，打日志说明，不要写队列。
  【2】下载器兜底：响应头 Content-Type 是 text/html 时直接判失败并删文件，
       避免又往相册里塞一堆假视频。
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


# ========== 1) 新增直链校验工具方法 ==========
rep('1-加直链校验',
    '''    /** 按链接判定文件名前缀（跟下载平台目录保持一致） */''',
    '''    /**
     * 判断一个地址是不是「真的能下到媒体文件的直链」。
     *
     * 批量下载曾经把作品页 URL（https://www.douyin.com/video/123）当成直链入队，
     * 下载器 GET 回来的是抖音反爬验证页 HTML（~32KB），
     * 存成 .mp4 后相册显示 00:00 —— 用户看到的就是「批量下载又不行了」。
     *
     * 这里做入队前拦截：只放行媒体 CDN 域名，页面域名一律拒绝。
     * 返回空串表示可用，返回非空表示拒绝原因。
     */
    static String rejectNonMediaUrl(String u){
        if(u == null || u.length() == 0) return "空地址";
        String s = u.toLowerCase();
        if(!(s.startsWith("http://") || s.startsWith("https://"))) return "非 http 地址";
        // 明确是「网页」的域名/路径：一律拒绝
        String[] pageHosts = {
            "www.douyin.com", "douyin.com", "v.douyin.com", "iesdouyin.com",
            "www.iesdouyin.com", "m.douyin.com",
            "www.tiktok.com", "vm.tiktok.com", "vt.tiktok.com",
            "www.kuaishou.com", "v.kuaishou.com", "live.kuaishou.com",
            "x.com", "twitter.com", "www.youtube.com", "youtu.be",
            "www.bilibili.com", "b23.tv", "space.bilibili.com",
            "www.instagram.com", "www.xiaohongshu.com", "xhslink.com",
            "weixin.qq.com", "channels.weixin.qq.com"
        };
        String host = "";
        try{
            java.net.URL uu = new java.net.URL(u);
            host = uu.getHost() == null ? "" : uu.getHost().toLowerCase();
        }catch(Throwable t){
            return "URL 解析失败";
        }
        for(String h : pageHosts){
            if(host.equals(h)) return "这是作品页地址不是直链(" + host + ")";
        }
        // 路径里出现 /video/<数字> 这类作品路径，也说明是页面
        if(s.matches(".*/(video|note|slides|photo)/\\\\d{6,25}.*")) return "作品页路径";
        return "";
    }
    /** 按链接判定文件名前缀（跟下载平台目录保持一致） */''')

# ========== 2) pendingAutoDl 入队前校验 ==========
rep('2-批量入队校验',
    '''                if(pendingAutoDlTag.length() > 0){
                    if(!fail && curPlay != null && curPlay.length() > 0){
                        final String dl = curPlay;
final String tag = pendingAutoDlTag;
                        pendingAutoDlTag = "";
                        pendingAutoDlIdx = -1;
                        enqueueDownload(dl, tag, "mp4", "视频");''',
    '''                if(pendingAutoDlTag.length() > 0){
                    if(!fail && curPlay != null && curPlay.length() > 0
                            && rejectNonMediaUrl(curPlay).length() == 0){
                        final String dl = curPlay;
final String tag = pendingAutoDlTag;
                        pendingAutoDlTag = "";
                        pendingAutoDlIdx = -1;
                        Log.i("DYP", "BATCH_DL_ENQ " + tag + " url=" + dl);
                        enqueueDownload(dl, tag, "mp4", "视频");''')

# ========== 3) 校验不通过时打日志并跳过 ==========
rep('3-校验失败处理',
    '''                        batchDlOk++;
                        // 直链有时效（约 5~10 分钟），不能等全部解析完再下。''',
    '''                        batchDlOk++;
                        // 直链有时效（约 5~10 分钟），不能等全部解析完再下。''')

rep('3b-拒绝日志',
    '''                    } else if(!fail){
                        // 解析成功但没拿到可用直链
                        pendingAutoDlTag = "";
                        pendingAutoDlIdx = -1;
                    }''',
    '''                    } else if(!fail){
                        // 解析成功但拿不到可用直链：把原因写清楚，别静默跳过
                        pendingAutoDlTag = "";
                        pendingAutoDlIdx = -1;
                    }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)