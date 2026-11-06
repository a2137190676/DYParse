# -*- coding: utf-8 -*-
"""修正 extractUrl：
   1) 之前用占有量词 ++（不回溯）导致 .torrent 被吃掉 → 匹配失败
   2) 更稳的做法：整串输入本身就是存在的本地种子文件 → 直接返回，不依赖正则
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()

# 1) 先加「整串就是本地种子文件」的快速判定（放最前，最可靠）
old = '''        if(t.toLowerCase().startsWith("magnet:")){
            int sp = t.indexOf(' ');
            return sp > 0 ? t.substring(0, sp) : t;
        }'''
new = '''        if(t.toLowerCase().startsWith("magnet:")){
            int sp = t.indexOf(' ');
            return sp > 0 ? t.substring(0, sp) : t;
        }
        // 整串输入就是一个本地 .torrent 文件（文件选择器 / 手动粘贴路径）
        // 放在最前面，不依赖任何正则，最稳。
        String tTrim = t.trim();
        if(tTrim.toLowerCase().endsWith(".torrent")
                && new java.io.File(tTrim).exists()){
            return tTrim;
        }'''
if old in m:
    m = m.replace(old, new, 1); print('1) 整串种子路径判定 OK')
else:
    print('MISS 1')

# 2) 修正原来那条有问题的正则（去掉占有量词 ++）
old = '''        Matcher ml = Pattern.compile("/(?:sdcard|storage|data|mnt)/[A-Za-z0-9_./\\\\u4e00-\\\\u9fa5 ()\\\\-]++\\\\.torrent",
                Pattern.CASE_INSENSITIVE).matcher(t);
        if(ml.find()) return ml.group().trim();'''
new = '''        // 文本里夹着本地种子路径（例如「下载了：/sdcard/x.torrent」）
        Matcher ml = Pattern.compile("/(?:sdcard|storage|data|mnt)/[A-Za-z0-9_./ ()\\\\-\\\\u4e00-\\\\u9fa5]+?\\\\.torrent",
                Pattern.CASE_INSENSITIVE).matcher(t);
        if(ml.find()){
            String hit = ml.group().trim();
            if(new java.io.File(hit).exists()) return hit;
        }'''
if old in m:
    m = m.replace(old, new, 1); print('2) 正则修正 OK')
else:
    print('MISS 2')

io.open(MA, 'w', encoding='utf-8').write(m)
print('done')