# DY解析器 (DYParse)

> **作者：大爱仙尊 / 尘** ｜ 反馈群：995434160

一个轻量级的多平台内容解析 Android 客户端：粘贴分享链接即可解析视频、图文、动图，并内置独立播放器。

当前支持 **抖音 / 快手 / TikTok** 三个平台。

> ⚠️ **仅供个人学习与技术研究使用**。请勿用于任何商业用途、批量抓取或侵犯他人权益的场景。内容版权归原作者所有。

## 开源地址

| 平台 | 仓库 | 下载 |
|---|---|---|
| **Gitee**（国内推荐） | https://gitee.com/adaaixianzun/dyparse | [Releases](https://gitee.com/adaaixianzun/dyparse/releases) |
| **GitHub** | https://github.com/a2137190676/DYParse | [Releases](https://github.com/a2137190676/DYParse/releases) |

两个仓库同步更新。App 内「设置 → 更新源」可自由切换从哪边检查更新。

**相关文档**：[更新日志](CHANGELOG.md) · [贡献指南](CONTRIBUTING.md) · [开源协议](LICENSE)

---

## 功能

- 支持 **抖音 / 快手 / TikTok** 的 **视频 / 图文 / 动图** 解析
- 图集左右滑动切图、点击查看大图
- 独立播放器：
  - 竖屏视频保持竖屏、横屏视频自动横屏
  - 单击显隐控制栏（4 秒自动淡出）
  - **双击左/右 → 回退/前进 10 秒**
  - **长按左/右 → 连续快退/快进**
  - 倍速播放 `0.5X / 1.0X / 1.25X / 1.5X / 2.0X`
  - 内置下载按钮
  - 沉浸式全屏 + 刘海区域铺满
- **小窗与全屏体验一致**：双击暂停、长按左右快退/快放、倍速选择
- **支持幻灯片（slides）**：满足多图动图作品
- **音频提取为 MP3**（192kbps），快手额外支持独立音轨直转
- **BGM 提取**：抖音背景音乐原曲 MP3 直接下载，免转码
- 自动提取分享口令里的链接
- 切到后台自动暂停播放
- **下载目录分类**：视频 / 图片 / 音频 / 合集各自独立目录，均支持自定义路径
- **双更新源**：Gitee / GitHub 可切换，内置服务器加速优先
- **运行日志**：设置内查看解析 / 下载 / 报错记录

详细的三平台实现说明见 App 内 **关于 → 平台支持说明**。

## 界面预览

主界面采用深色卡片风格（`#0E0E12` / 抖音红 `#FE2C55`），播放器为纯黑沉浸式 + 上下渐变遮罩。

## 技术实现

| 模块 | 方案 |
|---|---|
| 抖音 | `WebView` 加载分享页，注入 JS hook 捕获 `fetch` / `XMLHttpRequest` 中的 `aweme_detail` 响应 |
| 快手 | 纯 HTTP，无 WebView，解析分享页 SSR 里的 `window.INIT_STATE` |
| TikTok | 纯 HTTP + 自管 Cookie 会话，双页面模板互为兜底 |
| 数据提取 | 抖音优先 `window._ROUTER_DATA`，降级到 HTML 正则 |
| JSON 解析 | 自实现轻量 `JP` 解析器，避免引入第三方依赖 |
| 播放器 | `VideoView` + 反射调用 `getMediaPlayer()` 拿底层实例做倍速控制 |
| 手势 | `GestureDetector` 处理单击/双击，`OnTouchListener` + `Handler` 处理长按连续 seek |

### 抖音：为什么不用直接 HTTP 请求？

抖音分享页有较为严格的风控（`a_bogus`、`X-Bogus` 签名、滑块验证）。直接用 `curl`/`OkHttp` 请求 `iesdouyin.com` 通常返回验证页。本项目通过 **真实 WebView 加载页面** 让浏览器自己完成签名与验证，再从 JS 层截获结构化数据，鲁棒性更好。

