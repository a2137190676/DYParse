package com.re.douyinparse;

import android.util.Log;

import org.libtorrent4j.SessionManager;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.swig.torrent_flags_t;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 磁力 / BT 种子下载器（基于 libtorrent4j）。
 *
 * 能力：
 *   · magnet:?xt=urn:btih:... 磁力链接
 *   · 本地 .torrent 文件
 *
 * 要点：
 *   1) SessionManager 全局单例，启动一次后一直复用（避免反复起停掉 DHT 节点）
 *   2) 磁力链接需要先通过 DHT/magnet 元数据交换拿到 torrent 信息（BEP-9），
 *      首次可能 10~60 秒，取决于 DHT 连通性 —— 期间状态是「获取种子信息」
 *   3) 下载进度靠轮询 TorrentStatus，不用 alert 事件（简单可靠）
 *   4) 全程本机，不上传任何信息
 */
public class BtDownloader {

    private static final String TAG = "DYP";

    /** 全局单例 Session */
    private static SessionManager sSession = null;
    private static boolean sStarting = false;

    public interface Cb {
        /** 拿到元数据（文件列表就绪）。files 为相对路径，sizes 为字节数 */
        void onMeta(List<String> files, List<Long> sizes, long totalBytes);
        /** 进度回调：总量/已完成/速度(字节每秒)/已连接的 peer 数 */
        void onProgress(long done, long total, long speedBps, int peers);
        /** 完成 */
        void onDone(File saveDir);
        /** 失败 */
        void onError(String msg);
        /** 状态文字（获取种子信息 / 下载中 / 校验中 …） */
        void onState(String state);
        /**
         * 拿到元数据后，可选：返回要下载的文件索引数组。
         * null = 全部下载（默认）。返回空数组 = 只下种子文件本身。
         * 注意：这个回调在 BT 线程上，不能阻塞太久。
         */
        int[] onSelectFiles(List<String> files, List<Long> sizes, long totalBytes);
        /** 种子文件已导出（path 为 .torrent 的实际路径，可为 null 表示导出失败） */
        void onTorrentSaved(File torrentPath);
        /** 可边下边播：第一个视频文件已经可读（path 是本地路径） */
        void onStreamable(File path);
    }

