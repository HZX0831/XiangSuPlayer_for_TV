# TV 实施与验收

本轮在优化工程内落地「封面节目册」。家人从固定左栏找到首页、搜索、曲库、榜单和更多；首页突出继续播放，全屏把封面与歌词并排。Android 主项目的封面配色、标题层次、主播放按钮和进度语言贯穿 TV。参考及取舍见 [安卓版研究](ANDROID-BRAND-STUDY.md)，页面和焦点约定见 [DESIGN.md](../../DESIGN.md)。

## 实施范围

- 首页、导航、MiniPlayer、全屏播放器采用新的版式；搜索、曲库、榜单、发现、队列、歌单、收藏、评论、音源市场和设置继续使用既有 TV 入口和数据能力，共用新的文字、封面、状态、面板和焦点样式。
- 优化版播放、歌词、Room、账号、音源与 ncmapi 保留。手机配对补齐受原 token 保护的网页；沿用 JSON 提交、队列和权限校验。未移植手机版额外业务。
- 原仓库仅作为读取基线。调试 APK 保持优化工程独立 ID `com.cutedino.xiangsuplayer.backendreview`。

## 原生截图

均来自 API 36 Android TV 模拟器，1920×1080。生产应用截图为真实 Activity 与系统输入法；封面、歌词和曲目截图使用合成数据运行实际生产 Composable，以稳定复现内容和配色。

| 场景 | 默认字号 | 1.3 倍字号 |
| --- | --- | --- |
| 首页含最近播放、继续播放和 MiniPlayer | [首页](implementation/device/home-populated.png) | [大字号首页](implementation/device/home-populated-large.png) |
| 专辑色、歌词、进度及五项播放控制 | [播放页](implementation/device/player-lyrics.png) | [大字号播放页](implementation/device/player-lyrics-large.png) |
| 榜单与长标题歌曲 | [榜单](implementation/device/toplist-rows.png) | [大字号榜单](implementation/device/toplist-rows-large.png) |

生产应用：[冷启动](implementation/home-cold.png) · [进入搜索](implementation/search-initial.png) · [打开键盘](implementation/search-ime.png) · [返回关闭键盘](implementation/search-ime-closed.png) · [进入曲库](implementation/library.png)

## 验证

- **构建通过**：JDK 21、Gradle 8.13、Android SDK 36。执行 `:app:assembleDebug :app:assembleDebugAndroidTest`；[调试 APK](../../app/build/outputs/apk/debug/app-debug.apk)（24,868,009 字节）。SHA-256：`ed5d38b9800f251ed67081cf1926799b1a82c06d0a583d376d9cad66bb34b67d`。
- **JVM：48 项通过**（app 40，ncmapi 8；零失败、零跳过）。`:app:testDebugUnitTest :ncmapi:testDebugUnitTest`。本机离线运行从工程缓存提供 Robolectric Android 14 SDK JAR（`-ProbolectricDependencyDir=<含 JAR 的目录>`）。覆盖播放器快照和歌词、Room、音源、WebDAV、会话相关功能，以及栏目、播放控制、榜单、进度失效和已卸载焦点请求器。
- **Lint：两模块零错误**。`:app:lintDebug :ncmapi:lintDebug`。app 211 个警告、3 个提示；ncmapi 5 个警告，包含未用资源、依赖版本与 API 使用提示，未将警告当作已修复。
- **设备：默认字号 4 项通过；1.3 倍字号重复 3 个界面用例并通过**。包括媒体服务绑定与 Room 查询，首页渲染、播放器的暂停 → 下一曲 → 队列 → 暂停焦点路径，以及榜单确定后显示长曲名。核对截图后，首页固定展示一条完整最近记录，保留全部历史入口；榜单在参考 1080p 窗口显示主从双栏。
- **真实 Activity 的 D-pad 烟测**：冷启动左栏焦点落在「首页」；↓、确定进入搜索；→、确定打开系统键盘；返回关闭键盘并把焦点留在搜索入口；再次返回左栏，再按 ↓、确定、→ 进入曲库并落在「我的收藏」。
- Gradle 的 `connectedDebugAndroidTest --offline` 缺 UTP 宿主插件，未进入运行阶段。用 ADB 安装同一构建的两个 APK，直接运行同一个 AndroidJUnitRunner，得到上述设备结果。

设备运行命令（安装两个 APK 后）：

~~~text
adb shell am instrument -w -r com.cutedino.xiangsuplayer.backendreview.test/androidx.test.runner.AndroidJUnitRunner
~~~

## 验证边界

没有使用真实平台账号、商业串流或 WebDAV 服务器逐一走查，也没有用实体手机验证扫码，或在 API 21／低性能盒子与物理遥控器上测量远距离阅读和掉帧。设备截图和焦点测试覆盖 UI 布局及确定的交互路径，不将合成曲目视为真实取流成功。未重做第一轮 P1 审计。
