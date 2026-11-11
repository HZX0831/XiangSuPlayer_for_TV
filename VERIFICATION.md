# 后端修复与验证

## 修复记录

| 审查项 | 修复 | 回归证据 |
| --- | --- | --- |
| P1 退出后会话写回 | 退出前捕获远端注销凭据，原子推进本地会话版本。迟到的 Cookie 和资料写入被丢弃。 | NcmBackendRegressionTest |
| P1 坏地址反复获胜 | 播放器按当前曲目和音质记录失败音源，在剩余预算内换候选。手动重试保留未耗尽的排除集合。 | SourceBackendRegressionTest、ResolutionTest |
| P1 音源索引损坏阻断启动 | 校验索引及文件路径。主索引无效时读取最后有效副本，两个索引均不可读时以空动态音源启动。 | SourceIndexRecoveryTest |
| P1 网络响应无大小上限 | 为模板、原生接口及其余主要数据入口增加读取上限。网易云单独限制解压结果和 JSON 嵌套。 | NcmBackendRegressionTest |
| P2 网易云错误变成空数据 | 区分 HTTP、业务状态和格式错误。扫码状态单独校验，同源 307/308 保留原请求方法。 | NcmBackendRegressionTest、NcmEndpointTest |
| P2 匿名会话失败后不重试 | 并发请求共享一次注册。按会话版本检查令牌，失败后退避重试，注册步骤有独立截止时间。 | NcmBackendRegressionTest |
| P2 单曲无资源触发全局熔断 | 只对传输故障和服务端 HTTP 错误累计全局熔断。冷却源以延后探测参与同一预算。 | SourceBackendRegressionTest |
| P2 咪咕续播缺少稳定身份 | 分开稳定 ID 和临时 URL。恢复时按相同平台与曲目 ID 查找，快照、Room 和第三方模板参数均排除临时 URL。 | SourceBackendRegressionTest、PlaybackSnapshotStoreTest、AppDatabaseMigrationTest |
| P2 云端红心静默失败 | 本地收藏和云端待同步记录同事务写入。后台按账号重试，使用令牌保护较新的操作。 | RepositoryWritesTest |
| P2 配对慢请求耗尽连接 | 请求头及请求体共用绝对截止时间，服务内置配对有效期。配对改为纯 JSON 接口。 | PhoneInputServerTest |
| P2 WebDAV 区间探测阻断播放 | Range 不可用时允许普通 GET，关闭拖动。鉴权失败仍单独报错。 | WebDavClientTest |
| P2 目录分页重复全量枚举 | 按目录缓存排序快照，本地只为当前页读取媒体标签。WebDAV 流式解析，大响应触及限制时返回带 incomplete 标记的部分结果。 | WebDavClientTest |
| P2 空音源配置被恢复成默认值 | 明确区分配置缺失与显式空集合。 | SourceIndexRecoveryTest |
| P3 数字曲目 ID 跨平台串用评论 | 仅对网易云曲目直接使用数字 ID。其他云平台需匹配同一录音，本地目录不发起外部匹配。 | CommentTrackIdentityTest |
| P3 历史表无磁盘保留上限 | 事务内写入并裁剪到最新 200 条，v3 升级时清理旧历史。 | RepositoryWritesTest、AppDatabaseMigrationTest |
| P3 部分 YRC 丢失歌词 | 合并有效 YRC 和缺失的 LRC 行，普通歌词行保留行级时间。 | LyricEngineTest |

## 副本边界

- 保留 Android Application、媒体服务、网络、持久化、两个 Gradle 模块和构建所需的安全 XML。Room 升级到 v4，并导出 schema。
- 前端源码、界面资源、Activity、Compose 配置和前端测试均未纳入。配对服务不包含网页。设备验证直接从 Android instrumentation 启动后端。
- 复核了复制前后原项目 67 个源文件和配置文件的 SHA-256，结果全部一致。

## 实测结果

- Gradle Wrapper 构建通过，生成调试 APK 和设备测试 APK。
- JVM 测试全部通过。`app` 34 个，`ncmapi` 8 个，零跳过。
- 两个模块 Lint 均为零错误。`app` 44 条警告，`ncmapi` 5 条警告。
- API 36 Android TV 模拟器通过 1 个设备测试。测试从无 Activity 的应用进程绑定 Media3 服务，并成功读取 Room。APK manifest 核对 `minSdk 21`、`targetSdk 34`。
- 网易云、第三方音源和 WebDAV 回归使用合成数据或响应。真实服务、API 21 设备及完整音频解码路径属于后续验收范围。

[验证结果摘录](verification/results.txt)
