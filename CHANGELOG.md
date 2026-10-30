# 更新日志

本文记录每个版本的变更内容。版本号遵循 `major.minor.patch`，对应 `versionCode = major*10000 + minor*100 + patch`。

> 自 v1.3.7.1 起支持四位版本号 `major.minor.patch.sub`，`versionCode` 按数字段拼接生成：
> `1.3.7` → `10307`，`1.3.7.1` → `103701`（sub 段两位补零）。纯数字单调递增，
> 旧版本 App 的更新检查走数值比较，可正常识别为更新。

---
## v1.3.7.5 (2026-10-30)

本次为 **画质与播放器体验** 版本。核心是打通抖音原画（实测最高 8K），
并统一小窗 / 全屏播放器的控制栏自动隐藏行为。

### 新增

- **抖音原画下载（最高 8K）**
  - 改用 `ratio=default` 取流，这是平台真正的原始画质入口。
  - 实测同一条 4K60 作品：`ratio=1080p` 为 1024×576 / 160MB，
    `ratio=default` 为 7680×4320 / 4.36GB。
  - 注意：`ratio=4k` 是无效档位，平台会静默回落到 1080p，不要用它。
  - 原画为 WebM 容器（AV1 视频 + Opus 音频），下载扩展名按 URL 动态判定，
    不再写死 `mp4`，避免播放器按 MP4 解析失败。
  - 新增 `extOf(url)`：`/aweme/v1/play/` 且 `ratio=default` → `webm`。
  - 参考实现：JoeanAmier/TikTokDownloader 的 `generate_original_quality_url()`
    与 `__request_video_size()`。

- **播放器控制栏自动隐藏（小窗 + 全屏统一）**
  - 小窗 `MINI_BAR_HIDE_MS = 3000`，全屏 `CTRL_HIDE_MS = 4000`。
  - 统一模式：`getVisibility() == VISIBLE` 作唯一真相；代次号 `miniBarGen` /
    `ctrlGen` 每次显示自增，定时器回调首行校验代次，旧定时器自动作废。
  - 隐藏走直接 `setVisibility(GONE)`，不再依赖动画 `withEndAction`
    （动画被打断时回调不执行，是「有时不隐藏」的元凶）。
  - 删掉「暂停时不隐藏」这类例外。
  - 拖进度条时暂停计时（`onStartTrackingTouch` 递增代次），松手续命。

- **不支持的链接前置校验**
  - 新增 `isSupportedParseUrl()`，覆盖 7 平台域名与短链
    （抖音 / 快手 / TikTok / X / YouTube / B站 / Instagram）。
  - 命中失败弹 `showUnsupportedUrlDialog()`，列出支持平台并显示对方 host，
    不再把 GitHub 等无关页面当 JSON 解析后抛 `SyntaxError`。

### 优化

- **画质档位识别修正**
  - 分辨率改从 `play_addr.width/height` 读（不在 `bit_rate` 顶层，
    也不在 `video` 顶层——顶层是最高档，会把所有档位显示成同一分辨率）。
  - 帧率字段是大写 `FPS`；HDR 读 `HDR_type` / `HDR_bit`；H.265 读 `is_h265`。
  - 排序键从 `quality_type` 改为「像素数 → 码率」，
    因为 `quality_type` 在抖音侧不单调（实测 8K 档 q=292 却排在 1080 之后）。

- **小窗点击命中修复**
  - 根因：`TextureView` 会吃掉触摸事件，叠在上面的容器收不到点击。
  - 修法参照 ExoPlayer `PlayerView` / DKVideoPlayer `StandardVideoController`
    的结构，在画面之上加一层 control layer（`miniTouch`），由它统一接点击。
  - `miniBar` 自身用 `onTouch` + `return false` 续命，不设 `OnClickListener`，
    避免吞掉子按钮点击。

- **原画探测不再阻塞主线程**
  - `NetworkOnMainThreadException` 修复：探测放后台线程，
    探到后清 `startedUrl` 并重新 `doPlay()`（WebM/AV1 必须换解码器重新初始化）。
  - 新增 `InnerDownloader.probeSizeRange()`：`GET` + `Range: bytes=0-0` 读
    `Content-Range`。原 `probeSize()` 走 HEAD，对 `/aweme/v1/play/` 的
    302 跳转链拿不到 `Content-Range`（CDN 只回分段 Content-Length）。

### 修复

- **部分视频「有声音没画面」**
  - 根因：原画是 WebM + AV1 + Opus。没有 AV1 硬解的设备
    （骁龙 8 Gen1 及更早、天玑 1200 及更早）音轨能放、视频轨全黑。
  - 修法：新增 `hasAv1Decoder()`，查 `MediaCodecList` 是否有 `video/av01`。
    有才上原画，没有则老实走档位，避免黑屏。

- **图文 / 实况图解析后显示解析失败、图片加载不出来**
  - SSR 抓取识别条件从「只认 `videoInfoRes/slidesInfoRes/imagesInfoRes`」
    改为「`_ROUTER_DATA` 里含 `aweme_id` 即认」，并补 `noteInfoRes` / `imageInfoRes`。
    原判定对 note 页面永远不触发。
  - 图片 URL 铁律：**绝不能删 `~tplv-` 段**，那是资源名的一部分，
    删了直接 404（`imgErr java.io.FileNotFoundException`）。
    只在 `shrink`/`resize` 家族里换尺寸规格，认不出的模板原样返回。
  - 取图用 `url_list[0]`（页面实际渲染用的），不用末尾
    （`p3-pc-sign.douyinpic.com` 这类签名域名会失效）。

- **小窗进度条点了不显示、显示了不自动隐藏**
  - 触摸事件铁律：`ACTION_DOWN` 必须 `return true`，否则后续
    `MOVE`/`UP` 根本不派发。
  - `miniBar` 初始 `GONE`，不点击不显示。

- **全屏播放器控制栏不自动隐藏**
  - `hideControls()` 原先靠 `controlsVisible` 布尔变量判断，
    该变量会与其他地方的 `setVisibility` 脱钩，改为按 `getVisibility()` 判断。

- **移动端 detail 补抓失败不再打扰用户**
  - `"1".equals(gs(get(m,"mob")))` 恒假：JP 把 JSON 数字 `1` 解析成
    `Double 1.0`，`toString()` 得 `"1.0"`。改为 `parseLong(get(m,"mob"), 0) == 1`。
  - 移动端 detail 参数（`version_code=290100` 等）与 PC UA 不匹配会被风控返回 HTML；
    改成 `190500` 后暴露 `Blocked by ArgusSecurityPlugin Uifid Not Found`
    ——移动端 detail 强制要求 UIFID 访客凭据，无法伪造。
    该路径失败只写日志（`DETAIL_MOBILE_ERR`），不弹用户提示。

### 已知限制

- 抖音原画需要设备支持 AV1 解码。无 AV1 硬解的机型会自动降级到兼容档（H.264）。
- 移动端 detail 补抓（用于争取更高档位）受 UIFID 限制，实际走不通，已静默。

---

## v1.3.7.4 (2026-10-28)

本次为 **批量解析体验重构** 小版本。核心是让批量解析不再「一条条闪过去」，
结果统一进列表页并可跨重启保留。

### 新增

- **后台保活设置（下载不再被省电策略杀掉）**
  - 设置页新增「后台保活（下载不中断）」分组，两项：
    - **电池优化白名单**：显示当前状态（已放行 / 未放行），点击弹说明 + 一键
      调起系统「允许忽略电池优化」框（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）
    - **厂商后台限制**：按机型（华为/荣耀/小米/红米/OPPO/一加/真我/vivo/iQOO/
      魅族/华硕/三星）逐个试该品牌的「自启动 / 后台运行管理」私有页面，
      全部失败自动退回系统应用详情页
  - 首次启动会弹一次「让下载在后台不中断」引导（用户选「以后再说」后不再打扰，
    已放行的设备不弹）
  - Manifest 新增 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限
  - **说明**：这类设置属于系统级权限，App 无法替用户修改，只能引导跳转
  - 弹窗里会显示机型、系统版本、当前放行状态，以及手动操作路径
    （系统设置 → 电池 → 电池优化 → 本应用 → 不限制）