### 快手：为什么要按结构找节点

短链 `v.kuaishou.com/xxx` 会 302 到 `v.m.chenzhongtech.com/fw/photo/{photoId}`。
页面里内嵌 `window.INIT_STATE`，**顶层 key 被逐字符 -1 混淆**：

```
tusjoh  → string
qipup   → photo
```

但 value 全是明文。所以解析器不认任何 key 名，直接递归找「同时有 `photoId` 和 `mainMvUrls`/`manifest`/`atlas` 」的那个节点。这样即使快手改 key，解析也不会挂。

快手分享页额外提供一条独立音轨（`soundTrack.audioUrls`，m4a），转 MP3 时优先用它——比下载整条视频再抽音轨快一个数量级（实测 0.9s vs 14s）。

### TikTok：两个模板 + Cookie 会话

TikTok 的页面模板由 URL 形式决定，两种都要兜：

| URL 形式 | 数据载体 | 字段路径 |
|---|---|---|
| `@用户名/video/{id}` | `__UNIVERSAL_DATA_FOR_REHYDRATION__` | `__DEFAULT_SCOPE__["webapp.video-detail"]` 或 `["webapp.reflow.video.detail"]` |
| `@i/video/{id}` | `<script id="api-data">` | `{"videoDetail": ...}` |

`@i` 是占位用户名，TikTok 不校验。**模板 A 有时会返回 HTTP 400**，此时自动切模板 B 兜底——这不是可选优化，是必须的。

**关键机制：Cookie 会话必须自洽。**

`playAddr` 里带 `tk=tt_chain_token`，CDN 会拿这个参数去比对请求头里的 Cookie。页面和视频必须来自同一个 `CookieStore`，否则一律 403。所以 `TtParser` 全程用一个 `java.net.CookieManager` 自己管，不碰 WebView 的 CookieManager。

**下载分流：两套 CDN 行为相反。**

实测：
```
v16m.tiktokcdn.com           直连 200，走代理反而 SSL_ERROR_SYSCALL
v16-webapp-prime.tiktok.com  必须走代理
```

所以下载器按 host 判断首选方式，4xx 或抛异常就自动换另一种。

### 关于开源项目的调研

无登录解析 TikTok 目前有四种流派：

| 流派 | 代表项目 | 做法 | 代价 |
|---|---|---|---|
| SSR hydration JSON | `imputnet/cobalt` (45k★) | 抓页面，切出 hydration JSON | 无需签名，最轻 |
| Web API + 自算签名 | `Evil0ctal/Douyin_TikTok_Download_API` (20k★) | 纯 Python 实现 `X-Dynosaur`/`X-Bogus`/`X-Gnarly` | 需逆向 webmssdk 字节码 VM |
| jsdom 执行真实 SDK | `TobyG74/tiktok-api-dl` | 把官方签名 JS 塞进 jsdom 跑 | SDK 一更新就得换文件 |
| 移动端 App API | 同上项目备选 | `api16-normal-useast5.tiktokv.us` | 风控最严 |

本项目采用**流派一**：够用、稳定、不依赖签名算法。签名流派的价值在于能调 `item/detail` 等接口拿更全的数据，但解析单个作品用不上。

一个共通点：**没有任何项目是真·零状态请求**。都要一个服务端下发的 `tt_chain_token` 类 cookie、一批自洽的浏览器指纹参数、以及格式正确的 `msToken`。所谓「无登录」是「不需要账号」，不是「不需要状态」。

## 服务器对接与内置

### 为什么用服务器

服务器不是必须的，App 不连服务器也能完整工作（本地 WebView 解析 + Gitee 更新）。服务器是**加速层**：

| 场景 | 有服务器 | 无服务器 |
|---|---|---|
| 抖音实况照片 | 服务端带凭据中转 detail 接口，免填、秒出 | 需在设置里手填 UIFID_TEMP 凭据，失效要重抓 |
| 短链解析 | 服务端秒解，抗本地 DNS 波动 | 本地解析，偶发慢/失败 |
| 自更新 | 服务器直下 APK，快 | 走 Gitee Releases，可用但慢 |

