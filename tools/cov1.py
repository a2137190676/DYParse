# -*- coding: utf-8 -*-
# 给快手、B 站、YouTube 三个主页解析补封面
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# ---------- 快手 ----------
old = '''            Object photo = get(ph, "photo");
            if(photo instanceof Map){
                String mv = gs(get((Map) photo, "mainMvUrl"));
                if(mv.length() == 0) mv = gs(get((Map) photo, "mainMvUrls"));
                if(mv.length() == 0) mv = gs(get((Map) photo, "photoUrl"));
                if(mv.length() > 0) it.directUrl = mv;
            }
            it.url = "https://www.kuaishou.com/short-video/" + photoId;'''

new = '''            Object photo = get(ph, "photo");
            if(photo instanceof Map){
                String mv = gs(get((Map) photo, "mainMvUrl"));
                if(mv.length() == 0) mv = gs(get((Map) photo, "mainMvUrls"));
                if(mv.length() == 0) mv = gs(get((Map) photo, "photoUrl"));
                if(mv.length() > 0) it.directUrl = mv;
                // 封面：coverUrls[0] 不带、webpCoverUrls 更省流
                String cv = gs(get((Map) photo, "coverUrl"));
                if(cv.length() == 0) cv = gs(get((Map) photo, "coverUrls"));
                if(cv.length() == 0) cv = gs(get((Map) photo, "webpCoverUrls"));
                if(cv.length() == 0) cv = gs(get((Map) photo, "cover"));
                if(cv.length() > 0) it.cover = cv;
            }
            // 兜底：ph 自己顶上也可能有
            if(it.cover.length() == 0){
                String cv = gs(get(ph, "coverUrl"));
                if(cv.length() > 0) it.cover = cv;
            }
            it.url = "https://www.kuaishou.com/short-video/" + photoId;'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('KS 封面 OK')
else:
    print('MISS KS')

# ---------- B 站 ----------
old2 = '''            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = bvid;
            it.title = gs(get(v, "title"));
            it.url = "https://www.bilibili.com/video/" + bvid;
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;'''

new2 = '''            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = bvid;
            it.title = gs(get(v, "title"));
            // B 站封面就是 pic（无协议头要补 https:）
            String bp = gs(get(v, "pic"));
            if(bp.startsWith("//")) bp = "https:" + bp;
            else if(bp.length() > 0 && !bp.startsWith("http")) bp = "https://" + bp;
            it.cover = bp;
            it.durSec = parseLong(gs(get(v, "length")));
            it.url = "https://www.bilibili.com/video/" + bvid;
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('BILI 封面 OK')
else:
    print('MISS BILI')

# ---------- YouTube ----------
old3 = '''            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = vid;
            it.title = title;
            it.url = "https://www.youtube.com/watch?v=" + vid;
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;'''

new3 = '''            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = vid;
            it.title = title;
            // YouTube 缩略图按 ID 直接拼最好：https://i.ytimg.com/vi/<id>/mqdefault.jpg
            // 比抓 data 里的 thumbnail 快，而且 yt-dlp 的 thumbnail 字段经常要带协议头
            it.cover = "https://i.ytimg.com/vi/" + vid + "/mqdefault.jpg";
            it.url = "https://www.youtube.com/watch?v=" + vid;
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;'''
if old3 in d:
    d = d.replace(old3, new3, 1); n += 1; print('YT 封面 OK')
else:
    print('MISS YT')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)