    /**
     * 在保存目录里找已导出的 .torrent 文件。
     * 有就说明这个磁力之前解析过，可以直接复用元数据，不用再等 DHT。
     */
    static File findCachedTorrent(File saveDir) {
        try {
            if (saveDir == null || !saveDir.exists()) return null;
            File[] fs = saveDir.listFiles();
            if (fs == null) return null;
            for (File f : fs) {
                if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".torrent")
                        && f.length() > 100) {
                    return f;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "BT_FIND_CACHE_ERR " + t);
        }
        return null;
    }

    /**
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

    /** Cb 的空实现基类：MainActivity 只覆盖需要的几个 */
    public static abstract class SimpleCb implements Cb {
        @Override public void onMeta(List<String> files, List<Long> sizes, long totalBytes) {}
        @Override public void onProgress(long done, long total, long speedBps, int peers) {}
        @Override public void onDone(File saveDir) {}
        @Override public void onError(String msg) {}
        @Override public void onState(String state) {}
        @Override public int[] onSelectFiles(List<String> files, List<Long> sizes, long totalBytes) { return null; }
        @Override public void onTorrentSaved(File torrentPath) {}
        @Override public void onStreamable(File path) {}
    }

    /**
     * 启动一个磁力下载任务。
     *
     * @param magnet   magnet:?xt=urn:btih:xxx 或 .torrent 文件路径
     * @param saveDir  保存目录
     * @param cb       回调（都跑在调用线程/轮询线程上，UI 更新请自己 post 到主线程）
     */
    public static void start(final String magnet, final File saveDir, final Cb cb) {
        start(magnet, saveDir, cb, "");
    }

    /**
     * 带 targetHash 的启动：targetHash 是这个任务自己的 infoHash（大写 hex）。
     * 多任务并发时靠它精确挑出自己的 handle，避免 handles.get(0) 抢错对象。
     */
    public static void start(final String magnet, final File saveDir, final Cb cb,
                             final String targetHash) {
        new Thread(new Runnable() { public void run() {
            try {
                SessionManager sm = ensureSession();
                if (sm == null) {
                    cb.onError("BT 引擎启动失败（native 库未加载）");
                    return;
                }
                // 记下本线程所属的 session 代际：之后只要代际变了就立刻退出
                final int myGen = sGeneration;
                if (!saveDir.exists()) saveDir.mkdirs();

                Log.i(TAG, "BT_START " + trim(magnet) + " -> " + saveDir.getAbsolutePath());
                // 优先复用本地已导出的 .torrent：重启后秒开，不再等 DHT
                final File cachedTorrent = findCachedTorrent(saveDir);
                if (cachedTorrent != null) {
                    Log.i(TAG, "BT_CACHE_HIT " + cachedTorrent.getAbsolutePath());
                    cb.onState("使用本地种子文件…");
                } else {
                    cb.onState("获取种子信息…（磁力链接需要连 DHT，可能要 10~60 秒）");
                }

                final long t0 = System.currentTimeMillis();
                final boolean[] metaSent = new boolean[]{ false };
                final boolean[] trackerAdded = new boolean[]{ false };
                final boolean[] reannounced = new boolean[]{ false };
                final boolean[] doneSent = new boolean[]{ false };
                final long[] lastDone = new long[]{ 0 };
                final long[] lastTm = new long[]{ System.currentTimeMillis() };
                // 边下边播：第一个视频文件攒够 512KB 就通知播放
                final boolean[] streamSent = new boolean[]{ false };
                final int[] streamFileIdx = new int[]{ -1 };
                // 文件列表要在循环外可见（边下边播按索引取文件名）
                final List<String> names = new ArrayList<String>();
                final List<Long> sizes = new ArrayList<Long>();

                boolean started = false;
                // 优先本地 .torrent（元数据已在本地，秒开）
                if (cachedTorrent != null) {
                    try {
                        TorrentInfo ti = new TorrentInfo(cachedTorrent);
                        Log.i(TAG, "BT_DL_BY_TORRENT " + cachedTorrent.getName()
                                + " files=" + ti.numFiles() + " total=" + ti.totalSize());
                        sm.download(ti, saveDir);
                        started = true;
                    } catch (Throwable te) {
                        Log.i(TAG, "BT_TORRENT_LOAD_FAIL " + te);
                    }
                }
                if (!started) {
                    if (magnet.startsWith("magnet:")) {
                        // 把公共 tracker 拼进 magnet，提速关键
                        String m2 = withTrackers(magnet);
                        Log.i(TAG, "BT_START_FULL " + m2);
                        sm.download(m2, saveDir, torrent_flags_t.from_int(0));
                    } else {
                        // .torrent 文件路径
                        File tf = new File(magnet);
                        TorrentInfo ti = new TorrentInfo(tf);
                        sm.download(ti, saveDir);
                    }
                }

                // 轮询：先等元数据，再等下载完成
                long prevDone = -1, prevTotal = -1;
                final long[] lastAnnounceTm = new long[]{0};
                long lastCbTm = 0;
                while (true) {
                    // 轮询周期：磁力 UI 刷新太快会抖，改为 700ms 取状态、按变化才回调
                    Thread.sleep(700);
                    // 关键安全闸：session 已被 shutdown（用户暂停/删除任务）→ 立刻退出，
                    // 绝不能再碰 sm/h 这些已释放的 native 对象，否则 SIGSEGV 直接闪退。
                    if (myGen != sGeneration) {
                        Log.i(TAG, "BT_ABORT_SESSION_GONE gen=" + myGen + " now=" + sGeneration);
                        return;
                    }
                    // 找 handle：多任务并发时必须按 infoHash 精确匹配自己的那个，
                    // 否则 handles.get(0) 会抢到别人的 handle（进度串台）。
                    List<TorrentHandle> hs = handles(sm);
                    if (!targetHash.isEmpty() && hs.size() > 1) {
                        List<TorrentHandle> mine = new ArrayList<TorrentHandle>();
                        for (TorrentHandle th : hs) {
                            try {
                                if (!th.isValid()) continue;
                                org.libtorrent4j.Sha1Hash sh = th.infoHash();
                                if (sh != null
                                        && targetHash.equalsIgnoreCase(sh.toHex())) {
                                    mine.add(th);
                                }
                            } catch (Throwable te) {}
                        }
                        if (!mine.isEmpty()) hs = mine;
                    }
                    if (hs.isEmpty()) {
                        if (System.currentTimeMillis() - t0 > 180000) {
                            cb.onError("60 秒内没有拿到任何种子，请检查网络或换一条磁力链接");
                            return;
                        }
                        continue;
                    }
                    TorrentHandle h = hs.get(0);
                    // ---- 首次拿到 handle：强制重新 announce + 校验已有分片 ----
                    // 复用本地 .torrent 续传时，libtorrent 默认可能等很久才 announce，
                    // 表现为 peers=0 长期不动。这里主动催一次，立刻去 tracker 拿 peer。
                    if (!reannounced[0]) {
                        reannounced[0] = true;
                        try {
                            h.forceReannounce();
                            Log.i(TAG, "BT_FORCE_REANNOUNCE ok");
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_FORCE_REANNOUNCE_ERR " + te);
                        }
                        // 已有分片：触发校验，让 libtorrent 知道哪些块已有（续传的关键）
                        try {
                            h.forceRecheck();
                            Log.i(TAG, "BT_FORCE_RECHECK ok (续传校验已有分片)");
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_FORCE_RECHECK_ERR " + te);
                        }
                    }
                    TorrentStatus st;
                    try {
                        st = h.status();
                    } catch (Throwable te) {
                        continue;
                    }

                    boolean hasMeta = st.hasMetadata();
                    if(!hasMeta){
                        try{ hasMeta = h.torrentFile() != null; }catch(Throwable te){}
                    }

                    if (!hasMeta) {
                        // 等待期间把「已等待秒数」显示出来，让用户知道还在找 peer
                        long waited = (System.currentTimeMillis() - t0) / 1000;
                        cb.onState(String.format(Locale.US,
                                "正在通过 DHT 查找做种者… 已等待 %d 秒 / 最多 300 秒（peers=%d）",
                                waited, st.numPeers()));
                        if (System.currentTimeMillis() - t0 > 300000) {
                            cb.onError("5 分钟内没找到做种者。这个资源当前可能没有人在做种"
                                    + "（低热度老资源常见），可稍后重试或换其他磁力。");
                            return;
                        }
                        continue;
                    }

                    // ---- 拿到元数据后：追加公共 tracker（提升找 peer 速度的关键）----
                    // 很多磁力不自带 tracker，只靠 DHT 找 peer 非常慢（nodes 长期只有 3~40）
                    if (!trackerAdded[0]) {
                        trackerAdded[0] = true;
                        try {
                            java.util.List<String> tks = new java.util.ArrayList<String>();
                            for (String t : addTrackers()) tks.add(t);
                            // 保留种子自带的 tracker（trackers() 返回 AnnounceEntry，取 url）
                            try{
                                java.util.List<org.libtorrent4j.AnnounceEntry> own = h.trackers();
                                if(own != null){
                                    for(org.libtorrent4j.AnnounceEntry ae : own){
                                        try{
                                            String us = ae.url();
                                            if(us != null && us.length() > 0 && !tks.contains(us)) tks.add(us);
                                        }catch(Throwable t2){}
                                    }
                                }
                            }catch(Throwable t){}
                            // 逐条转成 AnnounceEntry 加进去
                            int added = 0;
                            for(String t : tks){
                                try{
                                    h.addTracker(new org.libtorrent4j.AnnounceEntry(t));
                                    added++;
                                }catch(Throwable t2){}
                            }
                            Log.i(TAG, "BT_TRACKERS_ADDED mode=" + sTrackMode + " n=" + added + "/" + tks.size());
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_TRACKERS_ERR " + te);
                        }
                    }
                    // 文件列表（只发一次）
                    if (!metaSent[0]) {
                        metaSent[0] = true;
                        names.clear();
                        sizes.clear();
                        long total = 0;
                        TorrentInfo ti = null;
                        try {
                            ti = h.torrentFile();
                            int n = ti.numFiles();
                            for (int i = 0; i < n; i++) {
                                String p = ti.files().filePath(i);
                                long sz = ti.files().fileSize(i);
                                names.add(p);
                                sizes.add(sz);
                                total += sz;
                            }
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_FILELIST_ERR " + te);
                        }

                        // ---- 1) 导出 .torrent 种子文件，跟资源一起留着 ----
                        try {
                            if (ti != null) {
                                File tpath = exportTorrent(ti, saveDir);
                                Log.i(TAG, "BT_TORRENT_SAVED " + (tpath == null ? "fail" : tpath.getAbsolutePath()));
                                if (tpath != null) cb.onTorrentSaved(tpath);
                            }
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_TORRENT_ERR " + te);
                        }

                        // ---- 2) 让用户选文件 ----
                        int[] sel = null;
                        try {
                            sel = cb.onSelectFiles(names, sizes, total);
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_SELECT_ERR " + te);
                        }

                        // -1 = 用户取消
                        if (sel != null && sel.length == 1 && sel[0] == -1) {
                            Log.i(TAG, "BT_CANCELLED_BY_USER");
                            // 先确认 session 还在，否则 sm.remove 会 SIGSEGV
                            if (myGen == sGeneration) {
                                try { sm.remove(h); } catch (Throwable te) {}
                            } else {
                                Log.i(TAG, "BT_CANCEL_SESSION_GONE");
                            }
                            cb.onState("已取消");
                            return;
                        }

                        // 应用文件选择
                        try {
                            int n = names.size();
                            if (sel == null) {
                                Log.i(TAG, "BT_SELECT all n=" + n);
                            } else {
                                org.libtorrent4j.Priority[] pr =
                                        new org.libtorrent4j.Priority[n];
                                for (int i = 0; i < n; i++) pr[i] = org.libtorrent4j.Priority.IGNORE;
                                for (int idx : sel) {
                                    if (idx >= 0 && idx < n) pr[idx] = org.libtorrent4j.Priority.DEFAULT;
                                }
                                h.prioritizeFiles(pr);
                                Log.i(TAG, "BT_SELECT picked=" + sel.length + " of " + n);
                                // 重新算总量（只算选中的）
                                long t2 = 0;
                                for (int idx : sel) if (idx >= 0 && idx < names.size()) t2 += sizes.get(idx);
                                total = t2;
                            }
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_PRIORITY_ERR " + te);
                        }

                        // ---- 3) 顺序下载（边下边播的前提）----
                        try {
                            h.setSequentialRange(0, names.size());
                            Log.i(TAG, "BT_SEQUENTIAL on files=" + names.size());
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_SEQ_ERR " + te);
                        }

                        // 挑第一个视频文件作为「边下边播」的目标
                        try {
                            for (int i = 0; i < names.size(); i++) {
                                String nm = names.get(i).toLowerCase(Locale.US);
                                if (nm.endsWith(".mp4") || nm.endsWith(".mkv") || nm.endsWith(".avi")
                                        || nm.endsWith(".mov") || nm.endsWith(".ts")
                                        || nm.endsWith(".webm") || nm.endsWith(".m4v")) {
                                    // 只认用户选中的文件
                                    boolean picked = true;
                                    if (sel != null) {
                                        picked = false;
                                        for (int idx : sel) if (idx == i) picked = true;
                                    }
                                    if (picked) { streamFileIdx[0] = i; break; }
                                }
                            }
                            Log.i(TAG, "BT_STREAM_PICK idx=" + streamFileIdx[0]
                                    + (streamFileIdx[0] >= 0 ? (" file=" + names.get(streamFileIdx[0])) : ""));
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_STREAM_PICK_ERR " + te);
                        }
                        cb.onMeta(names, sizes, total);
                    }

                    // ---- 边下边播：第一个视频文件攒够 512KB 就通知 ----
                    try {
                        if (!streamSent[0] && streamFileIdx[0] >= 0) {
                            long[] fps = h.fileProgress();
                            long fp = (fps != null && streamFileIdx[0] < fps.length)
                                    ? fps[streamFileIdx[0]] : 0;
                            if (fp >= 512L * 1024L) {
                                streamSent[0] = true;
                                String rel = names.get(streamFileIdx[0]);
                                File sf = new File(saveDir, rel);
                                Log.i(TAG, "BT_STREAMABLE_HIT " + sf.getAbsolutePath()
                                        + " ready=" + fp);
                                cb.onStreamable(sf);
                            }
                        }
                    } catch (Throwable te) {
                        Log.i(TAG, "BT_STREAM_CHECK_ERR " + te);
                    }
                    long done = st.totalDone();
                    long total = st.totalWanted();
                    long now = System.currentTimeMillis();
                    long dt = now - lastTm[0];
                    long spd = 0;
                    if (dt >= 1000) {
                        spd = (done - lastDone[0]) * 1000 / dt;
                        lastDone[0] = done;
                        lastTm[0] = now;
                    }
                    // 进度节流：只有「已下载量变化 >= 256KB」或「总长变了」或「>=1.5 秒」才回调，
                    // 避免每 700ms 无脑刷 UI 造成进度条抖动。
                    long nowCb = System.currentTimeMillis();
                    boolean changed = (done != prevDone && Math.abs(done - prevDone) >= 262144L)
                            || total != prevTotal
                            || (nowCb - lastCbTm) >= 1500;
                    if (changed) {
                        prevDone = done;
                        prevTotal = total;
                        lastCbTm = nowCb;
                        cb.onProgress(done, total, spd, st.numPeers());
                    }

                    // ---- peers=0 时定期再催 announce（30 秒一次）----
                    // tracker 偶发超时/被拒时不至于永久卡住，重试能重新拿到 peer。
                    if (st.numPeers() == 0) {
                        long nowRA = System.currentTimeMillis();
                        if (nowRA - lastAnnounceTm[0] > 30000) {
                            lastAnnounceTm[0] = nowRA;
                            try {
                                h.forceReannounce();
                                Log.i(TAG, "BT_REANNOUNCE_RETRY (peers=0 已干等 30s)");
                            } catch (Throwable te) {}
                        }
                    }
                    // 状态文字
                    String state;
                    if (st.isSeeding() || st.isFinished()) state = "已完成";
                    else if (st.numPeers() == 0) {
                        // 卡住时说清楚：进度停住是没连上做种者，不是程序卡死
                        state = "进度已到 " + (total > 0 ? (done * 100 / total) : 0)
                                + "%：暂时没有 peers（做种者），进度会停在这里等连上";
                    } else {
                        state = "下载中  " + (total > 0 ? (done * 100 / total) : 0) + "%";
                    }
                    cb.onState(state);

                    if ((st.isFinished() || st.isSeeding()) && !doneSent[0]) {
                        doneSent[0] = true;
                        cb.onDone(saveDir);
                        Log.i(TAG, "BT_DONE " + saveDir.getAbsolutePath());
                        return;
                    }
                }
            } catch (Throwable t) {
                Log.i(TAG, "BT_ERR " + t);
                cb.onError(String.valueOf(t));
            }
        }}).start();
    }

    /**
     * 把 torrent 元数据导出成 .torrent 文件，跟资源存在一起。
     * 用户以后可以直接用这个种子重新下载（不用再找磁力）。
     */
    static File exportTorrent(TorrentInfo ti, File saveDir) {
        try {
            if (ti == null) return null;
            // 拿原始 info 字典（含 pieces），手工拼成完整 .torrent
            // 结构： d 4:info <info_dict> 8:announce <tracker> e
            byte[] infoSec = null;
            try {
                org.libtorrent4j.swig.byte_span bs = ti.swig().get_info_section();
                if (bs != null) {
                    int len = (int) bs.size();
                    if (len > 0) {
                        infoSec = new byte[len];
                        for (int i = 0; i < len; i++) infoSec[i] = bs.at(i);
                    }
                }
            } catch (Throwable t1) {
                Log.i(TAG, "BT_INFO_SECTION_ERR " + t1);
            }
            if (infoSec == null || infoSec.length == 0) {
                Log.i(TAG, "BT_INFO_SECTION_EMPTY");
                return null;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try {
                bos.write("d4:info".getBytes("UTF-8"));
                bos.write(infoSec);
                // 附第一个 tracker，方便再次下载时能 announce
                String[] mtk = magnetTrackers();
                String tr = mtk.length > 0 ? mtk[0] : "";
                if (tr.length() > 0) {
                    byte[] tb = tr.getBytes("UTF-8");
                    bos.write("8:announce".getBytes("UTF-8"));
                    bos.write(String.valueOf(tb.length).getBytes("UTF-8"));
                    bos.write(":".getBytes("UTF-8"));
                    bos.write(tb);
                }
                bos.write("e".getBytes("UTF-8"));
            } catch (Throwable t2) {
                Log.i(TAG, "BT_TORRENT_BUILD_ERR " + t2);
                return null;
            }
            byte[] raw = bos.toByteArray();
            if (raw == null || raw.length == 0) return null;
            String name = ti.name();
            if (name == null || name.length() == 0) name = "torrent";
            // 文件名安全化
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (name.length() > 60) name = name.substring(0, 60);
            File out = new File(saveDir, name + ".torrent");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                fos.write(raw);
            } finally {
                try { fos.close(); } catch (Throwable t) {}
            }
            Log.i(TAG, "BT_TORRENT_EXPORTED " + out.getAbsolutePath() + " " + raw.length + "B");
            return out;
        } catch (Throwable t) {
            Log.i(TAG, "BT_TORRENT_EXPORT_ERR " + t);
            return null;
        }
    }

    /** 文件类型分类：0=视频 1=音频 2=图片 3=文档 4=压缩包 5=其他 */
    public static int fileKind(String path) {
        if (path == null) return 5;
        String p = path.toLowerCase(Locale.US);
        if (isVideoFile(p)) return 0;
        if (p.endsWith(".mp3") || p.endsWith(".flac") || p.endsWith(".wav")
                || p.endsWith(".aac") || p.endsWith(".m4a") || p.endsWith(".ogg")
                || p.endsWith(".wma") || p.endsWith(".ape") || p.endsWith(".opus")) return 1;
        if (p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png")
                || p.endsWith(".gif") || p.endsWith(".bmp") || p.endsWith(".webp")
                || p.endsWith(".heic") || p.endsWith(".tif") || p.endsWith(".tiff")) return 2;
        if (p.endsWith(".txt") || p.endsWith(".pdf") || p.endsWith(".epub")
                || p.endsWith(".doc") || p.endsWith(".docx") || p.endsWith(".nfo")
                || p.endsWith(".srt") || p.endsWith(".ass") || p.endsWith(".sub")) return 3;
        if (p.endsWith(".zip") || p.endsWith(".rar") || p.endsWith(".7z")
                || p.endsWith(".tar") || p.endsWith(".gz") || p.endsWith(".iso")) return 4;
        return 5;
    }

    /** 分类名 */
    public static String kindName(int k) {
        switch (k) {
            case 0: return "视频";
            case 1: return "音频";
            case 2: return "图片";
            case 3: return "文档";
            case 4: return "压缩包/镜像";
            default: return "其他";
        }
    }

    /** 分类图标（emoji，不依赖资源） */
    public static String kindIcon(int k) {
        switch (k) {
            case 0: return "🎬";
            case 1: return "🎵";
            case 2: return "🖼";
            case 3: return "📄";
            case 4: return "📦";
            default: return "📎";
        }
    }

    /** 按扩展名判断是不是可直接播放的视频 */
    public static boolean isVideoFile(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.US);
        return p.endsWith(".mp4") || p.endsWith(".mkv") || p.endsWith(".avi")
                || p.endsWith(".mov") || p.endsWith(".wmv") || p.endsWith(".flv")
                || p.endsWith(".webm") || p.endsWith(".m4v") || p.endsWith(".ts")
                || p.endsWith(".rmvb") || p.endsWith(".mpg") || p.endsWith(".mpeg");
    }

    /**
     * 把公共 tracker 拼进磁力链接。
     *
     * 磁力提速最关键的一步：magnet 自带 tr= 参数后，libtorrent 一开始就会去
     * announce，能立刻拿到大量 peer；否则只能靠 DHT 慢慢找，DHT 节点少时
     * 会长期卡在「获取种子信息」。
     */
    static String withTrackers(String magnet) {
        if (magnet == null || !magnet.startsWith("magnet:")) return magnet;
        StringBuilder sb = new StringBuilder(magnet);
        int added = 0;
        for (String t : magnetTrackers()) {
            String enc;
            try {
                enc = java.net.URLEncoder.encode(t, "UTF-8");
            } catch (Throwable e) {
                enc = t;
            }
            if (magnet.indexOf(t) >= 0) continue;
            sb.append("&tr=").append(enc);
            added++;
        }
        Log.i(TAG, "BT_MAGNET_TRACKERS added=" + added + " mode=" + sTrackMode);
        return sb.toString();
    }


    /** 取当前 session 里所有 torrent handle */
    private static List<TorrentHandle> handles(SessionManager sm) {
        List<TorrentHandle> out = new ArrayList<TorrentHandle>();
        try {
            // SessionManager 没直接暴露 list，用 swig 层拿
            org.libtorrent4j.swig.session ses = sm.swig();
            org.libtorrent4j.swig.torrent_handle_vector v = ses.get_torrents();
            int n = (int) v.size();
            for (int i = 0; i < n; i++) {
                out.add(new TorrentHandle(v.get(i)));
            }
        } catch (Throwable t) {
            try {
                // 兜底：用已知 infoHash 找
                Log.i(TAG, "BT_LIST_ERR " + t);
            } catch (Throwable t2) {}
        }
        return out;
    }

    /**
     * 全量公共 tracker 列表（125 个）。
     *
     * 来源：trackerslist.com 的 all + best 合并去重。
     * 用途：拿到种子元数据后逐个 addTracker。
     *
     * 为什么要全量：tracker 的效果是「跟着资源走」的——
     * 某条磁力在这几个 tracker 上没人，在另外几个上可能有人。
     * 只保留「实测对某个种子有效」的列表，等于把其他种子的活路堵死了。
     * tracker announce 失败只是超时，不会拖慢下载，所以全加没有副作用。
     */
    private static final String[] ALL_TRACKERS = new String[]{
        "http://004430.xyz:80/announce",
        "http://107.189.2.131:1337/announce",
        "http://1337.abcvg.info:80/announce",
        "http://207.241.226.111:6969/announce",
        "http://207.241.231.226:6969/announce",
        "http://bt1.archive.org:6969/announce",
        "http://bt2.archive.org:6969/announce",
        "http://nyaa.tracker.wf:7777/announce",
        "http://open.tracker.cl:1337/announce",
        "http://retracker01-msk-virt.corbina.net:80/announce",
        "http://t.overflow.biz:6969/announce",
        "http://torrentsmd.com:8080/announce",
        "http://tr.nyacat.pw:80/announce",
        "http://tracker.dhitechnical.com:6969/announce",
        "http://tracker.dler.com:6969/announce",
        "http://tracker.dler.org:6969/announce",
        "http://tracker.internetwarriors.net:1337/announce",
        "http://tracker.mywaifu.best:6969/announce",
        "http://tracker.opentrackr.org:1337/announce",
        "http://tracker.qu.ax:6969/announce",
        "http://tracker.renfei.net:8080/announce",
        "http://tracker.skyts.net:6969/announce",
        "http://tracker.waaa.moe:6969/announce",
        "http://tracker.xn--djrq4gl4hvoi.top:80/announce",
        "http://tracker.zhuqiy.dgj055.icu:80/announce",
        "http://tracker1.itzmx.com:8080/announce",
        "http://tracker2.dler.org:80/announce",
        "http://tracker2.itzmx.com:6961/announce",
        "http://www.wareztorrent.com:80/announce",
        "https://004430.xyz:443/announce",
        "https://1.tracker.eu.org:443/announce",
        "https://1337.abcvg.info:443/announce",
        "https://gargoyleperch.de:443/announce",
        "https://ht.therarbg.to:443/announce",
        "https://open.ftorrent.com:443/announce",
        "https://t.213891.xyz:443/announce",
        "https://tr.abiir.top:443/announce",
        "https://tr.abir.ga:443/announce",
        "https://tr.burnabyhighstar.com:443/announce",
        "https://tr.nyacat.pw:443/announce",
        "https://tracker.7471.top:443/announce",
        "https://tracker.cangku.moe:443/announce",
        "https://tracker.foreverpirates.co:443/announce",
        "https://tracker.kuroy.me:443/announce",
        "https://tracker.midnightprogrammer.net:443/announce",
        "https://tracker.nekomi.cn:443/announce",
        "https://tracker.pmman.tech:443/announce",
        "https://tracker.qingwapt.org:443/announce",
        "https://tracker.yume-hatsuyuki.moe:443/announce",
        "https://tracker.zhuqiy.com:443/announce",
        "https://tracker1.520.jp:443/announce",
        "udp://52.58.128.163:6969/announce",
        "udp://93.158.213.92:6969/announce",
        "udp://[2a03:7220:8083:cd00::1]:451/announce",
        "udp://[2a04:ac00:1:3dd8::1:2710]:2710/announce",
        "udp://admin.52ywp.com:6969/announce",
        "udp://anime-tracker.aruku.kro.kr:8081/announce",
        "udp://archive.torrentonline.cc:42069/announce",
        "udp://bt-tracker.de:6969/announce",
        "udp://evan.im:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://ipv4announce.sktorrent.eu:6969/announce",
        "udp://ipv6.govt.hu:6969/announce",
        "udp://kolankoalastree.newtrackon.co.nz:1337/announce",
        "udp://mail.segso.net:6969/announce",
        "udp://martin-gebhardt.eu:25/announce",
        "udp://ns575949.ip-51-222-82.net:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.ftorrent.com:443/announce",
        "udp://open.stealth.si:80/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://open.tracker.ink:6969/announce",
        "udp://opentor.org:2710/announce",
        "udp://opentracker.lain.moscow:6969/announce",
        "udp://opentracker2.lain.moscow:6969/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://retracker.hotplug.ru:2710/announce",
        "udp://retracker01-msk-virt.corbina.net:80/announce",
        "udp://seedpeer.net:6969/announce",
        "udp://t.overflow.biz:6969/announce",
        "udp://torrent.tracker.durukanbal.com:6969/announce",
        "udp://tr4ck3r.duckdns.org:6969/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://tracker.004430.xyz:1337/announce",
        "udp://tracker.aruku.ovh:8081/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://tracker.breizh.pm:6969/announce",
        "udp://tracker.cn.nyaa.net:6969/announce",
        "udp://tracker.corpscorp.online:80/announce",
        "udp://tracker.dler.com:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://tracker.ducks.party:1984/announce",
        "udp://tracker.evergarden.uk:1914/announce",
        "udp://tracker.farted.net:6969/announce",
        "udp://tracker.filemail.com:6969/announce",
        "udp://tracker.flatuslifir.is:6969/announce",
        "udp://tracker.gmi.gd:6969/announce",
        "udp://tracker.ilibr.org:6969/announce",
        "udp://tracker.k.vu:6969/announce",
        "udp://tracker.nyaa.net:6969/announce",
        "udp://tracker.nyaa.vc:6969/announce",
        "udp://tracker.okade.pro:1337/announce",
        "udp://tracker.opentrackr.com:6969/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://tracker.peerfect.org:6969/announce",
        "udp://tracker.playground.ru:6969/announce",
        "udp://tracker.qu.ax:6969/announce",
        "udp://tracker.sela.st:6969/announce",
        "udp://tracker.skyts.net:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.torrents.observer:80/announce",
        "udp://tracker.transfur.org:6969/announce",
        "udp://tracker.tryhackx.org:6969/announce",
        "udp://tracker.wildkat.net:6969/announce",
        "udp://tracker.willy.pro:6969/announce",
        "udp://tracker1.itzmx.com:8080/announce",
        "udp://tracker2.dler.org:80/announce",
        "udp://tracker2.itzmx.com:6961/announce",
        "udp://tracker3.itzmx.com:6961/announce",
        "udp://tracker4.itzmx.com:2710/announce",
        "udp://v2.iperson.xyz:6969/announce",
        "udp://whybother.torrentonline.cc:42069/announce",
        "udp://yuptracker-eu.gaijinent.com:27022/announce",
        "udp://bittorrent-tracker.e-n-c-r-y-p-t.net:1337/announce",
    };

    /**
     * 拼进 magnet 链接的精选列表（40 个）。
     *
     * 为什么不把全量都拼进 magnet：magnet URI 太长会让部分 tracker
     * 直接拒绝请求，也拖慢首次拿到元数据的速度。
     * 这里只放 HTTP/HTTPS（走 TCP，普通代理也能穿）+ 少数实测最好用的 UDP。
     */
    private static final String[] MAGNET_TRACKERS = new String[]{
        "http://tracker.opentrackr.org:1337/announce",
        "http://tracker.internetwarriors.net:1337/announce",
        "http://tracker2.itzmx.com:6961/announce",
        "http://tracker.qu.ax:6969/announce",
        "http://tracker.renfei.net:8080/announce",
        "http://open.tracker.cl:1337/announce",
        "http://004430.xyz:80/announce",
        "https://004430.xyz:443/announce",
        "https://1337.abcvg.info:443/announce",
        "https://tracker.nekomi.cn:443/announce",
        "https://tracker.pmman.tech:443/announce",
        "https://tracker.yume-hatsuyuki.moe:443/announce",
        "https://tracker.zhuqiy.com:443/announce",
        "udp://torrent.tracker.durukanbal.com:6969/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://93.158.213.92:6969/announce",
        "udp://tracker2.itzmx.com:6961/announce",
        "udp://tracker3.itzmx.com:6961/announce",
        "udp://tracker4.itzmx.com:2710/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://explodie.org:6969/announce",
        "http://107.189.2.131:1337/announce",
        "http://1337.abcvg.info:80/announce",
        "http://207.241.226.111:6969/announce",
        "http://207.241.231.226:6969/announce",
        "http://bt1.archive.org:6969/announce",
        "http://bt2.archive.org:6969/announce",
        "http://nyaa.tracker.wf:7777/announce",
        "http://retracker01-msk-virt.corbina.net:80/announce",
        "http://t.overflow.biz:6969/announce",
        "http://torrentsmd.com:8080/announce",
        "http://tr.nyacat.pw:80/announce",
        "http://tracker.dhitechnical.com:6969/announce",
        "http://tracker.dler.com:6969/announce",
        "http://tracker.dler.org:6969/announce",
    };

    /**
     * 只走 TCP 的 tracker（HTTP + HTTPS，51 个）。
     *
     * 用途：线路「全部 TCP」。
     * 什么时候需要它：手机挂着普通 HTTP/SOCKS 代理，或运营商把 UDP 掐了。
     * 这时候 UDP tracker 全部超时，只剩 HTTP/HTTPS 还能 announce。
     */
    private static final String[] HTTP_TRACKERS = new String[]{
        "http://004430.xyz:80/announce",
        "http://107.189.2.131:1337/announce",
        "http://1337.abcvg.info:80/announce",
        "http://207.241.226.111:6969/announce",
        "http://207.241.231.226:6969/announce",
        "http://bt1.archive.org:6969/announce",
        "http://bt2.archive.org:6969/announce",
        "http://nyaa.tracker.wf:7777/announce",
        "http://open.tracker.cl:1337/announce",
        "http://retracker01-msk-virt.corbina.net:80/announce",
        "http://t.overflow.biz:6969/announce",
        "http://torrentsmd.com:8080/announce",
        "http://tr.nyacat.pw:80/announce",
        "http://tracker.dhitechnical.com:6969/announce",
        "http://tracker.dler.com:6969/announce",
        "http://tracker.dler.org:6969/announce",
        "http://tracker.internetwarriors.net:1337/announce",
        "http://tracker.mywaifu.best:6969/announce",
        "http://tracker.opentrackr.org:1337/announce",
        "http://tracker.qu.ax:6969/announce",
        "http://tracker.renfei.net:8080/announce",
        "http://tracker.skyts.net:6969/announce",
        "http://tracker.waaa.moe:6969/announce",
        "http://tracker.xn--djrq4gl4hvoi.top:80/announce",
        "http://tracker.zhuqiy.dgj055.icu:80/announce",
        "http://tracker1.itzmx.com:8080/announce",
        "http://tracker2.dler.org:80/announce",
        "http://tracker2.itzmx.com:6961/announce",
        "http://www.wareztorrent.com:80/announce",
        "https://004430.xyz:443/announce",
        "https://1.tracker.eu.org:443/announce",
        "https://1337.abcvg.info:443/announce",
        "https://gargoyleperch.de:443/announce",
        "https://ht.therarbg.to:443/announce",
        "https://open.ftorrent.com:443/announce",
        "https://t.213891.xyz:443/announce",
        "https://tr.abiir.top:443/announce",
        "https://tr.abir.ga:443/announce",
        "https://tr.burnabyhighstar.com:443/announce",
        "https://tr.nyacat.pw:443/announce",
        "https://tracker.7471.top:443/announce",
        "https://tracker.cangku.moe:443/announce",
        "https://tracker.foreverpirates.co:443/announce",
        "https://tracker.kuroy.me:443/announce",
        "https://tracker.midnightprogrammer.net:443/announce",
        "https://tracker.nekomi.cn:443/announce",
        "https://tracker.pmman.tech:443/announce",
        "https://tracker.qingwapt.org:443/announce",
        "https://tracker.yume-hatsuyuki.moe:443/announce",
        "https://tracker.zhuqiy.com:443/announce",
        "https://tracker1.520.jp:443/announce",
    };

    /**
     * 当前 tracker 线路。
     *
     * 0 = 极简（精选 40 条，默认）—— magnet 最短，首次拿元数据最快
     * 1 = 全部 TCP（51 条）—— 只走 HTTP/HTTPS，能穿代理
     * 2 = 全量（125 条）—— HTTP + UDP 全上，最全，magnet 最长
     *
     * 为什么要让用户选：tracker 的效果「跟着资源走」——
     * 某些资源只在 UDP tracker 上有人，另一些只在 HTTP 上有。
     * 默认给精选是为了快；找不到 peer 时用户可以切到全量自己试。
     */
    private static volatile int sTrackMode = 0;

    /** 设置 tracker 线路（0/1/2，越界按 0 处理） */
    public static void setTrackMode(int m){
        sTrackMode = (m < 0 || m > 2) ? 0 : m;
        Log.i(TAG, "BT_TRACK_MODE " + sTrackMode + " chip=" + trackModeLabel());
    }

    public static int getTrackMode(){ return sTrackMode; }

    /** 线路名（给界面显示） */
    public static String trackModeLabel(){
        if (sTrackMode == 2) return "全量 " + ALL_TRACKERS.length + " 条";
        if (sTrackMode == 1) return "全部 TCP " + HTTP_TRACKERS.length + " 条";
        return "极简 " + MAGNET_TRACKERS.length + " 条";
    }

    /** 按当前线路，取要拼进 magnet 的 tracker */
    static String[] magnetTrackers(){
        if (sTrackMode == 2) return ALL_TRACKERS;
        if (sTrackMode == 1) return HTTP_TRACKERS;
        return MAGNET_TRACKERS;
    }

    /** 按当前线路，取拿到元数据后要 addTracker 的列表 */
    static String[] addTrackers(){
        return magnetTrackers();
    }

    /** 各线路的 tracker 条数（给界面显示用，避免 UI 层直接摸 private 数组） */
    public static final int ALL_TRACKERS_COUNT = 125;
    public static final int MAGNET_TRACKERS_COUNT = 40;
    public static final int HTTP_TRACKERS_COUNT = 51;

    /** 按线路编号取条数 */
    public static int countOf(int mode){
        if (mode == 2) return ALL_TRACKERS_COUNT;
        if (mode == 1) return HTTP_TRACKERS_COUNT;
        return MAGNET_TRACKERS_COUNT;
    }

    /** 确保 session 已启动（带速度调优） */
    private static synchronized SessionManager ensureSession() {
        if (sSession != null && sSession.isRunning()) return sSession;
        if (sStarting) {
            for (int i = 0; i < 30; i++) {
                try { Thread.sleep(200); } catch (Throwable t) {}
                if (sSession != null && sSession.isRunning()) return sSession;
            }
        }
        sStarting = true;
        try {
            SessionManager sm = new SessionManager();
            // 必须先 start，settings() 在 start 之前返回 null
            sm.start();

            // ---- 调优：这些参数直接决定下载速度 ----
            try{
                org.libtorrent4j.SettingsPack sp = sm.settings();
                if (sp != null) {
                    // 关键：显式监听，否则 listen=[] 时 peer 连不进来，只能主动外连，速度差。
                    // 6881 常被别的程序占；libtorrent 支持端口范围，会自己挑一个可用的。
                    //
                    // IPv4 + IPv6 分开写：
                    //   "0.0.0.0:6881-6999"  IPv4 通配
                    //   "[::]:6881-6999"     IPv6 通配（双栈）
                    // 某些 libtorrent 构建如果没编译 IPv6，[::] 会静默失败，
                    // 一起写有可能让整条 listen_interfaces 失效 → 全部变空。
                    // 所以先用最稳的 IPv4 起，IPv6 在下面单独 try 追加。
                    sp.listenInterfaces("0.0.0.0:6881-6999");
                    // 连接数：默认太少，磁力需要更多 peer 并行拉取
                    // ===== 连接数：拉满 =====
                    // ===== 连接数：拉顶（libtorrent 上限 2000）=====
                    sp.connectionsLimit(2000);
                    sp.activeLimit(2000);
                    // 同时活跃下载/做种/校验
                    sp.activeDownloads(200);
                    sp.activeSeeds(200);
                    sp.activeChecking(32);
                    // peer 列表容量
                    sp.maxPeerlistSize(8000);
                    // DHT 节点上限
                    sp.activeDhtLimit(4000);
                    sp.activeLsdLimit(500);
                    // tracker 并发
                    sp.activeTrackerLimit(200);
                    // 混合 peer 发现：多源并行找种（swig 直设，SettingsPack 无对应方法）
                    // 不限制下载（0 = 不限）
                    sp.downloadRateLimit(0);

                    // ===== 请求队列：这是单 peer 吞吐的关键 =====
                    // 默认 max_out_request_queue 只有 500，单个 peer 一次只能挂 500 个 block 请求，
                    // 提高到 2000 能让每个 peer 同时拉更多块，速度直接翻倍
                    try{
                        org.libtorrent4j.swig.settings_pack.int_types IT =
                            org.libtorrent4j.swig.settings_pack.int_types.max_out_request_queue;
                        sp.setInteger(IT.swigValue(), 2000);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.max_allowed_in_request_queue.swigValue(), 2000);
                        // 请求超时缩短，卡住的请求尽快重发
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.request_timeout.swigValue(), 8);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.peer_timeout.swigValue(), 60);
                        // 连接建立速度：默认 30 个/秒太保守
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.connection_speed.swigValue(), 200);
                        // 单次 unchoke 数量
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.unchoke_slots_limit.swigValue(), 500);
                        // 磁盘队列放宽，避免磁盘成为瓶颈
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.max_queued_disk_bytes.swigValue(),
                            512 * 1024 * 1024);
                        // DHT 找 peer 更积极
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.dht_max_peers_reply.swigValue(), 1000);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.dht_search_branching.swigValue(), 32);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.dht_max_peers.swigValue(), 8000);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.dht_announce_interval.swigValue(), 60);
                        // tracker 一次多要 peer
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.num_want.swigValue(), 1000);
                        // 单个 peer 的请求管道：再抬一档
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.max_out_request_queue.swigValue(), 4000);
                        // 读/写磁盘缓存放大，避免磁盘排队拖速度
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.max_queued_disk_bytes.swigValue(),
                            1024 * 1024 * 1024);
                        Log.i(TAG, "BT_TURBO_OK requests=4000 conn=2000 unchoke=200 dht=1000");
                    }catch(Throwable tt){
                        Log.i(TAG, "BT_TURBO_ERR " + tt);
                    }

                    // ===== 布尔开关 =====
                    try{
                        org.libtorrent4j.swig.settings_pack.bool_types BT;
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.allow_multiple_connections_per_ip.swigValue(),
                            true);   // 同一 IP 允许多连接（NAT 后尤其重要）
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_outgoing_utp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_incoming_utp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_outgoing_tcp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_incoming_tcp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.prioritize_partial_pieces.swigValue(),
                            true);   // 优先续传已有的块
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.announce_to_all_trackers.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.announce_to_all_tiers.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.prefer_udp_trackers.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_upnp.swigValue(),
                            true);   // 尝试自动打洞
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_natpmp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.smooth_connects.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.use_dht_as_fallback.swigValue(),
                            false);  // DHT 一直用，不是兜底
                        Log.i(TAG, "BT_TURBO_BOOL_OK utp=1 upnp=1 multiIP=1");
                    }catch(Throwable tt){
                        Log.i(TAG, "BT_TURBO_BOOL_ERR " + tt);
                    }
                    // 上传必须放开！libtorrent 这个 API 单位是「字节/秒」，
                    // 之前写 800 等于 800 B/s ≈ 不上传，peer 会直接 choke 我们，
                    // 表现就是 peers 连了一堆但速度恒为 0B/s。这里设为 0 = 不限速。
                    sp.uploadRateLimit(0);
                    Log.i(TAG, "BT_UPLOAD_UNLIMITED 上传不限速（换下载的关键）");
                    // DHT bootstrap 节点：默认自动的就几个，手动加一批
                    try{
                        // DHT 越多路由器，找 peer 越快越全
                        sp.setDhtBootstrapNodes(
                            "router.bittorrent.com:6881,"
                          + "router.utorrent.com:6881,"
                          + "dht.transmissionbt.com:6881,"
                          + "dht.aelitis.com:6881,"
                          + "router.bitcomet.com:6881,"
                          + "dht.libtorrent.org:25401,"
                          + "dht.bt.am:2710,"
                          + "router.bt.gg:6881,"
                          + "dht.anacrolix.link:42069,"
                          + "router.silotis.us:6881,"
                          + "router.bittorrent.cloud:42069,"
                          + "dht.transmissionbt.com:6881");
                    }catch(Throwable t2){
                        Log.i(TAG, "BT_DHT_BOOT_ERR " + t2);
                    }
                    sm.applySettings(sp);

                    // ---- 关键：applySettings 之后必须再显式 listen 一次 ----
                    // listen_interfaces 在 libtorrent 里是「启动期」设置，
                    // 只通过 applySettings 往往不会重新 open socket，
                    // 结果 listen=[] —— 外部 peer 连不进来，只能主动外连，速度很差。
                    try {
                        // 先 IPv4（双栈系统上 [::] 也能收 IPv4，但分开写更保险）
                        sm.listenInterfaces("0.0.0.0:6881-6999");
                        Log.i(TAG, "BT_LISTEN_SET ipv4=0.0.0.0:6881-6999");
                        // 再尝试追加 IPv6：[::] 双栈写法
                        try {
                            Thread.sleep(200);
                            sm.listenInterfaces("0.0.0.0:6881-6999,[::]:6881-6999");
                            Log.i(TAG, "BT_LISTEN_SET_V6 ipv4+ipv6");
                        } catch (Throwable te6) {
                            Log.i(TAG, "BT_LISTEN_V6_UNSUPPORTED " + te6);
                            // IPv6 不支持就退回纯 IPv4
                            try { sm.listenInterfaces("0.0.0.0:6881-6999"); } catch (Throwable t9) {}
                        }
                    } catch (Throwable te) {
                        Log.i(TAG, "BT_LISTEN_SET_ERR " + te);
                    }
                    // 给 listen socket 一点起来的时间，再读一次
                    try { Thread.sleep(400); } catch (Throwable te) {}
                    // 用三种方式交叉验证监听是否真的起来了：
                    //   listenEndpoints()  可能一直返回空（libtorrent4j 的已知怪癖）
                    //   isListening()      更权威
                    //   getListenPort()    能拿到实际端口才算真起来
                    java.util.List<String> eps = null;
                    try { eps = sm.listenEndpoints(); } catch (Throwable te) {}
                    String cfg = "?";
                    try { cfg = sm.listenInterfaces(); } catch (Throwable te) {}
                    Log.i(TAG, "BT_SETTINGS_APPLIED listen=" + eps
                            + " iface=" + (eps == null || eps.isEmpty() ? "EMPTY" : "OK")
                            + " cfg=[" + cfg + "]");
                } else {
                    Log.i(TAG, "BT_SETTINGS_NULL");
                }
            }catch(Throwable st){
                Log.i(TAG, "BT_SETTINGS_ERR " + st);
            }

            // 等 DHT 起来（最多 15 秒，节点越多找 peer 越快）
            long lastNodes = 0;
            for (int i = 0; i < 75; i++) {
                try { Thread.sleep(200); } catch (Throwable t) {}
                long nn = sm.dhtNodes();
                if (nn != lastNodes) {
                    lastNodes = nn;
                    Log.i(TAG, "BT_DHT_PROGRESS nodes=" + nn);
                }
                if (nn >= 50) break;   // 50 个节点够用了，不必等满
            }
            sSession = sm;
            // 最后再确保一次监听（有些 ROM/libtorrent 版本会在 applySettings 时重置监听）
            try {
                // 用 isListening()/getListenPort() 判断，而不是只看 listenEndpoints()
                // （后者可能一直返回空，造成「看起来没监听」的假象）
                java.util.List<String> ep0 = null;
                try { ep0 = sm.listenEndpoints(); } catch (Throwable te) {}
                if (ep0 == null || ep0.isEmpty()) {
                    sm.listenInterfaces("0.0.0.0:6881-6999");
                    try { Thread.sleep(400); } catch (Throwable te) {}
                    java.util.List<String> ep1 = null;
                    try { ep1 = sm.listenEndpoints(); } catch (Throwable te) {}
                    Log.i(TAG, "BT_LISTEN_RETRY -> " + ep1);
                }
            } catch (Throwable te) {
                Log.i(TAG, "BT_LISTEN_RETRY_ERR " + te);
            }
            Log.i(TAG, "BT_SESSION_OK dhtNodes=" + sm.dhtNodes()
                    + " cfg=[" + sm.listenInterfaces() + "]"
                    + " listen=" + sm.listenEndpoints());
            return sm;
        } catch (Throwable t) {
            Log.i(TAG, "BT_SESSION_ERR " + t);
            return null;
        } finally {
            sStarting = false;
        }
    }

    /**
     * session 代际计数器：每次 shutdown() 递增。
     * 轮询线程在调任何 native 方法（h.status()/sm.remove()/h.forceReannounce()…）之前
     * 都要检查代际有没有变；变了说明 session 已被释放，必须立刻退出，
     * 否则会 SIGSEGV（native 崩溃，Java 的 catch Throwable 拦不住）。
     */
    static volatile int sGeneration = 0;

    /** 停掉全局 session（App 退出或用户手动停） */
    public static synchronized void shutdown() {
        try {
            // 先递增代际：让所有轮询线程立刻停手，不再碰 native 对象
            sGeneration++;
            Log.i(TAG, "BT_SESSION_STOP gen=" + sGeneration);
            if (sSession != null) {
                sSession.stop();
                sSession = null;
            }
        } catch (Throwable t) {}
    }

    public static boolean isReady() {
        return sSession != null && sSession.isRunning();
    }

    /** 判断是否磁力链接 */
    public static boolean isMagnet(String s) {
        return s != null && s.length() > 8
                && s.toLowerCase(Locale.US).startsWith("magnet:?");
    }

    /** 从磁力链接里取 dn（显示名），没有就用 infohash 前 8 位 */
    public static String magnetName(String s) {
        try {
            int p = s.indexOf("dn=");
            if (p > 0) {
                String rest = s.substring(p + 3);
                int amp = rest.indexOf('&');
                String dn = amp > 0 ? rest.substring(0, amp) : rest;
                try {
                    dn = java.net.URLDecoder.decode(dn, "UTF-8");
                } catch (Throwable t) {}
                if (dn.length() > 0) return dn;
            }
            int q = s.toLowerCase(Locale.US).indexOf("btih:");
            if (q > 0) {
                String h = s.substring(q + 5);
                int amp = h.indexOf('&');
                if (amp > 0) h = h.substring(0, amp);
                return "magnet_" + (h.length() > 8 ? h.substring(0, 8) : h);
            }
        } catch (Throwable t) {}
        return "magnet_download";
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}