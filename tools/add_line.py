# -*- coding: utf-8 -*-
"""给 BtDownloader 加「三条线路」支持。

现状：
  ALL_TRACKERS     125 个（全量）
  MAGNET_TRACKERS  40 个（精选，magnet 用）

需求：让用户自己选线路，不要固定只用 40 个。

设计三档（都存在，用户切换）：
  0 极简（默认）  精选 40 条        magnet 短、解析快，够用
  1 全部 TCP      所有 HTTP/HTTPS  只走 TCP，能穿普通代理；UDP 被掐时更稳
  2 全量          125 全部          HTTP + UDP 全上，最全、magnet 最长

实现：
  新增 HTTP_TRACKERS（从 ALL_TRACKERS 里筛 http:// https://）
  新增 sTrackMode + setTrackMode/getTrackMode
  新增 magnetTrackers()/addTrackers() —— 按线路返回数组
  withTrackers()、addTracker 段、exportTorrent 都改成走这两个函数
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
s = io.open(P, encoding='utf-8').read()

# ---------- 1) 抽出 ALL_TRACKERS 的实际条目 ----------
start = s.find('    private static final String[] ALL_TRACKERS = new String[]{')
if start < 0:
    raise SystemExit('找不到 ALL_TRACKERS')
body_start = s.find('{', start) + 1
body_end = s.find('    };', body_start)
all_body = s[body_start:body_end]
entries = []
for line in all_body.split('\n'):
    t = line.strip().rstrip(',').strip()
    if t.startswith('"') and t.endswith('"'):
        entries.append(t[1:-1])
if len(entries) < 100:
    raise SystemExit('ALL_TRACKERS 数量异常: %d' % len(entries))
print('ALL_TRACKERS:', len(entries))

http_tk = [t for t in entries if t.startswith(('http://', 'https://'))]
udp_tk = [t for t in entries if t.startswith('udp://')]
print('  http/https:', len(http_tk), ' udp:', len(udp_tk))

# 抽出 MAGNET_TRACKERS 条目数
_m_start = s.find('    private static final String[] MAGNET_TRACKERS = new String[]{')
_m_body_start = s.find('{', _m_start) + 1
_m_body_end = s.find('    };', _m_body_start)
magnet_list = []
for line in s[_m_body_start:_m_body_end].split('\n'):
    t = line.strip().rstrip(',').strip()
    if t.startswith('"') and t.endswith('"'):
        magnet_list.append(t[1:-1])
print('MAGNET_TRACKERS:', len(magnet_list))


def java_arr(items, indent='        '):
    return '\n'.join('%s"%s",' % (indent, t) for t in items)


# ---------- 2) 在 MAGNET_TRACKERS 后面插入 HTTP_TRACKERS + 线路逻辑 ----------
# 找 MAGNET_TRACKERS 块的结束（858 行那个 "    };"）
m_start = s.find('    private static final String[] MAGNET_TRACKERS = new String[]{')
if m_start < 0:
    raise SystemExit('找不到 MAGNET_TRACKERS')
m_end = s.find('    };\n', m_start)
if m_end < 0:
    raise SystemExit('找不到 MAGNET_TRACKERS 结束')

insert_at = m_end + len('    };\n')

extra = '''
    /**
     * 只走 TCP 的 tracker（HTTP + HTTPS，%d 个）。
     *
     * 用途：线路「全部 TCP」。
     * 什么时候需要它：手机挂着普通 HTTP/SOCKS 代理，或运营商把 UDP 掐了。
     * 这时候 UDP tracker 全部超时，只剩 HTTP/HTTPS 还能 announce。
     */
    private static final String[] HTTP_TRACKERS = new String[]{
%s
    };

    /**
     * 当前 tracker 线路。
     *
     * 0 = 极简（精选 %d 条，默认）—— magnet 最短，首次拿元数据最快
     * 1 = 全部 TCP（%d 条）—— 只走 HTTP/HTTPS，能穿代理
     * 2 = 全量（%d 条）—— HTTP + UDP 全上，最全，magnet 最长
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
''' % (len(http_tk), java_arr(http_tk), len(magnet_list),
       len(http_tk), len(entries))

s = s[:insert_at] + extra + s[insert_at:]

# ---------- 3) withTrackers 改用 magnetTrackers() ----------
s = s.replace(
    '''        for (String t : MAGNET_TRACKERS) {
            String enc;''',
    '''        for (String t : magnetTrackers()) {
            String enc;''', 1)

s = s.replace(
    '''        Log.i(TAG, "BT_MAGNET_TRACKERS added=" + added);''',
    '''        Log.i(TAG, "BT_MAGNET_TRACKERS added=" + added + " mode=" + sTrackMode);''', 1)

# ---------- 4) addTracker 段改用 addTrackers() ----------
s = s.replace(
    '''                            for (String t : ALL_TRACKERS) tks.add(t);''',
    '''                            for (String t : addTrackers()) tks.add(t);''', 1)

# 打印加了几个 tracker 的日志也带上线路
s = s.replace(
    '''Log.i(TAG, "BT_TRACKERS_ADDED n="''',
    '''Log.i(TAG, "BT_TRACKERS_ADDED mode=" + sTrackMode + " n="''', 1)

# ---------- 5) exportTorrent 里的引用 ----------
s = s.replace(
    '''                String tr = MAGNET_TRACKERS.length > 0 ? MAGNET_TRACKERS[0] : "";''',
    '''                String[] mtk = magnetTrackers();
                String tr = mtk.length > 0 ? mtk[0] : "";''', 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('done')
print('HTTP_TRACKERS 数量:', s.count('HTTP_TRACKERS'))
print('setTrackMode 存在:', 'setTrackMode' in s)
