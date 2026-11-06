# -*- coding: utf-8 -*-
"""在 parse() 里对磁力/本地种子做前置分流：
   否则本地 .torrent 路径会被 isSupportedParseUrl 当成「不支持的网址」拦掉。
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

old = '''         if(url==null){ s("未找到链接"); return; }
         // ===== 平台前置校验 ====='''
new = '''         if(url==null){ s("未找到链接"); return; }
         // 磁力链接 / 本地种子文件：直接交给 BT 引擎，不走平台解析
         // （本地 .torrent 路径会被下面的 isSupportedParseUrl 当成「不支持的网址」拦掉）
         if(BtDownloader.isMagnet(url)
                 || (url.toLowerCase().endsWith(".torrent") && new java.io.File(url).exists())){
             Log.i("DYP", "PARSE_BT_DIRECT " + url.substring(0, Math.min(80, url.length())));
             startMagnetDownload(url);
             return;
         }
         // ===== 平台前置校验 ====='''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('parse 前置分流 OK')
else:
    print('MISS')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)