package com.re.douyinparse;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 后台保活助手。
 *
 * 背景：Android 的省电策略会把后台 App 杀掉，下载自然中断。
 * 用户的说法「省电策略改成无限制」在系统里对应两个东西：
 *
 *  1. **忽略电池优化**（Android 6+ 标准接口）
 *     把这个 App 加入白名单后，系统不会因为它「后台耗电」而限制它。
 *     可通过 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 直接弹系统框，
 *     用户点「允许」即可，无需手动找设置。
 *
 *  2. **厂商自启动 / 后台运行管理**（国内 ROM 特有，标准接口管不到）
 *     华为/小米/OPPO/vivo 等都有独立的「应用启动管理」「后台运行管理」，
 *     必须跳到各自的设置页让用户手动开。这里做了机型判断，尽量直达。
 *
 * App 没有系统权限，**不能替用户改**这些设置，只能：
 *  - 检测当前是否已放行
 *  - 一键跳转到最相关的系统页面
 *  - 用清单弹窗写清楚每个开关怎么找
 */
public class PowerHelper {

    /** 是否已在「忽略电池优化」白名单里 */
    public static boolean isIgnoringBattery(Context c){
        try{
            if(c == null || Build.VERSION.SDK_INT < 23) return true;   // 6.0 以下没这个概念
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if(pm == null) return false;
            return pm.isIgnoringBatteryOptimizations(c.getPackageName());
        }catch(Throwable t){
            return false;
        }
    }

    /**
     * 弹系统「是否允许忽略电池优化」框（最快路径，用户点一下就好）。
     * 需要 Manifest 声明 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS。
     *
     * 兼容性说明：部分国产 ROM（ColorOS / MIUI 等）会拦截这个 action 直接返回，
     * 用户看起来「点了没反应」。所以这里做两级：
     *   ① 先弹白名单请求框（能弹最好，一步到位）
     *   ② 弹不出来就跳「电池优化」列表页，让用户自己找本应用
     * 返回 true 表示成功拉起界面。
     */
    public static boolean requestIgnoreBattery(Context c){
        if(Build.VERSION.SDK_INT < 23){
            openBatteryOptimizeList(c);
            return true;
        }
        // ① 直接请求白名单
        try{
            Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            it.setData(Uri.parse("package:" + c.getPackageName()));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(it);
            return true;
        }catch(Throwable t){
            // 被拦截 / 没有对应 Activity
        }
        // ② 部分 ROM 的私有入口（先试白名单请求页）
        try{
            Intent it = new Intent();
            it.setClassName("com.android.settings",
                    "com.android.settings.fuelgauge.RequestIgnoreBatteryOptimizations");
            it.setData(Uri.parse("package:" + c.getPackageName()));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(it);
            return true;
        }catch(Throwable t){}
        // ③ 退回电池优化列表
        return openBatteryOptimizeList(c);
    }

    /** 打开系统「电池优化」列表页（用户自己找本应用改成「不优化」） */
    public static boolean openBatteryOptimizeList(Context c){
        try{
            Intent it = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(it);
            return true;
        }catch(Throwable t){
            openAppDetail(c);
            return false;
        }
    }

    /** 打开本应用的系统详情页（兜底，一般都能开） */
    public static void openAppDetail(Context c){
        try{
            Intent it = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            it.setData(Uri.parse("package:" + c.getPackageName()));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(it);
        }catch(Throwable t){}
    }

    /**
     * 打开厂商的「自启动 / 后台运行管理」页面。
     * 这些是各家私有 Activity，不存在标准接口，只能按机型逐个试。
     * 全部失败时退回系统应用详情页。
     */
    public static String openOemBackgroundSettings(Context c){
        String brand = (Build.BRAND == null ? "" : Build.BRAND).toLowerCase();
        String manu  = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER).toLowerCase();
        String key = brand.length() > 0 ? brand : manu;

        // 每家准备若干候选 Activity，按顺序试
        String[][] cands;
        if(key.contains("huawei") || key.contains("honor")){
            cands = new String[][]{
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
                {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"}
            };
        }else if(key.contains("xiaomi") || key.contains("redmi") || key.contains("poco")){
            cands = new String[][]{
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"}
            };
        }else if(key.contains("oppo") || key.contains("realme") || key.contains("oneplus")){
            cands = new String[][]{
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"}
            };
        }else if(key.contains("vivo") || key.contains("iqoo")){
            cands = new String[][]{
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"}
            };
        }else if(key.contains("meizu")){
            cands = new String[][]{
                {"com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"}
            };
        }else if(key.contains("asus")){
            cands = new String[][]{
                {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"}
            };
        }else if(key.contains("samsung")){
            cands = new String[][]{
                {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
                {"com.samsung.android.incallui", ""}
            };
        }else{
            cands = new String[0][];
        }

        for(String[] pair : cands){
            try{
                Intent it = new Intent();
                it.setClassName(pair[0], pair[1]);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(it);
                return pair[0] + "/" + pair[1];
            }catch(Throwable ignored){}
        }
        // 都不行：退回系统应用详情页
        openAppDetail(c);
        return "fallback:appdetail";
    }

    /** 机型名（弹窗里显示，方便用户对照） */
    public static String deviceLabel(){
        try{
            String b = Build.BRAND == null ? "" : Build.BRAND;
            String m = Build.MODEL == null ? "" : Build.MODEL;
            return (b + " " + m).trim();
        }catch(Throwable t){ return "未知机型"; }
    }

    /** 一句话状态描述 */
    public static String stateLabel(Context c){
        if(Build.VERSION.SDK_INT < 23) return "系统较老，无需设置";
        return isIgnoringBattery(c) ? "已放行 · 下载不会被省电策略限制"
                                    : "未放行 · 后台可能被系统杀掉";
    }
}