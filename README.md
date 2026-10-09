# LibreTV 家庭影院

基于 [LibreSpark/LibreTV](https://github.com/LibreSpark/LibreTV) 的影视采集协议，重新实现的原生 Android TV 应用。面向小米电视 / 小米盒子及其他 Android 6.0（API 23）以上电视设备。无需账号或启动密码，无需部署网站、Node.js、Docker、代理服务或应用后端。

## 安装

从 [GitHub Releases](https://github.com/gcdxuzhiwei/LibreTV-Home/releases) 的版本附件（Assets）下载 APK；发布后即可在这里下载安装包。当前本地构建生成 `dist/LibreTV-Home-1.1.6.apk`，这是已签名的个人使用调试版，可直接侧载安装。`dist` 仅保存本地产物，不提交到源码仓库。从 1.1.6 起固定签名，后续可覆盖升级；此前 GitHub 版本的签名不同，需要卸载后重新安装一次。

1. 将 APK 复制到 U 盘，插入电视。
2. 在电视设置中允许相应文件管理器安装未知来源应用；不同 MIUI TV 版本入口不同。
3. 用电视文件管理器打开 APK 并安装；在应用列表打开「LibreTV 家庭影院」。

如电视支持 ADB 调试，也可运行：

```powershell
adb connect 电视IP:5555
adb install -r .\LibreTV-Home-1.1.6.apk
```

APK 不含原生 CPU 库，适用于 32 位 / 64 位 ARM 和 x86 设备。具体系统、解码器和安装权限仍取决于电视型号。本项目不要求 Google Play 服务。

## 使用

- 打开直接显示最新影视列表。通过「选择来源」和「全部分类」浏览列表，底部加载下一页。
- 搜索名称时同时请求所有启用的影视源，逐源展示结果；同名不同源保留独立卡片，便于换源。
- 选择影片进入详情，立即播放、选集或切换播放线路。「搜索其他来源」可查找同名片源。
- 「继续观看」保存最近 30 部影片的集数与播放进度，播放器每 5 秒及退出时保存，可清空记录。
- 「影视源」支持启用、检测、添加和删除苹果 CMS JSON 接口，不依赖远程配置订阅。

遥控器：每次进入首页先聚焦「继续观看」并重新加载第一页，加载完成后聚焦列表第一项（加载期间主动移动焦点则保留选择）；列表页没有焦点时，第一次按任意方向键恢复到「继续观看」，随后方向键移动焦点，确定选择。详情页返回回到列表，列表页返回询问是否退出，选择「确定」退出应用，「取消」或返回关闭弹窗。播放时菜单键打开选集；焦点在播放区域时，无论控制条是否显示，左右键均快退 / 快进 15 秒，确定暂停 / 继续。上键切换到顶部按钮，左右选择按钮、确定执行；下键回到播放区域。顶部按钮获得焦点时控制条保持显示。中央仅保留快退 15 秒、暂停 / 继续、快进 15 秒图标；上方可选集、切线路、上一集或下一集。返回第一次收起控制条，再按一次退出播放器。当前集播完会自动播放下一集。

## 无后端的含义

搜索、列表、详情、封面和视频均由电视直接访问第三方影视来源，仍需要互联网和可用片源。App 不保存视频，不提供影视内容；第三方源可能失效、限流或要求特殊请求头。检测接口可用不代表每部影片都可播放。只支持直接媒体线路（HLS、MP4、DASH 等），不支持网页解析、TVBox Spider/JAR 脚本或付费平台 DRM。

内置源是可修改的设备端默认配置，不是对其内容或长期可用性的承诺。不要关闭 TLS 证书验证来绕过接口错误；可更换来源。观看进度和源设置仅在应用私有存储中保存，没有登录、启动密码或远程同步。

## 构建

Android Studio 打开项目，使用 JDK 17、Android SDK 35。命令行：

```powershell
.\gradlew.bat assembleDebug lintDebug
```

已下载本地工具的当前工作区也可直接运行：

```powershell
.\scripts\build.ps1
```

构建脚本使用项目目录的 `.tools` 工具与 Gradle 缓存，从构建元数据读取版本号，校验 APK 签名后复制到 `dist/LibreTV-Home-<版本号>.apk`，并生成 `dist/SHA256-<版本号>.txt`。其他环境需要设置 `JAVA_HOME`、`ANDROID_HOME` 或 `local.properties`。固定密钥需恢复到 `.tools/signing/debug.keystore`，或通过 `LIBRETV_KEYSTORE` 指定路径，详见 [发布说明](docs/releasing.md)。首次构建需要下载 Gradle / Maven 依赖；这是开发时依赖，App 运行不需要构建环境。没有提交密钥；正式发布应配置自己的 release 签名并保持升级签名一致。

## 项目结构与发布

```text
app/                 Android 应用源码、资源与模块配置
gradle/wrapper/      Gradle Wrapper（含必要的 wrapper.jar）
scripts/             本地构建脚本
.github/workflows/   推送到 main 后构建并发布 APK
docs/                发布说明、更新日志、验证记录与上游参考
build.gradle         根构建配置
settings.gradle      模块与依赖仓库配置
gradle.properties    共享构建参数
gradlew / gradlew.bat 跨平台构建入口
LICENSE              项目许可
```

`.tools/`、`.gradle/`、`.idea/`、各模块的 `build/`、`dist/`、本机 SDK 路径和签名密钥均不提交。应用图片资源、文档截图及 Gradle Wrapper 属于项目所需文件，保留在仓库中。

每个发布版本将 APK 和校验文件上传到 GitHub Release 附件；源码随版本 tag 保存，GitHub 自动提供源码 ZIP / tar.gz，无需把每版 APK 和源码 ZIP 提交进 Git。操作步骤见 [发布说明](docs/releasing.md)，版本变化见 [更新日志](docs/CHANGELOG.md)。

已配置 GitHub Actions：每次推送到 `main` 后，构建与 Lint 通过即发布一个独立的预发布版本。首次使用需按 [发布说明](docs/releasing.md#首次配置签名-secret) 设置 `ANDROID_DEBUG_KEYSTORE_BASE64`，确保安装包签名一致；本地提交尚未推送时不会触发。

## 实现与许可

- Java 原生 Activity / GridLayout / ScrollView，标准电视启动入口、横屏与遥控器焦点。
- AndroidX Media3 ExoPlayer 1.5.1：HLS / DASH / 常见文件播放，硬件解码失败时尝试备用解码器，自动下一集与续播。
- `Catalog.java` 将 LibreTV 的苹果 CMS 请求与 `vod_play_url` 解析迁移到设备端，使用独立网络线程、超时、响应大小限制和过期请求保护。分类只展示叶子分类，避免部分源的父分类返回空列表。
- 未移植 LibreTV 网站的服务端鉴权、代理、豆瓣推荐和网页播放器。`docs/upstream-cms-parser.ts` 与 `docs/upstream-types.ts` 是 2026-10-09 从上游 main 获取的参考源码。

本项目遵循 GNU AGPL-3.0，完整许可见 `LICENSE`。原 LibreTV 归 LibreSpark / LibreTV 贡献者所有；本项目并非小米或 LibreSpark 官方客户端。再分发应用时应一并提供对应版本源码和许可。Media3 使用 Apache-2.0 许可。

验证结果与限制见 `docs/validation.md`。
