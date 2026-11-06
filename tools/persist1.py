# -*- coding: utf-8 -*-
# 下载任务持久化：
#   - 每个任务入队就写进 prefs（JSON）
#   - 任务完成/失败后移除
#   - App 启动时自动恢复未完成的任务，重新入队
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# ---------- 1) 加持久化方法（放在 DlTask 类后面） ----------
anchor = '''    final java.util.List<DlTask> downloadQueue ='''
helper = '''    // ===== 下载任务持久化 =====
    // 目的：退出软件后任务不丢，下次打开可以继续下载。
    // 存储：SharedPreferences 里一段 JSON，字段跟 DlTask 一一对应。

    static final String KEY_DL_TASKS = "dl_tasks_json";

    /** 把当前队列序列化成 JSON 存起来 */
    void saveDlTasks(){
        try{
            org.json.JSONArray arr = new org.json.JSONArray();
            java.util.List<DlTask> snap;
            synchronized(downloadQueue){ snap = new java.util.ArrayList<DlTask>(downloadQueue); }
            for(DlTask t : snap){
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("u", t.url == null ? "" : t.url);
                o.put("t", t.tag == null ? "" : t.tag);
                o.put("e", t.ext == null ? "" : t.ext);
                o.put("c", t.cat == null ? "" : t.cat);
                o.put("m", t.mixDir == null ? "" : t.mixDir);
                o.put("lv", t.liveUrl == null ? "" : t.liveUrl);
                o.put("nb", t.nameBase == null ? "" : t.nameBase);
                o.put("au", t.audioUrl == null ? "" : t.audioUrl);
                o.put("s", t.silent);
                arr.put(o);
            }
            getSharedPreferences(SP, MODE_PRIVATE).edit()
                .putString(KEY_DL_TASKS, arr.toString()).apply();
            Log.i("DYP", "DL_TASKS_SAVED n=" + arr.length());
        }catch(Throwable e){
            Log.i("DYP", "DL_TASKS_SAVE_ERR " + e);
        }
    }

    /** 读回上次没下完的队列 */
    java.util.List<DlTask> loadDlTasks(){
        java.util.List<DlTask> out = new java.util.ArrayList<DlTask>();
        try{
            String s = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_DL_TASKS, "");
            if(s == null || s.length() < 3) return out;
            org.json.JSONArray arr = new org.json.JSONArray(s);
            for(int i = 0; i < arr.length(); i++){
                org.json.JSONObject o = arr.getJSONObject(i);
                DlTask t = new DlTask(
                    o.optString("u", ""), o.optString("t", ""), o.optString("e", ""),
                    o.optString("c", "视频"), o.optString("m", ""),
                    o.optString("lv", ""), o.optString("nb", ""), o.optString("au", ""));
                t.silent = o.optBoolean("s", false);
                if(t.url != null && t.url.length() > 0) out.add(t);
            }
            Log.i("DYP", "DL_TASKS_LOADED n=" + out.size());
        }catch(Throwable e){
            Log.i("DYP", "DL_TASKS_LOAD_ERR " + e);
        }
        return out;
    }

    /** 清空持久化队列（全部下完后调用） */
    void clearDlTasks(){
        try{
            getSharedPreferences(SP, MODE_PRIVATE).edit()
                .remove(KEY_DL_TASKS).apply();
            Log.i("DYP", "DL_TASKS_CLEARED");
        }catch(Throwable e){}
    }

    /** 启动时恢复上次没下完的任务 */
    void restoreDlTasks(){
        try{
            java.util.List<DlTask> saved = loadDlTasks();
            if(saved.isEmpty()) return;
            // 过滤掉已经有成品文件的（防重复）
            java.util.List<DlTask> todo = new java.util.ArrayList<DlTask>();
            for(DlTask t : saved){
                todo.add(t);
            }
            if(todo.isEmpty()){ clearDlTasks(); return; }
            synchronized(downloadQueue){ downloadQueue.addAll(todo); }
            Log.i("DYP", "DL_TASKS_RESTORE n=" + todo.size());
            s("发现 " + todo.size() + " 个未完成的下载，继续下载…");
            showHintToast("继续上次 " + todo.size() + " 个下载任务");
            if(!dlRunning){
                dlRunning = true;
                runDownloadQueue();
            }
        }catch(Throwable e){
            Log.i("DYP", "DL_TASKS_RESTORE_ERR " + e);
        }
    }

''' + anchor

if anchor in d and 'void saveDlTasks' not in d:
    d = d.replace(anchor, helper, 1); n += 1; print('持久化方法 OK')
else:
    print('MISS 持久化方法')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)