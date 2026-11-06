# -*- coding: utf-8 -*-
"""
【3】【4】目录选择改用系统自带选择器 + 修「不能返回上一级」。

【用户反馈】
  · 我们的目录选择器只能进下一级，不能返回上一级（截图 2 卡在 /storage/emulated/0/AITerminal）
  · 为什么不用系统自带的
  · 「下载根目录」这个词用户可能不懂

【为什么不早用 SAF】
  担心 SAF 返回 content:// 树，而下载全链路用 File。
  但实际可以转换：
      treeUri 的 documentId 形如 "primary:Download/DYParse"
      拆出 "primary" 和 "Download/DYParse"
      primary → /storage/emulated/0
      拼起来就是真实绝对路径。
  在 MANAGE_EXTERNAL_STORAGE 权限下，这个路径完全可读写，
  下游 dlDirFor2() / ScopedStore.canWriteDir() / File 全都不受影响。

【改法】
  ① 首选：ACTION_OPEN_DOCUMENT_TREE（系统目录选择器，能返回上一级、能看全盘）
     onActivityResult 里把 treeUri 转成真实路径写进同一个 prefs key。
  ② 兜底：自建选择器补「⬆ 上一级」按钮（万一系统选择器不可用 / 转换失败）。

  同时把「下载根目录」文案改成用户能懂的：
    标题：选择下载保存位置
    副标题：所有平台的文件都存到这个文件夹里，点这里选
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
# 1. 新增 REQ_DIR_TREE 请求码
# ============================================================
rep('1-请求码',
    '''    static final int REQ_TORRENT = 1002;''',
    '''    static final int REQ_TORRENT = 1002;
    /** 系统目录选择器（ACTION_OPEN_DOCUMENT_TREE） */
    static final int REQ_DIR_TREE = 1003;
    /** 本次系统目录选择要写进哪个 prefs key（选完在 onActivityResult 里用） */
    volatile String pendingDirKey = null;
    /** 本次选择的默认值（用来判断「选的就是默认目录」→ 存空值） */
    volatile String pendingDirDef = null;''')

# ============================================================
# 2. 新增：打开系统目录选择器 + URI/路径互转
# ============================================================
anchor = '''    /** 目录选择器的当前对话框引用（切目录时要 dismiss 旧的） */'''

helper = '''    /** 打开系统目录选择器（SAF）。
     *
     *  为什么用它：系统选择器自带「返回上一级」、能浏览全盘、
     *  还能新建文件夹，比自建的好用得多。
     *
     *  选完在 onActivityResult 里把 treeUri 还原成真实绝对路径
     *  （见 treeUriToPath），下游全部用 File，不受影响。
     */
    void pickDirWithSystem(final String key, final String defAbs, final String title){
        try{
            pendingDirKey = key;
            pendingDirDef = defAbs;
            android.content.Intent it = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
            it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            // 起点：尽量让系统选择器直接落在当前目录
            try{
                String cur = getSharedPreferences(SP, MODE_PRIVATE).getString(key, "");
                String start = (cur != null && cur.length() > 0) ? cur : defAbs;
                android.net.Uri u = android.provider.DocumentsContract
                        .buildDocumentUri("com.android.externalstorage.documents",
                                pathToTreeDocId(start));
                it.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, u);
            }catch(Throwable t){}
            startActivityForResult(it, REQ_DIR_TREE);
            DypLog.i("DIR_TREE_OPEN key=" + key + " title=" + title);
        }catch(Throwable t){
            DypLog.e("DIR_TREE_OPEN_ERR " + t);
            showHintToast("系统选择器打不开，改用内置选择器");
            showDirPicker(key, defAbs, title);
        }
    }

    /** 绝对路径 → SAF 的 documentId（primary:Download/DYParse） */
    String pathToTreeDocId(String absPath){
        try{
            String root = android.os.Environment.getExternalStorageDirectory()
                    .getAbsolutePath();
            String p = new java.io.File(absPath).getAbsolutePath();
            if(p.startsWith(root)){
                String rel = p.substring(root.length());
                if(rel.startsWith("/")) rel = rel.substring(1);
                return rel.length() == 0 ? "primary:" : ("primary:" + rel);
            }
            if(p.startsWith("/storage/")){
                String rest = p.substring("/storage/".length());
                int slash = rest.indexOf('/');
                if(slash > 0){
                    return rest.substring(0, slash) + ":" + rest.substring(slash + 1);
                }
                return rest + ":";
            }
        }catch(Throwable t){}
        return "primary:";
    }

    /** SAF treeUri → 真实绝对路径。
     *
     *  documentId 形如：
     *      primary:Download/DYParse        → /storage/emulated/0/Download/DYParse
     *      XXXX-XXXX:Movies                → /storage/XXXX-XXXX/Movies
     *  还原失败返回空串。
     */
    String treeUriToPath(android.net.Uri treeUri){
        try{
            String docId = android.provider.DocumentsContract
                    .getTreeDocumentId(treeUri);
            if(docId == null || docId.length() == 0) return "";
            int colon = docId.indexOf(':');
            if(colon < 0) return "";
            String vol = docId.substring(0, colon);
            String rel = docId.substring(colon + 1);
            String base;
            if("primary".equalsIgnoreCase(vol)){
                base = android.os.Environment.getExternalStorageDirectory()
                        .getAbsolutePath();
            }else{
                base = "/storage/" + vol;
            }
            String full = rel.length() == 0 ? base : (base + "/" + rel);
            return new java.io.File(full).getAbsolutePath();
        }catch(Throwable t){
            DypLog.e("TREE_URI_TO_PATH_ERR " + t);
            return "";
        }
    }

    /** 目录选择器的当前对话框引用（切目录时要 dismiss 旧的） */'''

if 'void pickDirWithSystem(' not in s:
    s = s.replace(anchor, helper, 1)
    ok.append('2-系统选择器')
else:
    ok.append('2-已存在')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)