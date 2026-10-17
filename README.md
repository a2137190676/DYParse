# DY解析器 (DYParse)

一个轻量级的抖音内容解析 Android 客户端：粘贴分享链接即可解析视频、图文、动图，并内置独立播放器。

> ⚠️ **仅供个人学习与技术研究使用**。请勿用于任何商业用途、批量抓取或侵犯他人权益的场景。内容版权归原作者所有。

---

## 功能

- 支持 **视频 / 图文 / 动图** 解析
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
- 自动提取分享口令里的链接
- 切到后台自动暂停播放
- **内置检查更新**（GitHub Releases，无需自建服务器）

## 界面预览

主界面采用深色卡片风格（`#0E0E12` / 抖音红 `#FE2C55`），播放器为纯黑沉浸式 + 上下渐变遮罩。

## 技术实现

| 模块 | 方案 |
|---|---|
| 数据获取 | `WebView` 加载分享页，注入 JS hook 捕获 `fetch` / `XMLHttpRequest` 中的 `aweme_detail` 响应 |
| 数据提取 | 优先解析 `window._ROUTER_DATA`（SSR 渲染数据），降级到 HTML 正则 |
| JSON 解析 | 自实现轻量 `JP` 解析器，避免引入第三方依赖 |
| 播放器 | `VideoView` + 反射调用 `getMediaPlayer()` 拿底层实例做倍速控制 |
| 手势 | `GestureDetector` 处理单击/双击，`OnTouchListener` + `Handler` 处理长按连续 seek |

### 为什么不用直接 HTTP 请求？

抖音分享页有较为严格的风控（`a_bogus`、`X-Bogus` 签名、滑块验证）。直接用 `curl`/`OkHttp` 请求 `iesdouyin.com` 通常返回验证页。本项目通过 **真实 WebView 加载页面** 让浏览器自己完成签名与验证，再从 JS 层截获结构化数据，鲁棒性更好。

## 编译

### 依赖

- `aapt` / `d8` / `zipalign` / `apksigner`（Android SDK Build-Tools）
- `javac`（JDK 8+）
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

`build.sh` 中的 `d8` 路径需要按你自己的 SDK 位置调整：

```bash
java -cp /opt/android/android-14/lib/d8.jar com.android.tools.r8.D8 ...
```

### 签名

脚本默认使用项目内 `debug.keystore`（密码 `android`）。**发布前请替换为你自己的 keystore。**

```bash
keytool -genkey -v -keystore release.keystore -alias dyp -keyalg RSA -keysize 2048 -validity 10000
```

修改 `build.sh` 的 `apksigner` 行：

```bash
apksigner sign --ks release.keystore --ks-pass pass:你的密码 --out build/DYParse.apk build/app-aligned.apk
```

## 使用

### 方式一：界面操作

粘贴链接 → 点「解析」→ 结果出来后点「全屏播放」。

### 方式二：Intent 直传（调试用）

```bash
adb shell am start -n com.re.douyinparse/.MainActivity \
  --es url 'https://www.iesdouyin.com/share/video/<视频ID>/'
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
    ├── MainActivity.java           # 主界面 + WebView 解析逻辑
    └── PlayerActivity.java         # 播放器 + 手势 + 倍速
```


## 版本更新机制

App 内置检查更新，基于 **GitHub Releases API**，不需要自建服务器。

### 配置

打开 `java/com/re/douyinparse/MainActivity.java`，改这两行：

```java
static final String GH_OWNER = "你的GitHub用户名";
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

- **不支持真正的倒放**：Android `MediaPlayer.setPlaybackParams` 不接受负速度，长按左侧实现为高频精准 `seekTo` 回退。
- **依赖抖音页面结构**：解析逻辑基于 `_ROUTER_DATA` 与 `aweme_detail` 响应格式，页面改版可能导致失效。
- **分享页链接有效期**：图文（note）类链接有效期较短（约 2 天），过期需重新获取。
- **CDN 校验**：视频/图片 CDN 需要 `Referer: https://www.douyin.com/` 与移动端 UA，代码中已统一处理。
- **抖音分享页风控**：高频请求会触发滑块验证。请勿批量抓取。

## 免责声明

本项目仅供 **安全研究与 Android 逆向技术学习** 使用。使用者应自行承担使用风险，包括但不限于违反平台服务条款、侵犯内容版权等。作者不对任何滥用行为负责。

如本项目涉及侵权，请联系删除。

**本软件全程免费且开源，严禁用于任何商业用途或违法行为，否则一切后果自负。**

问题反馈：QQ 2137190676