- **更新源可自由切换**（设置 → 更新源，固定入口）
  - 之前只有「检查更新失败」弹窗里才能切换，正常情况根本看不到入口
  - 现在 Gitee / GitHub 随时可换，切换后立即重新检查
- **GitHub 访问方式：镜像加速 / 直连**（设置 → GitHub 访问方式，切到 GitHub 才显示）
  - 镜像加速：自动从 4 个公开镜像（ghfast.top / gh-proxy.com / ghproxy.net / mirror.ghproxy.com）
    里按顺序挑可用的，全部失效自动回退直连
  - 直连：自己挂代理时用，速度最快
  - 覆盖三个请求点：GitHub API、raw 静态 version.json、APK 下载（HEAD 探测优选镜像）
  - 实测（未挂任何代理）：`GH_MIRROR_FAIL idx=0 ghfast.top` → `GH_MIRROR_OK idx=1 gh-proxy.com`
    → `UPDATE_SRC api(github) tag=v1.3.7.3`，GitHub 源可用
- **自动播放关闭时显示封面预览图**
  - 之前自动播放关着，解析完视频控件是黑的（SurfaceView 未起播），一片黑屏
  - 现在改为藏掉 video、用 img 显示作品封面，点「播放」再无缝切回视频
  - 实测：`VIDEO_PREVIEW 显示封面预览` + 界面 TextureView 0 个 / ImageView 1 个
- **解析后自动播放 / 自动下载开关**（设置 → 解析后动作，默认都关闭）
  - 两个都开启时同时进行：先起下载任务（后台线程），再进播放器
  - 自动播放走全屏播放器，不影响页内小窗预览
  - 不检测主页模式：主页/合集解析时不自动下载，避免误下几百个文件
- **解析成功后清空链接开关**（默认关闭）：解析完成自动清空输入框
- **剪贴板自动识别**（内置常开，无开关）
  - 回到前台时读一次剪贴板，识别到分享口令就自动填进输入框
  - 只在输入框为空时填，不覆盖正在输入的内容；同一内容不重复覆盖
  - 支持一段文本里含多条链接（一行一个或混在文字中），自动去重保序
- **多链接批量解析**
  - 输入框支持一行一个链接；粘贴多链接后直接点解析即进入批量队列
  - 串行执行（WebView 是单实例，不能并发），每条解析完 3 秒推进，60 秒兜底
  - 设置页新增「批量解析」入口，提供多行输入框 + 「读取剪贴板」按钮
- **静默批量解析**：全部跑完才出结果，中途不逐条渲染解析页
  - 开头自动切到列表页，跑完自动跳回列表页并提示「列表共 N 条」
  - 可随时切页签或放着不管
- **批量结果进列表页**
  - 跑完自动渲染到「列表」页签，逐条可下载 / 解析看详情
  - **结果追加，不清掉上一次的批量结果**（`BATCH_START n=2 existing=4`）
- **列表持久化**（设置 → 「保留解析列表」，默认开启）
  - 存 `Android/data/com.re.douyinparse/files/batch_list.tsv`
  - 退出重进自动恢复（TSV 格式，标题/链接/直链/是否图集）
  - 关闭开关时立即删掉已存文件；清空列表时同步删文件
- **清空列表按钮**（列表页「全部下载」下方，带二次确认）
- **上一条 / 下一条导航**（解析页，仅批量模式显示）
  - 显示 `2/3` 位置标签，边界时提示「已经是第一个/最后一个」
- **主页/合集批量下载可选第几个**
  - 「全部下载」改为两步弹窗：选择下载 / 下载全部
  - 多选列表 + 「全选本页」，标签为「第 N 个 · 标题」
  - 文案明确【只处理当前页】，换页请先用上/下一页切到目标页
- **主页 / 合集 / 短剧解析前置确认弹窗**
  - 说明会一次性抓全部作品、会清空现有列表
  - 开了自动下载时额外警告「可能产生大量下载任务」
  - 三个按钮：继续解析 / 关掉自动下载并继续 / 取消
  - 页面自动跳到主页时会拦截确认，确认期间丢弃页面自己抓到的数据
- **批量解析前置确认**：开了自动下载时给同样三选项，可先关掉再跑

### 优化

- **批量解析等待策略**：固定 15 秒改为「解析完 3 秒后推进」+ 60 秒兜底超时
- 批量结果列表标题按来源区分：`批量解析` / `主页`
- 主页 / 合集解析会先自动清空列表（批量模式下不清，保护自己收集的结果）
- 设置页「解析后动作」分组，三个开关副标题实时显示当前状态

### 修复

- **后台下载几分钟就断 / 通知栏进度不同步（重要）**
  - 根因一：`InnerDownloader` 读超时只有 30 秒。切后台后系统限流，30 秒收不到
    新数据就判超时断开，表现为「下载几分钟突然断」。
    → 普通 CDN 读超时改为 **90 秒**、连接超时 20 秒。
  - 根因二：重试只有 3 次且无间隔（断网瞬间立刻重连大概率还是失败）。
    → 改为 **6 次** + 递增退避（1/2/3/5/8 秒），失败时会报已保留的字节数。
  - 根因三（**断点续传形同虚设**）：下载文件名带 `System.currentTimeMillis()`，
    每次点下载都生成新名字，旧的 `.part` 永远匹配不上，续传等于没做。
    → 文件名改为稳定命名（`dy_<作品ID>_<角标>.mp4`），同作品重复下载会接着传；
    已有完整文件时自动加 `_2/_3` 后缀，不会覆盖已下好的文件。
  - 根因四：失败后 `.part` 被主动删除。→ 改为**保留**，供下次续传。
  - 通知栏进度：之前只有静态「下载准备中...」。现在每次进度回调同步到通知栏，
    显示百分比 + 已下载/总大小 + **实时速度**（如 `1.8MB/s`），400ms 节流防刷屏。
  - 下载结束通知栏会显示「N 成功 / M 失败」再收起；失败时提示
    「已保留 X 断点数据，重新点下载会接着传」。
- **Android 7/8 下载崩溃（NoSuchMethodError）**
  - 根因：`DownloadService` 用了 `new Notification.Builder(this, CHANNEL_ID)`，
    双参构造是 **API 26+** 才有。Android 7.x（API 24/25）会抛 `NoSuchMethodError`，
    前台服务起不来 → 下载队列直接崩。
  - 修法：按 `SDK_INT >= 26` 分别构造（低版本用单参 `new Notification.Builder(this)`）。
  - 顺带修 `InnerDownloader.headOk()` 里的 `getContentLengthLong()`（API 24+），
    低版本改用 `getContentLength()`。
- **Android 7 权限被拒后彻底无法下载**
  - 根因：Android 7 上权限被拒绝一次后系统不再弹窗，用户点下载完全没反应，也不知道去哪开。
  - 修法一：App 启动 1.2 秒后主动申请一次存储权限（每次启动只申请一次），不用等点下载。
  - 修法二：新增 `AppInfo.storagePermBlocked()` 判断永久拒绝
    （`shouldShowRequestPermissionRationale == false` 且申请过）。
  - 修法三：被永久拒绝时弹「需要存储权限」引导弹窗，带「去设置」直接跳系统应用详情页，
    并写明操作步骤（存储 → 允许 → 返回重试）。
