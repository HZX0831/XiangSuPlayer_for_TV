# Android 品牌设计语言研究与 TV 转译

2026-11-11 · 延续第一轮产品/页面分析，不重复初始化或 P1 审计。

## 证据范围

实际读取 Android 主项目 22 个 UI/主题/组件文件，保存于 [references/android](references/android/manifest.json)，固定 main 快照 **09a1fc3d40d56ac2ba534056feb27ac3e8438cf6**。四张 assets/screenshot1–4.jpg 已取得并查看；截图没有独立拍摄日期，不宣称反映当前所有播放器样式。源码比截图暴露更多可选布局与主题；冲突时以固定源码的能力为准。

指定 TV 分支固定为 **4b791abcefab1975d89830f380def25443a6cb23**，实际读取主题、公共组件和播放器。远端该分支含旧 Fragment/Activity/Compose 混合代码；本地前端已有后续焦点恢复、稳定队列 ID 与目录/点歌修复。新 UI 使用本地前端作为功能迁移来源，优化工程 core/data/ncmapi 继续作业务基线，未回退到远端旧业务实现。

## 七个研究维度

| 维度 | Android 实际证据与结论 | TV 决策 |
| --- | --- | --- |
| 主色/辅助色 | [Color.kt](https://github.com/r3n011/XiangsuPlayerHQ/blob/09a1fc3d40d56ac2ba534056feb27ac3e8438cf6/app/src/main/java/com/theveloper/pixelplay/ui/theme/Color.kt) 是深蓝基底、蓝主色、粉与橙辅助；[Theme.kt](https://github.com/r3n011/XiangsuPlayerHQ/blob/09a1fc3d40d56ac2ba534056feb27ac3e8438cf6/app/src/main/java/com/theveloper/pixelplay/ui/theme/Theme.kt) 在无专辑覆盖且支持系统动态色时采用系统色，有覆盖时采用专辑方案 | 共享深色与语义；TV 用更亮的蓝焦点、粉仅承担收藏，不恢复蓝粉导航 |
| 动态色 | [ColorRoles.kt](references/android/ui/theme/ColorRoles.kt) 包含取色降采样、缓存、种子评分、灰度处理、调色方案和逐角色对比修复，绝不是只取 Vibrant 色值 | TV 实现独立的低开销 Palette/小图缓存与前景纠偏；不宣称复制其完整 HCT 算法，黑白/缺图回品牌基线 |
| 字体/图标/形状 | [Type.kt](references/android/ui/theme/Type.kt) 的主题字体为 Google Sans Flex rounded；Montserrat 是局部展示字。Shape.kt 为 8/16/24 dp，但组件有独立形状与可选布局；截图显示圆润字形、Rounded 图标和主按钮的强弱对比 | 继承字重层级和亲和感，中文不横向拉伸；使用系统中文回退和统一 Rounded 图标；不把所有区域设成同一圆角 |
| 首页/发现 | HomeScreen、FeaturedCarouselSection、DailyMixSection、RecentlyPlayedSection 中，大标题与音乐内容优先；轮播有 phone/tablet 分支，phone 甚至是方角出血图而非卡片。最近播放用内容驱动的多行布局 | 首页为固定 rail、当前曲目、最近记录与单张封面；发现独立，保留现有日推/FM/心动/新歌，不引入手机版全部内容模块 |
| 封面/歌词/播放器 | [FullPlayerContent](references/android/presentation/components/player/FullPlayerContent.kt)、LyricsSheet 和 UnifiedPlayerSheetV2 将封面、歌词与主题关联；[ImmersiveCoverBackdrop](references/android/presentation/components/player/ImmersiveCoverBackdrop.kt) 是每曲一次预渲染小图模糊与分层混合，并非要求持续 GPU 模糊 | 横屏左封面资料/右歌词，歌词不逐行套卡；纯歌词回归空场；TV 默认用暗色场而非手机上半屏封面折叠结构 |
| 进度/播放反馈 | StyledPlayerSeekBar、WavyMusicSlider 与 [AnimatedPlaybackControls](references/android/presentation/components/player/AnimatedPlaybackControls.kt) 显示可选进度风格、主播放按钮形变/尺寸主次、按下状态反馈 | 留下克制的已播放波形与主按钮更柔和的形状；删除持续相位动画、邻居挤压和 180ms 后才执行上下曲的触摸节奏 |
| 页面一致性/动效 | ExpressiveButtonGroup、TabAnimation、FloatingNavBarContent、SettingsComponents 共用 Material 角色；图标与状态主次贯穿，触感/胶囊/手势是手机的具体表达 | 统一文字动作、焦点轮廓、TV 对话层和字阶；手机 BottomSheet 改受保护的 TV 面板；无触摸专属路径 |

## 截图与源码交叉判断

[首页截图](https://github.com/r3n011/XiangsuPlayerHQ/blob/09a1fc3d40d56ac2ba534056feb27ac3e8438cf6/assets/screenshot1.jpg) 的大标题/大播放动作、[播放截图](https://github.com/r3n011/XiangsuPlayerHQ/blob/09a1fc3d40d56ac2ba534056feb27ac3e8438cf6/assets/screenshot2.jpg) 的封面同色背景和主按钮、[曲库截图](https://github.com/r3n011/XiangsuPlayerHQ/blob/09a1fc3d40d56ac2ba534056feb27ac3e8438cf6/assets/screenshot3.jpg) 的标题/曲目层级、[歌词截图](https://github.com/r3n011/XiangsuPlayerHQ/blob/09a1fc3d40d56ac2ba534056feb27ac3e8438cf6/assets/screenshot4.jpg) 的大歌词与波形共同构成辨识度。它们也显示手机底导航、圆形拼贴和底部操作层，这些不适合机械迁往 TV。

源码里的新 phone 出血轮播说明“品牌一致 = 所有组件都圆”并不成立。手机当前可选播放器样式众多，TV 不需要同时移植这些业务设置。取共同原则即可：音乐为主体、封面决定氛围、状态表达清楚、形状有主次。

## 第一轮方案修订

A 的目录与固定栏目最符合家庭找歌，但默认纸色/朱红缺少品牌联系；B 的听歌留白最接近 Android 的内容表达；C 的固定目标有价值，但黄绿网格将其推向独立标牌品牌。最终保留 **A 的导航/目录骨架 + B 的歌词空间**，用统一的品牌深色、蓝色焦点、封面色和圆润主动作重新表达。C 只留下稳定入口的操作原则，不保留皮肤和网格。

当前完整规范见 [DESIGN.md](../../DESIGN.md)。第一轮 SVG/PNG 不删除，作为探索历史；实现截图另存，不能让旧图被误当作本轮成品。

## 实施边界

迁入 12 个已有 TV UI 文件并在优化工程内重构；原仓库不写入。播放器、数据与音源算法不从手机版拷贝。唯一必要的后端接入补充是 token 下的同源 /client 页面，为既有 JSON 配对恢复手机输入/点歌；原 JSON URL、Host/Origin、到期、限长、并发和队列行为保留，并新增兼容回归测试。

未新增 AI 助手、下载、音效、听歌识曲、MV、社交发布或家庭档案。没有从 README 的功能宣传推导 TV 新业务。
