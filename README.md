# 像素播放器 TV · 品牌重构工程

基于独立优化后端接回 Kotlin / Jetpack Compose TV 前端。应用 ID 保留 com.cutedino.xiangsuplayer.backendreview，便于与原版并存；面向 16:9、电视遥控器和家人共用场景。

本轮设计定位：同一品牌，不同终端；视觉同源，体验独立。

- [PRODUCT.md](PRODUCT.md)：已确认产品与业务边界。
- [DESIGN.md](DESIGN.md)：当前品牌设计系统与页面/交互规范。
- [Android 品牌研究](docs/design/ANDROID-BRAND-STUDY.md)：固定提交的 22 个源码文件、四张截图与三方向评估。
- [第一轮方案存档](docs/design/DESIGN-PROPOSALS.md)：初次探索，已由当前 DESIGN.md 取代实施权威。

## 构建和验证

使用 JDK 21、Android SDK Platform 36 与 Gradle Wrapper 8.13。通过 ANDROID_HOME 或本地 local.properties 配置 SDK。minSdk 21、targetSdk 34、JVM 17 保持原基线。

~~~powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :ncmapi:testDebugUnitTest :app:lintDebug :ncmapi:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
~~~

APK 输出至 app/build/outputs/apk/debug/app-debug.apk。设备测试包含既有媒体服务/Room 检查与使用合成曲目的原生 UI 截图；合成截图不代表真实平台取流或登录成功。

## 代码与功能边界

ui/ 保留并重构首页、发现、搜索、曲库、榜单、歌单、历史、账号、设置、音源管理、全屏播放与双行点歌。core/data/ncmapi 继续使用优化后的实现；未从手机主项目引入 AI、下载、音效或社交等额外业务。

PhoneInputServer 的原 URL 继续提供 token 保护的 JSON 协议；新增同源 /client 网页，TV 二维码使用 clientUrl。Host/Origin、超时、限长、结果 ID、会话生命周期与队列行为沿用既有约束。浏览器可完成手机输入与点歌，不需要公开部署服务。

最初的无界面后端边界与修复历史保留在 [VERIFICATION.md](VERIFICATION.md)；该文件记录的是先前后端交付，不是本轮 TV UI 验证结论。
