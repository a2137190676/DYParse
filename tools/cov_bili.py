# -*- coding: utf-8 -*-
# B 站分P/合集条目：用 bvid 拼封面 URL
# v.bvid 是当前合集的主 bvid，每集用它本身就能显示封面（避免服务端调用）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''                                String epAid = (p.aid != null && p.aid.length() > 0) ? p.aid : v.aid;
                                it.url = "bili://play/" + epAid + "/" + p.cid;
                                items.add(it);'''

new = '''                                String epAid = (p.aid != null && p.xAid != null) ? p.xAid
                                        : (p.aid != null && p.aid.length() > 0) ? p.aid : v.aid;
                                it.url = "bili://play/" + epAid + "/" + p.cid;
                                // B 站封面用 bvid 拼（避免去服务端拉 pic）
                                it.cover = (v.bvid != null && v.bvid.length() > 0)
                                        ? ("https://i0.hdslb.com/bfs/archive/" + v.bvid + ".jpg")
                                        : "";
                                items.add(it);'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('B 站分P封面 OK')
else:
    print('MISS BILI pages')