**服务器到期/挂了 App 自动回退本地**，功能不中断，只是部分场景降级。

### 怎么对接（自建服务器）

1. 把 `server/` 目录拷到你的 Linux 机器，`python3 server.py 8080`
2. 可选：`uifid.txt` 里放抖音访客凭据（一行一个），实况解析才有加速
3. 可选：`auth_key.txt` 放 32 hex 密钥，开启 HMAC 鉴权（App 源码里也要配同一个 key）
4. App 内 **设置 → 代理/服务器** 填你的地址（`http://ip:8080`），覆盖内置

> 注意：自填服务器走**兼容模式**（无 TLS pin），因为 App 无法预知你的证书。生产环境请自己套 Nginx + HTTPS。

### 怎么内置（编译期注入，防止暴露）

App 内置服务器地址不进源码仓库，走**编译期注入**：

1. 项目根新建 `secret_srv.txt`，写一行地址 `http://ip:8080`（已在 `.gitignore`，不会误传）
2. `bash build.sh` 时 `tools/inject_srv.py` 自动把地址写进 `SrvConfig.java`，编译完恢复占位符 `<INJECT_SERVER>`
3. 要覆盖内置地址：用户设置里自填，优先级最高；清空即回退内置

**安全加固（内置地址场景）**：内置服务器走 HTTPS + 证书 pin + HMAC 鉴权，对应配置在 `SrvSecret`（地址与 key 混淆存储）与 `SrvCert`（证书混淆存储）。这两类文件在仓库里是**空壳占位版**，真实值只存在于本地 `secret_src/`（已 `.gitignore`），由 `build.sh` 构建时自动覆盖、构建后自动还原。

## 编译

### 依赖

- `aapt` / `zipalign` / `apksigner`（Android SDK Build-Tools）
- `d8.jar` 或 `r8.jar`（二者同一份 jar 两个入口，脚本会自动找；找不到会自动下载）
- `javac`（JDK 8+）
- `python3`（合包与对齐检查用）
- `android.jar`（Android SDK Platform，放在项目根目录）

### 步骤

```bash
# 1. 准备 android.jar（示例：Android 14）
cp $ANDROID_HOME/platforms/android-34/android.jar .

# 2. 下载依赖库（Media3 / Guava / Kotlin stdlib 等，约 14MB）
bash fetch_libs.sh

# 3. 编译 + 签名
bash build.sh

# 4. 产物
# build/DYParse.apk
```

> `libs/` 下的依赖不会进入仓库（已加入 .gitignore），请用 `fetch_libs.sh` 获取。

`build.sh` 会自动按以下顺序找 dex 工具链，**不需要手动改路径**：

1. `$ANDROID_HOME/build-tools/*/lib/d8.jar`
2. `$ANDROID_SDK_ROOT/build-tools/*/lib/d8.jar`
3. `/opt/android*`、`/usr/lib/android-sdk` 等标准位置
4. 项目内 `./tools/r8.jar`、`./r8.jar`
5. 都没有则自动从阿里云下载 `r8.jar` 到 `./tools/`

可用的环境变量：

| 变量 | 作用 |
|---|---|
| `D8_JAR` | 手动指定 d8.jar / r8.jar 路径 |
| `NO_DOWNLOAD=1` | 禁止联网下载 r8.jar |
| `NO_SDCARD=1` | 不自动复制到 `/sdcard/Download` |
| `KS_FILE` / `KS_PASS` | 指定 keystore 与密码（默认 `./debug.keystore` / `android`） |

### 打包细节

`classes.dex`、`resources.arsc`、`lib/**/*.so` **必须 STORED**（不压缩），否则 `zipalign` 无法给 so 做 4 字节对齐，安装后可能告警。脚本用 Python 统一重写 zip 保证这点，并在打包后自检：

