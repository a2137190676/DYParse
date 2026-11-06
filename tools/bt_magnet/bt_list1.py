# -*- coding: utf-8 -*-
# 磁力也进「列表」页：解析磁力时先往列表插一条，点它可重新进入下载
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 在 BT_CLICK 之后把磁力塞进 profileCollected 并渲染列表
old = '''        Log.i("DYP", "BT_CLICK name=" + name + " uri=" + magnet);
'''
new = '''        Log.i("DYP", "BT_CLICK name=" + name + " uri=" + magnet);

        // 磁力也留一条在「列表」页，方便回看/重复下载
        try{
            ProfileParser.ProfileItem bi = new ProfileParser.ProfileItem();
            bi.id = "magnet:" + name;
            bi.title = "【磁力】" + name;
            bi.url = magnet;
            bi.isImage = false;
            bi.durSec = 0;
            bi.cover = "";
            synchronized(profileCollected){ profileCollected.add(0, bi); }
            h.post(new Runnable(){ public void run(){
                try{
                    showProfileList(new java.util.ArrayList<ProfileParser.ProfileItem>(profileCollected));
                }catch(Throwable t){}
            }});
            Log.i("DYP", "BT_LIST_ADDED " + name);
        }catch(Throwable t){
            Log.i("DYP", "BT_LIST_ADD_ERR " + t);
        }
'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('磁力入列表 OK')
else:
    print('MISS BT_CLICK')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)