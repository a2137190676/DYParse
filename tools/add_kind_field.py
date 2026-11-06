# -*- coding: utf-8 -*-
"""
在「解析阶段」就把每条作品的类型定死，下载阶段不再猜。

【用户提的思路，正确】
  不应该下载时才去试「是视频还是图集」，
  应该在主页解析拿到 aweme 的那一刻就判定好。

【类型定义】
  KIND_VIDEO = 1  普通视频：directUrl 是真视频轨
  KIND_IMAGE = 2  图集：imgs 非空、无实况动轨
  KIND_LIVE  = 3  实况图：imgs 非空，且至少一张有动轨
  KIND_MUSIC = 4  音乐作品：aweme_type=163 / media_type=43，只有音频
  KIND_UNKNOWN = 0 其他/待定

【判定依据（全部来自主页 aweme 原始字段，不用再请求）】
  aweme.images[] 存在且非空              → 图集族
      images[i].video.play_addr 有值     → 该张是实况 → KIND_LIVE
  aweme.aweme_type==163 或 media_type==43 → KIND_MUSIC
  aweme.video 有效 + bit_rate/play_addr 是真视频轨 → KIND_VIDEO

【好处】
  · 批量下载按 kind 直接分派，不再「先当视频试一遍」
  · KIND_MUSIC 在解析阶段就标出来，列表里能直接看到，不会白白触发风控
  · 图集 / 实况在解析时就知道张数，UI 能显示「图集 5 张 · 含 2 张实况」
"""
import io

# ---------- 1. ProfileItem 加字段 ----------
PP = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/ProfileParser.java'
s = io.open(PP, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


rep('P1-类型常量+字段',
    '''    public static class ProfileItem {
        public String id = "";''',
    '''    /* ===== 作品类型：解析阶段就定死，下载阶段直接按它分派 ===== */
    public static final int KIND_UNKNOWN = 0;
    public static final int KIND_VIDEO   = 1;   // 普通视频
    public static final int KIND_IMAGE   = 2;   // 图集（纯图片）
    public static final int KIND_LIVE    = 3;   // 实况图（图片 + 动轨）
    public static final int KIND_MUSIC   = 4;   // 音乐作品（只有音频，无视频）

    public static class ProfileItem {
        public String id = "";''')

rep('P2-kind字段',
    '''        /** 与 imgs 一一对应的实况视频地址（空串表示该张不是实况） */
        public java.util.List<String> dyns = new java.util.ArrayList<String>();
    }''',
    '''        /** 与 imgs 一一对应的实况视频地址（空串表示该张不是实况） */
        public java.util.List<String> dyns = new java.util.ArrayList<String>();
        /**
         * 作品类型（解析阶段定死）：KIND_VIDEO / KIND_IMAGE / KIND_LIVE / KIND_MUSIC。
         * 下载阶段直接按它分派，不再「先当视频试一遍」。
         */
        public int kind = KIND_UNKNOWN;

        /** 类型中文名（列表 UI 显示用） */
        public String kindLabel(){
            switch(kind){
                case KIND_VIDEO: return "视频";
                case KIND_IMAGE: return "图集 " + imgs.size() + " 张";
                case KIND_LIVE: {
                    int n = 0;
                    for(String d : dyns){ if(d != null && d.length() > 0) n++; }
                    return "实况 " + imgs.size() + " 张(动" + n + ")";
                }
                case KIND_MUSIC: return "音乐(无视频)";
                default: return "";
            }
        }
    }''')

io.open(PP, 'w', encoding='utf-8').write(s)
print('ProfileParser 成功:', ok)
print('ProfileParser 失败:', miss)


# ---------- 2. 主页解析时判定类型 ----------
P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok2, miss2 = [], []


def rep2(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok2.append(name)
    else:
        miss2.append(name)


# upgrade_parse_dy_profile 里那段图集收集的末尾，补 kind 判定
rep2('M1-解析时判类型',
     '''                    DypLog.i("PROF_WEB_IMGS id=" + awemeId + " n=" + imgUrls.size()
                            + " live=" + liveN);
                }
            }''',
     '''                    DypLog.i("PROF_WEB_IMGS id=" + awemeId + " n=" + imgUrls.size()
                            + " live=" + liveN);
                }
            }
            // ============ 解析阶段就把类型定死 ============
            // 下载阶段不再靠猜：直接看 it.kind 分派。
            try{
                if(isImage){
                    it.kind = hasLiveDyn(it) ? ProfileParser.ProfileItem.KIND_LIVE
                                             : ProfileParser.ProfileItem.KIND_IMAGE;
                }else{
                    long at = parseLong(get(aw, "aweme_type"), 0);
                    long mt = parseLong(get(aw, "media_type"), 0);
                    if(at == 163 || mt == 43){
                        it.kind = ProfileParser.ProfileItem.KIND_MUSIC;
                    }else if(it.directUrl != null && it.directUrl.length() > 0){
                        it.kind = ProfileParser.ProfileItem.KIND_VIDEO;
                    }
                }
            }catch(Throwable kt){}
            DypLog.i("PROF_KIND id=" + awemeId + " kind=" + it.kind
                    + "(" + it.kindLabel() + ")");''')

# 辅助方法
rep2('M2-hasLiveDyn',
     '''    int appendDyProfileItems(List list){''',
     '''    /** 该条图集是否含实况动轨 */
    static boolean hasLiveDyn(ProfileParser.ProfileItem it){
        if(it == null || it.dyns == null) return false;
        for(String d : it.dyns){
            if(d != null && d.length() > 0) return true;
        }
        return false;
    }

    int appendDyProfileItems(List list){''')

io.open(P, 'w', encoding='utf-8').write(s)
print('MainActivity 成功:', ok2)
print('MainActivity 失败:', miss2)