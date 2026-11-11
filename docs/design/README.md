# 当前实施入口

第二轮已研究 Android 主项目并接入 TV 前端。当前规范见 [DESIGN.md](../../DESIGN.md)，研究见 [ANDROID-BRAND-STUDY.md](ANDROID-BRAND-STUDY.md)。本目录下第一轮方案与核验保留为历史，不能代替当前原生实现及验证记录。

# 第一轮方案存档

本轮仅设计规划；产品逻辑、构建配置和既有源码未修改。全部文件位于 backend-review-2026-11-10/PixelPlayer-Backend 内。

1. [打开可视评审页](preview.html)：三套方案，各有首页和播放页。
2. [六屏对照图](concepts/comparison.png)：左 A 节目簿 / 中 B 幕间 / 右 C 唱片索引；上首页 / 下播放页。
3. [产品事实 PRODUCT.md](../../PRODUCT.md)：用户场景、能力、约束和未决事项。
4. [三套方向与视觉系统](DESIGN-PROPOSALS.md)：色彩、字阶、布局、焦点、动效、风险和建议。
5. [页面结构与完整功能映射](PAGE-STRUCTURE.md)：19 个页面/状态承载点，三套方向如何覆盖。
6. [遥控器与状态规格](REMOTE-INTERACTION.md)：方向键、确定、返回、队列、异常与未来验收路径。
7. [仓库深查记录](REPOSITORY-AUDIT.md)：源码依据、优化后端边界、现有视觉反参考。
8. [本轮核验结果](REVIEW-CHECKS.md)：文档、图稿与文件不变性检查。

建议优先评审 A，其次依据“更重歌词”或“更重固定入口”比较 B/C；尚未选定任何方向。没有建立冒充已批准设计的 DESIGN.md。

图稿使用虚构曲目、歌词、数量及原创几何演示封面，不是实际服务数据。SVG 为可编辑矢量原稿，PNG 为 1920×1080 输出。浏览器仅用于渲染这些方案图，没有模拟 Android UI 实测。

后续如进入实现，应以优化后端为基线补齐原生 Compose 前端，并恢复 JSON 配对服务所缺的手机客户端；不得直接假定现有后端 APK 有可启动界面。
