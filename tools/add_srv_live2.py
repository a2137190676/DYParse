# -*- coding: utf-8 -*-
"""
给服务器 tt_module 的 tikwm 分支加上 live_images（实况图动轨）。

【关键发现】
tikwm 的返回里有 `live_images` 字段，是 32 条 mp4 动轨直链，与 images 一一对应：
    images[0]      = ...photomode-image.jpeg   （静态图）
    live_images[0] = ...tos-alisg-ve-.../xxx   （mp4 动轨）

而我们之前只读了 images，完全没看 live_images —— 这就是
「昨天能看到 30 多张 + 实况动轨，现在只剩静态图」的真正原因。

App 端已有的读取逻辑（MainActivity 11724 行）：
    String liveJson = jobj(body, "liveImages");
    ...
    curDyn = db.toString(); curIsLive = true;
所以服务器只要输出 liveImages 这个 key，全链路就通了。
"""
import io

p = '/storage/emulated/0/MT2/mcp/DYParse/server/tt_module.py'
s = io.open(p, encoding='utf-8').read()

OLD = '''    if is_img:
        out["images"] = [{"url": _abs(u), "width": 0, "height": 0} for u in imgs if u]
        out["imageTitle"] = d.get("title") or ""
        out["video"] = {"cover": _abs(d.get("origin_cover") or d.get("cover") or "")}'''

NEW = '''    if is_img:
        out["images"] = [{"url": _abs(u), "width": 0, "height": 0} for u in imgs if u]
        out["imageTitle"] = d.get("title") or ""
        out["video"] = {"cover": _abs(d.get("origin_cover") or d.get("cover") or "")}
        # 实况图动轨：tikwm 的 live_images 是与 images 一一对应的 mp4 直链。
        # 空位用 "" 占住，保证 App 端下标不错位。
        lives = d.get("live_images") or []
        if lives:
            out["liveImages"] = [_abs(x) if x else "" for x in lives]'''

if OLD in s:
    s = s.replace(OLD, NEW, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('OK: tikwm 分支已输出 liveImages')
else:
    print('FAIL: 未命中 tikwm 分支')
    i = s.find('out["imageTitle"]')
    print(repr(s[max(0, i-300):i+200]))