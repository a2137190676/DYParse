# -*- coding: utf-8 -*-
# refererFor 缺 douyinpic.com：抖音图床有防盗链，不带 Referer 会 403
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''        // 字节系
        if(u.contains("byteimg.com") || u.contains("toutiaoimg.com") || u.contains("toutiaovod.com")
                || u.contains("bdstatic.com") || u.contains("baidu.com") || u.contains("haokan")){
            return "https://haokan.baidu.com/";
        }'''

new = '''        // 抖音图床（douyinpic / douyinvod 有防盗链，必须带 douyin Referer）
        if(u.contains("douyinpic.com") || u.contains("douyinvod.com")
                || u.contains("douyin.com") || u.contains("iesdouyin.com")){
            return "https://www.douyin.com/";
        }
        // 字节系
        if(u.contains("byteimg.com") || u.contains("toutiaoimg.com") || u.contains("toutiaovod.com")
                || u.contains("bdstatic.com") || u.contains("baidu.com") || u.contains("haokan")){
            return "https://haokan.baidu.com/";
        }'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('douyinpic Referer OK')
else:
    print('MISS')

# 检查 loadNetThumb 用的是哪个取图函数
i = d.find('void loadNetThumb')
print('loadNetThumb 取图方式:')
seg = d[i:i+2500]
for kw in ['httpGetBytes', 'httpGetBytesWithReferer', 'refererFor']:
    print('  ', kw, ':', kw in seg)