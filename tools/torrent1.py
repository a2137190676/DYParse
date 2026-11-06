# -*- coding: utf-8 -*-
"""1) BtDownloader 增 inspectTorrent（读 .torrent 拿 infohash/name）
   2) MainActivity：REQ_TORRENT 常量 + onActivityResult + pickTorrentFile
      + loadTorrentFromUri + btDisplayName + btSaveDir 支持 .torrent 路径
"""
import io

BT = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'

# ============================================================
# A) BtDownloader: inspectTorrent
# ============================================================
d = io.open(BT, encoding='utf-8').read()
n = 0

anchor = '''    /** Cb 的空实现基类：MainActivity 只覆盖需要的几个 */'''
add = '''    /**
     * 读取本地 .torrent 文件，返回 [infoHashHex, name]，失败返回 null。
     * 迅雷等客户端保存的 .torrent 通常自带它自己的 tracker，
     * 这些 tracker 可能有活着的 peer，所以直接加载种子文件有时能救活
     * 公开 swarm 已死的资源。
     */
    public static String[] inspectTorrent(File f) {
        try {
            if (f == null || !f.exists() || f.length() < 100) {
                Log.i(TAG, "BT_TORRENT_INSPECT_BAD len=" + (f == null ? -1 : f.length()));
                return null;
            }
            TorrentInfo ti = new TorrentInfo(f);
            String hash = "";
            try {
                if (ti.infoHash() != null) hash = ti.infoHash().toHex();
            } catch (Throwable t) {
                Log.i(TAG, "BT_TORRENT_HASH_ERR " + t);
            }
            String nm = "";
            try { nm = ti.name(); } catch (Throwable t) {}
            int nf = -1;
            try { nf = ti.numFiles(); } catch (Throwable t) {}
            Log.i(TAG, "BT_TORRENT_INSPECT hash=" + hash + " name=" + nm + " files=" + nf);
            return new String[]{ hash == null ? "" : hash, nm == null ? "" : nm };
        } catch (Throwable t) {
            Log.i(TAG, "BT_TORRENT_INSPECT_ERR " + t);
            return null;
        }
    }

'''
if anchor in d:
    d = d.replace(anchor, add + anchor, 1); n += 1; print('A) inspectTorrent OK')
else:
    print('MISS A anchor')
io.open(BT, 'w', encoding='utf-8').write(d)

# ============================================================
# B) MainActivity
# ============================================================
m = io.open(MA, encoding='utf-8').read()
n2 = 0

# B1) REQ_TORRENT 常量
old = '''    static final int REQ_STORAGE = 1001;'''
new = '''    static final int REQ_STORAGE = 1001;
    /** 选本地 .torrent 种子文件 */
    static final int REQ_TORRENT = 1002;'''
if old in m:
    m = m.replace(old, new, 1); n2 += 1; print('B1) REQ_TORRENT OK')
else:
    print('MISS B1')

# B2) btSaveDir 支持 .torrent 路径
old = '''    java.io.File btSaveDir(String magnet){
        String h = "";
        try{
            int p = magnet.toLowerCase().indexOf("btih:");
            if(p > 0) h = magnet.substring(p + 5, Math.min(magnet.length(), p + 5 + 8));
        }catch(Throwable t){}
        if(h.length() == 0) h = "unknown" + System.currentTimeMillis() % 100000;
        java.io.File dir = new java.io.File(dlPlatDir("磁力"), "magnet_" + h.toUpperCase());
        if(!dir.exists()) dir.mkdirs();
        return dir;
    }'''
