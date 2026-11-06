# -*- coding: utf-8 -*-
# 方案 B-1：count 从 18 提到 50；若第一页仍然 <8 条，说明是服务端硬限，
# 再试一次 publish_video_strategy_type=1 的变体看能不能多给。
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) count 18 -> 50
old = '''        final int MAX_PAGE = 40;
        final int COUNT = 18;'''
new = '''        final int MAX_PAGE = 40;
        // 方案 B：把单页条数拉大。抖音匿名限流时 count 再大也只给前几条，
        // 但登录态下 count 越大一次拿得越多，翻页次数也少，被风控概率更低。
        final int COUNT = 50;'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('count 50 OK')
else:
    print('MISS count')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)

# 2) 看看 buildPostQuery 里 publish_video_strategy_type 的位置
d2 = io.open(P, encoding='utf-8').read()
i = d2.find('publish_video_strategy_type')
print('publish_video_strategy_type 出现:', d2[:i].count('\n') + 1 if i > 0 else 'MISS')