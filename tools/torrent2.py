# -*- coding: utf-8 -*-
"""1) 设置页加「本地种子文件」入口
   2) 磁力/种子任务「立刻」进列表页 + 落盘（重启后仍在）
      - 修复根因：条目原来只在 onMeta 回调里加内存列表 profileCollected，
        拿不到元数据时列表里什么都不显示；且 profileCollected 不落盘。
      改为：点击就写进 batchCollected（持久化列表）并 saveBatchList()，
      拿到元数据后再更新标题。
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# ---------- 1) 设置页入口 ----------
old = '''        addSettingRow("批量解析", "一次粘贴多条链接，一行一个", new View.OnClickListener(){
            public void onClick(View v){ showBatchInputDialog(); }});'''
new = '''        addSettingRow("批量解析", "一次粘贴多条链接，一行一个", new View.OnClickListener(){
            public void onClick(View v){ showBatchInputDialog(); }});
        addSettingRow("本地种子文件", "选择手机里的 .torrent 种子，直接开始下载",
            new View.OnClickListener(){
                public void onClick(View v){ pickTorrentFile(); }});'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) 设置页入口 OK')
else:
    print('MISS 1')

# ---------- 2) 点击立刻进列表 + 落盘 ----------
old = '''        dlRowAdd(rowKey, "【磁力】" + btDisplayName(magnet));
        dlRowUpdate(rowKey, -1, "获取种子信息…");'''
new = '''        dlRowAdd(rowKey, "【磁力】" + btDisplayName(magnet));
        dlRowUpdate(rowKey, -1, "获取种子信息…");

        // 立刻进「列表」页并落盘：拿不到种子元数据（低热度资源）时，
        // 用户至少能在列表里看到这条任务，而不是完全查无此项。
        try{
            final String listId = "magnet_dl_" + saveDir.getName();
            btListItemId = listId;
            ProfileParser.ProfileItem bi = new ProfileParser.ProfileItem();
            bi.id = listId;
            bi.title = "【磁力】" + btDisplayName(magnet) + "  ·  正在获取种子信息…";
            bi.url = magnet;
            bi.isImage = false;
            synchronized(batchCollected){
                // 同一条（同 infohash）只保留一条，避免重复点击堆叠
                for(int k = batchCollected.size() - 1; k >= 0; k--){
                    ProfileParser.ProfileItem o = batchCollected.get(k);
                    if(o != null && listId.equals(o.id)) batchCollected.remove(k);
                }
                batchCollected.add(0, bi);
            }
            saveBatchList();
            Log.i("DYP", "BT_LIST_ADDED_EARLY " + listId);
            h.post(new Runnable(){ public void run(){
                renderBatchList();
                switchTab(1);
            }});
        }catch(Throwable t){ Log.i("DYP", "BT_LIST_EARLY_ERR " + t); }'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) 点击立刻进列表 OK')
else:
    print('MISS 2')

# ---------- 3) onMeta 改为更新已有条目标题 ----------
old = '''                    // 磁力条目也进「列表」页，方便和历史作品一起看
                    try{
                        ProfileParser.ProfileItem bi = new ProfileParser.ProfileItem();
                        bi.id = "magnet_" + saveDir.getName();
                        bi.title = "【磁力】" + saveDir.getName() + "  (" + files.size() + " 个文件 "
                                + InnerDownloader.sizeText(totalBytes) + ")";
                        bi.url = magnet;
                        bi.isImage = false;
                        synchronized(profileCollected){ profileCollected.add(0, bi); }
                        showProfileList(profileCollected);
                        switchTab(1);
                        Log.i("DYP", "BT_LIST_ADDED " + saveDir.getName());
                    }catch(Throwable t){ Log.i("DYP", "BT_LIST_ERR " + t); }'''
new = '''                    // 拿到元数据：更新「列表」页那条的标题（文件数 + 体积）
                    try{
                        final String listId = btListItemId != null && btListItemId.length() > 0
                                ? btListItemId : ("magnet_dl_" + saveDir.getName());
                        boolean hit = false;
                        synchronized(batchCollected){
                            for(ProfileParser.ProfileItem o : batchCollected){
                                if(o != null && listId.equals(o.id)){
                                    o.title = "【磁力】" + btDisplayName(magnet) + "  ("
                                            + files.size() + " 个文件 "
                                            + InnerDownloader.sizeText(totalBytes) + ")";
                                    hit = true;
                                }
                            }
                        }
                        if(!hit){
                            // 兜底：早前的条目丢了就补一条
                            ProfileParser.ProfileItem bi = new ProfileParser.ProfileItem();
                            bi.id = listId;
                            bi.title = "【磁力】" + btDisplayName(magnet) + "  ("
                                    + files.size() + " 个文件 "
                                    + InnerDownloader.sizeText(totalBytes) + ")";
                            bi.url = magnet;
                            bi.isImage = false;
                            synchronized(batchCollected){ batchCollected.add(0, bi); }
                        }
                        saveBatchList();
                        renderBatchList();
                        switchTab(1);
                        Log.i("DYP", "BT_LIST_ADDED " + listId + " hit=" + hit);
                    }catch(Throwable t){ Log.i("DYP", "BT_LIST_ERR " + t); }'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('3) onMeta 更新标题 OK')
else:
    print('MISS 3')

# ---------- 4) btListItemId 字段 ----------
old = '''    /** 磁力：保存目录 = Download/DYParse/磁力/magnet_<hash前8位> */'''
new = '''    /** 当前磁力/种子任务在「列表」页的条目 id（拿到元数据后按它更新标题） */
    volatile String btListItemId = "";

    /** 磁力：保存目录 = Download/DYParse/磁力/magnet_<hash前8位> */'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('4) btListItemId OK')
else:
    print('MISS 4')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)