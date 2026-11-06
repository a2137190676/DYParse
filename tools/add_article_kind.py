# -*- coding: utf-8 -*-
"""
新增第 5 种作品类型：抖音「文章」（KIND_ARTICLE）。

【用户实测反馈】
  主页前 5 条不是图文，是文章：
      亚空间的观测者（1）
      【AI爆发，感觉我们好像生活并没有变好】
  分享文案是「即可阅读文章」，点「解析看详情」解析不出来。

【实际数据结构（从 detail dump 确认）】
  aweme_detail.article_info = {
      article_id:          "...",
      article_title:       "亚空间的观测者（1）",
      article_type:        ...,
      article_content:     "{\\"long_article_abstract\\":\\"...\\",\\"markdown\\":\\"...\\"}"
                            ← 注意是 JSON 字符串，要二次解开
      read_time:           ...,
      has_more:            ...,
      container_lynx_url / detail_lynx_url ...
  }
  aweme_detail.aweme_type = 163 / media_type = 43（和音乐作品撞了，所以
  必须先判 article_info，再判音乐）

【改法】
  · ProfileParser 增加 KIND_ARTICLE = 5
  · 主页解析时：article_info 存在 → KIND_ARTICLE（优先于 MUSIC 判定）
  · detail 解析时：article_info 存在 → 抽 article_title + markdown，
    存成 UTF-8 的 .md 文件到「文章」目录
  · 批量下载遇到 KIND_ARTICLE → 走同一个文章保存逻辑
"""
import io

# ---------- 1. ProfileParser 加类型 ----------
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


rep('P1-KIND_ARTICLE',
    '''    public static final int KIND_MUSIC   = 4;   // 音乐作品（只有音频，无视频）''',
    '''    public static final int KIND_MUSIC   = 4;   // 音乐作品（只有音频，无视频）
    public static final int KIND_ARTICLE = 5;   // 文章（长文，导出 markdown）''')

rep('P2-label',
    '''                case KIND_MUSIC: return "音乐(无视频)";''',
    '''                case KIND_MUSIC: return "音乐(无视频)";
                case KIND_ARTICLE: return "文章";''')

# 文章正文（解析阶段就能带出来，落盘时不重新请求）
rep('P3-article正文字段',
    '''        /** 类型中文名（列表 UI 显示用） */''',
    '''        /** 文章正文（markdown，仅 KIND_ARTICLE 有效） */
        public String articleMd = "";
        /** 文章标题（KIND_ARTICLE） */
        public String articleTitle = "";

        /** 类型中文名（列表 UI 显示用） */''')

io.open(PP, 'w', encoding='utf-8').write(s)
print('ProfileParser 成功:', ok)
print('ProfileParser 失败:', miss)


# ---------- 2. MainActivity：主页解析判定 + detail 抽取 + 落盘 ----------
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


# 2a. appendDyProfileItems 里的 kind 判定：article 优先
rep2('M1-主页判文章',
     '''                if(isImage){
                    it.kind = (liveCnt > 0) ? ProfileParser.KIND_LIVE
                                            : ProfileParser.KIND_IMAGE;
                }else{
                    long at = parseLong(get(aw, "aweme_type"), 0);
                    long mt = parseLong(get(aw, "media_type"), 0);
                    if(at == 163 || mt == 43){
                        it.kind = ProfileParser.KIND_MUSIC;
                    }else if(it.directUrl != null && it.directUrl.length() > 0){
                        it.kind = ProfileParser.KIND_VIDEO;
                    }
                }''',
     '''                // 【文章必须最先判】抖音文章也带 aweme_type=163 / media_type=43，
                // 和音乐作品撞了。只有 article_info 能把它们区分开。
                Object artObj = get(aw, "article_info");
                if(artObj instanceof Map){
                    it.kind = ProfileParser.KIND_ARTICLE;
                    try{ it.articleTitle = gs(get((Map) artObj, "article_title")); }catch(Throwable t2){}
                }else if(isImage){
                    it.kind = (liveCnt > 0) ? ProfileParser.KIND_LIVE
                                            : ProfileParser.KIND_IMAGE;
                }else{
                    long at = parseLong(get(aw, "aweme_type"), 0);
                    long mt = parseLong(get(aw, "media_type"), 0);
                    if(at == 163 || mt == 43){
                        it.kind = ProfileParser.KIND_MUSIC;
                    }else if(it.directUrl != null && it.directUrl.length() > 0){
                        it.kind = ProfileParser.KIND_VIDEO;
                    }
                }''')

# 2b. 文章落盘方法 + 正文抽取
rep2('M2-文章方法',
     '''    /** 该条图集是否含实况动轨 */''',
     '''    /**
     * 从 article_info 抽出 markdown 正文并落盘。
     *
     * 结构：
     *   article_info.article_content = "{\\"markdown\\":\\"...\\",\\"long_article_abstract\\":\\"...\\"}"
     *   → 是 JSON 字符串，要二次解开
     */
    static String dyArticleMd(Object artObj){
        try{
            if(!(artObj instanceof Map)) return "";
            Map am = (Map) artObj;
            String content = gs(get(am, "article_content"));
            if(content.length() == 0) return "";
            Object c = new JP(content).parse();
            if(!(c instanceof Map)) return content;
            Map cm = (Map) c;
            String md = gs(get(cm, "markdown"));
            if(md.length() == 0) md = gs(get(cm, "long_article_abstract"));
            return md;
        }catch(Throwable t){
            DypLog.i("DY_ARTICLE_MD_ERR " + t);
            return "";
        }
    }

    /** 把文章存成 .md 到「文章」目录，返回文件路径（失败返回 ""） */
    String saveDyArticle(String id, String title, String md){
        try{
            if(md == null || md.length() == 0) return "";
            String t = (title == null || title.trim().length() == 0) ? ("文章_" + id) : title.trim();
            // 文件名去掉非法字符
            t = t.replaceAll("[\\\\\\\\/:*?\\"<>|\\\\r\\\\n\\\\t]", "_");
            if(t.length() > 60) t = t.substring(0, 60);
            java.io.File dir = new java.io.File(dlDirFor("抖音", "文章"));
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, "dy_" + id + "_" + t + ".md");
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(md.getBytes("UTF-8"));
            fo.close();
            scanMedia(f.getAbsolutePath());
            DypLog.i("DY_ARTICLE_SAVED path=" + f.getAbsolutePath() + " len=" + md.length());
            return f.getAbsolutePath();
        }catch(Throwable t){
            DypLog.i("DY_ARTICLE_SAVE_ERR " + t);
            return "";
        }
    }

    /** 该条图集是否含实况动轨 */''')

io.open(P, 'w', encoding='utf-8').write(s)
print('MainActivity 成功:', ok2)
print('MainActivity 失败:', miss2)