- **华为 P40 / Android 10 等系统下载失败（能播能听、就是下不了）**
  - 根因：Manifest 缺 `android:requestLegacyExternalStorage="true"`。
    Android 10（API 29）只要有 `targetSdk>=29` 就强制分区存储，
    此时 `WRITE_EXTERNAL_STORAGE` 不再允许直接写 `/sdcard/Download/...`，
    `mkdirs()` / 文件落盘全部失败，表现为「播放正常（走网络流）但下载失败」。
  - 修法一：Manifest 加 `requestLegacyExternalStorage="true"`，
    `WRITE_EXTERNAL_STORAGE` 加 `maxSdkVersion="29"`，Android 10 恢复传统写入。
  - 修法二（兜底，覆盖 Android 11~17）：新增 `ScopedStore`，
    下载前用 `canWriteDir()` 探测目标目录；不可写就先落到应用私有目录
    `cache/dl_tmp`，下完经 **MediaStore** 搬进公共 `Download/DYParse/<分类>/`。
    实测日志：`DL_SCOPED_FALLBACK` → `SCOPED_SAVE_OK` → `DL_SCOPED_MOVED`。
  - 修法三：MediaStore 也失败时，文件保留在私有目录并提示用户；
    `AppInfo.hasStoragePerm()` 修正 Android 10 判定（原先 >=30 一律返回 true）。
  - 新增设置页「存储写入检测」：显示系统版本、权限状态、目标目录、目录是否可写，
    并提供「申请权限 / 重新检测」。用户反馈问题时可据此自查。
  - 长按「存储写入检测」可切换强制兜底（调试用，验证 MediaStore 通道）。
- **批量解析最后一条永不渲染**（列表空但「清空列表」说有 4 条记录）
  - 根因：`batchItemFinished()` 里 `if(curNo >= total) return;` 让最后一条直接返回，
    永远进不了 `runNextBatch()` 的结束分支，导致 `BATCH_DONE` + `renderBatchList()` 永不执行
  - 修法：删掉该 return，最后一条也必须走 `runNextBatch()`
- **自动播放开关无效**（默认关闭时解析视频仍会自动播放）
  - 根因：`render()` 视频分支写死 `doPlay();`，与开关无关
  - 修法：三处渲染出口改为 `if(hasVideo && autoPlayOn()) doPlay();`
- **批量模式下主页判定误清空自己的结果**
  - 根因：抖音短链在 `doParseResolved` 里会被判定成主页，4 处主页入口无条件清空列表
  - 修法：改为 `if(!batchCollectMode)` 保护，批量模式下不清
- **页面在确认框弹出瞬间就抓完整页数据**（绕过确认直接出 20 条）
  - 修法：确认期间拦截主页列表请求（`PROFILE_LIST_SKIP`）+ 丢弃页面数据（`PROFILE_WEB_DROP`）
  - 确认后主动 kick 一次签名翻页（`PROFILE_KICK_SIGNED`），不依赖 WebView 重发

反馈群：995434160

---
## v1.3.7.3 (2026-10-28)
本次为 **版本号算法重构 + 弹窗主题修复** 小版本。核心是修掉一个会让所有老用户
永久收不到更新的版本号算法缺陷。

### 修复
- **检查更新永远提示「已是最新版」（严重）**
  - 根因：`parseVer()` 只解析三段版本号，`v1.3.7.2` 被算成 `1*10000 + 3*100 + 7 = 10307`，
    而 `1.3.7.1` 的 `versionCode` 是 `103701`，`10307 < 103701`，所以永远判定「已是最新」。
  - 更糟的是：老规则下 `1.3.8` 只有 `10308`，比 `1.3.7.1` 的 `103701` 还小，
    **一装 1.3.7.1 就永远收不到后续任何更新**。
  - 修法：版本号规则改为**每段占两位**，`major*1000000 + minor*10000 + patch*100 + sub`：
    | 版本号 | versionCode |
    |---|---|
    | 1.3.7 | 1030700 |
    | 1.3.7.1 | 1030701 |
    | 1.3.7.3 | 1030703 |
    | 1.3.8 | **1030800**（三位版本也能正常递增） |
    | 1.4.0 | 1040000 |
    | 2.0.0 | 2000000 |
    这样三位、四位版本都能正确递增，不会再出现数值倒退。
- **更新源优先级形同虚设**
  - 根因：自建服务器（源 0）拿到 `tag` 后，Gitee/GitHub API（源 1）无条件覆盖赋值，
    服务器发了更高版本也会被仓库的旧版本盖掉。
  - 修法：源 1 改为「只有版本更高才覆盖」，并输出 `UPDATE_SRC api_skip 已有更高版本` 诊断日志。
- **更新弹窗不跟随主题（亮色 App 弹暗色框）**
  - 根因：`showUpdateDialog()` 用的是裸 `new AlertDialog.Builder(this)`，没有指定主题，
    ColorOS 上 `Theme.DeviceDefault` 默认解析成深色。
  - 修法：全工程 **15 处**裸 `new android.app.AlertDialog.Builder(this)` 统一替换为 `dypDlg()`。
    同时让 `dypDlg()` 内部实时调用 `isDarkMode()`，不再依赖可能尚未赋值的 `curDark`，
    避免深色模式下反而弹出亮色框。

### 新增
- **服务器下发升级公告（`upgrade_notice`）**
  - 用于引导老版本用户手动更新：那些装过 1.3.7.1 及以前版本的用户，因版本号算法缺陷
    无法通过 App 内检测拿到更新，只能靠这条公告把他们带到 Gitee 下载页。
  - `version.json` 中这样配置：
    ```json
    "upgrade_notice": {
      "title": "请手动更新到 v1.3.7.3",
      "message": "……说明正文……",
      "url": "https://gitee.com/adaaixianzun/dyparse/releases",
      "until": 1030703
    }
    ```
  - `until` 为版本号阈值：当前 `versionCode` 小于它才弹；同一个 `versionCode` 只弹一次。
    装上 1.3.7.3 之后 `1030703 >= 1030703`，自动不再打扰。

### 变更
- **Manifest versionCode**：`1.3.7.3` → `1030703`（新规则）。
- **老用户升级路径说明**：1.3.7.1 及以前版本自身无法修复该缺陷，
  需要手动到 Gitee 发布页下载一次；覆盖安装不丢数据，装完后续更新恢复正常。

反馈群：995434160

---
## v1.3.7.2 (2026-10-28)
本次为 **抖音主页全量翻页 + 登录体验统一** 小版本，重点是抖音主页能一次拿全（实测 280 条作品 / 16 页），
以及三平台登录后自动回解析页。

### 新增
- **抖音主页全量翻页**：主页作品列表不再只拿第一页。实测 280 条作品主页 **16 页全部翻完**，
  日志 `DYPROF_DONE n=280 pages=16`，列表页 `SHOW_PROFILE_LIST n=280`。
  - 走 PC 主页（`www.douyin.com/user/{sec_uid}`，桌面 UA）+ 签名接口 `aweme/v1/web/aweme/post/`
    （`aid=6383`、`count=18`）+ `a_bogus` + secsdk 签名 + 1500ms 翻页间隔。
  - 同一 sec_uid 只翻一次（`dyProfileDoneSecUid` 锁），避免页面反复发请求导致并发翻页互相覆盖。
  - 翻页完成后自动收起 WebView（`about:blank` + 隐藏 + 收成 1×1），防止页面后续活动覆盖已拿到的全量结果。
- **设置 → 抖音主页登录**：登录成功后**自动回到解析页**，和 YouTube 一致。
  抓到 Cookie 后 800ms 自动隐藏顶栏、清空网页、恢复手机 UA、显示解析页，并提示「登录完成，已回到解析页」。
- **设置 → 抖音 Cookie 状态**：可查看当前已保存的字段（只显示字段名与有无，不显示值）、手动清空。

