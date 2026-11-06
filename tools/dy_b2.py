# -*- coding: utf-8 -*-
# 方案 B-2：用极简参数（buildSimpleQuery），去掉那 40 多个 webapp 指纹。
# 代码注释明确说：那套指纹参数反而会被判定为「非真实浏览器」。
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''            query = DyDetail.buildPostQuery(secUid, cursor, COUNT, msToken, uifid);'''

new = '''            // 方案 B-2：先用极简参数试（去掉 40+ 指纹，更接近真实浏览器）
            // 若拿不到东西，再回退到完整指纹参数
            query = DyDetail.buildSimpleQuery(secUid, cursor, COUNT, msToken,
                    "", "", "reflow_pc_loading", "", "", "0", "6383");
            if(dyProfUseFullQuery){
                query = DyDetail.buildPostQuery(secUid, cursor, COUNT, msToken, uifid);
            }'''

if old in d:
    d = d.replace(old, new, 1)
    print('改用极简查询 OK')
else:
    print('MISS')
    i = d.find('buildPostQuery(secUid')
    print(repr(d[i-200:i+100]))