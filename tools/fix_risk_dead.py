# -*- coding: utf-8 -*-
"""
把风控拆成两级，别再混成一种：

  【A 级】可验证挑战 —— 「请完成安全验证」「拖动滑块」「验证码中间页」
          抖音还愿意给你机会，显示 WebView + 操作条，用户过了点按钮。

  【B 级】终态拒绝 —— 「抱歉出错了」「请尝试在抖音内观看」
          抖音已经彻底不给你了，不会再出滑块。
          这时候显示 WebView 毫无意义，用户重试也没用。
          必须直说：等 10~15 分钟、换网络、换 Cookie。

用户实测踩的就是这个：
  「这次又没有滑块验证了，我重试了也没有」
  —— 因为已经在 B 级，不是挑战，是拒绝。
  旧代码把 B 级也当挑战，把用户晾在一个永远出不来验证的页面上。
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
# 1) isDyRiskPage 旁边加一个「终态拒绝」判断
# ============================================================
rep('1-加终态拒绝判断',
    '''    /**
     * 风控验证通过后，把抖音 Cookie 存到本地。''',
    '''    /**
     * 判断是不是「终态拒绝」—— 抖音彻底不给机会了。
     *
     * 和 isDyRiskPage 的区别：
     *   isDyRiskPage  = 任意风控/验证页（含可挑战的滑块）
     *   isDyRiskDead  = 连挑战都不给的死页面
     *
     * 「抱歉出错了 / 请尝试在抖音内观看」这一档，抖音已经不会再下发滑块，
     * 这时候把 WebView 亮出来让用户「完成验证」是骗人的 ——
     * 页面上根本没有可操作的东西，用户点重试也不会好。
     * 实测：用户报「这次又没有滑块验证了，我重试了也没有」就是这个。
     *
     * 对这种情况只能直说：等、换网络、换 Cookie。
     */
    static boolean isDyRiskDead(String body, String url){
        try{
            String b = body == null ? "" : body;
            if(b.indexOf("抱歉出错") >= 0) return true;
            if(b.indexOf("请尝试在抖音内观看") >= 0) return true;
            if(b.indexOf("请稍后再试") >= 0 && b.indexOf("抖音") >= 0
                    && b.indexOf("验证") < 0) return true;
        }catch(Throwable t){}
        return false;
    }

    /**
     * 风控验证通过后，把抖音 Cookie 存到本地。''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)