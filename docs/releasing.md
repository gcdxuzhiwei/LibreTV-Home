# 发布安装包

源码保存在 Git 中，安装包通过 [GitHub Releases](https://github.com/gcdxuzhiwei/LibreTV-Home/releases) 的附件分发。`dist/` 是本地输出目录，旧 APK、源码 ZIP 和校验文件可以保留作本地备份，均不需要提交到远端。

## 构建与版本

构建环境需要 Node.js 22、JDK 17、Android SDK 35 和 Build Tools 34.0.0。首次构建先执行 `npm ci`，Android Studio / Gradle 同步也依赖 `node_modules` 中的 React Native 插件。`scripts/build.ps1` 会在缺少依赖时安装，并在 Android 构建前执行 TypeScript 检查；APK 内包含 JS 与 Hermes，运行时无需 Metro。

1. 在 `app/build.gradle` 更新 `versionName`，并递增 `versionCode`。
2. 同步 `package.json` 与锁文件中的项目版本、`docs/CHANGELOG.md`，运行 `./scripts/build.ps1`。脚本执行类型检查、`assembleDebug lintDebug`，然后按构建的实际版本号生成 APK 和 SHA256 文件。
3. 核验安装包版本、签名、安装与遥控器操作，并将结果记录到 `docs/validation.md`。

当前脚本生成 **debug APK**，不是正式 release 签名包。若分发该包，请在发布说明中标明调试版。长期公开发行应先配置自己的 release 签名，并安全备份密钥；覆盖安装要求签名一致。密钥和密码不放入仓库，其他电脑或 CI 的默认 debug 密钥也不保证与已有安装包一致。

### 固定签名与覆盖升级

从 1.1.6 起，`assembleDebug` 明确使用 `.tools/signing/debug.keystore`，也可通过 `LIBRETV_KEYSTORE` 指定已有密钥文件的路径。不再依赖构建环境的默认 debug 密钥位置。新电脑需从安全备份恢复这把密钥，不要生成替代密钥。

构建前校验证书是否与仓库中的 `signing-certificate.sha256` 一致；本地打包脚本和 CI 还用 `apksigner` 校验最终 APK，签名不同即停止。该文件仅含公开证书指纹，可以提交；私钥仍禁止提交。当前固定指纹为 `22c687c5b0e6290da2c98f88765d3730c97e74e418545dec5ec2f5055eecd9d5`。

此前 GitHub 1.1.3、1.1.4、1.1.5 的 APK 实际签名互不相同，也与当前固定证书不同，无法直接覆盖这些旧版。需卸载后安装新固定签名版本一次，之后始终保留这把密钥并递增 `versionCode`，即可覆盖升级。不要修改指纹来绕过校验，否则已有安装仍无法升级。

## 自动发布（推送到 main 且版本变化）

`.github/workflows/release.yml` 在推送到 `main` 后，先比较整次推送前后的 `app/build.gradle`：只有 `versionCode` 或 `versionName` 变化，才执行构建与 Lint，成功后创建一个正式 GitHub Release、标记为 **Latest** 并上传 APK 和 SHA256 文件。APK 仍为 debug 构建，GitHub 的正式发布标记不改变构建类型或签名。版本未变化时，仅运行版本检查，跳过 APK 构建和发布；改动 App 代码但未更新版本也会跳过。首次推送允许构建，也可在 Actions 页面手动运行，手动运行不受版本变化限制。仅本地 `git commit` 不触发；一次 push 包含多个提交时，比较推送前后的版本，只构建该次推送的最后一个提交。其他分支不自动发布。

版本 tag 采用与 `versionName` 对应的 `v<版本号>`，例如 `v1.1.10`，绑定实际构建的完整提交。标准版本标签便于 GitHub 正确比较同日发布的版本，避免 `build-9` 排在 `build-10` 前面。每个应用版本保留独立下载记录；重复运行时，若该版本已发布，则跳过发布并保留原有附件。需要发布新代码时，应同时更新应用版本。旧的 `build-*` 历史发布保留不变。

### 首次配置签名 Secret

为了让自动构建与本地调试包使用同一签名，需要将项目固定密钥保存为仓库的 Actions Secret。已有 Secret 也需要确保它是这把密钥。

1. 在构建过当前安装包的电脑上，执行以下 PowerShell 命令，将密钥的 Base64 内容复制到剪贴板（不写入项目文件）：

   ```powershell
   $tvDebugKeystore = Join-Path (Get-Location) '.tools/signing/debug.keystore'
   [Convert]::ToBase64String([IO.File]::ReadAllBytes($tvDebugKeystore)) | Set-Clipboard
   ```

2. 在 GitHub 仓库 **Settings → Secrets and variables → Actions → New repository secret** 中，新建 `ANDROID_DEBUG_KEYSTORE_BASE64`，粘贴剪贴板内容并保存。随后清空剪贴板。
3. 将完整项目（含 `.github/workflows/release.yml`）提交并推送到 `main`，到 **Actions → Build and publish APK** 查看执行结果。如果版本未变化，可使用 **Run workflow** 手动构建。GitHub 自动提供的 `GITHUB_TOKEN` 用于发布，无需另建个人访问令牌；仓库或组织策略须允许 Actions 写入仓库内容。

未配置签名 Secret 时，流程会明确报错并停止，不会生成随机签名的安装包。Base64 是编码，不是加密，内容只粘贴到 Secret，不提交到源码或发布附件。当前流程使用标准 Android debug 密钥配置；正式 release 签名包需要另行配置签名流程。

CI 将密钥恢复到 runner 的临时目录，并用 `LIBRETV_KEYSTORE` 明确传给 Gradle；任务结束时删除临时密钥。Secret 内容错误、证书指纹不匹配或 APK 签名校验失败时均不会发布附件。本地 SDK 需包含 Build Tools 34.0.0，以供打包脚本调用 `apksigner`。

自动构建使用 `app/build.gradle` 中的版本号，不自动修改源码。每次升级都需递增 `versionCode`、更新 `versionName`，以创建新的版本 tag。自动发布仅表示构建和 Lint 通过，不代表实体电视操作已验证。

## GitHub 网页发布（正式版本）

1. 将该版本对应的完整源码、构建脚本、文档及许可提交并推送到仓库。
2. 打开仓库的 Releases 页面，选择 **Draft a new release**。
3. 创建与 `versionName` 对应的 tag，例如 `v1.1.3`，确认它指向构建 APK 所用的源码提交。
4. 填写版本标题与更新说明，注明构建类型及已完成的验证。
5. 将 `dist/LibreTV-Home-1.1.3.apk` 与 `dist/SHA256-1.1.3.txt` 拖入附件区域，检查后发布。后续版本替换为实际版本号。

发布完成后，用户在该 Release 的 **Assets** 中下载 APK。GitHub 会按 tag 自动提供 **Source code (zip)** 和 **Source code (tar.gz)**，无需重复制作源码 ZIP。必须先提交完整源码再创建 tag，确保下载到的源码与 APK 对应。

已有 `dist` 文件可作为本地历史备份；不要将历史 APK 挂在不对应的源码 tag 下。本地构建脚本会重写当前版本的校验文件，仅列入本次生成的 APK。自动发布的产物在 GitHub runner 上生成，不依赖本地 `dist`。

官方说明：[关于 Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)、[管理 Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)。
