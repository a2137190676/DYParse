# -*- coding: utf-8 -*-
# 磁力接入主链路：
#   1) isSupportedParseUrl 认 magnet:
#   2) loadForParse 开头拦 magnet -> 直接起 BT 下载（不走平台解析）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) isSupportedParseUrl 加磁力
old1 = '''    static boolean isSupportedParseUrl(String url){
        if(url == null || url.length() == 0) return false;
        String u = url.toLowerCase();'''
new1 = '''    static boolean isSupportedParseUrl(String url){
        if(url == null || url.length() == 0) return false;
        // 磁力 / BT 种子
        if(BtDownloader.isMagnet(url)) return true;
        String u = url.toLowerCase();'''
if old1 in d:
    d = d.replace(old1, new1, 1); n += 1; print('isSupportedParseUrl OK')
else:
    print('MISS isSupportedParseUrl')

# 2) loadForParse 开头拦磁力
m = d.find('void loadForParse(')
if m < 0:
    m = d.find('void loadForParse')
print('loadForParse at', m)
# 找方法体起始 {
b = d.index('{', m)
# 找到方法体第一个换行后的位置插入
insert_at = d.index('\n', b) + 1

snippet = '''        // 磁力 / BT 种子：直接交给 BtDownloader，不走平台解析
        if(BtDownloader.isMagnet(u)){
            startMagnetDownload(u);
            return;
        }
'''
# 检查 u 变量名对不对
head = d[m:b]
print('签名:', repr(head))
d = d[:insert_at] + snippet + d[insert_at:]
n += 1
print('磁力拦截 OK')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)