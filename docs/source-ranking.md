# 内置来源排序

2026-10-10 直接请求应用使用的三个来源接口，以 `ac=videolist&pg=1` 返回的 `total` 作为全库条目数：

| 顺序 | 来源 | total | pagecount | 每页条目数 |
| --- | --- | ---: | ---: | ---: |
| 1 | [暴风资源](https://bfzyapi.com/api.php/provide/vod/?ac=videolist&pg=1) | 164,054 | 8,203 | 20 |
| 2 | [量子资源](https://cj.lziapi.com/api.php/provide/vod/?ac=videolist&pg=1) | 158,137 | 7,907 | 20 |
| 3 | [非凡资源](https://api.ffzyapi.com/api.php/provide/vod/?ac=videolist&pg=1) | 98,496 | 4,925 | 20 |

三个接口均返回 20 条首屏数据，分页数与总量相符。网上搜索后直接核查来源接口，未使用第三方描述推算数量。总量随更新变化，统计单位是接口条目，并非去重后的影片、集数或已验证可播放的视频；也包含应用会过滤掉的分类。本次能取得可比的总量，因此无需改用封面稳定性排序。

`LocalStore.sources()` 按此顺序返回三个内置来源，包括已有安装保存的来源；保留启用状态和自定义来源，自定义来源排列在内置来源之后且保持彼此顺序。前端原有逻辑会在启动时选择第一个启用来源，因此默认选择暴风；暴风禁用时依次选择量子、非凡。用户手动换源后，返回首页或从设置返回继续保留仍启用的当前选择。排序采用本次调查结果，不增加每次启动的网络请求。

验证：TypeScript 类型检查、Java 编译和 Android Lint 通过。本地缺少项目要求的固定签名密钥，`scripts/build.ps1` 在签名校验阶段停止；随后仅执行 `compileDebugJavaWithJavac lintDebug -x verifyStableSigning` 完成编译及静态检查，未生成或安装新 APK，未做本次排序的电视实机验证。
