# -*- coding: utf-8 -*-
"""
修：图集判断被直链分支抢先，图集仍然被当视频下载。

【证据】日志：
    BATCH_DL_START total=3 dropped=0
    BATCH_DL_DIRECT idx=0/3 tag=视频1 plat=抖音   ← 第 0 条是图文，走了视频直链
    没有 BATCH_DL_IMAGE

【根因】stepBatchProfileDl 里的顺序是：

    String direct = it.directUrl;              // ← 图集的 directUrl = 第一张图地址
    if(direct.length() > 0 && why.length() == 0){
        enqueueDownload(..., "mp4", "视频");    // ← 图片地址被当视频下，这里就 return 了
    }
    ...
    if(it.isImage){ ... }                       // ← 永远到不了

  而 collectBatchItem 里：
      if(hasImgs) it.directUrl = imgUrls.get(0);
  所以只要图集有图，directUrl 一定非空、一定被当成视频直链。

【改法】把 isImage 判断整体挪到 direct 分支之前。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# ---- 1. 先把图集分支整段剪出来 ----
start = s.index('        if(it.isImage){')
# 找到这段结束：后面紧跟的是「回落一：有作品 id」注释
end_marker = '        // ---- 回落一：有作品 id → 调 detail 接口取真视频 ----'
end = s.index(end_marker)
img_block = s[start:end].rstrip() + '\n\n'
s = s[:start] + s[end:]

# ---- 2. 插到 direct 分支之前 ----
anchor = '''        String direct = (it.directUrl != null) ? it.directUrl : "";'''
if anchor not in s:
    print('FAIL 找不到 direct 锚点')
    raise SystemExit(1)

note = '''        // ============================================================
        // 【图集必须最优先】isImage 判断要放在直链分支之前。
        //
        // 因为 collectBatchItem 里做了：
        //     if(hasImgs) it.directUrl = imgUrls.get(0);
        // 图集的 directUrl 就是第一张图的地址，非空、也不是音频，
        // 所以一定会命中下面那个「直链」分支，
        // 被当成 mp4 视频去下 —— 存出来是个打不开的假视频。
        //
        // 之前的顺序把 isImage 写在直链后面，永远进不来。
        // ============================================================
'''
s = s.replace(anchor, note + img_block + anchor, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('OK 图集分支已提到直链之前')