new = '''    java.io.File btSaveDir(String magnet){
        String h = "";
        try{
            // 本地种子文件：从 .torrent 里读 infoHash，保证目录稳定可续传
            if(magnet != null && magnet.toLowerCase().endsWith(".torrent")){
                String[] ins = BtDownloader.inspectTorrent(new java.io.File(magnet));
                if(ins != null && ins[0] != null && ins[0].length() >= 8){
                    h = ins[0].substring(0, 8);
                    Log.i("DYP", "BT_SEEDDIR_BY_TORRENT " + h);
                }
            }
            if(h.length() == 0){
                int p = magnet.toLowerCase().indexOf("btih:");
                if(p > 0) h = magnet.substring(p + 5, Math.min(magnet.length(), p + 5 + 8));
            }
        }catch(Throwable t){}
        if(h.length() == 0) h = "unknown" + System.currentTimeMillis() % 100000;
        java.io.File dir = new java.io.File(dlPlatDir("磁力"), "magnet_" + h.toUpperCase());
        if(!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 磁力/种子的友好显示名（本地种子用文件名，不用路径） */
    String btDisplayName(String src){
        try{
            if(src == null || src.length() == 0) return "磁力任务";
            if(src.toLowerCase().endsWith(".torrent")){
                String nm = new java.io.File(src).getName();
                return "种子 " + nm.replaceAll("(?i)\\\\.torrent$", "");
            }
            return "磁力 " + src.substring(0, Math.min(38, src.length()));
        }catch(Throwable t){ return "磁力任务"; }
    }

    /** 选手机里的 .torrent 种子文件（SAF，无需存储权限） */
    void pickTorrentFile(){
        try{
            android.content.Intent it = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            it.setType("*/*");
            it.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/x-bittorrent", "application/octet-stream", "*/*" });
            startActivityForResult(it, REQ_TORRENT);
            Log.i("DYP", "TORRENT_PICK_OPEN");
        }catch(Throwable t){
            Log.i("DYP", "TORRENT_PICK_ERR " + t);
            showHintToast("无法打开文件选择器：" + t);
        }
    }

    /** 选完种子文件：复制到私有目录 → 解析 infohash → 走磁力同一条下载链路 */
    void loadTorrentFromUri(final android.net.Uri uri){
        if(uri == null){ showHintToast("没有选到文件"); return; }
        showHintToast("正在读取种子文件…");
        new Thread(new Runnable(){ public void run(){
            try{
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                if(is == null){
                    toastMain("打不开这个文件（可能没权限）");
                    return;
                }
                java.io.File dir = new java.io.File(getExternalFilesDir(null), "torrents");
                if(!dir.exists()) dir.mkdirs();
                java.io.File dst = new java.io.File(dir,
                        "seed_" + System.currentTimeMillis() + ".torrent");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(dst);
                byte[] buf = new byte[65536];
                int r; long tot = 0;
                while((r = is.read(buf)) > 0){
                    fo.write(buf, 0, r);
                    tot += r;
                    if(tot > 64L * 1024 * 1024) break;   // 种子文件不可能这么大，防呆
                }
                fo.close(); is.close();
                Log.i("DYP", "TORRENT_COPIED bytes=" + tot + " -> " + dst.getAbsolutePath());
                if(tot < 100){
                    try{ dst.delete(); }catch(Throwable t){}
                    toastMain("这个文件太小，不像是种子文件");
                    return;
                }
                final String[] ins = BtDownloader.inspectTorrent(dst);
                if(ins == null || ins[0] == null || ins[0].length() < 8){
                    toastMain("种子文件解析失败（文件可能损坏）");
                    return;
                }
                final String hash = ins[0];
                final String nm = ins[1];
                Log.i("DYP", "TORRENT_OK hash=" + hash + " name=" + nm);
                h.post(new Runnable(){ public void run(){
                    showHintToast("已读取种子：" + (nm.length() > 0 ? nm : hash.substring(0, 12)));
                    startMagnetDownload(dst.getAbsolutePath());
                }});
            }catch(final Throwable t){
                Log.i("DYP", "TORRENT_LOAD_ERR " + t);
                toastMain("读取种子失败：" + t);
            }
        }}).start();
    }

    /** 子线程里安全弹 toast */
    void toastMain(final String msg){
        try{ h.post(new Runnable(){ public void run(){ showHintToast(msg); }}); }catch(Throwable t){}
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data){
        super.onActivityResult(req, res, data);
        if(req != REQ_TORRENT) return;
        try{
            if(res != RESULT_OK || data == null || data.getData() == null){
                Log.i("DYP", "TORRENT_PICK_CANCEL");
                return;
            }
            loadTorrentFromUri(data.getData());
        }catch(Throwable t){
            Log.i("DYP", "TORRENT_RESULT_ERR " + t);
        }
    }'''
if old in m:
    m = m.replace(old, new, 1); n2 += 1; print('B2) btSaveDir + 种子入口方法 OK')
else:
    print('MISS B2')

# B3) 磁力行显示名用 btDisplayName
old = '''        dlRowAdd(rowKey, "【磁力】" + magnet.substring(0, Math.min(38, magnet.length())));'''
new = '''        dlRowAdd(rowKey, "【磁力】" + btDisplayName(magnet));'''
if old in m:
    m = m.replace(old, new, 1); n2 += 1; print('B3) 磁力行显示名 OK')
else:
    print('MISS B3')

# B4) resumeOneTask 支持 .torrent 路径
old = '''            if(BtDownloader.isMagnet(t.url)){
                Log.i("DYP", "DL_RESUME_MAGNET " + t.url.substring(0, Math.min(50, t.url.length())));
                startMagnetDownload(t.url);
                return;
            }'''
new = '''            if(BtDownloader.isMagnet(t.url)
                    || (t.url != null && t.url.toLowerCase().endsWith(".torrent"))){
                Log.i("DYP", "DL_RESUME_MAGNET " + t.url.substring(0, Math.min(50, t.url.length())));
                startMagnetDownload(t.url);
                return;
            }'''
if old in m:
    m = m.replace(old, new, 1); n2 += 1; print('B4) resumeOneTask OK')
else:
    print('MISS B4')

io.open(MA, 'w', encoding='utf-8').write(m)
print('MainActivity changed:', n2)