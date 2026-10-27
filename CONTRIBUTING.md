# 贡献指南

感谢关注本项目。提交代码前请先阅读以下约定。

## 开发环境

- JDK 8 及以上
- Android SDK Build-Tools（含 `aapt`、`d8`、`zipalign`、`apksigner`）
- 不需要 Android Studio / Gradle

## 代码风格

本项目采用的是 **单文件 Activity + 匿名内部类** 的极简写法，目的有二：

1. 体积小，产物仅 40 KB 左右；
2. 便于用 `aapt` + `javac` + `d8` 直接构建，不引入 Gradle 依赖链。

因此请遵循：

- **不要引入第三方库**（OkHttp、Gson、AndroidX 等一律不加）
- JSON 解析继续用现有的 `JP` 内部类
- 回调统一用匿名内部类，不用 lambda（`javac -source 8` 虽支持，但 `d8` desugar 在无 AndroidX 环境下有坑）
- 字符串里的中文用 `\uXXXX` 转义（源码保持 ASCII，避免编码问题）

## 提交前自测

```bash
bash build.sh
# 确认输出 SIGN_OK，且 build/obj 下有 .class 文件
```

然后装机验证：

```bash
adb install -r build/DYParse.apk
adb shell am start -n com.re.douyinparse/.MainActivity \
  --es url 'https://www.iesdouyin.com/share/video/<任意视频ID>/'
```

## 提交 PR

- 一个 PR 只做一件事，别混功能
- 说明改了哪里、为什么改、怎么验证的
- 涉及解析逻辑的改动，请附上验证用的样本链接与 logcat 关键片段

## 不要做的事

- 不要加入批量抓取、并发轰炸类功能
- 不要绕过平台风控（签名算法、验证码识别等）
- 不要提交任何真实用户数据、Cookie、Token

## 发布纪律（重要，防封号）

历史教训：GitHub REST API 批量发布 / 高频 push 曾触发风控导致账号被封、Actions 被滥用导致账号受限。所以：

- **两平台（Gitee + GitHub）一律慢节奏**：逐文件带间隔（≥15 秒），只推本次实际改动的文件，不整仓推
- **不批量连发 API**，不写循环脚本连续创建 Release
- **Actions 能不用就不用**：本地编译好后手动传 APK，不上传构建脚本
- **GitHub 只在账号正常时发**；被封 / 申诉期间只发 Gitee
- **开源仓库只放空壳版**：
  - `java/com/re/douyinparse/Srv*.java` 是占位版（无真实地址 / 凭据 / 证书）
  - 真版在 `secret_src/`，已 `.gitignore`，绝不进仓库
  - `tools/inject_srv.py` 只在本地构建时注入，不作为仓库源码
- **发布前必检**：`grep -rn "116.205.105.44" java/` 应为 0 命中；APK 用 `strings` 也应搜不到明文地址
- Release 描述与 version.json 的 body 必须带反馈群号 995434160

## 发布流程

1. 更新 `CHANGELOG.md`（新增 / 变更 / 修复分节）与 App 内 `changelogText()`
2. 更新 `README.md`（平台列表、功能列表、技术实现表、服务器依赖）
3. 更新 `version.json`（tag / name / body / apk 链接），APK 文件名从 Manifest 读版本号
4. 本地 `PLAIN=1 bash build.sh` 产原包留档，`bash build.sh` 产加密包
5. 加密包装机实测，确认关键日志正常
6. 按「发布纪律」慢节奏推 Gitee；GitHub 等通知后同样慢节奏补发
7. 服务器 `/opt/dyparse/version.json` 同步更新（scp + chmod 640）

本项目定位是 **Android 逆向与 WebView 数据提取的技术学习样本**，不是爬虫工具。
