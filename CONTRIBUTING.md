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

本项目定位是 **Android 逆向与 WebView 数据提取的技术学习样本**，不是爬虫工具。
