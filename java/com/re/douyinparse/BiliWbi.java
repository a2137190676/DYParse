package com.re.douyinparse;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * B 站 wbi 签名（移植自 bilibilias WebiTokenUtils.kt）：
 *   mixKey = (imgKey+subKey) 按 MIXIN 表取 32 字符
 *   w_rid  = md5(urlencode(排序参数) + mixKey)
 * 在 App 内本地直连 B 站空间 API（手机网络环境干净，无服务器风控）。
 */
public class BiliWbi {

    private static final int[] MIXIN = {
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45,
        35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38,
        41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60,
        51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
        20, 34, 44, 52,
    };

    private String mixKey = "";

    /** 从 nav 接口的 wbi_img 设置 key */
    public boolean setKey(String imgUrl, String subUrl){
        try{
            String imgKey = imgUrl.replace(".png", "");
            int slash = imgKey.lastIndexOf('/');
            if(slash >= 0) imgKey = imgKey.substring(slash + 1);
            String subKey = subUrl.replace(".png", "");
            slash = subKey.lastIndexOf('/');
            if(slash >= 0) subKey = subKey.substring(slash + 1);
            String mix = imgKey + subKey;
            if(mix.length() < 64) return false;
            StringBuilder sb = new StringBuilder(32);
            for(int i = 0; i < 32; i++){
                sb.append(mix.charAt(MIXIN[i]));
            }
            mixKey = sb.toString();
            return true;
        }catch(Throwable t){
            return false;
        }
    }

    /** 给参数表加 wts + w_rid（TreeMap 保证排序，URLEncoder 编码） */
    public Map<String, String> sign(Map<String, String> params) throws IllegalStateException{
        if(mixKey.length() == 0){
            throw new IllegalStateException("wbi key not set");
        }
        TreeMap<String, String> sorted = new TreeMap<String, String>(params);
        sorted.put("wts", String.valueOf(System.currentTimeMillis() / 1000));
        StringBuilder data = new StringBuilder();
        try{
            for(Map.Entry<String, String> e : sorted.entrySet()){
                data.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
        }catch(Exception e){
            throw new IllegalStateException("encode failed", e);
        }
        data.append(mixKey);
        sorted.put("w_rid", md5(data.toString()));
        return sorted;
    }

    static String md5(String s){
        try{
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(32);
            for(byte b : hash) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        }catch(Exception e){
            return "";
        }
    }
}