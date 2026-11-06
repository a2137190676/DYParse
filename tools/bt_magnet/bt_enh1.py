# -*- coding: utf-8 -*-
# BtDownloader 增强三件事：
#   1) 保留 .torrent 种子文件（拿到元数据后导出到保存目录）
#   2) 文件选择（prioritizeFiles，只下用户勾的）
#   3) 边下边播（setSequentialRange 顺序下载 + 暴露第一个视频文件路径）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

# ---------- 1) Cb 接口扩展 ----------
old_cb = '''    public interface Cb {
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
    }'''
new_cb = '''    public interface Cb {
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
    }'''
if old_cb in d:
    d = d.replace(old_cb, new_cb, 1); print('Cb 扩展 OK')
else:
    print('MISS Cb')

io.open(P, 'w', encoding='utf-8').write(d)
print('step1 done')