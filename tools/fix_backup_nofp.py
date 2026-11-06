# -*- coding: utf-8 -*-
"""把「分享/导出」按钮改成不依赖 FileProvider 的版本
   —— 项目没引 androidx.core，也没有 FileProvider 配置。"""
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


rep('1-去FileProvider',
    '''                .setNeutralButton("分享/导出", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        try{
                            Uri u = androidx.core.content.FileProvider.getUriForFile(
                                    MainActivity.this, getPackageName() + ".fileprovider", f);
                            android.content.Intent it = new android.content.Intent(
                                    android.content.Intent.ACTION_SEND);
                            it.setType("application/zip");
                            it.putExtra(android.content.Intent.EXTRA_STREAM, u);
                            it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(android.content.Intent.createChooser(it, "导出备份"));
                        }catch(Throwable t){
                            // 没配 FileProvider 就降级为「打开文件管理器看」
                            showHintToast("备份在：" + BackupMgr.BAK_DIR);
                        }
                    }})''',
    '''                .setNeutralButton("打开备份目录", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        // 项目没引 androidx.core / 没配 FileProvider，
                        // 所以不做系统分享，直接引导到备份目录：
                        // 用户可以用文件管理器自己拷到网盘 / 电脑。
                        showHintToast("备份在：" + BackupMgr.BAK_DIR);
                        try{
                            android.content.Intent it = new android.content.Intent(
                                    android.content.Intent.ACTION_VIEW);
                            it.setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"),
                                    "vnd.android.document/root");
                            startActivity(it);
                        }catch(Throwable t){
                            // 打不开就算了，Toast 里已经有路径
                        }
                    }})''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)