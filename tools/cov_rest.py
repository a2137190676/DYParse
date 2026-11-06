# -*- coding: utf-8 -*-
# 把剩下 6 处 ProfileItem 构造的 cover 一并补全
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) B 站主页空间 (12561)
old1 = '''                                pi.url = "https://www.bilibili.com/video/" + pi.id;
                                items.add(pi);
                            }
                            showProfileList(items);'''
new1 = '''                                pi.url = "https://www.bilibili.com/video/" + pi.id;
                                // 主页封面用 bvid 拼
                                String pib = pi.id == null ? "" : pi.id;
                                pi.cover = pib.length() > 0
                                        ? ("https://i0.hdslb.com/bfs/archive/" + pib + ".jpg") : "";
                                items.add(pi);
                            }
                            showProfileList(items);'''
if old1 in d:
    d = d.replace(old1, new1, 1); n += 1; print('B 站主页 OK')

# 2) 快手合集 12796（主页壳页列表）
old2 = '''                                for(Map<String, String> it : plist){
                                    ProfileParser.ProfileItem pi = new ProfileParser.ProfileItem();
                                    pi.id = it.get("id");
                                    pi.title = it.get("title");
                                    pi.url = it.get("url");
                                    pi.directUrl = it.get("mv");
                                    items.add(pi);
                                }'''
new2 = '''                                for(Map<String, String> it : plist){
                                    ProfileParser.ProfileItem pi = new ProfileParser.ProfileItem();
                                    pi.id = it.get("id");
                                    pi.title = it.get("title");
                                    pi.url = it.get("url");
                                    pi.directUrl = it.get("mv");
                                    // 快手壳页解析对象里也带 cover（没有就空）
                                    String kc = it.get("cover");
                                    if(kc == null) kc = it.get("coverUrl");
                                    if(kc == null) kc = "";
                                    pi.cover = kc;
                                    items.add(pi);
                                }'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('快手主页 OK')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)