# -*- coding: utf-8 -*-
# BtDownloader 主体改造：
#   拿到元数据后 -> 导出 .torrent -> 让用户选文件 -> prioritizeFiles -> 顺序下载
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

# 找 onMeta 段并替换整块
old = '''                    // 文件列表（只发一次）
                    if (!metaSent[0]) {
                        List<String> names = new ArrayList<String>();
                        List<Long> sizes = new ArrayList<Long>();
                        long total = 0;
                        try {
                            TorrentInfo ti = h.torrentFile();
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
                        cb.onMeta(names, sizes, total);
                        metaSent[0] = true;
                    }'''

new = '''                    // 文件列表（只发一次）
                    if (!metaSent[0]) {
                        metaSent[0] = true;
                        List<String> names = new ArrayList<String>();
                        List<Long> sizes = new ArrayList<Long>();
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
                            try { sm.remove(h); } catch (Throwable te) {}
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

                        cb.onMeta(names, sizes, total);
                    }'''

if old in d:
    d = d.replace(old, new, 1); print('主流程改造 OK')
else:
    print('MISS 主流程')
    i = d.find('// 文件列表（只发一次）')
    print(repr(d[i:i+300]))

io.open(P, 'w', encoding='utf-8').write(d)
print('done')