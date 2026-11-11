# v2.1.0 preview 发布前检查

## 范围

检查对象为根工程 TV 重构预览版，范围覆盖播放及快照、歌词、曲目身份、数据库升级、音源与市场、账号与会话、目录／WebDAV、手机输入协议、共享 TV 组件、导航／弹层／输入、清单、构建及发布文档。

此次执行静态检查与定向回归。此前设备与在线服务结果见验收记录；真实账号和外部设备的验证范围分别列明。

## 已修复

| 问题 | 影响与修正 | 证据 |
| --- | --- | --- |
| 作者混淆、版本写死 | R3n_011 为主项目作者；暮间雾列入鸣谢；cutedino 与 HZX0831 分开；关于页读取 BuildConfig.VERSION_NAME，默认及中文资源同步 | README、关于页、四种窗口／字号定向检查 |
| Cookie 读改写竞态 | 并行响应各自读取旧 Cookie 后覆盖另一次写入；所有 Cookie 修改在同一 Session 锁内串行执行，不改变持久化格式 | NcmSessionTest 控制两次响应交错，验证两项字段均保留 |
| 市场失败被吞没 | HTTP 错误、网络错误和格式错误被恢复为空列表，界面无法提示重试；现在保持 Result.failure，取消继续向上传递，合法空数组仍为空成功 | SourceMarketFailureTest：HTTP／格式／断网、合法空、取消三项回归；原生失败→重试→空状态 |
| 跨平台评论串曲 | QQ／酷狗纯数字 ID 被当成网易云 ID；只有网易云曲目直用本平台 ID，其他平台先以曲名与歌手匹配 | CommentIdentityTest：跨平台匹配及网易云原 ID 两项回归 |
| 文档与实现不符 | 去掉收藏专辑、独立歌手结果、即时搜索及菜单键切音轨等描述；修正源码结构、构建与兼容说明 | README 本地链接和截图路径检查 |

评论关键词匹配仍无法保证同一录音版本，README 已说明。没有扩大音源大小限制、变更应用 ID、数据库结构或手机协议。

## 构建与回归

- 环境：JDK 21、Gradle 8.13、SDK 36，离线依赖缓存。
- `:app:assembleDebug :app:assembleDebugAndroidTest`：通过。
- `:app:testDebugUnitTest :ncmapi:testDebugUnitTest`：48 项通过（app 44，ncmapi 4），零失败／错误。
- `:app:lintDebug :ncmapi:lintDebug`：零错误；既有警告 app 662、ncmapi 5，未增加。
- 初次构建因受限守护进程无法读取本机 SDK 失败；使用有 SDK 访问权限的独立构建进程后通过。
- `.gitignore` 验证：`banked-*`、旧嵌套工程、工具缓存、签名配置和密钥均被忽略。
- 发布候选文本扫描未发现匹配的私钥、GitHub Token 或长 MUSIC_U 凭据。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，27,647,471 字节。

SHA-256：`457ff6078f40d17b81769920565559f37ffb57d6c7372e65de937f102e4424c3`。

正式版签名、main 推送、分支清理和 GitHub prerelease 在 README 审阅后继续；此处不预先标记完成。
