# -*- coding: utf-8 -*-
"""
修：TikTok 解析成功后没有「BGM」按钮，也提不出背景音乐。

【根因】
  buildTtResult() 里有一行：
      curMusic = "";   // TikTok 的 music.playUrl 是配乐片段，不是原声，不当音轨用

  这句注释本身是错的。
  TikTok 的 music.playUrl 指向的是「作品使用的那首完整音乐」，
  是独立音轨（通常 m4a/mp3，几百 KB 到几 MB），不是视频里的片段。
  抖音的 music 才是 15~30 秒试听片段，TikTok 不是。

  更要命的是：buildTtResult 拼 JSON 时**压根没放 i_bgm / i_bgmTitle**。
  而 render() 是靠 i_bgm 回填 curBgm 的（第 12479 行）：
      curBgm = gs(get(m,"i_bgm"));
      ...
      if(curBgm != null && curBgm.length() > 0) btnMusic.setVisibility(VISIBLE);
  取不到 → curBgm 永远为空 → BGM 按钮永远 GONE → 转 MP3 只能从视频里扒。

【改法】
  1. buildTtResult 补 i_bgm（完整音轨 URL）和 i_bgmTitle / i_bgmAuthor。
  2. 删掉那行 curMusic = ""，改成正确赋值。
     注意：curMusic 是「整段视频音轨」的语义（快手 m4a），
     render() 里对非快手会强制清空（第 12477 行），所以给 TikTok 赋值也没用，
     真正的通路是 curBgm。这里只做注释纠正，逻辑走 curBgm。
"""
import io

p = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

# ---- 1) 纠正错误注释 ----
OLD1 = '''            curMusic = "";   // TikTok 的 music.playUrl 是配乐片段，不是原声，不当音轨用'''
NEW1 = '''            // TikTok 的 music.playUrl 是「作品所用那首完整音乐」的独立音轨（m4a/mp3），
            // 不是视频片段；抖音的 music 才是试听片段。所以这里要把它当 BGM 保留。
            // curMusic 语义是「整段视频音轨」（目前只有快手用），TikTok 走 curBgm。
            curMusic = "";
            curBgm = v.musicUrl == null ? "" : v.musicUrl;
            curBgmTitle = v.musicTitle == null ? "" : v.musicTitle;'''

if OLD1 in s:
    s = s.replace(OLD1, NEW1, 1)
    print('注释已纠正 + curBgm 已赋值')
else:
    print('WARN: 注释行未命中')

# ---- 2) JSON 里补 i_bgm / i_bgmTitle / i_bgmAuthor ----
OLD2 = '''            TtParser.Kv(b, "i_musicTitle", v.musicTitle);
            TtParser.Kv(b, "i_musicAuthor", v.musicAuthor);'''

NEW2 = '''            TtParser.Kv(b, "i_musicTitle", v.musicTitle);
            TtParser.Kv(b, "i_musicAuthor", v.musicAuthor);
            // BGM：必须放进 JSON —— render() 靠 i_bgm 回填 curBgm，
            // 没有这个 key，BGM 按钮永远是 GONE，转 MP3 也只能从视频扒。
            if(v.musicUrl != null && v.musicUrl.length() > 0){
                TtParser.Kv(b, "i_bgm", v.musicUrl);
                TtParser.Kv(b, "i_bgmTitle", v.musicTitle);
                TtParser.Kv(b, "i_bgmAuthor", v.musicAuthor);
            }'''

if OLD2 in s:
    s = s.replace(OLD2, NEW2, 1)
    print('i_bgm 已补进 JSON')
else:
    print('WARN: JSON 锚点未命中')

io.open(p, 'w', encoding='utf-8').write(s)

# ---- 3) 检查 render() 是否会覆盖回空 ----
s2 = io.open(p, encoding='utf-8').read()
print('i_bgm 出现次数:', s2.count('"i_bgm"'))
print('curBgm 赋值行:')
for i, ln in enumerate(s2.split('\n'), 1):
    if 'curBgm =' in ln:
        print('  %d: %s' % (i, ln.strip()))