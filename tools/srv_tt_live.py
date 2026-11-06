#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
服务器端 tt_module.py 补 live_images：
  tikwm 的 live_images 是实况图动图轨，与 images 一一对应。
  之前只输出 images，实况图退化成静态图集。
  新增输出 liveImages 数组，App 端据此做实况图下载。
"""
import io, sys

P = '/opt/dyparse/tt_module.py'
d = io.open(P, encoding='utf-8').read()

# 1) 读 live_images
old1 = '''    imgs = d.get("images") or []'''
new1 = '''    imgs = d.get("images") or []
    live_imgs = d.get("live_images") or []'''
if old1 in d:
    d = d.replace(old1, new1, 1)
    print('读取 live_images OK')
else:
    print('MISS 读取')

# 2) 图集分支里输出 liveImages
old2 = '''    if is_img:
        out["images"] = [{"url": _abs(u), "width": 0, "height": 0} for u in imgs if u]
        out["imageTitle"] = d.get("title") or ""'''
new2 = '''    if is_img:
        out["images"] = [{"url": _abs(u), "width": 0, "height": 0} for u in imgs if u]
        # 实况图：live_images 与 images 同下标对应
        if live_imgs:
            out["liveImages"] = [_abs(u) if u else "" for u in live_imgs]
            out["isLive"] = True
            print("[tt] live_images n=%d" % len(live_imgs), file=sys.stderr)
        out["imageTitle"] = d.get("title") or ""'''
if old2 in d:
    d = d.replace(old2, new2, 1)
    print('输出 liveImages OK')
else:
    print('MISS 输出')

io.open(P, 'w', encoding='utf-8').write(d)
print('written', len(d), 'bytes')