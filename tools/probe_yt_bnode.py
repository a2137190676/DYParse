# -*- coding: utf-8 -*-
"""验证 B 方案思路：在 JS 引擎里跑 player.js，看能否让 YouTube 自己算出 n"""
import urllib.request, gzip, json, re, subprocess, os

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

def get(url, timeout=40):
    h = {"User-Agent": UA, "Accept": "*/*", "Accept-Encoding": "gzip",
         "Accept-Language": "en-US,en;q=0.9", "Connection": "close"}
    r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
    raw = r.read()
    if "gzip" in r.headers.get("Content-Encoding", ""):
        try: raw = gzip.decompress(raw)
        except Exception: pass
    return raw.decode("utf-8", "ignore")

print("=== 1. 检查 node ===")
try:
    v = subprocess.run(["node", "--version"], capture_output=True, text=True, timeout=10)
    print("  node:", v.stdout.strip() or v.stderr.strip())
except Exception as e:
    print("  没有 node:", e)
    raise SystemExit

print()
print("=== 2. 找 PlayabilityStatus / 需要 n 的格式 ===")
# youtube 的生成 URL 逻辑：先拿 playerResponse，对无 url 的格式拼出基础 url
# 实际上新版是「连 baseUrl 都不给」，只有 itag/mimeType
# 所以要自己拼： https://rr---.googlevideo.com/videoplayback?...
# 先看看 playerResponse 里到底有没有任何可拼的线索
html = get("https://www.youtube.com/watch?v=Do_LjDsisIE")
m = re.search(r'ytInitialPlayerResponse\s*=\s*\{', html)
print("  有 ytInitialPlayerResponse:", bool(m))

# 试着看 playerResponse 里有没有 serverAbrStreamingUrl / hlsManifestUrl
for k in ["serverAbrStreamingUrl", "hlsManifestUrl", "dashManifestUrl", "probeUrl"]:
    idx = html.find('"%s"' % k)
    if idx > 0:
        print("  %-24s @%d" % (k, idx))
        seg = html[idx:idx + 300]
        print("      ", seg[:260].replace("\n", " "))

print()
print("=== 3. 用 node 跑 player.js，试让 g.zs 解 n ===")
js = get("https://www.youtube.com/s/player/a5c2aafe/player_es6.vflset/en_US/base.js")
open("/tmp/pj2.js", "w").write(js)
print("  player.js len:", len(js))

# 构造 node 引导：模拟最小 browser 环境
harness = r'''
const fs = require('fs');
const vm = require('vm');

// 最小浏览器桩
const sandbox = {
  window: {},
  document: {
    location: { href: 'https://www.youtube.com/watch?v=Do_LjDsisIE', protocol: 'https:' },
    createElement: () => ({ style:{}, setAttribute(){}, appendChild(){} }),
    documentElement: { style:{} },
    cookie: '',
    addEventListener(){},
    querySelector: () => null,
    getElementsByTagName: () => [],
  },
  navigator: { userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36', language:'en-US' },
  location: { href: 'https://www.youtube.com/watch?v=Do_LjDsisIE', protocol:'https:', host:'www.youtube.com' },
  console: console,
  setTimeout, clearTimeout, setInterval, clearInterval,
  XMLHttpRequest: function(){ return { open(){}, send(){}, setRequestHeader(){}, addEventListener(){} }; },
  fetch: () => Promise.reject(new Error('no fetch')),
  TextEncoder, TextDecoder, URL, URLSearchParams,
  crypto: require('crypto').webcrypto,
  performance: { now: () => Date.now() },
  Math, Date, JSON, Object, Array, String, Number, Boolean, RegExp, Error,
  encodeURIComponent, decodeURIComponent, parseInt, parseFloat, isNaN, isFinite,
};
sandbox.window = sandbox;
sandbox.self = sandbox;
sandbox.globalThis = sandbox;

const code = fs.readFileSync('/tmp/pj2.js', 'utf8');
const ctx = vm.createContext(sandbox);
try {
  vm.runInContext(code, ctx, { timeout: 60000 });
  console.log('PLAYER_LOADED');
} catch(e) {
  console.log('LOAD_ERR', e.message.slice(0,200));
}

// 看看全局挂了什么
const keys = Object.keys(sandbox).filter(k => !['Math','Date','JSON','Object','Array','String','Number','Boolean','RegExp','Error'].includes(k));
console.log('GLOBAL_KEYS', keys.slice(0,40).join(','));

// 尝试找 _yt_player
if (sandbox._yt_player) {
  console.log('HAS__yt_player keys=', Object.keys(sandbox._yt_player).slice(0,20).join(','));
  console.log('HAS_g=', !!sandbox._yt_player.g);
  if (sandbox._yt_player.g) {
    console.log('g keys=', Object.keys(sandbox._yt_player.g).slice(0,30).join(','));
  }
}
'''

open("/tmp/harness.js", "w").write(harness)
r = subprocess.run(["node", "/tmp/harness.js"], capture_output=True, text=True, timeout=120)
print("  stdout:", r.stdout[:1500])
print("  stderr:", r.stderr[:600])