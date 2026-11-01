package com.re.douyinparse;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 代理策略（2026-10，v1.3.6）：只留一个「内置代理」开关。
 *
 *   内置代理 = 海外平台（TikTok/X/YouTube/IG）走自建服务器中转。
 *   开关关着（默认）→ 全部直连，用户自己挂的代理 / TUN 自然生效，App 不干预。
 *
 * 不再探测本机代理端口，不再让用户自填代理地址。
 */
public class ProxyPolicy {

    /** 内置代理（服务器中转）是否开启 */
    public static boolean builtinOn(Context ctx){
        if(ctx == null) return false;
        try{
            SharedPreferences p = ctx.getSharedPreferences(MainActivity.SP, Context.MODE_PRIVATE);
            return p.getBoolean(MainActivity.KEY_PROXY_ON, false);
        }catch(Throwable t){ return false; }
    }
}