### 优化
- **三平台登录行为统一**：YouTube（`stopYtLogin`）/ 抖音（`stopDyProfileLogin`）/ B站（`stopBiliLogin`）
  全部改为「抓到 Cookie → 自动隐藏顶栏 + 清空网页 + 恢复手机 UA + 回解析页」，不再需要手动点「完成」。
- **登录菜单改为两步弹窗**：第一步说明文字 + 「选择操作」，第二步纯列表（页面内登录 / 手动填 Cookie /
  查看 Cookie 状态 / 清空 Cookie）。解决之前 `setMessage` + `setItems` 同时使用导致列表被挤掉、
  界面上只剩一个「关闭」按钮的问题。
- **抖音 Cookie 存储路径迁移**：从应用内部目录迁到 `Android/data/<包名>/files/cookies/`，
  文件管理器可见、可备份，卸载自动清理。旧文件自动搬迁，新目录已有内容不覆盖。

### 修复
- **检查更新失效（提示「已是最新版」但实际有新版）**
  - 根因一：`parseVer()` 只解析三段版本号，`v1.3.7.2` 被算成 `1*10000 + 3*100 + 7 = 10307`，
    与三位版本 `1.3.7` 完全相同，且远小于当前 `103701`，所以永远判定「已是最新」。
  - 根因二：更新源优先级形同虚设。自建服务器（源 0）先拿到 `tag` 后，Gitee/GitHub API（源 1）
    无条件覆盖赋值，服务器发了更高版本也会被仓库的旧版本盖掉。
  - 修法：`parseVer` 支持四位版本号 `major*100000 + minor*1000 + patch*100 + sub`
    （`1.3.7.2` → `103702`，与 Manifest 的 `versionCode` 完全一致）；
    源 1 改为「只有版本更高才覆盖」，并输出 `UPDATE_SRC api_skip 已有更高版本` 诊断日志。
- **崩溃：`ClassCastException: LinearLayout cannot be cast to FrameLayout`**
  - 位置：`MainActivity.showDyLoginBar()`。
  - 根因：`web.getParent()` 实际是 `LinearLayout`，代码里硬转 `FrameLayout`。
  - 修法：改为 `instanceof android.view.ViewGroup` 判断，并按父容器类型分别创建 `LayoutParams`
    （FrameLayout 需 `gravity = Gravity.TOP`）。B站 `showBiliLoginBar()` 同类问题一并修好，
    全工程已无 `(FrameLayout) web.getParent()`。
- **连续解析第二次主页只拿到 20/38 条**
  - 根因一：`extractSecUid()` 的正则只匹配 `sec_uid=`，而 PC 主页接口 URL 里是 `sec_user_id=`。
    第一次解析 URL 来自分享页（有 `sec_uid=`）能正常翻页；第二次 URL 已在 PC 主页，取不到 sec_uid，
    进入「单页回退」分支只请求一次。
  - 根因二：翻页完成时把 `profileMode` 置了 false，而 `scheduleProfileRender()` 内部有
    `if(!profileMode) return;`，导致 280 条渲染不出去，屏幕停在 WebView 抢先渲染的少量条目。
  - 修法：`extractSecUid` 补 `sec_user_id=` 与 `/user/MS4wLjABAAAA...` 两种匹配；
    渲染门禁改用独立标志 `dyProfileRenderLocked`，不再依赖 `profileMode`；
    `doParseResolved()` 补齐 `profileMode` / `profileFetching` / `profileCollected` / `profileSeen` /
    `dyProfileJumped` / `dyProfileDoneSecUid` / `dyProfileRenderLocked` 等状态重置。
- **抖音登录页顶部操作条崩溃后无法恢复**：修好 `LayoutParams` 类型后不再抛异常。
- **抖音主页跳转被打回手机版**：跳转前显式设置桌面 UA 并置 `dyProfilePcUA`，
  在 `shouldOverrideUrlLoading` 里拦一次 `m.douyin.com/share/user/`，`onPageStarted` 对 PC 主页模式
  注入 `injectDesktopSpoof`。

### 变更
- **抖音 Cookie 读写策略定版**
  - `dyEffectiveCookie()` 优先级：`CookieManager` 实时（且含 `sessionid=` / `sid_tt=`）→ 本地文件兜底。
    本地文件是登录那一刻的**死快照**，`msToken` / `__ac_signature` / `ttwid` 几分钟即过期，
    拿它翻页第 2 页必返 `{"status_code":0}`，所以不能作为首选。
  - `saveDyCookieLocal()` 只落盘 17 个长期票据字段（`sessionid` / `sessionid_ss` / `sid_tt` /
    `uid_tt` / `uid_tt_ss` / `sid_guard` / `sid_ucp_v1` / `ssid_ucp_v1` / `passport_csrf_token` /
    `passport_auth_status` / `ttwid` / `odin_tt` / `d_ticket` / `login_time` / `is_staff_user` 等），
    不落盘短时效签名字段。
  - `restoreDyCookieToWebView()` 在 `onCreate` 调用，仅当 WebView 里没有登录票据时才灌回，
    避免覆盖实时值。
- **抖音移动版登录入口已彻底移除**：`m.douyin.com` 已下线（404）；手机 UA 访问 `www.douyin.com`
  被 302 到 `/home`；`passport/web/login` 返回「非法应用」（`error_code: 22`）。
  这是平台风控，无免扫码的移动版登录入口。

### 依赖变化
- 抖音主页全量解析**不需要服务器**，也不需要访客凭据，只要有登录 Cookie（登录一次即可）。
- 服务器到期不影响抖音主页解析与下载。

### 已知限制
- 抖音主页翻页依赖登录 Cookie。未登录时只能拿到第一页约 20 条。
- 抖音没有免扫码的网页登录入口，需要手机 App 扫码；或从电脑浏览器复制 Cookie 手动填入。

反馈群：995434160

---
## v1.3.7.1 (2026-10-25)
本次为 **抖音实况图凭据自动获取 + 本地解析** 小版本，主要是把「实况图必须靠服务器」这条依赖砍掉。

版本号说明：本次开始支持四位版本号，`1.3.7.1` 对应 `versionCode = 103701`
（沿用 `major*10000 + minor*100 + patch*1 + sub`，纯数字递增，旧版本可正常识别为更新）。

### 新增
- **抖音访客凭据自动获取**：首次解析实况图时，后台静默开一个 1×1 透明 WebView（桌面 UA）打开 `www.douyin.com`，
  通过挑战后从 `CookieManager` 取 `UIFID_TEMP` 存本地。实测 **2.5 秒**拿到，全程无弹窗、无跳转、无需用户操作。
- **设置 → 抖音访客凭据**：新增入口，副标题实时显示状态（`已有凭据（224 字符）  实况图可本地解析` / `未获取`）。
  点开三选项：自动获取 / 手动填写 / 清空或查看原理。
- **获取过程进度弹窗**：手动触发时显示「正在获取访客凭据…通常 5-10 秒，全程静默，无需你操作」，可取消。
- **获取失败引导**：失败时弹窗说明原因，提供「重试 / 手动填写」两个按钮，不会静默失败。
- **原理说明弹窗**：解释为什么这个值没有算法可伪造、为什么必须由真实浏览器签发。

### 修复
- **抖音实况图只出 1 张静态图、LIVE 角标不出来**
  - 根因：`/aweme/v1/web/aweme/detail/` 接口强制要求 `uifid`，缺失时固定返回
    `403 Blocked by ArgusSecurityPlugin Uifid Not Found`，与 UA（桌面/iPhone/安卓共 3 种）、
    Cookie（无 / 仅 ttwid / 仅 msToken / ttwid+msToken 共 4 种）组合无关（12 组矩阵实测全部 403）。
  - 无法用算法伪造，只能由真实浏览器内核通过 `__ac_signature` 挑战后由服务端签发。
  - 修法：App 内静默获取凭据后本地签名请求，一次通过（`DYDETAIL local-first ok=true http=200 tries=1`）。
