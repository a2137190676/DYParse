package com.re.douyinparse;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 代理策略：用户代理优先，服务器机场兜底。
 *
 * 策略（2026-10，v1.3.4）：
 *   1) 用户在设置里开了代理 → 国外解析「优先走用户代理」直连数据源，
 *      不碰服务器机场（省流量 + 服务器到期后依然可用）。
 *   2) 用户没开代理 → 回退服务器中转（机场流量）。
 *   3) 机场流量耗尽（sub_usable=false）→ 提示用户自己挂代理。
 *
 * 与 MainActivity.staticProxyFor 的区别：
 *   staticProxyFor 受「仅 TikTok」开关控制（KEY_PROXY_TT_ONLY），
 *   本类判断「用户是否配了代理」不看那个开关 —— 只要开关开着、
 *   地址合法，就认为用户有代理可用（境外平台一律适用）。
 */
public class ProxyPolicy {

    /** 用户是否已配置并开启代理（不看「仅 TikTok」过滤） */
    public static boolean hasUserProxy(Context ctx){
        if(ctx == null) return false;
        try{
            SharedPreferences p = ctx.getSharedPreferences(MainActivity.SP, Context.MODE_PRIVATE);
            if(!p.getBoolean(MainActivity.KEY_PROXY_ON, false)) return false;
            return MainActivity.parseProxy(p.getString(MainActivity.KEY_PROXY, "")) != null;
        }catch(Throwable t){ return false; }
    }

    /** 用户的代理对象；没配返回 null。境外平台强制适用（不套「仅 TikTok」过滤） */
    public static java.net.Proxy userProxy(Context ctx){
        if(ctx == null) return null;
        try{
            SharedPreferences p = ctx.getSharedPreferences(MainActivity.SP, Context.MODE_PRIVATE);
            if(!p.getBoolean(MainActivity.KEY_PROXY_ON, false)) return null;
            return MainActivity.parseProxy(p.getString(MainActivity.KEY_PROXY, ""));
        }catch(Throwable t){ return null; }
    }

    /**
     * 给「境外解析直连」用的连接器：用户代理优先。
     * 返回带代理的连接；调用方自己决定超时与鉴权。
     */
    public static java.net.HttpURLConnection open(Context ctx, String url) throws Exception{
        java.net.Proxy px = userProxy(ctx);
        return (java.net.HttpURLConnection) new java.net.URL(url).openConnection(
                px != null ? px : java.net.Proxy.NO_PROXY);
    }
}
