# React Native TV 改造记录

## 范围

- 首页、跨来源搜索、来源与分类选择、分页、详情、播放线路、选集和观看记录改为 React Native TV。
- 以大海报背景、深色面板、青柠色焦点和卡片弹性缩放实现家庭影院视觉；遵守系统减少动画设置。
- `Catalog`、`Network`、`LocalStore` 和 `PlayerActivity` 继续提供设备端请求、内容过滤、私有存储和原生 Media3 播放。
- 设置按钮进入已有原生影视源管理，返回时重新读取启用来源及记录。
- Launcher 为 `TvActivity`；影视源管理提取为 `SettingsActivity`，旧 Java 首页、搜索、详情与观看记录页面已移除。

## 模拟器截图

以下截图于 2026-10-10 从清理后的 1.2.0 APK 重新截取；本次验证范围见 [代码与文档清理](validation.md#120代码与文档清理)。

![React Native TV 首页](screenshots/home-rn-tv.png)

![React Native TV 详情与选集](screenshots/detail-rn-tv.png)

## 版本与构建

当前源码为 1.2.2 / versionCode 16，版本更新见 [更新日志](CHANGELOG.md#122)。1.2.1 统一弹窗与焦点按钮的本地验证见 [验证记录](validation.md#121统一弹窗与焦点按钮)。下方 1.2.0 版本与截图保留为改造时的历史记录。

固定 React 18.3.1 / `react-native-tvos` 0.75.4-0，仍支持 API 23。使用旧架构 NativeModule 与 Hermes；不同时安装标准 React Native。`.npmrc` 的 `legacy-peer-deps` 避免 TV 虚拟列表的 peer dependency 再安装另一个标准或最新 TV 包。锁文件用于固定依赖。

TV 焦点与按键 API 参考 [react-native-tvos 项目](https://github.com/react-native-tvos/react-native-tvos)。

准备 JDK 17、Android SDK 35、Node.js 22 和原有固定签名密钥：

```powershell
npm ci
npm run typecheck
.\scripts\build.ps1
```

或先安装 npm 依赖、自行配置 Java / SDK 后执行 `gradlew.bat assembleDebug lintDebug`。Gradle 对 debug 也打包 JS 和 Hermes 字节码，安装即可运行，无需启动 Metro。独立验证 JS 可用 `npm run bundle:android`，输出在忽略的 `.tools/rn-preview/`。GitHub Actions 包含 Node 与类型检查步骤，本地构建脚本不会自动推送或发布。

应用 ID、固定签名与 SharedPreferences 名称沿用原生版本，1.2.0 / versionCode 14 用于覆盖升级；已有数据兼容性仍以安装验证为准。React Native 带有原生 CPU 库，通用 APK 覆盖 armeabi-v7a、arm64-v8a、x86、x86_64，包体会比原生版本增大。

## 数据与焦点

- CMS 网络调用在 Java 工作线程执行；页面通过请求代数忽略切换条件后的过期响应。
- 每个来源分别保存页码，失败来源保留页码可重试，成功来源前进并按来源 URL / 影片 ID 去重。
- 焦点通过 `TVFocusGuideView` 和 `Pressable` 管理；弹窗限制焦点范围，列表保留挂载的卡片并在焦点移动时滚动。
- 详情返回、弹窗返回、搜索返回、首页退出按层级处理。退出需要确认。
- 播放资料继续使用私有 `playback.json` 传递，避免大选集超出 Intent 大小限制。
- 观看记录、来源与进度继续使用既有本机存储；播放器返回后刷新进度。

## 已知限制

为保留 Android 6.0，本次采用旧版 React Native TV。改造阶段保存的 `.tools/rn-audit.json` 报告 35 项依赖问题（29 high、6 moderate，包括间接传播的工具链问题），该数量属于当时的审计快照，需重新执行 `npm audit` 才能确定当前结果；不要将 Metro 暴露在不可信网络。正式长期维护前需评估迁移到新版本，并接受其更高 Android 最低版本要求。

JS 类型检查与打包不代表实体电视表现已验证。海报背景以 CMS 竖幅封面模糊处理，不依赖额外背景图接口。第三方来源的可用性、海报与播放线路仍取决于原有接口。

## 改造阶段验证记录（清理前）

- `npm run typecheck` 与独立 JS 打包通过。
- `assembleDebug lintDebug` 通过，Lint 0 errors / 12 warnings；固定签名校验通过。
- 试验通用 APK 约 52 MiB，输出 `dist/LibreTV-Home-1.2.0.apk`，对应校验文件 `dist/SHA256-1.2.0.txt`。版本号为 1.2.0 / versionCode 14，本次未正式发布。
- Android TV API 30 模拟器：真实片源列表、海报、方向键选择、详情与选集加载成功。
- 分类弹窗可以加载叶子分类并获得初始焦点；原生影视源管理返回 React Native 正常，观看记录页能显示新记录。
- 从 React Native 详情进入 Media3，真实视频已出现画面和时长；返回详情后显示“继续观看”，首页返回显示退出确认。
- 1280×720 与 960×540 布局均已目视检查，低分辨率自动压缩首页海报区并调整列数。
- Windows 首次构建发生单项 Gradle 缓存移动失败，清理对应缓存并使用 `--max-workers=2 -Dorg.gradle.vfs.watch=false` 重试后通过。
- 遥控器 EXE 已重新编译，实际调用“打开安卓模拟器”的处理函数成功进入 `TvActivity`；不再硬编码 `MainActivity`。
- 遥控器会恢复已有模拟器窗口；如已连接的是无窗口实例，则正常关闭并以相同 AVD 启动可见窗口，保留安装与本机记录。
- “发现”清除搜索与分类并重新加载首页；搜索按钮仅在获得焦点时高亮。输入框通过可聚焦外框参与方向键导航，按确认进入编辑；已验证搜索按钮按左键可以选中输入框。
- 首页首排海报显式设置向上目标为搜索输入框，搜索区不再记忆编辑控件焦点；已验证首排向上、列表逐行向上，以及编辑搜索后返回首页再向上。
- “加载更多 / 重试”先将焦点交给当前列表末项；首页按首项 key 保持行节点、按影片 key 保持海报节点，补齐末行不会重建已有海报。模拟器连续验证 20→40→60→80 部，60→80 时焦点保持在“萤火虫之婚”。
- 尚未验证实体电视、API 23 真机、全部 CPU 架构的运行以及远端 GitHub Actions。