- **实况图下载提示文案错误**：`imgTipText()` 一直写着「共N张实况，左右滑动切图，可存为 MP4」，
  实际从 v1.3.3 起封装的就是 `.jpg`（JPEG 主体 + MP4 数据 + XMP 元数据）。
  已改为「下载为 .jpg 实况照片，相册里长按可播放」。
- **设置页手动填写弹窗文案过时**：原文写「必须真实浏览器，App 内抓不到」，实测已证明 App 内可以抓到，已更正为推荐自动获取。

### 变更
- **抖音实况解析优先顺序调整**
  - 有凭据：本地签名（60ms 级）→ 失败回退服务器
  - 无凭据：服务器中转 → 失败回退本地
  - 实测第二次解析：`DYDETAIL local-first ok=true http=200 tries=1 len=51834`，**零服务器请求**，60 毫秒完成。
- **平台支持说明 · 抖音一栏**补充实况图机制说明（自动获取耗时、手动入口位置、本地解析）。
- **调试入口保留并注明**：`--ez dyprobe true`（打开凭据抓取页）、`--es dyurl <url>`（指定抓取页）、
  `--ez localonly true`（强制跳过服务器）。代码里已加注释说明用法，不影响正常用户。

### 依赖变化（重要）
| 场景 | v1.3.7 及之前 | v1.3.7.1 |
|---|---|---|
| 抖音实况图 | **必须**走服务器取签名 | 有凭据完全本地，服务器仅兜底 |
| 服务器宕机时 | 实况退化为静态图 | 提前拿过凭据的用户不受影响 |
| 用户操作成本 | 免填（靠服务器） | 免填（App 自动获取，2-3 秒） |

---
## v1.3.7 (2026-10-25)

本次为 **B站 DASH 画质 + App内登录 + 合集统一 + 纯号输入** 版本。
### 修复

**服务端热修（2026-10-25，无需升级 App）**

**抖音 live 图（实况）解析不出来的真正原因**
- 现象：解析只出 1 张静态图，`【图集】` 没有动图，`LIVE` 角标不出现
- 根因在**服务器**，不在 App：`server.py` 把**监听 socket** 直接 `ctx.wrap_socket()` 包成 TLS
  ```python
  # 旧（错误）
  https_srv = ThreadingHTTPServer(("0.0.0.0", HTTPS_PORT), Handler)
  https_srv.socket = ctx.wrap_socket(https_srv.socket, server_side=True)
  ```
  这样 `accept()` 会在**主循环里同步做 TLS 握手**。只要有一个客户端连上但不发数据
  （网络切换、代理半开、端口探测），整个 accept 就永久阻塞，后面所有连接在队列里排队超时
  （表现为 `Recv-Q` 持续积压、`curl` 全部超时、端口"在监听但连不上"）
- 后果：App 打 `/api/detail` 全部 `SocketTimeoutException`，回退到分享页 SSR；
  而 **SSR 不含实况字段**（`live_photo` / `motion_photo` / `life_share_ext` 全为 0），
  所以只能拿到静态图，实况退化
- 修法：新增 `HttpsServer` 子类，把 TLS 握手下放到 `finish_request()`，每连接一个独立线程
  - 握手加 10 秒超时，连上不发数据会被踢掉，不再拖死 accept
  - 握手失败只影响那一个连接，不影响其他用户
- 效果：`8443` 从「连不上（000 / 超时）」恢复为 `200`，响应 6ms；修复后 12 秒即有真实用户请求成功

**服务端自愈守卫（`dyparse-watchdog`）**
- 每 60 秒探测 8080 / 8443，连续 3 次失败（或监听队列积压超阈值）自动 `systemctl restart dyparse`
- 避免同类单点阻塞需要人工发现 + 手工重启

**说明：本次为纯服务端修复，App 端代码未改动，所有旧版本用户自动恢复，不需要升级。**

**B 站视频播放（不再走服务器中转，直连更快）**

- 根因：B 站 CDN 对**手机 UA** 一律返回 403，且现在强制校验 `buvid3` Cookie
- 现在解析时自动从 `x/frontend/finger/spi` 取 `buvid3` 并缓存，拉流用桌面 UA + Referer + buvid3
- 播放器与下载器都按 B 站分支走这套请求头
- 直连失败时才自动回退 `/api/bili/dl` 中转（作为兜底，不再默认走中转）

**YouTube 第二次解析不再回退（无画面无进度条后提示被 CDN 拒绝）**
- 根因：`ytSrvFallbackDone` / `xSrvFallbackDone` / `biliSrvFallbackDone` 三个「已回退」标志只在首次解析后置位，从不复位
- 结果：第一次解析回退成功后，第二次解析失败就直接放弃，表现为「没有画面没有进度条，过一会提示被 CDN 拒绝」
- 已在 `loadForParse()` 开头统一复位，每次新解析都能正常回退

**TikTok 图文加载不出来**
- 根因一：服务器 `tt_module.fetch_media` 缩进错误，`return p, True` 把整个下载逻辑变成了死代码，缓存未命中时函数返回 `None`，报 `cannot unpack non-iterable NoneType object`
- 根因二：App 端图片加载无缓存，同一张图被并发重复请求，服务器在同一 HTTPS 连接上重复写响应导致 `SSL: BAD_LENGTH` / `Software caused connection abort`
- 已重写服务器 `fetch_media`；App 端 `loadImage` 增加图片内存缓存与并发去重；服务器图片响应后主动关闭连接

### 新增

**B 站合集（ugc_season）解析**
- 以前只看 `pages`（分P），合集视频（如 `av1603124347` 的「游戏移植教程合集」17 集）只解析到当前一集
- 现在读 `view` 接口返回的 `ugc_season.sections[].episodes[]`，把整个合集列成 17 集
- 关键点：合集里**每集是独立视频，aid 各不相同**（如第 3 集 av1603124347、第 5 集 av1453701054）
  - 下载/播放改为用「该集自己的 aid + cid」，不再统一用主 aid
  - 列表标题显示集标题，列表页头显示「B站合集 · 共 17 集」
- 合集优先于分P：同时存在时按合集展示（与 B 站网页一致）

**合集统一进「列表」页（抖音 + B站）**
- 抖音合集原来把列表灌到「解析」页替换详情；现在与 B站一致，全部列到「列表」页签
- **抖音解析页不再空白**：渲染列表时把合集首集的标题/封面/直链填进「解析」页，可直接播放/下载第 1 集
- 解析完自动 `switchTab(1)` 切到列表页，并弹提示告知用户去「列表」页找
  - 抖音：「合集 N 集，已放进「列表」页」
  - B站：「B站合集 N 集，已放进「列表」页」
- B站 主「解析」页保留当前集详情，不再被覆盖

**列表「选择下载」（抖音合集 + B站分P/合集）**
- 右下按钮由「全部下载」改为「选择下载」，点开是多选弹窗
- 可以勾选任意几条一起下，也有「全选」一键提交，不用一条条点
- 文件名统一「标题_第N集」

**B 站画质升级为 DASH（可到 1080P60 / 4K）**
- 原来走 `fnval=1` 整段 mp4，B站对这条老路限制极严，实测匿名只发 480P
- 改为优先 `fnval=4048`（DASH + 4K + Dolby + 8K），从 `dash.video` 里挑 id 最大的一档：
  - 匿名：封顶 480P/720P（服务端决定）
  - 登录（SESSDATA）：1080P（qn=80）
  - 大会员：1080P60（qn=116）/ 1080P+（qn=112）
  - 大会员 + 4K 视频：4K（qn=120），**前提是 UP 主上传的就是 4K**
