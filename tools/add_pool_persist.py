# -*- coding: utf-8 -*-
"""
三个数据池落盘（重启后保留）。

【现状】
  只有 batchCollected 会 saveBatchList() 落盘成 batch_list.tsv。
  itemsFromSingle / itemsFromBatch / itemsFromProfile 全在内存，
  App 一重启就全空 —— 用户重启后进列表页，三个子页签都是 0 条。

【改法】
  新增 list_pools.tsv，每行：
      池编号 \t title \t url \t directUrl \t isImage \t id \t cover \t imgs \t dyns
      池编号：0=批量解析  1=视频/图文解析  2=主页解析

  写入时机：三个池任一变动后调 saveListPools()（延迟 300ms 合并写，避免频繁 IO）
  读取时机：onCreate 里 loadBatchList() 之后调 loadListPools()

  为什么不复用 batch_list.tsv：
    那个文件是 batchCollected 的镜像（渲染用），
    和「三个来源池」是两套语义，混在一起会互相覆盖。
    本项目已经踩过这个坑（showProfileList 里 batchCollected.clear() 反写池子）。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# ============================================================
# 1) 新增 saveListPools / loadListPools / listPoolsFile
# ============================================================
anchor = '''    /** 列表持久化文件 */
    java.io.File batchListFile(){'''

helpers = '''    /** 三个子页签数据池的持久化文件 */
    java.io.File listPoolsFile(){
        try{
            java.io.File dir = getExternalFilesDir(null);
            if(dir == null) dir = new java.io.File(getFilesDir(), "data");
            if(!dir.exists()) dir.mkdirs();
            return new java.io.File(dir, "list_pools.tsv");
        }catch(Throwable t){ return null; }
    }

    /** 写池子的节流锁 */
    final Object poolSaveLock = new Object();
    volatile boolean poolSavePending = false;

    /** 三个池任一变动后调用。300ms 合并写，避免连续解析时反复 IO。 */
    void saveListPools(){
        synchronized(poolSaveLock){
            if(poolSavePending) return;
            poolSavePending = true;
        }
        h.postDelayed(new Runnable(){ public void run(){
            synchronized(poolSaveLock){ poolSavePending = false; }
            saveListPoolsNow();
        }}, 300);
    }

    /** 立即落盘三个池 */
    void saveListPoolsNow(){
        try{
            java.io.File f = listPoolsFile();
            if(f == null) return;
            StringBuilder sb = new StringBuilder();
            int n0, n1, n2;
            synchronized(itemsFromBatch){
                for(ProfileParser.ProfileItem it : itemsFromBatch){
                    appendPoolLine(sb, 0, it);
                }
                n0 = itemsFromBatch.size();
            }
            synchronized(itemsFromSingle){
                for(ProfileParser.ProfileItem it : itemsFromSingle){
                    appendPoolLine(sb, 1, it);
                }
                n1 = itemsFromSingle.size();
            }
            synchronized(itemsFromProfile){
                for(ProfileParser.ProfileItem it : itemsFromProfile){
                    appendPoolLine(sb, 2, it);
                }
                n2 = itemsFromProfile.size();
            }
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(sb.toString().getBytes("UTF-8"));
            fo.close();
            Log.i("DYP", "POOLS_SAVE batch=" + n0 + " single=" + n1
                    + " profile=" + n2 + " bytes=" + f.length());
        }catch(Throwable t){
            Log.i("DYP", "POOLS_SAVE_ERR " + t);
        }
    }

    void appendPoolLine(StringBuilder sb, int pool, ProfileParser.ProfileItem it){
        if(it == null) return;
        sb.append(pool).append('\\t')
          .append(batchEsc(it.title)).append('\\t')
          .append(batchEsc(it.url)).append('\\t')
          .append(batchEsc(it.directUrl)).append('\\t')
          .append(it.isImage ? "1" : "0").append('\\t')
          .append(batchEsc(it.id)).append('\\t')
          .append(batchEsc(it.cover)).append('\\t')
          .append(batchEsc(joinList(it.imgs))).append('\\t')
          .append(batchEsc(joinList(it.dyns))).append('\\n');
    }

    /** 启动时把三个池读回来。必须在 itemsFrom* 定义之后才能调。 */
    void loadListPools(){
        try{
            java.io.File f = listPoolsFile();
            if(f == null || !f.exists() || f.length() == 0) return;
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            java.util.List<ProfileParser.ProfileItem> l0 =
                    new java.util.ArrayList<ProfileParser.ProfileItem>();
            java.util.List<ProfileParser.ProfileItem> l1 =
                    new java.util.ArrayList<ProfileParser.ProfileItem>();
            java.util.List<ProfileParser.ProfileItem> l2 =
                    new java.util.ArrayList<ProfileParser.ProfileItem>();
            String line;
            while((line = br.readLine()) != null){
                if(line.length() == 0) continue;
                String[] p = line.split("\\t", -1);
                if(p.length < 3) continue;
                int pool;
                try{ pool = Integer.parseInt(p[0].trim()); }catch(Throwable t){ continue; }
                ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
                it.title = batchUnesc(p[1]);
                it.url = batchUnesc(p[2]);
                it.directUrl = p.length > 3 ? batchUnesc(p[3]) : "";
                it.isImage = p.length > 4 && "1".equals(p[4]);
                it.id = p.length > 5 ? batchUnesc(p[5]) : "";
                it.cover = p.length > 6 ? batchUnesc(p[6]) : "";
                if(p.length > 7) it.imgs = splitList(batchUnesc(p[7]));
                if(p.length > 8) it.dyns = splitList(batchUnesc(p[8]));
                // 老数据 / 缺 imgs 时用 cover 兜底（图集必须能批量下载）
                if(it.isImage && it.imgs.isEmpty() && it.cover != null
                        && it.cover.indexOf("http") == 0 && isImageCdnUrl(it.cover)){
                    it.imgs.add(it.cover);
                    it.dyns.add("");
                }
                if(pool == 0) l0.add(it);
                else if(pool == 1) l1.add(it);
                else l2.add(it);
            }
            br.close();
            synchronized(itemsFromBatch){ itemsFromBatch.clear(); itemsFromBatch.addAll(l0); }
            synchronized(itemsFromSingle){ itemsFromSingle.clear(); itemsFromSingle.addAll(l1); }
            synchronized(itemsFromProfile){ itemsFromProfile.clear(); itemsFromProfile.addAll(l2); }
            Log.i("DYP", "POOLS_LOAD batch=" + l0.size() + " single=" + l1.size()
                    + " profile=" + l2.size());
            // 批量池也同步进 batchCollected，否则列表页「批量下载」读不到
            if(!l0.isEmpty()){
                synchronized(batchCollected){
                    batchCollected.clear();
                    batchCollected.addAll(l0);
                }
            }
        }catch(Throwable t){
            Log.i("DYP", "POOLS_LOAD_ERR " + t);
        }
    }

'''

if 'void loadListPools()' not in s:
    s = s.replace(anchor, helpers + anchor, 1)
    ok.append('1-池子落盘函数')
else:
    ok.append('1-已存在')

# ============================================================
# 2) onCreate 启动时加载（跟在 loadBatchList 后面）
# ============================================================
rep('2-启动加载',
    '''        h.postDelayed(new Runnable(){ public void run(){ loadBatchList(); }}, 600);''',
    '''        h.postDelayed(new Runnable(){ public void run(){
            loadBatchList();
            // 三个子页签的数据池（批量解析 / 视频图文 / 主页解析）也要恢复，
            // 否则重启后列表页三个子页签全是 0 条。
            loadListPools();
        }}, 600);''')

# ============================================================
# 3) saveBatchList 里顺带落盘池子（这是最频繁的触发点）
# ============================================================
rep('3-saveBatchList联动',
    '''            Log.i("DYP", "BATCH_SAVE n=" + snap.size() + " -> " + f.getAbsolutePath());''',
    '''            Log.i("DYP", "BATCH_SAVE n=" + snap.size() + " -> " + f.getAbsolutePath());
            // 顺手把三个来源池也落盘（节流写）
            saveListPools();''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)