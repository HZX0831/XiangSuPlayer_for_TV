# 第一轮仓库审查（存档）

审查日期：2026-11-11。以下描述重构前的仓库与后端副本，文件路径和行号对应当时版本。当前产品范围见 [PRODUCT.md](../../PRODUCT.md)，实现与设备结果见 [验收记录](IMPLEMENTATION-REVIEW.md)。

## 1. 两个工程分别说明什么

| 范围 | 事实 | 对设计的影响 |
| --- | --- | --- |
| 原仓库 app/src/main/.../ui | 12 个 Kotlin UI 文件，包含 Compose 主框架、播放器、曲库与目录等 | 用于现有功能清点、术语和交互回归基线；不是新视觉约束 |
| 优化工程 PixelPlayer-Backend | README/manifest/Gradle 一致表明没有 Activity、启动器、Compose 和界面资源 | 本轮不能把后端 APK 当成新界面预览；下阶段需要新建前端接入，不能只改主题 |
| 优化工程 core/data/ncmapi | 保留媒体服务、源解析、歌词、账号、目录、配对和持久化 | 新设计依赖这些真实契约；不改动它们 |
| 原仓库工作区 | 开始工作时已有大量修改、删除和未跟踪文件 | 不回滚、不暂存、不提交这些既有更改；设计产物单独新增 |

## 2. 页面与入口盘点

原 UI 路径前缀为 G:/PixelPlayer/app/src/main/java/com/cutedino/xiangsuplayer/ui/。

| 文件与起始位置 | 检查内容 | 必须带入新方案 |
| --- | --- | --- |
| main/MainActivity.kt:107 | 主框架、页面状态、导航/内容/mini 三组焦点、账号弹层 | 当前项恢复、返回导航与退出层级、后台播放独立 |
| components/TvCommonComponents.kt:117、385、439 | 导航项、胶囊导航、全局 mini、波形进度、封面与弹窗 | 能力保留；胶囊和漂浮圆角容器不继承 |
| main/TvPrimaryScreens.kt:127 | 个性/游客推荐、私人 FM、心动模式、新歌地区 | 两类账号状态、不自动切歌 |
| main/TvPrimaryScreens.kt:556 | 平台/榜单/歌曲主从结构、刷新 | 三平台三十榜、列表位置记忆 |
| main/TvPrimaryScreens.kt:703 | 输入编辑态、IME 返回、手机输入、平台/歌单过滤、热搜/历史 | 搜索提交与编辑分离；结果回来不抢其他区域焦点 |
| main/TvSecondaryScreens.kt:124 | 本地红心、三平台入口、云盘、目录 | 不把公开资料/榜单包装成私人同步 |
| library/PlaylistDetailActivity.kt:95 | 歌单曲目分批加载、总数、补充重试、全部/随机 | 部分成功独立于完全失败 |
| main/TvFolderLibrary.kt:53、206 | 授权、层级、分页、移除、HTTPS WebDAV 配置 | 系统选择器依赖、移除只是移除入口而非删除音乐文件 |
| player/PlayerActivity.kt:143 | 全屏歌词、译文、90 秒纯歌词、定位预览、主控制 | 唤醒键不连带执行；定位确定提交/返回取消 |
| player/PlayerActivity.kt:607、694、855 | 队列条目菜单、评论、音源音质 | 下一首/移除、只读评论、请求档位与实际流分开 |
| player/TvKaraokeScreen.kt:55 | 双行歌词、YRC 逐字、点歌、暂停/跳过、队列 | 非麦克风 KTV；退出双行模式的行为需与 begin/end 契约一致 |
| main/TvSecondaryScreens.kt:416 | 模板音源市场/安装/导入/替换、矩阵启停/测速/卸载 | 普通听歌路径不用面对竞速矩阵；高级功能仍完整可达 |
| main/TvSecondaryScreens.kt:872 | 历史单删/清空确认/全部播放 | 删除后稳定回焦；空状态可返回找歌 |
| main/TvSecondaryScreens.kt:1024、1282 | 默认音质、壁纸/遮罩、兼容、关于、网易云及 QQ | 所有设置有位置；账号过期/扫码/刷新/解绑保留 |
| theme/TvFocusNavigation.kt、TvFocusModifiers.kt | 稳定 key、FocusRequester、焦点恢复与滚动 | 复用逻辑思路；圆角、缩放、色彩与新设计分别处理 |

## 3. 现有视觉证据与反参考

PixelPlayTvTheme.kt 定义 #0F1827 背景、#162236 容器、#5C9CFF 蓝与 #F06292 粉，10/16/24 dp 圆角；封面取色参与 primary/secondary。TvCommonComponents.kt 使用顶部胶囊导航与底部漂浮 mini；播放器使用 PlayerControlPill。大量页面用卡片网格、文字小胶囊和圆角弹层。

这些结论是源码证据，不是看过当前 APK 后的视觉测量。未发现专用 screenshot/golden/capture 文件；资源目录的框架图标不当成截图。后端没有可启动 UI，ADB 检查因其用户配置目录不可写而未取得设备状态。本轮不安装原 APK，也不声称做过遥控器实测。