- 同画质优先 AVC(H.264)，避免部分设备解不了 HEVC/AV1
- 播放用 `MergingMediaSource` 合并视频轨 + 音频轨（复用 YouTube 那套双轨逻辑）
- 下载走 `enqueueDownloadWithAudio`，下完两轨用 `MediaMuxer` 无损合成 mp4
- 拿不到 DASH 时自动回退 `fnval=1` 整段 mp4（老设备/老视频兼容）

**B 站 Cookie 支持（App 内登录，1080P）**
- 设置 → Cookie（本地保存）→ **B站 Cookie**，点开三个选项：
  - **App 内登录**：打开 B站 登录页，登录后自动抓 `SESSDATA` 等，页面保留等点「完成」
  - **手动填写**：粘贴 Cookie（支持浏览器整行 `a=1; b=2`）
  - **清空**：删除本地 Cookie
- 匿名只有 480P（`real=32`），登录后 1080P，大会员 1080P60 / 1080P+
- 与 buvid3 自动合并成一个 Cookie 头，CDN 拉流与下载都生效
- 文件位置：`files/cookies/bilibili.txt`，一行一个 `name=value`
- **Cookie 完全本地，不上传服务器**：view / playurl / CDN 拉流 / 下载全部直连 B站；
  `/api/bili/dl` 中转（仅在直连失败时兜底）只传 `src` 与 `key`，不携带任何 Cookie

**B 站纯 av 号 / BV 号输入**
- 输入框可以直接填 `av1603124347` 或 `BV17m421j7ad`，自动转成标准链接解析
- 支持大小写混合的 BV 号（前缀规范为 BV，后面保持原样）

**B 站 b23.tv 短链展开**
- `https://b23.tv/xxxx` 不再直接丢给解析器，先跟 302 展开成真实链接
  - 单视频：`b23.tv/tWzVg6M` → `bilibili.com/video/BV1Zk7Z66EVn`
  - 合集：`b23.tv/8CbSQFY` → `bilibili.com/video/BV17m421j7ad`（识别 ugc_season 17 集）
  - 主页：`b23.tv/tTxqxYM` → `m.bilibili.com/space/85420121`
- 展开后自动清理追踪参数（`buvid` / `share_session_id` / `timestamp` 等），只保留 `p=` / `bvid=` / `aid=`
- 展开在网络线程执行（主线程会抛 `NetworkOnMainThreadException`）
- `extractUrl` 新增 b23.tv / `m.bilibili.com` 规则；`extractBiliMid` 兼容 `m.bilibili.com/space/{mid}`

**输入区平台提示补全**
- 输入框上方原先写死「支持 抖音 · 快手 · TikTok · X · YouTube」五个平台，B站 和 Instagram 一直没加进去
- 现为「支持 抖音 · 快手 · TikTok · X · YouTube · B站 · Instagram」七个平台

### 变更

- B 站 CDN 拉流新增桌面 UA（`BiliParser.UA_DESKTOP`），仅用于 CDN 请求，不影响 API 请求

---
## v1.3.6 (2026-10-25)

本次为 **Instagram 登录解析 + 代理简化** 版本。

### 新增

**Instagram 登录解析（全本地，不依赖服务器）**
- App 内嵌 WebView 登录 Instagram，自动抓取 Cookie（含 HttpOnly 的 sessionid）保存到手机本地
- Cookie 本地文件：`files/cookies/instagram.txt`，格式一行一个 `name=value`
- 登录后带 Cookie 直连 IG 接口解析 Reel / 视频，拿到 CDN 视频直链，可播放可下载
- 支持手动粘贴 Cookie（浏览器整行 `a=1; b=2` 会自动拆成一行一个）
- **IG 主页解析**：用户名主页列出全部作品，每页 100 条带翻页，支持逐条下载与当页全部下载
  - 优先走 `api/v1/users/web_profile_info`，被 IG 限流(429)时自动回退 WebView + 注入本地 Cookie 收集
- 每次点击「Instagram Cookie」都会先弹**登录风险提示**，需点「已阅读并知晓风险」才进入登录

**B 站解析（本地直连）**
- 单视频 / av 号 / BV 号 / 分P合集解析
- 空间主页列表解析
- view / playurl 接口免签直连；CDN 403 时走 `/api/bili/dl` 中转

**通用 Cookie 本地化**
- 新增 `CookieStore`：`files/cookies/{platform}.txt`，一行一个 `name=value`，name 必须带
- 设置页新增「Cookie（本地保存）」分区：YouTube Cookie / Instagram Cookie / Cookie 文件位置
- YouTube 登录抓到的 Cookie 现在也会落一份到本地文件（服务器到期后仍可查看/复用）

### 变更

- **代理设置简化为单一「内置代理」开关**：
  - 删掉「自动探测本机代理」和「手动自填代理地址」，不再检测用户代理、不再往用户流量上套 Proxy
  - 开着 = 海外平台（TikTok/X/YouTube/IG）走服务器中转
  - 关着（默认）= 全部直连，用户自己挂的代理 / TUN 由系统接管，App 完全不干预
- X 解析优先级调整：内置代理开 → 服务器中转优先；关 → 直连 fxtwitter/syndication 优先，失败再兜底服务器
- TikTok 下载 / 网页请求一律直连（不再有代理分支）
- IG 主页列表改为分页展示（每页 100 条）

### 修复

- X 单推文链接（x.com/user/status/id）被主页判定误吞，恢复单视频解析
- IG 视频直链错放进 `i_imgs` 字段导致走图片队列，改为 `i_play`

---
## v1.3.5 (2026-10-24)

本次为 **YouTube 登录解析 + 下载提速** 版本。

### 新增

**YouTube 登录解析**
- App 内嵌 WebView 登录 Google 账号，自动抓取 Cookie（含 SID/HSID/__Secure-1PSID 等 HttpOnly 登录态）
- Cookie 上传服务器（HMAC 鉴权），yt-dlp 带登录态解析，解除 YouTube 对服务器 IP 的风控
- 登录入口：设置 → 服务器 → YouTube 登录

**YouTube 流式中转**
- 新增 `/api/yt/stream` 路由：服务器实时拉 googlevideo 回传 App，支持 Range（进度条可拖）
- 播放直连优先，403 自动回退服务器中转（无需手动干预）
- 主页列表下载解析完成后自动入队（不用再点一次）

### 优化

- YouTube 下载走服务器中转（googlevideo 直链绑定解析 IP，App 直连必 403），已缓存视频吃本地缓存
- YouTube 优先 H.264（AV1 在部分设备解码失败）
- YouTube 解析合并 yt-dlp 调用：首解 ~22s（原 60+），元数据缓存 4 小时内二次解析 0s
- 服务器升级 yt-dlp 2026.10.21 + yt-dlp-ejs + `--js-runtimes node`，解 n-challenge（格式列表全恢复）
- 所有弹窗跟随 App 主题（`dypDlg()` helper），修复 ColorOS 下弹窗暗色异常
- 服务器地址弹窗精简：只保留服务器地址输入框

### 修复

- YouTube 下载空文件（googlevideo 403 时残留半成品 + 文件名空指针崩溃）
- 主页列表 YouTube 条目下载需二次点击（pendingAutoDl 未在服务器解析路径触发）
- 用户协议 / YouTube 登录弹窗暗色显示异常

---
## v1.3.4 (2026-10-22)

本次为**主页列表 + 三页签 + 境外直连优化**版本。

### 新增

**主页列表**
- 支持 **YouTube 频道 / TikTok 用户 / X 用户主页** 解析（粘贴主页链接即可列出全部作品）
- 主页作品**逐条下载**：点「下载」自动解析直链并入队
- **全部下载**：一键把主页所有作品加入下载队列，串行下载
- 抖音/快手主页暂不支持（网页端需 a_bogus / __NS_sig3 签名，后续版本补充）

