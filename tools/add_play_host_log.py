# -*- coding: utf-8 -*-
"""
加一条日志：把播放地址的域名打出来。

现在只知道「解析成功、有封面、播放器报 SSLHandshakeException」，
但不知道播放走的是哪个 CDN 域名。
TikTok 的播放域名有好几种：
  v16-webapp.tiktok.com / v16-webapp-prime.tiktok.us
  v19.tiktokcdn.com / v26-web.douyinvod.com
  *.tiktokcdn-us.com / *.tiktokcdn.com
不同域名在不同机场的分流规则里命中结果不一样，
所以必须先把真实域名打出来，才能判断是「没走代理」还是「代理节点拒绝」。
"""
import io

p = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

OLD = '''DypLog.i("RENDER_VIDEO_BRANCH curPlayLen=" + (curPlay == null ? -1 : curPlay.length())'''

NEW = '''DypLog.i("RENDER_VIDEO_BRANCH curPlayLen=" + (curPlay == null ? -1 : curPlay.length())
                        + " host=" + (curPlay == null ? "null" : hostOf(curPlay))'''

if OLD in s and 'hostOf(curPlay)' not in s:
    s = s.replace(OLD, NEW, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('RENDER_VIDEO_BRANCH 已加 host')
else:
    print('未命中或已加过')

# 加 hostOf 工具方法
ANCHOR = '    /** 强制把 host 解析成 IPv4（绕开设备 IPv6-only 导致的 UnknownHost） */'
HELPER = '''    /** 取 URL 的 host，用于日志定位（不做异常抛出） */
    static String hostOf(String url){
        try{
            if(url == null) return "null";
            if(url.startsWith("/")) return "local";
            return new java.net.URL(url).getHost();
        }catch(Throwable t){ return "bad:" + url; }
    }

'''
if 'static String hostOf(String url)' not in s:
    s = io.open(p, encoding='utf-8').read()
    if ANCHOR in s:
        s = s.replace(ANCHOR, HELPER + ANCHOR, 1)
        io.open(p, 'w', encoding='utf-8').write(s)
        print('hostOf 已加')
    else:
        print('hostOf 锚点未找到')
else:
    print('hostOf 已存在')