# -*- coding: utf-8 -*-
"""
主页解析改回「WebView 拦截优先」，签名翻页降级为兜底。

【为什么要改】
  现在走的是「签名翻页接管」：
      dyProfileSignedOwned = true  → WebView 抓到的列表被直接丢弃
      → 全靠 DyDetail.buildPostQuery + ABogus 自己发请求

  实测结果（36 条主页）：
      PROF_DBG  id=... bit_rate=null
      PROF_ITEM_AUDIO id=... 拿到的是音频地址
          url=https://sf11-cdn-tos.douyinstatic.com/obj/ies-music/7210597696699206455.mp3
  → 我们拼的 aweme/post 请求，抖音只回「壳数据」：
      video 字段在，但 bit_rate 没了，play_addr 指向背景音乐 mp3，
      真视频流（douyinvod）全被剥离。
  → 于是 directUrl 全空 → 批量下载回落 detail → detail 要 uifid → 403 → 0/4

  而 WebView 那套是「抖音自己的网页自己算签名、自己发请求」，
  页面拿到的是完整数据结构（含 douyinvod 真链），我们只做旁路拦截。

【改法】
  1. enterProfileMode 里不再预设 dyProfileSignedOwned = true，
     改成：WebView 先自己收集 + 自动滚动；
  2. onProfileDataFromWeb 不再丢弃 aweme_list（WebView 优先）；
  3. WebView 收集 N 秒后，如果条数太少（< 5），才启动签名翻页兜底。
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
# 1. enterProfileMode：不再让签名翻页抢占
# ============================================================
rep('1-不抢占',
    '''                if(sec != null && sec.length() > 10){
                    DypLog.i("PROFILE_KICK_SIGNED 确认后主动翻页");
                    new Thread(new Runnable(){ public void run(){
                        try{ fetchAllDyProfilePages(uu2); }
                        catch(Throwable t){ DypLog.i("PROFILE_KICK_ERR " + t); }
                    }}).start();
                }''',
    '''                if(sec != null && sec.length() > 10){
                    // 【策略变更】以前这里直接让签名翻页接管，
                    // 结果我们拼的 aweme/post 只拿到壳数据（play_addr 是背景音乐 mp3），
                    // 真视频直链全被抖音剥离。
                    //
                    // 现在改回 WebView 优先：让抖音网页自己算签名、自己发请求，
                    // 我们旁路拦截完整数据。只有在 WebView 收不到东西时，
                    // 才退回签名翻页兜底。
                    DypLog.i("PROFILE_WEB_FIRST 改为 WebView 优先收集");
                    // 页面已经在加载了，这里只负责「启动自动滚动 + 兜底计时」
                    h.postDelayed(new Runnable(){ public void run(){
                        if(!profileMode) return;
                        try{ startDyProfileScroll(); }catch(Throwable t){}
                    }}, 600);
                    // 兜底：12 秒后如果 WebView 一条都没收到，才启动签名翻页
                    h.postDelayed(new Runnable(){ public void run(){
                        if(!profileMode) return;
                        int have;
                        synchronized(profileCollected){ have = profileCollected.size(); }
                        if(have >= 5){
                            DypLog.i("PROFILE_WEB_OK WebView 已收集 " + have + " 条，无需签名兜底");
                            return;
                        }
                        DypLog.i("PROFILE_WEB_FEW WebView 只有 " + have + " 条，启动签名翻页兜底");
                        new Thread(new Runnable(){ public void run(){
                            try{ fetchAllDyProfilePages(uu2); }
                            catch(Throwable t){ DypLog.i("PROFILE_KICK_ERR " + t); }
                        }}).start();
                    }}, 12000);
                }''')

# ============================================================
# 2. WebView 数据不再丢弃
# ============================================================
rep('2-不丢弃WebView',
    '''        // 抖音主页已由签名翻页接管，WebView 自己抓到的数据会重复混进来（数量虚高、易重复下载）
        if(dyProfileSignedOwned && body != null && body.indexOf("aweme_list") >= 0){
            DypLog.i("DYPROF_WEB_DROP 签名翻页已接管，丢弃 WebView 抓到的列表 len=" + body.length());
            return;
        }''',
    '''        // 【策略变更】以前这里丢弃 WebView 抓到的 aweme_list（因为签名翻页接管了）。
        // 但签名翻页拿到的 aweme/post 是壳数据（play_addr 全是背景音乐 mp3），
        // 真视频直链只有 WebView 拿得到。所以现在反向：WebView 的数据优先收，
        // 只有「签名翻页已经拿到了结果」并且「WebView 这份明显更少」时才丢弃。
        if(dyProfileSignedOwned && body != null && body.indexOf("aweme_list") >= 0){
            int have;
            synchronized(profileCollected){ have = profileCollected.size(); }
            // 签名翻页已经在跑、而且已经有结果了 → 这份重复，丢掉
            if(have >= 5){
                DypLog.i("DYPROF_WEB_DROP 签名翻页已出结果(" + have + ")，丢弃重复 len=" + body.length());
                return;
            }
            DypLog.i("DYPROF_WEB_TAKE 签名翻页还没结果，先收 WebView 这份 len=" + body.length());
        }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)