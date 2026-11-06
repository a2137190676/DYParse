# -*- coding: utf-8 -*-
"""服务器 tt_module 增加 liveImages 输出（实况图动轨）。"""
import io

p = '/storage/emulated/0/MT2/mcp/DYParse/server/tt_module.py'
s = io.open(p, encoding='utf-8').read()

OLD = '''        for im in (image_post.get("images") or []):
            u = _pick_url(im.get("imageURL") or im.get("image_url") or {})
            if u:
                imgs.append({"url": u,
                             "width": int(im.get("imageWidth") or 0),
                             "height": int(im.get("imageHeight") or 0)})
        out["images"] = imgs'''

NEW = '''        live_list = []
        for im in (image_post.get("images") or []):
            u = _pick_url(im.get("imageURL") or im.get("image_url") or {})
            if u:
                imgs.append({"url": u,
                             "width": int(im.get("imageWidth") or 0),
                             "height": int(im.get("imageHeight") or 0)})
            # 实况图动轨：images[i].video.playAddr / playAddr / dynamicUrl
            live_u = ""
            for k in ("video", "playAddr", "play_addr", "dynamicUrl", "dynamic_url"):
                cand = im.get(k)
                if isinstance(cand, dict):
                    cand = cand.get("playAddr") or cand.get("play_addr") or cand
                if isinstance(cand, dict):
                    lu = _pick_url(cand)
                elif isinstance(cand, str):
                    lu = cand
                else:
                    lu = ""
                if lu and lu.startswith("http"):
                    live_u = lu
                    break
            live_list.append(live_u)
        out["images"] = imgs
        if any(live_list):
            out["liveImages"] = live_list'''

if OLD in s:
    s = s.replace(OLD, NEW, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('OK: 服务器 liveImages 已加')
else:
    print('FAIL: 未命中')
    i = s.find('out["images"] = imgs')
    print(repr(s[max(0, i-400):i+30]))