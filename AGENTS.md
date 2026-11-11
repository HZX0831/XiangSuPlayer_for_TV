# Repository Guidelines

## 项目结构与模块组织

- `app/` 是 Android TV 播放器主模块。在 `app/src/main/java/com/cutedino/xiangsuplayer/` 下，`ui/` 存放界面、共享 Compose 组件和焦点控制修饰符，`core/` 负责播放、歌词和音源适配器，`data/` 管理 Room 持久化数据和账号会话。
- `app/src/main/res/` 存放 XML 布局、drawable 资源、字体和字符串。修改本地化文案时，也要更新 `values/` 和 `values-zh-rCN/` 中对应的内容。
- `ncmapi/` 是网易云音乐 API 库，其 Kotlin 源码位于 `ncmapi/src/main/java/net/moriafly/ncm/`。

## 构建、测试与开发命令

安装 Android SDK Platform 36，并使用 JDK 21 运行 Gradle 8.13 守护进程。两个模块均以 JVM 17 为目标，支持 Android API 21 及以上版本。在 `local.properties` 中设置 SDK 路径，或让 Android Studio 自动创建该文件。在仓库根目录运行 Gradle Wrapper。macOS 或 Linux 上使用 `./gradlew`。

- ` .\gradlew.bat :app:assembleDebug`：构建调试版 APK，输出至 `app/build/outputs/apk/debug/app-debug.apk`。
- ` .\gradlew.bat :app:installDebug`：将调试版安装到已连接的 Android TV 设备或模拟器，然后从电视主屏幕启动应用。
- ` .\gradlew.bat :app:testDebugUnitTest :ncmapi:testDebugUnitTest`：运行两个模块的 JVM 单元测试。
- ` .\gradlew.bat :app:lintDebug`：对调试版本运行 Android Lint 检查。

## 代码风格与命名约定

遵循 Kotlin 官方代码风格，使用四个空格缩进。类和 UI Composable 使用 `PascalCase`，函数和属性使用 `camelCase`，Android 资源名称使用 `lower_snake_case`。将界面代码放在对应功能包中，并复用共享的 TV 组件和焦点控制修饰符。

## 测试指南

在 `app/src/test/java/` 下按照生产代码的包结构放置测试。测试类以 `*Test` 结尾，测试方法名称应体现所验证的行为。逻辑测试使用 JUnit 4，D-pad 焦点回归测试使用 Robolectric 和 Compose UI 测试。修改导航或播放逻辑时，也要使用遥控器或模拟器 D-pad 实际验证受影响的操作流程。

## 提交与拉取请求指南

提交说明应简短并使用祈使语气。Compose 界面迁移的提交采用了 `feat(tv): ...` 这类带范围的格式。拉取请求应描述行为变更、列出验证结果，并在适用时关联相关问题。界面有可见变化时，请附上电视界面的截图或录屏，并说明已测试的 D-pad 操作步骤。
