# Android 6 电视 HTTPS 兼容

2026-10-10 在小米 MiTV4-ANSM0 / Android 6.0.1 / 应用 1.2.1 上取得现场日志：视频加载失败的异常为 `CertPathValidatorException: Trust anchor for certification path not found`。这与用户此前截图中的 HTTP 404 是不同错误，404 本次没有复现。

量子视频 `vip.lz-cdn12.com` 和暴风封面 `p.bfvp26.com` 的证书链均终止于 ISRG Root X1，电视系统缺少此根证书。官方平台兼容说明见 [Let's Encrypt Certificate Compatibility](https://letsencrypt.org/docs/certificate-compatibility/)。

## 应用修复

- `CompatibleTrust` 保留系统信任库，并增加官方自签名 ISRG Root X1 根证书。系统校验失败后，仍通过标准 TrustManager 校验额外信任链，不接受任意服务器证书。
- `Network.initialize` 在 Application 启动时配置 OkHttp。CMS 与 Media3 使用此客户端，React Native 的 OkHttp 工厂继承其配置，供 Fresco 封面和 JS 网络请求使用，并保留 React Native 的 CookieJarContainer。
- 域名校验、证书签名与有效期校验保留。初始化失败时回退到系统信任库。
- 采用应用内 TrustManager 是因为 Android 6 / API 23 尚不支持 Network Security Config；无需修改电视系统证书、DNS 或代理。

根证书位于 `app/src/main/res/raw/isrg_root_x1.pem`，下载自 [Let's Encrypt 官方 PEM](https://letsencrypt.org/certs/isrgrootx1.pem)，对应 [官方证书列表](https://letsencrypt.org/certificates/)。证书 DER 的 SHA-256 指纹为：

```text
96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6
```

## 实机网络验证

通过 ADB 在电视上运行独立 DEX 探针，使用项目实际 `CompatibleTrust.java` 与相同的 OkHttp 4.12.0。没有覆盖安装应用，也没有修改系统信任库。

| 请求 | 系统信任配置 | 修复信任配置 |
| --- | --- | --- |
| 量子视频 HLS 入口 | 缺少信任根，握手失败 | HTTP 200，application/vnd.apple.mpegurl |
| 暴风 JPEG 封面 | 缺少信任根，握手失败 | HTTP 200，image/jpeg |
| 量子 JPEG 封面 | 连接被重置 | 连接仍被重置 |

探针另外验证了不受信任的自签名证书被拒绝。兼容类通过 Java 8 编译，并生成 min-api 23 的 DEX 后在电视执行。

以上为最初的独立网络测试；后续完整应用验证如下。

## 本地临时签名 APK 与电视安装

用户明确授权卸载电视原应用并安装本地测试版。随后准备 Node.js 22.14.0、JDK 17、Android SDK 35 / Build Tools 34.0.0、Gradle 8.9，运行 `npm ci`、类型检查和 `assembleDebug lintDebug`，全部通过，Lint 0 errors / 11 warnings。

原固定签名密钥在另一台电脑。本次生成独立的 `.tools/signing/local-test.keystore`，通过 `LIBRETV_KEYSTORE` 指定，并仅在此次 Gradle 调用使用 `-x verifyStableSigning`；没有修改正式签名指纹或生产构建的签名校验逻辑。

- APK：`dist/LibreTV-Home-1.2.1-local-test.apk`，版本 1.2.1 / versionCode 15，54,031,386 字节。
- APK SHA-256：`905b67274e13de2fa977532976cdeaf513b3ee91a4e61afd76f4b929cd430193`。
- 临时证书 SHA-256：`8cd4bb39c19f2eb6f45c64830a7d810537c36bcdd79b98fb153d30bab60e791f`。
- 原 APK 备份到 `.tools/tv-backup/base.apk`，签名确认与仓库正式指纹一致；私有 `files` 和 `shared_prefs` 备份到 `.tools/tv-backup/app-data.tar`。这些目录及密钥均不提交 Git。
- 上传后核对电视端 APK SHA-256，再卸载原应用并安装测试版，安装返回 Success，设备确认安装时间 2026-10-10 23:10:34。
- 恢复观看记录、来源设置和播放资料，实际“继续观看”页面仍有原来的两部影片。
- 首页当前非凡资源列表的多个封面在 ADB 截图中正常显示。
- 重新播放量子《超级夜总会》20220702，加载总时长约 1:13:18，播放进度持续增加，无新的播放器 / 网络配置错误；用户在电视上确认画面和声音正常。

测试截图位于忽略目录 `.tools/diagnostics/tv-local-home.png`、`tv-local-playback.png`。视频硬件层未出现在 ADB 截图中，实际声画确认来自用户。当前验证覆盖此电视的首页列表和上述影片，不代表所有线路可用。恢复正式签名版本时仍需先卸载临时签名测试版。

## 网络拦截仍需单独处理

非凡封面 `fffgood.com` 和量子封面 `img.lzipic.com` 在电视及电脑直连时发生连接重置。非凡封面的 HTTP 请求被重定向到“该网站疑似诈骗网站”提示页；电脑通过已有代理访问原 HTTPS 图片则为 200。补充根证书无法修复此类网络拦截，不应将 HTTPS 降级到 HTTP 处理。

因此旧系统证书兼容修复后仍需逐个来源验证封面、媒体入口及分片；某一个影片能播放不能代表所有来源正常。

## 非凡第二页与量子封面：设备端补全

用户进一步反馈非凡第二页和量子封面仍不可见，要求 App 独立运行，不依赖电脑或代理。电视上再次使用同版本 OkHttp 探针请求非凡 `fffgood.com` 与量子 `img.lzipic.com` 的原始封面：系统信任与补根证书两种配置均出现连接重置。同一图片经电脑已有代理为 HTTP 200，但此结果仅用于确认文件存在，不作为应用运行方案。

对比 Google 与 Cloudflare 的公共 DNS，两者给出的 IPv4 地址与本地解析相符；资源方公布的旧图片域名也未找到可用直连地址。非凡当前公告将 `img.feifeiimg.vip` 改为 `fffgood.com`；量子公告将 `viptulz.com` / `img.lzzyimg.com` 改为 `img.lzipic.com`。原始域名的网络问题不能由补根证书解决。

新增 `CoverFallback` 与 `CoverImage`，原图失败后在设备上向其他已启用 CMS 搜索，严格匹配完整片名、四位年份及类型。保留原影片对象、来源、播放线路与观看进度，仅使用候选图片地址；仅跳过失败图片的完整地址，同域其他路径仍可作为候选。每次最多查询四个其他来源，两个独立后台线程、32 个排队任务，不占用列表/详情任务线程。图片展示共享最多 128 条、10 分钟的进程内缓存，缩略图、焦点区及详情复用查询，换影片时重置错误状态。队列繁忙或来源查询失败且未取得候选时不缓存为空结果，下次展示允许重试。无匹配或候选图片也失败则保留占位图。未添加代理、服务端或修改系统 DNS。

在小米电视上直接运行实际 `CoverFallback` / `Catalog` / `Network` 类的 DEX 探针，确认以下检查通过：精确匹配；拒绝不同年份、类型、季数、缺年份、缺类型；暴风资源能查到《末誓》《超级夜总会》《顶点武装》的相同影片封面，并经电视直接下载成功，分别为 15,778 / 27,687 / 34,302 字节。此验证不依赖电脑转发请求。

完整应用的类型检查、`assembleDebug lintDebug` 通过（0 errors / 11 warnings）。临时密钥签名校验通过；新 APK 为 `dist/LibreTV-Home-1.2.1-coverfix-local-test.apk`，54,489,313 字节，SHA-256 `dbb141c39c560c0f5748ebb8f920b8e7a51ee1f05170df34a2c900fe624ceef9`。电视端上传后再次核对哈希，以 `pm install -r` 覆盖上一临时测试版，返回 Success。

ADB 截图确认量子首页可见六张封面和顶部海报，包括《顶点武装》《超级夜总会》；非凡加载第二页后可见《Project XY》《借口Go》《剧场版假面骑士零一真实×时间》《后西游记第一季》《美人余》《喜剧之王2026》的封面。原来源标签仍为量子/非凡。截图保存在 `.tools/diagnostics/tv-coverfix-liangzi.png` 和 `tv-coverfix-feifan-page2.png`。未重新进行完整视频播放测试；这次没有改动播放器代码。匹配不到其他可用封面的影片仍显示占位图，不能保证全库封面可用。

2026-10-11 修复审查发现的缓存与任务生命周期问题：缓存键包含已启用来源的 URL 集合，增删或启停来源后，当前封面重新展示并使用对应配置的候选缓存。共享任务在最后一个图片组件卸载时取消，通过原有 RequestScope 取消进行中的 OkHttp 请求，并清除线程池中的已取消排队任务。队列繁忙时，仍挂载的图片以 0.5 秒起、最高 5 秒间隔退避重试；卸载时清除重试定时器。网络失败仍留待下次展示重试，不缓存为空结果。

隔离模拟验证了来源变更后的空缓存刷新、成功图片缓存复用、共享请求最后一个展示者卸载后取消、取消后重新挂载不受旧 Promise 影响、队列繁忙后重试成功以及卸载清除重试定时器。TypeScript 类型检查、`compileDebugJavaWithJavac lintDebug -x verifyStableSigning` 通过；仅跳过缺少固定密钥的签名校验以验证编译和静态检查，未生成安装包或进行本次电视实机验证。