**三页签布局**
- 顶部「解析 / 列表 / 历史」三个页签
- 列表页：主页作品卡片（标题 + 时长 + 下载/解析按钮）
- 历史页：下载记录卡片（文件名 + 时间 + 大小），点击显示完整路径

**X 外链卡片深挖**
- 纯卡片推文（无媒体但有外部短链）自动解析：解短链 → 抓落地页 → 挖出视频直链
- 已适配 tasters.bid 类站点（YOURLS 短链 + JS 拼接式视频源）

**安装体验**
- 下载完成后弹「立即安装」按钮，不自动拉起安装器
- 「安装未知应用」权限授权后返回 App 自动继续安装

### 优化

**境外平台代理策略**
- X 挂代理 → 直连解析/下载，不消耗服务器机场流量
- X 直连失败 → 自动回退服务器中转
- 服务器中转地址强制直连（不套用户代理，代理失效也不影响回退）
- YouTube 实测：googlevideo 直链绑定解析 IP，挂代理也拿不到，统一走服务器中转
- 大视频下载前提醒：已挂代理提示走代理；未挂代理弹窗说明服务器带宽限制

> **服务器依赖说明**：YouTube 解析/播放/下载、主页列表（YouTube/TikTok/X）、X 无代理时中转、X 外链深挖、TikTok 下载中转、抖音实况免填凭据、自更新加速——这些功能必须有服务器。服务器到期（2027-04）后自动降级：抖音/快手不受影响，X 挂代理可下载，YouTube 与主页列表不可用。详见 README「服务器依赖」章节。

**画质**
- X：fxtwitter `formats[]` 挑最高码率
- YouTube：`bv*[ext=mp4]+ba[ext=m4a]` 最高画质视频轨 + 音轨合并
- 抖音：`bit_rate` 挑最高码率（已有）
- TikTok：tikwm 原始最高档

**服务器稳定性**
- `fetch_media` 加同 key 并发锁，修复并发请求写坏缓存文件导致的 502
- 外链解析模块 `link_module.py`：短链跳转 + 落地页视频提取 + 缓存下载

### 修复

- X 视频无画面无时长：Referer 错误 + 代理失效无回退
- TikTok 主页下载 502：服务器并发竞态
- 更新安装权限二次调用失败
- 播放器 googlevideo 直链 Referer 错误
- 主页列表自动解析第一集失败会覆盖列表（改为列表优先展示）

---

## v1.3.3 (2026-10-21)

本次为**实况照片封装 + 动图目录**版本。

### 新增

**实况照片封装（真·Motion Photo）**
- 抖音实况图不再只导出 MP4，封装成相册可识别的实况照片（单个 `.jpg`）
- 采用 Google Motion Photo **V2** 格式：`GCamera:MotionPhoto="1"`
- 同时写 `OpCamera:OLivePhotoVersion="2"` / `MotionPhotoOwner="oplus"` / `VideoLength`，兼容 OPPO / 一加 / 华为图库
- 文件布局 `[SOI][XMP APP1][JPEG 剩余][MP4]`，华为图库顶部显示「实况」标签，长按可播放
- 静态图自动转 JPEG（抖音给的是 WEBP，直接拼相册不认）

**「动图」独立目录**
- 实况照片存到 `Download/DYParse/动图/`，与普通图片分开
- 路径可自定义：设置 → 下载路径 → 动图目录
- 「修复媒体库」同步覆盖该目录

**多选下载**
- 多图 / 多实况：弹窗列出每张，标注「图片」或「实况→动图/」，默认勾选当前，支持全选
- 视频合集：可选择下载第几集，文件名格式 `标题_第N集_时间戳.mp4`
- 下载完成显示**实际保存路径**，不再只显示分类名

**快手增强**
- 图文解析：`photoType=SINGLE_PICTURE`，图片取自 `coverUrls`
- live 图集解析：`photoType=HORIZONTAL_ATLAS`，图片在 `ext_params.atlas.list[]`（相对路径），自动拼 `cdnList` 域名
- 背景音乐适配：分享页给的完整音轨 m4a 直接作为 BGM 下载
- 同一文件的多 CDN 备份按 URL path 去重，不再识别成多张

### 优化

**更新策略（不再强制）**
- 弹窗改为三选：`立即更新` / `浏览器打开` / `下个版本再提醒`
- 选「下个版本再提醒」记下当前版本号，该版本及以下不再弹窗
- 手动点「检查更新」时无视该记录，一定会弹
- 保留强制更新能力：服务端 `version.json` 下发 `force:1` 或 `min_version`，低于该版本必须更新，弹窗只有「立即更新 / 退出」

**媒体库**
- 下载成功后自动调用 `MediaScannerConnection.scanFile`，相册立即可见
- 新增「设置 → 修复媒体库」：递归扫描下载目录（含自定义分类目录），批量补录历史文件

**图片后缀修正**
- 读文件头判断真实格式：`FF D8 FF` 保持 `.jpg`，`RIFF....WEBP` 改 `.webp`，`89 50 4E 47` 改 `.png`
- 实况照片（`ff d8 ff e1`）不受影响

### 修复

- **相册看不到已下载文件**：代码里从未通知媒体库，文件在磁盘但不在 MediaStore
- **连续解析残留上一次结果**：`parse()` 现在重置 18 个状态字段
- **先解析图文再解析实况，实况退化为静态图**：根因是 WebView SSR 抢先置 `webDone` 导致 detail 结果被丢弃。新增 `detailRendered` 标记 + `renderGen` 渲染代次，只允许最新代次改界面
- **图片点不开大图**：`OnTouchListener` 消费了触摸事件导致 `OnClick` 不触发，改回 `OnClickListener`
- 实况图 `dynS` 分隔符错位：第 1 张是静态图时，第 2 张的动轨地址被塞到第 1 个位置
- 快手图文/live 图集解析失败（`INIT_STATE 里没找到 photo 节点`）

### 已知限制

- **快手合集无法抓全**：网页端不下发合集列表
  - 分享页 `subBiz=BROWSE_SLIDE_PHOTO` 时只有当前一集
  - `subBiz=KS_HEJI_SHARE` 时也只有被分享的那一集
  - 全集接口（`collection/list`、`heji/list`、`photo/collection` 等）全部 404
  - 唯一存在的 `wd/photo/info` 返回 `2001 antispam need captcha`
  - 结论：只能逐集分享、逐条解析
- **快手不下发动图轨道**：`mainMvUrls` 恒为空，`sameFrame.allow=false`，atlas 路径改 `.mp4`/`.gif`/`.mov` 全 404。作者标 `#live实况动图` 也一样，只能存静态图
- TikTok CDN 直连需代理（平台限制，非 App 问题）

---
## v1.3.1 (2026-10-20)

本次为**内置服务器加速 + 整页滚动优化**版本。

### 新增
- **内置服务器（开箱即用）**
  - 抖音实况照片免填凭据：服务器 UIFID_TEMP 池代签名
  - 短链解析、自更新走服务器，更快更稳
  - 服务器地址编译期注入（`secret_srv.txt`），开源仓库只留占位符不暴露地址
  - 三级降级：用户自填 > 内置默认 > 本地签名/Gitee 回退，服务器到期不影响使用
- **四层安全防护**
  - 字符串混淆：地址/HMAC key/证书 DER 全部异或混淆存储
  - HTTPS 加密：8443 自签证书，抓包只见密文
  - 证书 pin：只信内置证书，中间人攻击直接拒连
  - HMAC 鉴权：`/api/*` 需 X-TS/X-NONCE/X-SIGN 三件套，防白嫖