- 所有 `.so` 的压缩方式必须是 STORED
- `zipalign -c -v 4` 对齐校验必须通过

## 项目结构

```
.
├── AndroidManifest.xml
├── build.sh                        # 一键编译脚本
├── fetch_libs.sh                   # 下载 libs/ 依赖
├── android.jar                     # 编译用 Android SDK stub
├── debug.keystore                  # 调试签名（发布请替换）
├── tools/r8.jar                    # dex 工具（build.sh 自动准备）
├── res/
│   ├── drawable/                   # 圆角背景、进度条、滑块等
│   ├── drawable-*dpi/              # 应用图标（圆形，非方块）
│   ├── layout/
│   │   ├── activity_main.xml       # 主界面
│   │   └── activity_player.xml     # 播放器界面
│   └── values/
│       ├── colors.xml
│       └── strings.xml
└── java/com/re/douyinparse/
    ├── MainActivity.java           # 主界面 + 抖音 WebView 解析 + 下载/转码调度
    ├── PlayerActivity.java         # 全屏播放器 + 手势 + 倍速
    ├── KsParser.java               # 快手：INIT_STATE 解析 + 独立音轨
    ├── TtParser.java               # TikTok：双模板 + Cookie 会话 + CDN 分流
    ├── InnerDownloader.java        # 应用内下载器（带进度）
    ├── Mp3Converter.java           # ffmpeg-kit 转 MP3
    ├── AudioExtractor.java         # MediaExtractor 抽音轨（备用）
    └── ApkProvider.java            # 自更新用的 FileProvider
```

### 签名

脚本默认使用项目内 `debug.keystore`（密码 `android`）。**发布前请替换为你自己的 keystore。**

```bash
keytool -genkey -v -keystore release.keystore -alias dyp -keyalg RSA -keysize 2048 -validity 10000
```

修改 `build.sh` 的 `apksigner` 行，或直接用环境变量覆盖（推荐，不改脚本）：

```bash
KS_FILE=./release.keystore KS_PASS=你的密码 bash build.sh
```

## 使用

### 方式一：界面操作

粘贴链接 → 点「解析」→ 结果出来后点「全屏播放」。

支持的链接形式：

```
抖音   https://v.douyin.com/xxxxxxx/
       https://www.iesdouyin.com/share/video/<id>/
       7.43 复制打开抖音，看看【xxx的作品】... https://v.douyin.com/xxx/ ...
快手   https://v.kuaishou.com/xxxxxxx
       https://www.kuaishou.com/short-video/<id>
       ...复制此链接，打开【快手】直接观看！https://v.kuaishou.com/xxx ...
TikTok https://www.tiktok.com/t/XXXXXXXXX/
       https://www.tiktok.com/@user/video/<id>
       https://vt.tiktok.com/XXXXXXX/
```

口令里的链接会自动抠出来，不用手动清理。

### 方式二：Intent 直传（调试用）

```bash
adb shell am start -n com.re.douyinparse/.MainActivity \
  --es url 'https://v.kuaishou.com/xxxxxxx'
```


## 项目结构

```
.
├── AndroidManifest.xml
├── build.sh                        # 一键编译脚本
├── android.jar                     # 编译用 Android SDK stub
├── debug.keystore                  # 调试签名（发布请替换）
├── res/
│   ├── drawable/                   # 圆角背景、进度条、滑块等
│   ├── layout/
│   │   ├── activity_main.xml       # 主界面
│   │   └── activity_player.xml     # 播放器界面
│   └── values/
│       ├── colors.xml
│       └── strings.xml
└── java/com/re/douyinparse/
    ├── MainActivity.java           # 主界面 + 抖音 WebView 解析 + 下载/转码调度
    ├── PlayerActivity.java         # 全屏播放器 + 手势 + 倍速
    ├── KsParser.java               # 快手：INIT_STATE 解析 + 独立音轨
    ├── TtParser.java               # TikTok：双模板 + Cookie 会话 + CDN 分流
    ├── InnerDownloader.java        # 应用内下载器（带进度）
    ├── Mp3Converter.java           # ffmpeg-kit 转 MP3
    ├── AudioExtractor.java         # MediaExtractor 抽音轨（备用）
    └── ApkProvider.java            # 自更新用的 FileProvider
```

