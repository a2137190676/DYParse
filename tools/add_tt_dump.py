# -*- coding: utf-8 -*-
"""
加临时诊断：把 TikTok 解析页面 HTML 落盘，并打印命中的模板 & 关键字段。

TikTok 实况图 / 图集（photo 作品）解析不出来，但 ID 能拿到：
    TT_FILL id=7637720726061026578 video=0 imgs=0 music=1

说明 itemStruct 拿到了，只是 imagePost 字段为空。
需要确认：是 HTML 里压根没有 imagePost，还是它在另一个模板里。
"""
import io

p = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/TtParser.java'
s = io.open(p, encoding='utf-8').read()

# 1) 在 tryParse 入口把 HTML 落盘（只落前 3 次，避免刷爆存储）
ANCHOR = '''    /** 依次尝试两种模板 */
    static boolean tryParse(String html, TtVideo v){'''

NEW_ANCHOR = '''    /** 依次尝试两种模板 */
    static int ttDumpLeft = 3;
    static boolean tryParse(String html, TtVideo v){
        // 诊断：把页面落盘，确认 photo 作品的 imagePost 在哪个模板里
        try{
            if(html != null && html.length() > 1000 && ttDumpLeft > 0){
                ttDumpLeft--;
                java.io.File f = new java.io.File("/sdcard/Download/DYParse/tt_dump_"
                        + System.currentTimeMillis() + ".html");
                if(f.getParentFile() != null) f.getParentFile().mkdirs();
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                fo.write(html.getBytes("UTF-8"));
                fo.close();
                Log.i(TAG, "TT_DUMP " + f.getAbsolutePath() + " len=" + html.length()
                        + " hasImagePost=" + html.contains("imagePost")
                        + " hasImageURL=" + html.contains("imageURL")
                        + " hasUniversal=" + html.contains("__UNIVERSAL_DATA_FOR_REHYDRATION__")
                        + " hasApiData=" + html.contains("id=\\"api-data\\""));
            }
        }catch(Throwable t){ Log.i(TAG, "TT_DUMP_ERR " + t); }'''

if ANCHOR in s:
    s = s.replace(ANCHOR, NEW_ANCHOR, 1)
    print('已加 HTML 落盘')
else:
    print('WARN: tryParse 锚点未命中')

# 2) 记录命中了哪个模板
s = s.replace('if(parseUniversal(json, v)){ v.template = "reflow"; return true; }',
              'if(parseUniversal(json, v)){ v.template = "reflow"; Log.i(TAG,"TT_TPL reflow imgs=" + v.images.size() + " video=" + v.hasVideo()); return true; }')
s = s.replace('if(parseApiData(json, v)){ v.template = "apidata"; return true; }',
              'if(parseApiData(json, v)){ v.template = "apidata"; Log.i(TAG,"TT_TPL apidata imgs=" + v.images.size() + " video=" + v.hasVideo()); return true; }')

io.open(p, 'w', encoding='utf-8').write(s)
print('已加模板日志')