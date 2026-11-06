# -*- coding: utf-8 -*-
"""
修正 platForItem 的兜底逻辑。

【我上一版的 bug】
    String p1 = platOf(it.url);
    if(p1.length() > 0) return p1;      // ← 永远成立！

    因为 platOf 认不出时返回的是 "其他"，不是空串。
    所以第一级就永远命中 "其他"，第二、三级（直链/封面）完全失效。

【这一版】
    把 "其他" 也当成「没认出来」，继续往下兜底。
    platOf 已覆盖 20+ 平台：
        抖音 快手 TikTok X YouTube B站 Instagram 微博 腾讯视频
        微信视频号 小红书 今日头条 西瓜视频 AcFun 好看视频 皮皮虾
        最右 微视 豆包 磁力
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

old = '''    String platForItem(ProfileParser.ProfileItem it){
        if(it == null) return "";
        String p1 = platOf(it.url == null ? "" : it.url);
        if(p1.length() > 0) return p1;
        String p2 = platOf(it.directUrl == null ? "" : it.directUrl);
        if(p2.length() > 0) return p2;
        if(it.cover != null && it.cover.length() > 0){
            String p3 = platOf(it.cover);
            if(p3.length() > 0) return p3;
        }
        return "其他";
    }'''

new = '''    String platForItem(ProfileParser.ProfileItem it){
        // 注意：platOf 认不出平台时返回的是 "其他"，不是空串。
        // 所以必须把 "其他" 也当成「没认出来」，继续往下兜底，
        // 否则第一级就永远命中 "其他"，后两级（直链 / 封面）形同虚设。
        if(it == null) return "其他";
        String p1 = platOf(it.url == null ? "" : it.url);
        if(!isOtherPlat(p1)) return p1;
        String p2 = platOf(it.directUrl == null ? "" : it.directUrl);
        if(!isOtherPlat(p2)) return p2;
        if(it.cover != null && it.cover.length() > 0){
            String p3 = platOf(it.cover);
            if(!isOtherPlat(p3)) return p3;
        }
        return "其他";
    }

    /** platOf 的「没认出来」判定：空串和 "其他" 都算没认出 */
    static boolean isOtherPlat(String p){
        return p == null || p.length() == 0 || "其他".equals(p);
    }'''

if old not in s:
    print('FAIL 找不到 platForItem 原文')
    raise SystemExit(1)

s = s.replace(old, new, 1)
io.open(P, 'w', encoding='utf-8').write(s)
print('OK platForItem 兜底逻辑已修正')