- **关于页增强**
  - 「为什么用服务器」与区别说明
  - 服务器有效期显示（2027-04-20）
  - 反馈群 995434160 + 一键加群按钮
  - 作者署名：大爱仙尊 / 尘
- **主页**：内置服务器有效期提示

### 优化
- **整页滚动**：视频区/图片区/结果卡片统一进 ScrollView，上滑整页动，不再固定占屏
- **卡片顺序**：核心信息（作品ID/类型/作者/时长）优先，超长直链排后
- **超长直链折叠**：超过 160 字符默认收 3 行，点「展开」看全部

### 修复
- 图片手势与页面滑动冲突：未放大时滑动交给页面，轻点才放大

---

## v1.3.0 (2026-10-20)

本次为**抖音实况照片（Live Photo）+ TikTok 播放修复**版本。

### 新增
**抖音实况照片解析（MP4 落盘）**
- 接入 PC Web 详情接口 `/aweme/v1/web/aweme/detail/`，作为图文/实况作品的主路径
- 完整签名链：a_bogus（SM3 + 自定义 RC4）+ x-secsdk-web-signature + msToken
- ttwid 自动注册：缺少时服务端会 200 空响应静默风控，现已自动获取
- 「抖音访客凭据（UIFID_TEMP）」设置入口：关于 → 访客凭据
  - 该值由服务端签发、无算法可伪造，需用户在桌面浏览器 F12 里复制自己的值填入
  - 不填也能用，只是实况照片退化为静态图
- 实况动轨提取：images[i].video.play_addr → LIVE 角标 → 下载存为 MP4

### 修复
- **TikTok 视频播放 403**：播放器 DataSource 未携带会话 Cookie，
  而直链的 tk=tt_chain_token 会比对 Cookie。现在播放器复用解析会话 Cookie
- 抖音链接自动识别丢路径 bug：正则字符集补上 `/`，`/note/{id}` 不再被截断

---

## v1.2.0 (2026-10-19)

本次为**多平台支持**版本：新增快手解析，TikTok 解析完全重写。

### 新增

**快手解析（全新）**
- 支持短链 `v.kuaishou.com/xxx`、长链 `www.kuaishou.com/short-video/{id}`
- 纯 HTTP 方案，不需要 WebView，解析速度 1.5 秒内完成
- 视频、图集、动图全支持
- **独立音轨提取**：快手分享页额外提供 `soundTrack.audioUrls`（m4a），转 MP3 时优先使用，比下载整条视频再抽音轨快一个数量级（实测 0.9s vs 14s）

**TikTok 解析（重写）**
- 双页面模板互为兜底：
  - `@用户名/video/{id}` → `__UNIVERSAL_DATA_FOR_REHYDRATION__`（含新旧两种 key）
  - `@i/video/{id}` → `<script id="api-data">`
- `@用户名` 页返回 400 时自动回退到 `@i` 占位模板
- 自管 Cookie 会话（`java.net.CookieManager`），解决 CDN 403 问题
- 下载自动分流：按 CDN host 选择直连或代理，失败自动切换
- 支持 `bitrateInfo` 多码率表，优先挑 h265 高码率

**应用内说明**
- 「关于」新增「平台支持说明」入口，内含三平台的实现原理、所需条件、已知限制
- 「关于」新增「更新日志」入口，可直接查看各版本变更
- 入口用抖音红加粗显示，位于版本号上方

**应用图标**
- 全新圆形图标（四角透明，非方块）：品牌红 `#FF2D55` → 紫 `#C026D3` 渐变
- 中心白色播放三角 + 外环三个来源点（代表多平台汇聚，不偏向任何单一平台）
- 径向高光 + 顶部玻璃弧 + 内侧描边，立体感更强
- 五档尺寸 48/72/96/144/192

**启动动画**
- 新增启动页动画（图标缩放淡入 → 上浮 → 标题淡入 → 整体淡出，约 620ms）
- 不用独立 SplashActivity，直接在同一个窗口盖层，无白屏切换

### 改进

- **界面重做**
  - 标题改为「解析器 Pro」+ 渐变竖条品牌块
  - 输入区上方新增平台提示「支持 抖音 · 快手 · TikTok」
  - 操作按钮简化文案（全屏 / 转MP3），4 个等分更整齐
  - 进度条改为卡片容器，播放器加圆角
  - 解析结果卡片加入场动画（逐条淡入上浮，错开 24ms）
- **流畅度优化**
  - WebView 改为懒加载：抖音解析时才初始化，冷启动不再等 Chromium 内核
  - WebView 关闭缩放、文件访问、表单保存、地理定位等无关能力
  - `makeRoundRect` 增加 `ConstantState` 缓存，减少重复创建 Drawable
  - 开启硬件加速，列表关闭 overscroll 效果
- **编译脚本重写**（`build.sh`）
  - d8/r8 自动查找（5 级搜索顺序），找不到自动下载
  - javac 源文件自动收集，新增文件不用改脚本
  - so / dex / arsc 强制 STORED 压缩，修复 `zipalign` 对齐失败
  - 打包后自检：so 压缩方式 + 对齐校验
  - 支持 `NO_DOWNLOAD` / `NO_SDCARD` / `KS_FILE` / `KS_PASS` 环境变量
- **文件名区分来源**：下载与 MP3 统一使用 `dy_`（抖音）/ `ks_`（快手）/ `tt_`（TikTok）前缀
- **提示文案修正**：快手有独立音轨时提示「已拿到独立音轨」而非「从视频中提取」
- **README 重写**：新增快手/TikTok 技术章节、开源项目调研对比、已按平台分组的已知限制

### 修复

- TikTok 解析失效：原代码只找 `id="api-data"`，实际页面已改用 `__UNIVERSAL_DATA_FOR_REHYDRATION__`
- TikTok 视频下载 403：页面与下载未共用同一 Cookie 会话
- 快手结果里重复输出「平台」字段
- 长说明弹窗文字不渲染：`ScrollView` 内 `TextView` 未显式设置 `WRAP_CONTENT` 导致被拉伸

### 已知限制

- TikTok 必须有能访问的网络，直连不通时需配置境外代理
- `*.tiktokcdn.com` 直连、`*webapp-prime.tiktok.com` 走代理，两套 CDN 行为相反
- 抖音倒放仍为高频 `seekTo` 模拟（Android `setPlaybackParams` 不支持负速度）

---

## v1.1.3 (2026-10-18)

### 新增
- TikTok 解析支持（普通链接 / 短链）
- 短剧、影视合集解析，实测 114 集全量抓取
- 合集自动翻页，抓全所有作品
- 「节点体检」诊断代理 IP
- 「仅 TikTok 走代理」分流开关
- 视频 / 图集 / 更新包 应用内下载，带进度条
- 提取音频两段式下载 / 转码进度条

### 修复
- MP3 导出静音（改用 ffmpeg-kit）
- 抖音短链解析失败
- 合集未抓全
- 检查更新失败（Gitee 匿名 API 已全面 403，改三级回退）

---

## v1.1.2 (2026-10-17)

### 新增
- 检查更新三级回退机制
- 代理设置界面

### 修复
- 更新检测在部分网络环境下失败

---

## v1.1.1 (2026-10-17)

### 修复
- 版本号解析错误
- Release 名称显示异常

---

## v1.1.0 (2026-10-17)

首个公开版本。

### 功能
- 抖音视频 / 图文 / 动图解析
- 独立播放器：竖屏保持、横屏自动旋转
- 手势控制：单击显隐控制栏、双击左右快退/快进、长按连续 seek
- 倍速播放 0.5X / 1.0X / 1.25X / 1.5X / 2.0X
- 图集左右滑动、点击看大图
- 小窗与全屏体验一致
- 内置检查更新