重设计明确替换：统一圆角容器、蓝粉双强调、悬浮胶囊导航、所有信息都卡片化、装饰性波形与流体背景作为默认骨架。保留封面/壁纸偏好能力，但限制在不会破坏文本与焦点的图像区域；新默认不复用旧皮肤。

## 4. 优化后端对页面状态的约束

以下路径相对优化工程 app/src/main/java/com/cutedino/xiangsuplayer/。

| 契约/证据 | 产品或设计结论 |
| --- | --- |
| core/audio/PlayerController.kt:19–31、106–133 | 队列项有独立 ID；uiState 包含 seekable/error；不能只用 song.id 表示当前队列位置 |
| core/audio/PlaybackEngine.kt、PlaybackSnapshotStore.kt | 恢复暂停；重取流；Karaoke begin/end 是真实播放行为，退出时不能擅自改写队列策略 |
| core/source/ISoundSourceAdapter.kt | Full / Trial；requestedQuality、bitRate 与 sourceName 的字段边界；实际码率未知时显示“实际规格未提供” |
| core/library/FolderLibrary.kt:22、114、169 | more 与 incomplete 不同；无法拖动不能画可操作进度滑块 |
| core/phone/PhoneInputServer.kt + README | JSON 服务、短期令牌、有限会话/选项、同源 Host/Origin；现有手机网页被移除，后续前端需要配套同源客户端 |
| data/auth/MultiPlatformAccountManager.kt:148、246、386 | 评论身份校验；QQ 公开资料关联；酷狗返回公开榜单 |
| data/auth/FavoriteSyncWorker.kt、data/db/FavoriteSyncEntry.kt | 本地收藏与云端待同步分离；状态字段不足时宁可显示“已收藏到本机”，不捏造详细故障诊断 |
| core/source/market/SourceMarketRepository.kt、UrlTemplateSourceAdapter.kt | 模板能力不等于任意 JS 插件运行时；保留不兼容资源的管理入口 |
| data/db/SongDao.kt、VERIFICATION.md | 最近 200 条历史上限；清空与移除需要完整回焦行为 |

## 5. 不从接口或注释推导成已上线功能

README 的“歌手搜索”是输入查询用途，不足以证明独立歌手结果/详情；曲库标题中的“收藏专辑”没有独立页面证据；源码注释写“酷狗账号/同步”但当前返回的是两张公开榜。ncmapi 广泛的 endpoint 不等于 TV 端拥有下载、MV、直播、账号家庭档案或评论发布能力。

## 6. 设计探索的方法与工具边界

产品机制：让家人用一只遥控器，跨云端与目录音乐找到歌曲，并在播放时清楚看见歌词与队列。要避开的常规图式是流媒体圆角海报货架；其常见反面“黑底极简大封面”也不能单独作为原创性依据。

七个候选来源在生成方案前建立：

| 候选来源 | 能承载的任务 | 可识别的视觉语法 | 去留 |
| --- | --- | --- | --- |
| 家庭电视节目表与音乐会节目簿 | 先认栏目，再认曲目 | 暖纸底、朱红定位、目录对齐、印刷层级 | A：节目簿 |
| 剧场字幕与幕间报幕 | 家人从远处认当前曲与歌词 | 炭黑、象牙字、浅苔色动作、大空场和水平基线 | B：幕间 |
| 唱片店分类目录与现代中文拼版 | 一屏看见主要任务和收藏路径 | 硬网格、墨绿/黄、粗细字重对照、开放分区 | C：唱片索引 |
| 公共图书馆楼层索引 | 清楚区分目录/层级 | 浅灰、深墨、单一色标、条目与位置编号 | 未采用；与 A 的索引逻辑接近 |
| 家庭相册接触印样 | 多张封面可同时识别 | 留白、图片边界、序号和简短注释 | 未采用；容易重回封面货架 |
| 展览作品清单 | 把作品与资料分开 | 大作品、侧边说明、稀疏分隔 | 未采用；浏览效率弱于前三项 |
| 音乐杂志内容页 | 支持发现、地区与榜单 | 标题主导、非对称列宽、规则化版面 | 未采用；与 C 合并会削弱差异 |

Impeccable context 的执行入口遇到 CET 系统错误；随后直接读取 PRODUCT/现有源码和技能 init、shape、new-work、android、operate 参考。concept-seed 的 Windows 启动器调用返回语法错误，未得到可执行的种子结果。因此三套是明确标注的人工原创候选，不伪造随机抽选、目录挑战者或工具评分。用户明确要求先给三套方案而不实施；本轮不锁定方向，不建立冒充已批准的 DESIGN.md 或设计 token 实现。

## 7. 核验方式

交付结束时核对优化工程既存 84 个文件的 SHA-256 与原仓库 12 个 UI 文件哈希；检查新增文档的本地链接、六张图的 16:9 尺寸、关键文字溢出与色彩对比。此项是文档/概念稿核验；没有新的 Android 构建、单元测试、Lint 或设备 D-pad 结果。
