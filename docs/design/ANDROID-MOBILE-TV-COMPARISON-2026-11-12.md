# 手机端功能与视觉效果的 TV 移植评估

## 对照范围

- 手机端以 [XiangsuPlayerHQ main 的 519d4c0 提交](https://github.com/r3n011/XiangsuPlayerHQ/commit/519d4c0325e475d66c1ce87d0d51665d8579892f) 为准，版本为 1.8.0 (72)。TV 以本地 `f105e4b57fda` 和本轮开始时的未提交工作树为准。以可追到页面、服务或实际调用点的实现为依据，README 和历史更新日志用于补充线索。手机仓库采用浅层部分克隆，稀疏检出 77 个主要对照文件，并按提交对象校验。
- TV 基线为 Android API 21、compileSdk 36、Compose BOM 2024.06.00、Media3 1.8.0。手机端 Gradle 配置为 API 23、compileSdk 37、Compose BOM 2026.05.01、Material 3 1.5.0-alpha21、Media3 1.10.1，另有 Hilt、Haze 1.7.2、Cloudy 与 CMake/JNI 等依赖。最低安装版本以 Gradle 为准，手机 README 的 Android 9+ 是设备要求说明。沿用算法与搬用组件的成本不同。 [TV 构建配置](../../app/build.gradle.kts) · [手机构建配置][build] · [手机依赖版本][deps]
- 必要性按客厅遥控场景判断。**高**表示直接改善找歌、持续播放或看歌词，**中**表示有明确使用场景但依赖账号、硬件或新增交互，**低**表示主要服务手机场景或投入显著高于电视上的收益。可移植性按 TV 当前架构和兼容范围估计。结论来自静态源码，设备帧耗、远距离观感、接口成功率和实物音频链路均未实测。

## 功能差异与同步建议

| 手机端能力 | TV 当前覆盖 | 必要性与可移植性 |
| --- | --- | --- |
| 歌词按歌曲所属源路由，支持可配置优先级、AMLLDB、LRCLIB、本地同名或内嵌歌词、缓存、手动检索和时间偏移 | 已有跨平台内置取词与回退、LRC／译文、网易云 YRC 逐字时间和纯歌词，播放引擎对目录曲目直接跳过歌词读取，更多逐字格式也有补充空间 | **高 / 中高**。优先做目录同名 LRC 与现有源失败后的逐字或普通歌词兜底，保留版本匹配、超时预算和逐字到普通行回退。手动编辑、分享和翻译生成适合低频二级入口。 [手机歌词仓库][lyricsrepo] · [TV 歌词解析](../../app/src/main/java/com/cutedino/xiangsuplayer/core/audio/LyricEngine.kt#L13) · [TV 源路由](../../app/src/main/java/com/cutedino/xiangsuplayer/core/source/SoundSourceRepository.kt#L212) · [目录曲目歌词入口](../../app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L646) |
| 歌手主页、专辑详情、按艺术家／专辑／流派组织的本地媒体库、类型化搜索与自建／智能歌单 | TV 搜索接受歌手、专辑关键词，歌曲和歌单有结果页，曲库支持平台资料与本地／WebDAV 目录 | **高 / 中**。先让播放页和搜索结果直接打开同歌手、同专辑的可播放列表，再考虑本地索引及简单歌单编辑。可参考 ID、曲目排序和分页，TV 曲库需自己的元数据与 D-pad 返回定位。规则歌单和批量管理的必要性较低。 [搜索][search] · [曲库][library] · [歌手主页][artist] · [专辑页][album] · [歌单管理][playlists] |
| 定时停止、播完当前曲目或指定曲数后停止 | 已有后台播放、快照恢复和纯歌词计时 | **高 / 高**。适合客厅长时间听歌。可参考计时与曲目结束语义，用 TV 自己的播放服务承接，覆盖切歌、退出页面和重复设置。手机端使用 AlarmManager 并处理精确闹钟可用性，TV 可按服务生命周期决定计时方式。 [手机定时入口][timerentry] · [手机定时状态][timer] · [TV 播放服务](../../app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackService.kt#L23) |
| 统一在线取流、按源和音质缓存、候选平台重搜、加密容器与占位流过滤，LX JS 支持 URL／歌词／封面 | 已有手动换源、音质降级、候选回退、音源测速与模板市场，TV 导入侧执行受限 HTTPS 模板 | **高（选取可靠性改进）/ 中**。重点比较同录音匹配、候选 ID、过期 URL、试听与占位流检测，复用协议和验证思路。完整 LX JS 引擎还需运行时、权限与网络边界，属于独立的中优先级能力。 [手机取流][resolver] · [LX 引擎][lx] · [TV 音源仓库](../../app/src/main/java/com/cutedino/xiangsuplayer/core/source/SoundSourceRepository.kt) |
| Jellyfin、Navidrome/Subsonic 专用曲库、会话、串流和媒体索引 | 已有只读 HTTPS WebDAV 目录和系统目录选择器 | **中高 / 中**。对 NAS 家庭，服务端索引、歌单和封面能明显改善找歌。有目标用户时可先做一个服务的只读浏览与播放，核对鉴权、断线、Range 与续播。B 站音频收藏、Google Drive 与 Telegram 更适合作为独立候选，Telegram 受 full/lite 构建约束。 [Jellyfin][jellyfin] · [Navidrome][navidrome] · [账户入口][accounts] · [full 清单][fullmanifest] |
| QQ 和酷狗账号能力、个人歌单与酷狗私人 FM，网易云手机号/Web 登录和一起听 | 已有网易云扫码、个人歌单、云盘、FM、心动模式。QQ 主要是公开资料关联和公开歌单，酷狗以公开榜单为入口 | **中 / 中低**。有私人曲库需求时优先验证手机授权到 TV 的链路。一起听和更深账号能力涉及续期、风控与同步，客厅共用设备需明确账号状态。TV 已有的公开内容无需依赖新增登录。 [账户入口][accounts] · [酷狗账号页][kugou] · [发现入口][discovery] · [一起听会话][together] |
| 网页远控有 PIN、HTTP API、WebSocket 状态、播放与定位、搜索、歌词、封面和串流，包含浏览器出声模式 | 已有短期扫码会话、手机输入与点歌网页，电视本身负责播放 | **中高 / 中**。先评估手机查看状态、暂停／跳歌和管队列，沿用 TV 配对与点歌协议边界。浏览器串流会新增会话、来源校验、连接恢复和音频输出状态的工作，价值取决于使用需求。 [手机远控服务][web] · [手机网页][webui] · [TV 配对](../../app/src/main/java/com/cutedino/xiangsuplayer/core/phone/PhoneInputServer.kt#L49) |
| 公共广播电台、酷狗听书、相似歌曲、网易云漫游与更丰富的发现入口 | 已有推荐、榜单、地区新歌、网易云私人 FM／心动、歌单随机漫游 | **中 / 中**。电台有明确的客厅连续播放用途，听书则要有章节和进度恢复的产品投入。相似歌曲适合放在当前歌曲的更多操作，继续使用现有播放入口。 [发现入口][discovery] · [公共电台][radio] · [听书导航][books] |
| 下载队列、离线索引、伴随 LRC 与模块化备份／恢复 | 已有本地目录与 U 盘播放、播放快照，未见用户下载管理或备份入口 | **中低 / 中低**。离线下载受来源授权、存储空间和下载任务恢复制约。对常联网电视，选择性备份收藏、目录配置和设置的价值更明确，可先定义 TV 自己的数据范围。 [下载页][downloads] · [下载服务][downloadservice] · [备份管理][backup] |
| ReplayGain、无缝衔接／交叉淡入淡出、音频处理与输出诊断、EQ／AutoEq、USB DAC、DSD/FFmpeg/miniaudio | TV 用单 ExoPlayer 与 MediaSession，提供请求音质和本地常用格式播放 | **中（音量一致性）/ 中**，**低（整套发烧链路）/ 低**。先评估不同曲目音量跳变和连续播放。双播放器交叉淡化依赖缓冲、焦点和增益组合，USB／DSD 还牵涉 JNI、ABI、接收器、透传与厂商差异。 [播放引擎][engine] · [音频设置][audioopts] · [手机构建][build] |
| 统计、编辑标签、批量整理、AI 歌单与助手、听歌识曲、私信和评论发布 | TV 聚焦最近播放、收藏、热门评论阅读和找歌 | **低 / 中低**。统计和简化歌单整理可按反馈考虑。文字密集编辑、麦克风录音、消息和 AI 配置适合手机。 [统计][stats] · [AI 助手][ai] · [识曲][recognition] · [路由][screens] · [手机 README](https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/README.md) |
| XS 通用插件、MCP 外接，以及 Android Auto、Wear、桌面小部件和 Chromecast 发送端 | TV 有受限音源市场、后台媒体会话和同网点歌 | **低 / 低**。XS 包安装、QuickJS、WebView 与 Host API 是独立于 LX 音源脚本的扩展系统。MCP 配对与工具服务也独立于网页远控。手机的 Cast 发送端若用于把歌投到 TV，TV 接收端需要另定协议。车机、手表与手机桌面组件有各自使用场景。 [XS 安装][plugininstall] · [XS 运行时][plugin] · [MCP 服务][mcp] · [应用清单][manifest] · [工程模块][modules] |

TV 的 WebDAV、短时扫码输入和独立点歌队列仍是这次对照里的客厅能力。TV 双行点歌直接使用现有歌词与独立队列，和手机的歌词样式、网易云一起听属于不同任务。 [TV 产品事实](../../PRODUCT.md) · [TV 点歌界面](../../app/src/main/java/com/cutedino/xiangsuplayer/ui/player/TvKaraokeScreen.kt)

## 手机界面与动效

| 实现 | TV 必要性与可移植性 |
| --- | --- |
| **主题与结构**。Material You 壁纸动态色在 Android 12+ 可用，另有固定配色、封面配色及 Google Sans Flex 圆润字形。手机使用底栏，横屏／平板走自适应侧栏，首页有精选轮播、推荐、混音和最近播放。 | **高（视觉语义）/ 高**。TV 已有深色基底、128 px 封面 Palette、可读强调色和固定左侧导航。适合继续共用色彩和形状语义，并让首页卡片按遥控器焦点成行。手机横屏分支仍按触控组织，直接移植成本高。 [主题][theme] · [字体][type] · [手机布局][shell] · [首页轮播][mobilehome] · [TV 主题](../../app/src/main/java/com/cutedino/xiangsuplayer/ui/theme/PixelPlayTvTheme.kt#L70) |
| **浏览与播放器转场**。详情路由用 AOSP shared-axis 进出场，主标签有自己的过渡。悬浮底栏用弹簧变化，标签可按选中态放大并偏移相邻项。迷你播放条与全屏播放器共用展开进度，用 `Animatable`、图层位移／缩放、滑动与预测返回处理拖动和中断。 | **低（整套转场）/ 低**。TV 更需要确定后立即反馈、返回恢复条目与滚动位置。可借鉴绘制阶段读动画状态及过渡可中断，TV 按键触发的短淡入和静态回退已经适用。 [导航][routes] · [悬浮栏][floating] · [标签动效][tab] · [播放器面板][sheet] |
| **播放控制与进度**。经典和 Expressive 布局可切换，播放状态驱动主按钮权重、圆角和图标变形。进度组件支持多种轨道，波形样式从当前曲目峰值绘制，未分析片段使用合成幅度。 | **中（主按钮短反馈）/ 高**，**低（多套进度皮肤及实时波形）/ 中低**。TV 可保留播放形态变化和已播放区静态浅波形，方向键定位仍应清楚地显示预览、确认与取消。 [外观设置][styles] · [控制动效][controls] · [进度波形][seek] · [TV 播放器](../../app/src/main/java/com/cutedino/xiangsuplayer/ui/player/PlayerActivity.kt) |
| **滚动歌词**。LazyColumn 配合吸附和当前行定位，位置高频更新通过 State 缩小到依赖进度的区域。歌词行可变字号、透明度、行距，当前字词用弹簧、位移和颜色，邻近句保持较弱的层级。手动拖动、点词定位和回到当前行都建立在触控交互上。 | **中高（行级定位与渐进高亮）/ 中高**。适合在 TV 的纯歌词和点歌路径复用时间轴、缓存测量与局部绘制，优先保住当前句和译文的远距离可读性。整行弹跳、逐字上浮和触控吸附的价值较低。 [歌词列表][lyrics] · [逐词动效][words] |

## 模糊、流体背景与液态玻璃

| 效果 | 手机端的渲染路径 | TV 必要性与可移植性 |
| --- | --- | --- |
| **原生毛玻璃与渐进模糊** | Haze 用 `hazeSource` 标记背后内容，用 `hazeEffect` 在顶栏、底栏、迷你播放器取样。可折叠顶栏使用垂直渐进模糊，透明度随收起进度变化，迷你条使用 ultraThin 与 40 dp 半径。导航栏在 API 31 以下或强制软件模式下另有回退，`rememberGraphicsLayer` 录制内容后约每 250 ms 读回整层位图，降采样、盒式模糊，再按浮层位置裁切。 [折叠顶栏][topbar] · [迷你条][minibar] · [导航门控][navgate] · [低版本实现][oldnav] | **低 / 中低**。TV 左栏与底栏已经有稳定底色，实时采样对找歌和可读性的收益有限。若评估单处毛玻璃，可限制区域并以有足够对比度的实色表面回退。整屏位图读回会给低端盒子带来额外带宽、分配和绘制成本。 |
| **全屏控件玻璃与按压高光** | 历史日志记载播放器控件玻璃开关。当前 `FullPlayerContent` 把 `playerGlassControls` 固定为 `false`，仍保留 Haze 30 dp、半透明底和径向高光分支。其他 Haze 入口独立生效。 [历史日志][changelog] · [当前开关][glassoff] · [模糊分支][glassnative] · [按压分支][glow] | **低 / 中低**。TV 焦点需要固定轮廓与即时按压状态，透明容器与内容底色耦合。当前原生分支不足以作为待移植的已启用效果。 |
| **歌词远行虚化与自定义背景模糊** | 非当前歌词行用 `Modifier.blur` 模糊自身内容，API 31 以下保持清晰。自定义背景在支持时用 GPU 模糊，兼容分支走 `BlurryBackdrop`。该组件当前实际用 Coil 小位图、`AndroidView(ImageView)` 与 Kotlin StackBlur，模糊调用在 View 的 update 中。 [歌词行][lyricblur] · [自定义背景][custom] · [StackBlur 调用][stack] | **低（模糊文字）/ 高（效果机制）**。电视远处的相邻歌词仍需要可读，用字号、字重和颜色即可分层。**中（静态封面或背景预模糊）/ 高**。一次性小图模糊可复用概念，在后台生成并按封面缓存，前景用固定暗层保护。 |
| **沉浸式封面** | 不透明主题色底，加一次性模糊的 256 px 封面延伸层、渐变遮罩和清晰封面。清晰层用 `DstIn` 渐隐和离屏混合，播放／暂停驱动 650 ms 展开与收回。自定义背景有优先路径。 [封面合成][immersive] · [展开进度][covermotion] · [背景层][fullbackground] | **中高 / 中高**。最适合 TV 的画面增强是同封面取色、局部模糊延伸和切歌淡入，按横屏封面／歌词分栏调整遮罩方向。可把耗时计算放在换图时，暂停仍保持稳定布局与焦点。 |
| **绚丽流体背景（AMLL 风格）** | `IsolationBackground` 解码 256 px 封面、提取配色并过渡。API 33+ 用硬件 `RenderNode` 探测着色器可用性，随后由 Canvas、AGSL `RuntimeShader` 和颜色／时间／低频 uniforms 单 pass 绘制，绘制阶段更新。帧循环有 60/30 fps 档和不可见／暂停门控。低版本或探测失败用 Cloudy 静态封面模糊 + 不透明色底。静态取色和纯色另有路径。 [取色与门控][isolation] · [硬件探测][probe] · [AGSL][agsl] · [Cloudy 回退][cpufallback] | **低（默认）/ 中低**。可以在可关闭的全屏模式实验，先测 1080p／4K 的 GPU、掉帧和音频连续性。API 33 以下及能力探测失败时用静态封面色或低分辨率图即可。TV 当前还需另接音频采样才能获得低频律动。 |
| **液态玻璃折射** | 在 `web-remote/index.html` 的玻璃视图。JS 按控件尺寸生成位移图，SVG `feDisplacementMap` 分别偏移 RGB 再合成色散，CSS `backdrop-filter: url(...)` 让背景折射。浏览器能力检测失败时使用普通毛玻璃。网页的粒子背景与 Folia 歌词另有 Web 渲染。 [SVG 滤镜][displace] · [位移图与能力检测][map] · [网页视图][webui] | **低 / 原生低、网页中**。TV Compose 无法直接使用 CSS/SVG backdrop 渲染链。原生近似需建立背景采样层和 API 33+ 的着色器，需额外处理叠层、对比度和设备兼容。若 TV 的手机网页后续确实采用此视觉，可以独立评估浏览器支持与普通玻璃回退。 |
| **Apple Music 动态封面** | provider 按曲名、歌手及可选专辑匹配视频，独立 ExoPlayer 在 TextureView 静音循环，首帧就绪后 500 ms 渐入，主 URL 失败换备用，静态封面承担过渡和缺省显示。 [封面查找][canvassrc] · [视频渲染][canvasplay] | **低 / 中低**。额外网络请求与视频解码会和持续音乐播放分享资源。静态封面已能表达同样的曲目信息，TV 的首轮视觉增强优先做离线可用的封面色与遮罩。 |

平台界限以 Android API 文档和 TV 导航规范为准。 [RenderEffect（API 31）](https://developer.android.com/reference/android/graphics/RenderEffect) · [RuntimeShader（API 33）](https://developer.android.com/reference/android/graphics/RuntimeShader) · [Android TV 遥控器导航](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv)

## 后续验证优先级

1. 先按“搜索或播放页 → 歌手／专辑 → 曲目 → 返回原条目”评估按键成本，再用同一首歌的本地 LRC、网易云 YRC、跨源普通歌词和无歌词状态检查回退。
2. 视觉实验从静态沉浸封面开始。覆盖高亮与暗色封面、无封面、系统动画倍率为 0、紧凑布局、大字号、纯歌词与点歌。记录 1080p／4K 的帧时间、内存和持续播放情况，之后再判断局部 Haze 或 AGSL 是否值得增加。
3. 将私有媒体库与手机远控分别验证。媒体库覆盖账号失效、断网和定位，手机远控覆盖配对、状态同步、断线恢复与普通播放／点歌切换。

[build]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/build.gradle.kts#L121
[deps]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/gradle/libs.versions.toml#L38
[routes]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/navigation/AppNavigation.kt#L145
[screens]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/navigation/Screen.kt#L12
[library]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt#L1297
[search]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/SearchScreen.kt#L458
[artist]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/ArtistHomepageScreen.kt
[album]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/AlbumDetailScreen.kt
[playlists]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlaylistViewModel.kt
[lyricsrepo]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/repository/LyricsRepositoryImpl.kt#L627
[resolver]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/playback/OnlineSourceResolver.kt#L125
[lx]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/lx/LxJsEngine.kt
[timer]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/SleepTimerStateHolder.kt#L109
[accounts]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/AccountsScreen.kt#L105
[kugou]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/kugou/dashboard/KugouDashboardScreen.kt
[jellyfin]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/jellyfin/JellyfinRepository.kt
[navidrome]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/navidrome/NavidromeRepository.kt
[fullmanifest]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/full/AndroidManifest.xml
[discovery]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/HomeScreen.kt#L665
[books]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/navigation/AppNavigation.kt#L763
[radio]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/navigation/AppNavigation.kt#L859
[together]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/listentogether/ListenTogetherCoordinator.kt
[downloads]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/DownloadManagerScreen.kt#L69
[downloadservice]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/service/http/MusicDownloadService.kt#L487
[backup]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/backup/AppDataBackupManager.kt
[engine]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt#L215
[audioopts]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/SettingsCategoryScreen.kt#L1655
[web]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/service/http/WebRemoteServerService.kt#L427
[webui]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/assets/web-remote/index.html#L2307
[plugin]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/plugin/XsPluginRuntime.kt
[plugininstall]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/plugin/XsPluginManager.kt#L118
[mcp]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/service/mcp/McpServerService.kt
[ai]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/AiAssistantScreen.kt
[stats]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/StatsScreen.kt
[recognition]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/recognition/SongRecognitionRepository.kt
[manifest]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/AndroidManifest.xml
[modules]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/settings.gradle.kts
[theme]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/ui/theme/Theme.kt#L110
[type]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/ui/theme/Type.kt
[shell]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/MainActivity.kt#L1150
[mobilehome]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/HomeScreen.kt#L957
[floating]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/FloatingNavBarContent.kt#L83
[tab]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/TabAnimation.kt#L46
[sheet]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheetV2.kt#L258
[styles]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlayerLyricsAppearanceScreen.kt#L431
[controls]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/player/AnimatedPlaybackControls.kt#L147
[seek]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/StyledPlayerSeekBar.kt#L628
[lyrics]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/LyricsSheet.kt#L1633
[words]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/LyricsSheet.kt#L2453
[lyricblur]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/LyricsSheet.kt#L2116
[glassoff]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt#L450
[glassnative]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt#L2444
[glow]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt#L3450
[topbar]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/CollapsibleCommonTopBar.kt#L120
[minibar]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheetLayers.kt#L159
[oldnav]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/blur/LowVersionBlurBackdrop.kt#L68
[navgate]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/MainActivity.kt#L1315
[custom]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/CustomPlayerBackground.kt
[stack]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/blur/BlurryBackdrop.kt#L65
[immersive]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/player/ImmersiveCoverBackdrop.kt#L46
[fullbackground]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt#L1497
[covermotion]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt#L639
[isolation]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/isolation/IsolationBackground.kt#L291
[agsl]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/isolation/IsolationBackground.kt#L555
[probe]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/isolation/IsolationBackground.kt#L178
[cpufallback]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/isolation/IsolationBackground.kt#L627
[displace]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/assets/web-remote/index.html#L2432
[map]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/assets/web-remote/index.html#L6140
[canvassrc]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/data/canvas/AppleMusicCanvasProvider.kt
[canvasplay]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/player/CanvasArtworkPlayer.kt#L47
[changelog]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/CHANGELOG.md#L71
[timerentry]: https://github.com/r3n011/XiangsuPlayerHQ/blob/519d4c0325e475d66c1ce87d0d51665d8579892f/app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerOverlaysLayer.kt#L397
