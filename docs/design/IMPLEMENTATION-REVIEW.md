# TV 根工程迁移与前端验收

最新结果见本文「v2.1.0 preview 发布前复验」；迁移与前端重构结果按阶段保留。

## v2.1.0 preview 发布前复验

版本为 `2.1.0 preview`（versionCode 12）。修复会话 Cookie 并发覆盖、市场请求失败被显示为空市场、跨平台数字 ID 查询错误评论；更正作者与鸣谢，并让关于页读取实际版本号。详见 [发布前检查](../RELEASE-REVIEW-v2.1.0-preview.md)。

- Debug／AndroidTest 构建通过；JVM 48 项通过（app 44、ncmapi 4）。
- Lint 零错误，既有警告 app 662、ncmapi 5，数量未增加。
- API 36 模拟器：960×540dp／字号 1.0 运行全部 10 项 UI 用例；其余三组定向复验设置和关于页，共 13 次通过。
- 关于页用方向键从音质进入兼容设置，再到署名阅读区。标准及紧凑、大字号下焦点区域完整可见，长文沿用方向键滚读。
- 归档 22 张原生截图、5 份日志及哈希清单；[构建日志](implementation/v2.1.0-preview/validation.log)、[标准 UI 回归](implementation/v2.1.0-preview/device-standard.log)、[标准关于页](implementation/v2.1.0-preview/settings-about-preview-standard.png)、[紧凑大字号关于页](implementation/v2.1.0-preview/settings-about-preview-compactLarge.png)。

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`，27,647,471 字节，SHA-256：`457ff6078f40d17b81769920565559f37ffb57d6c7372e65de937f102e4424c3`。正式签名与发布状态另行补充。

本次未重测所有真实账号、实体手机、WebDAV 或低性能盒子；下方各阶段保留各自验证范围。

## 迁移验收（历史）

迁移基线为 `feat/pixelplay-tv-compose-v2.1` 的 `4b791ab`。应用 ID 为 `com.cutedino.xiangsuplayer`。

## 迁入内容

- 首页、左侧导航、MiniPlayer、全屏播放及其他 TV 页面采用当前设计系统。产品事实和页面规范见 [PRODUCT.md](../../PRODUCT.md) 与 [DESIGN.md](../../DESIGN.md)。
- 手机配对页面与目录部分加载状态一并接入。根工程的 WebDAV 播放调用改为 `playbackClient(resolution.seekable)`，使客户端与曲目的定位能力一致。
- 迁入前文件及哈希保存在本机工具备份中；备份不随仓库发布。

## 根目录构建结果

使用 JDK 21、Gradle 8.13 和 Android SDK 36，在根目录完成以下任务。

~~~text
:app:assembleDebug :app:assembleDebugAndroidTest
:app:testDebugUnitTest :ncmapi:testDebugUnitTest
:app:lintDebug :ncmapi:lintDebug
~~~

- 两个 APK 构建成功。
- JVM 用例共 32 项通过，app 29 项、ncmapi 3 项，零失败、零跳过。
- Lint 两模块零错误。app 有 662 个警告、3 个提示，ncmapi 有 5 个警告。完整输出见 [构建日志](implementation/root/build.log)。
- 离线运行时通过 `-ProbolectricDependencyDir=<目录>` 指定缓存中的 Android 14 Robolectric JAR。

迁移验收时的调试 APK（现已由第三轮构建更新）大小为 27,581,051 字节。SHA-256 为 `11bec9060519f80773985dcf4bf690c0a1d37bf91295c0892758a3bc479c26a7`。

## TV 设备验证

使用 API 36、1920×1080 的 TESTNEW Android TV 模拟器。设备测试以根目录生成的 APK 和设备测试 APK 运行。测试包为 `com.cutedino.xiangsuplayer.test`。

- 四个设备用例通过，覆盖媒体服务绑定、Room 查询，以及首页、播放页和榜单的原生渲染。
- 播放控件以方向键从暂停经过下一曲到达队列，再返回暂停。榜单以确定键激活后，断言长标题曲目可见。
- 恢复中断的模拟器后，默认字号下再次通过三个界面用例。系统字号设为 1.3 后，同样通过三个界面用例，并恢复至原值 1.0。
- 实际 Activity 用方向键进入搜索、打开系统键盘。第一次返回关闭键盘并聚焦搜索框，第二次返回聚焦搜索栏目。随后下、确定、右进入曲库，焦点落在「我的收藏」。

日志包含 [四个设备用例](implementation/root/instrumentation.log)、[默认字号复测](implementation/root/default-font.log) 和 [大字号复测](implementation/root/large-font.log)。

~~~text
adb shell am instrument -w -r com.cutedino.xiangsuplayer.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class com.cutedino.xiangsuplayer.ui.TvBrandScreenshotTest -e reviewSuffix -large com.cutedino.xiangsuplayer.test/androidx.test.runner.AndroidJUnitRunner
~~~

## 原生截图

首页、歌词和榜单的内容截图使用合成曲目与封面运行生产 Composable。冷启动、系统键盘和曲库路径由生产 Activity 实际操作后截图。

| 场景 | 默认字号 | 1.3 倍字号 |
| --- | --- | --- |
| 首页与 MiniPlayer | [首页](implementation/root/device/home-populated.png) | [大字号首页](implementation/root/device/home-populated-large.png) |
| 封面、歌词与播放控制 | [播放页](implementation/root/device/player-lyrics.png) | [大字号播放页](implementation/root/device/player-lyrics-large.png) |
| 榜单与长标题 | [榜单](implementation/root/device/toplist-rows.png) | [大字号榜单](implementation/root/device/toplist-rows-large.png) |

[冷启动](implementation/root/home-cold.png) · [打开键盘](implementation/root/search-ime.png) · [关闭键盘](implementation/root/search-ime-closed.png) · [返回栏目](implementation/root/search-rail-return.png) · [曲库焦点](implementation/root/library.png)

## 验证范围

模拟器中预装的同包名旧版与本机调试签名不同。测试在本次启动的只读模拟器中卸载旧包后完成干净安装。覆盖升级及旧安装数据延续仍需验证。真实账号登录、音乐服务取流、实体手机扫码、实际 WebDAV 服务，以及 API 21／低性能盒子尚待实测。

优化副本的早期独立包名验证另存于本机；本文件仅列出根工程的可发布证据。

## 第三轮：前端修正与品牌统一

本轮在根目录继续实施，保留既有迁移成果与未提交修改。修改限于 TV UI、对应字符串、测试与设计文档；应用 ID、数据库结构、账号/音源/手机协议和播放业务契约不变。未自动提交或推送。

### 已完成的修正

- 统一窗口规格、形状、可访问性动作和显式 TV 编辑；普通按钮不再携带选择控件语义，主要动作强调与筛选选择分开。焦点、选中与当前播放分别表达。
- 导航可滚动并自动显露焦点，播放条独立占位。紧凑首页为封面来源标签预留实际字号行高；来源标签增加未裁切边界断言。
- 播放页采用紧凑顶部资料/主体歌词和五项等宽控制；按稳定条目 ID 标记队列当前项。更多与队列菜单恢复具体调用项。
- 歌单部分成功即可播放已加载列表快照，补充重试保留曲目；榜单和搜索刷新失败保留同一查询的旧内容，切换查询不混用数据。
- 市场明确区分加载、失败和空内容；保留导入、安装、替换、启停、测速和卸载，参数与完整版本说明进入可滚读详情。设置改为开放分组，高级兼容移到日常选项之后。
- QQ/壁纸/WebDAV 输入必须按确定编辑，返回恢复入口；二维码刷新、过期和切平台不继续显示旧码。配对面板与点歌页适配低高度，长说明/歌词支持方向键滚读。
- 所有动效响应系统动画倍率变化；播放器整页订阅排除高频时钟，进度与 MiniPlayer 局部订阅。封面继续降采样、有限取色缓存，不增加字体资产、全屏模糊或循环装饰。

设计规则见 [DESIGN.md](../../DESIGN.md)。

### 构建与自动化

JDK 21、Gradle 8.13、SDK 36，在根目录完成调试 APK、设备测试 APK、两模块 JVM 测试和 Lint。

- JVM：app 39 项、ncmapi 3 项，共 **42 项通过**，零失败、零跳过。新增回归覆盖编辑门控、忙碌防重复执行、普通按钮语义、动画倍率更新、长文案滚读、紧凑导航/控制、重复曲目标记、失败刷新与部分歌单快照。
- Lint：app **0 错误、662 警告、3 提示**；ncmapi **0 错误、5 警告**。与本轮修改前逐项比较，新增问题为 0。
- [完整构建日志](implementation/frontend-refinement/frontend-final-build.log)。

### 原生设备矩阵

API 36 Android TV 模拟器，density 320。标准物理画布 1920×1080 对应 960×540dp；紧凑物理画布 1280×720 对应 640×360dp。每种画布测试字号 1.0 与 1.3。

原生 UI 用例复用生产浏览框架、播放器布局与真实页面组件，使用合成曲目和封面。覆盖带/不带 MiniPlayer 的首页、导航末项、五个播放控制、长标题、无封面/无歌词、长译文、榜单、市场失败与空内容、设置滚动及点歌退出。关键断言包括完整边界、不得覆盖播放条、确定键执行与方向键可达；高级设置通过实际方向键逐项滚动验证。

| 逻辑画布 / 字号 | 通过用例 | 证据 |
| --- | --- | --- |
| 960×540dp / 1.0 | 11 | [日志](implementation/frontend-refinement/standard/instrumentation.log) · [首页](implementation/frontend-refinement/standard/home-populated.png) · [播放](implementation/frontend-refinement/standard/player-lyrics.png) |
| 960×540dp / 1.3 | 10 | [日志](implementation/frontend-refinement/large/instrumentation.log) · [首页](implementation/frontend-refinement/large/home-populated.png) |
| 640×360dp / 1.0 | 10 | [日志](implementation/frontend-refinement/compact/instrumentation.log) · [播放](implementation/frontend-refinement/compact/player-lyrics.png) |
| 640×360dp / 1.3 | 10 | [日志](implementation/frontend-refinement/compactLarge/instrumentation.log) · [首页](implementation/frontend-refinement/compactLarge/home-populated.png) · [导航末项](implementation/frontend-refinement/compactLarge/home-more-focus.png) · [高级设置](implementation/frontend-refinement/compactLarge/settings-compatibility.png) |

合计 **41 次通过**：10 项界面用例各运行四组，标准组另含媒体服务/Room 运行用例。归档 52 张原生截图。设置验证继续向下聚焦关于与致谢阅读区。新增安装失败详情用例模拟页面焦点环境，验证错误可完整阅读、按返回后回到原安装按钮；弹层不覆盖调用页面的焦点记忆。


### 实际 Activity 与在线服务

- 640×360dp、1.3 倍字号：真实 Activity 通过导航进入更多、音源市场、设置与账号。音源地址聚焦时是普通 View，按确定进入 EditText 并显示 TV 键盘，返回后恢复同一个输入入口；市场在线返回版本列表，网易云二维码进入等待扫码状态。
- 从电视首页进入搜索，用系统键盘输入“hello”，真实返回网易云与酷狗歌曲。选中 Adele 的《Hello》后，MiniPlayer 显示正在播放；媒体会话记录 PLAYING(3)、位置 7,329ms、已缓冲 58,258ms、速度 1.0、error=null。[搜索截图](implementation/frontend-refinement/actual/frontend-search-results.png) · [播放截图](implementation/frontend-refinement/actual/frontend-play-selected.png) · [媒体状态](implementation/frontend-refinement/media-playback-evidence.json)。模拟器无音频输出，此记录不代表实体音响听感或实际音质测量。
- 重启后恢复该曲目与队列，保持暂停。从未激活的「更多」进入 MiniPlayer，再向上返回「更多」；经 MiniPlayer 确定进入全屏，打开队列和行内更多，返回恢复行内更多，关闭队列恢复队列控制，退出全屏恢复 MiniPlayer 信息入口。[冷启动恢复](implementation/frontend-refinement/actual/frontend-flow-cold-restored.png) · [MiniPlayer 返回来源](implementation/frontend-refinement/actual/frontend-flow-mini-origin-return.png) · [队列子菜单返回](implementation/frontend-refinement/actual/frontend-flow-queue-action-return.png) · [队列关闭返回](implementation/frontend-refinement/actual/frontend-flow-queue-close-return.png)。
- 实际市场资源 V261107.zip（列表显示 614 KB）安装被既有后端校验拒绝，详情为「音源包超过大小上限」。页面解除忙碌，保留错误原因并可关闭重试；未绕过或放宽大小校验。该次操作不计为安装成功，兼容资源的真实安装、替换和卸载仍待验证。

### APK 与验证限制

第三轮调试 APK：28,787,905 字节，SHA-256：

~~~text
df1fe86d3220d560005956bb784c8d188b8dc009cd19daa88453d0e350b83b75
~~~

设备截图与渲染通过不等于账号授权、流媒体取流或实体手机端到端通过。真实账号登录/红心同步、实际手机扫码点歌、实际 WebDAV/U 盘授权、同签名覆盖升级与数据延续、API 21 和低性能实体盒子仍需在相应环境验证。未以截图代替这些结果，也未宣称已测得低端设备帧率。

设备验证使用本轮专用只读模拟器。因预装旧版与本地调试签名不同，仅在该临时实例移除旧包后安装；原 AVD 数据不变。完成后恢复显示尺寸和字号，并关闭该实例。
