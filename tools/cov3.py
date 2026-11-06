# -*- coding: utf-8 -*-
# 合集列表：mixItems 是 [id, desc, playUrl, cover]，但 pi.cover 没赋值
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''                    pi.url = it[2] != null ? it[2] : "";
                    pi.directUrl = it[2] != null ? it[2] : "";
                    itemList.add(pi);'''

new = '''                    pi.url = it[2] != null ? it[2] : "";
                    pi.directUrl = it[2] != null ? it[2] : "";
                    // 合集条目封面：第 4 个元素就是 cover
                    pi.cover = (it.length > 3 && it[3] != null) ? it[3] : "";
                    itemList.add(pi);'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('合集封面 OK')
else:
    print('MISS')