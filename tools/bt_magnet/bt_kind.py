# -*- coding: utf-8 -*-
# 磁力文件选择：分组 + 过滤 + 按类型全选
#   1) BtDownloader 加文件分类工具
#   2) MainActivity 的 onSelectFiles 换成自建 ListView 弹窗（支持动态过滤）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

anchor = '''    /** 按扩展名判断是不是可直接播放的视频 */'''
helper = '''    /** 文件类型分类：0=视频 1=音频 2=图片 3=文档 4=压缩包 5=其他 */
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

''' + anchor

if anchor in d and 'public static int fileKind' not in d:
    d = d.replace(anchor, helper, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('fileKind/kindName/kindIcon OK')
else:
    print('MISS or exists')