## 版本更新机制

App 内置检查更新，基于 **Gitee / GitHub Releases API**，不需要自建服务器。默认使用 Gitee。

### 配置

打开 `java/com/re/douyinparse/MainActivity.java`，改这两行：

```java
static final String UPD_SOURCE = "gitee";   // gitee 或 github
static final String GH_OWNER = "你的用户名";
static final String GH_REPO  = "仓库名";
```

### 发布新版本

1. 改 `AndroidManifest.xml` 里的版本号：
   ```xml
   android:versionCode="10100"     <!-- 必须递增！ -->
   android:versionName="1.1.0"     <!-- 给人看的 -->
   ```
2. 编译：`bash build.sh`
3. 在 GitHub 建 Release，**Tag 用 `v1.1.0`**（对应 versionName）
4. 把 `build/DYParse.apk` 上传为附件
5. 用户点「关于 → 检查更新」就会看到新版本，可直接下载

### 版本号规则

`v1.2.3` 会被解析成 `10203`，与 `versionCode` 比对：

```
versionCode = major*10000 + minor*100 + patch
```

所以 `versionName="1.2.3"` 必须配 `versionCode="10203"`。

> 注意：只有 `versionCode` 大于用户当前版本时才会提示更新。

## 已知限制

**通用**

- **不支持真正的倒放**：Android `MediaPlayer.setPlaybackParams` 不接受负速度，长按左侧实现为高频精准 `seekTo` 回退。
- **CDN 直链有时效**：`playAddr` 里带 `expire` 参数，过期后需重新解析。快手图文（note）类链接有效期更短（约 2 天）。

**抖音**

- **依赖页面结构**：解析基于 `_ROUTER_DATA` 与 `aweme_detail` 响应格式，页面改版可能导致失效。
- **CDN 校验**：需要 `Referer: https://www.douyin.com/` 与移动端 UA，代码中已统一处理。
- **风控**：高频请求会触发滑块验证。请勿批量抓取。

**快手**

- **依赖 INIT_STATE 结构**：解析按「有 photoId + manifest」的节点定位，不认 key 名，抗改名能力较强；但如果快手把整个数据搬出 `window.INIT_STATE`，需要改抓取点。
- **`video-def` 直链**：`manifest.adaptationSet` 里是 HLS（m3u8），目前用的是 `mainMvUrls` 的 mp4 直链，两者都能播，mp4 更省事。

**TikTok**

- **必须有能访问 TikTok 的网络**：直连不通时需在「代理设置」填境外代理。
- **两套 CDN 行为相反**：`*.tiktokcdn.com` 直连、`*webapp-prime.tiktok.com` 走代理。程序会自动切换，但若两边都被封，仍会失败。
- **Cookie 会话耦合**：页面与下载必须共用同一 `CookieStore`。若中途重新解析，旧的 `playAddr` 会失效。
- **`@用户名` 页可能 400**：自动回退到 `@i` 占位模板。占位模板数据完整，但拿不到该用户的其它信息。
- **风控**：`statusCode` 10000 / 10101 表示被拦截，需要换节点或稍后重试。

## 免责声明

本项目仅供 **安全研究与 Android 逆向技术学习** 使用。使用者应自行承担使用风险，包括但不限于违反平台服务条款、侵犯内容版权等。作者不对任何滥用行为负责。

如本项目涉及侵权，请联系删除。

**本软件全程免费且开源，严禁用于任何商业用途或违法行为，否则一切后果自负。**

问题反馈：QQ 2137190676
