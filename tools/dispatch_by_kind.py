# -*- coding: utf-8 -*-
"""
批量下载入口改成「按解析阶段定好的 kind 分派」。

【现状】
  stepBatchProfileDl 一进来就是一大串 if(it.isImage) / directUrl / detail，
  靠运行时反复试探，音乐作品、无直链、图集全混在一条判断链里。

【改成】
  进来先看 it.kind：
    KIND_MUSIC → 直接跳过（明确计数，不发任何请求）
    KIND_IMAGE / KIND_LIVE → 走图集分支（现有逻辑不动）
    KIND_VIDEO → 走直链 / detail
    KIND_UNKNOWN → 保持原有兜底链

  这样解析阶段已经判好的结论直接生效，不再重复试探。
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


rep('1-kind分派',
    '''        if(u.length() == 0){
            DypLog.i("BATCH_DL_SKIP_NO_URL idx=" + batchDlIndex);
            batchDlIndex++;
            stepBatchProfileDl();
            return;
        }''',
    '''        if(u.length() == 0){
            DypLog.i("BATCH_DL_SKIP_NO_URL idx=" + batchDlIndex);
            batchDlIndex++;
            stepBatchProfileDl();
            return;
        }
        // ============================================================
        // 【按解析阶段定好的类型直接分派】
        //
        // 解析主页时已经把每条判成 VIDEO/IMAGE/LIVE/MUSIC 了：
        //   PROF_KIND id=... kind=4(音乐(无视频))
        // 这里不再靠运行时试探，直接按结论走，
        // 音乐作品一次请求都不发（以前要白打一次 detail + 一次风控）。
        // ============================================================
        if(it.kind == ProfileParser.ProfileItem.KIND_MUSIC){
            batchDlMusic++;
            DypLog.i("BATCH_DL_KIND_MUSIC idx=" + batchDlIndex + " id=" + it.id
                    + " 解析阶段已判为音乐作品，跳过");
            batchDlIndex++;
            s("批量下载 " + batchDlIndex + "/" + batchDlTotal + "：音乐作品已跳过");
            h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 60);
            return;
        }
        if(it.kind == ProfileParser.ProfileItem.KIND_IMAGE
                || it.kind == ProfileParser.ProfileItem.KIND_LIVE){
            DypLog.i("BATCH_DL_KIND_IMG idx=" + batchDlIndex + " kind=" + it.kind
                    + " n=" + (it.imgs == null ? 0 : it.imgs.size()));
            // 落到下面的图集分支（it.isImage 也是 true）
        }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)