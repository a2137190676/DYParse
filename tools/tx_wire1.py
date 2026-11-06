# -*- coding: utf-8 -*-
# 接入腾讯视频：
#   1) isSupportedParseUrl 里认 v.qq.com
#   2) parse() 里在抖音之前先拦腾讯视频
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 平台识别
old = '''        // 微博（含 video.weibo.com / m.weibo.cn / t.cn / sinaimg）
        if(WeiboParser.isWeiboUrl(u)) return true;
        return false;'''
new = '''        // 微博（含 video.weibo.com / m.weibo.cn / t.cn / sinaimg）
        if(WeiboParser.isWeiboUrl(u)) return true;
        // 腾讯视频（v.qq.com / m.v.qq.com）
        if(TxParser.isTxUrl(u)) return true;
        return false;'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('平台识别 OK')
else:
    print('MISS 平台识别')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)

# 2) 找 parse() 里磁力拦截位置，在其后加腾讯视频拦截
d2 = io.open(P, encoding='utf-8').read()
i = d2.find('BT_PARSE')
print('--- BT_PARSE 附近 ---')
print(d2[max(0, i-500):i+300])