# PixelPlayer 后端 Bug 审计

审计日期：2026-11-11。基线为 `G:/PixelPlayer` 当前工作树，包含上轮已集成的后端修复。审计追踪了 `core/`、`data/`、`ncmapi/` 和播放相关的 Service、控制器及状态持有者。项目没有单独的播放器 ViewModel，主要状态由 `PlayerController`、`PlaybackEngine` 和 `PlaybackService` 协同管理。UI 只作为命令入口和状态消费端追踪。本轮未修改业务代码。

共确认 17 项问题：7 项通过 Android TV 模拟器、JVM 或本地服务复现，10 项有完整静态调用链。另列 3 项待验证风险。P1 表示核心播放、登录或队列恢复严重受损；P2 表示特定操作造成明显功能失败、状态错误或同步丢失；P3 表示影响局限或触发条件较窄。同级按对用户的影响排序。模拟复现只证明所列组件在给定输入下的行为。

## P1：核心功能受损

### 1. 系统停止播放后，应用内播放键无法续播

**严重程度与结论：** P1，已在 Android TV 模拟器复现。

**位置与原因：** [PlaybackService.kt:38](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackService.kt#L38) 的 Media3 转发播放器没有拦截 `stop()`，底层 ExoPlayer 进入 `IDLE`，媒体项仍在。应用内播放走 [PlaybackEngine.kt:605](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L605)，既有媒体项且没有记录的播放错误时只调用 `play()`，不重新准备媒体。

**复现与证据：** 播放本地测试曲目到约 7 秒，发送系统 `MEDIA_STOP`，再用 D-pad 按应用内播放键。连续两次按下后会话仍为 `NONE`，没有音频；同一状态下发送系统 `MEDIA_PLAY` 可在约 7.1 秒处恢复，证明媒体项仍可播放。

**建议修复与回归：** 将系统停止纳入引擎状态管理；应用收到播放意图且底层为 `IDLE` 时先准备当前项。断言两条播放入口都恢复同一媒体项、队列和进度。

### 2. 网易云扫码成功后拉取资料失败，账号卡在半登录状态

**严重程度与结论：** P1，半登录状态已在 JVM 复现，电视端入口限制由静态调用链确认。

**位置与原因：** [NcmModules.kt:272](ncmapi/src/main/java/net/moriafly/ncm/NcmModules.kt#L272) 在扫码返回 803 时先提交登录 Cookie；[TvSecondaryScreens.kt:1047](app/src/main/java/com/cutedino/xiangsuplayer/ui/main/TvSecondaryScreens.kt#L1047) 随后在弹窗协程请求用户资料。失败或关闭弹窗可以留下 Cookie、但没有用户 ID。[重新扫码入口](app/src/main/java/com/cutedino/xiangsuplayer/ui/main/TvSecondaryScreens.kt#L1105) 依赖 `!isLogin`，[账号曲库](app/src/main/java/com/cutedino/xiangsuplayer/ui/main/TvSecondaryScreens.kt#L175) 和收藏同步仍需要有效账号身份。

**复现与证据：** 用合成扫码成功输入仅保存 `MUSIC_U`，让资料请求失败、超时或被取消。JVM 观察到 `isLogin=true`、`userId=0`、`accountIdentity=null`；未使用真实账号复现扫码过程。

**建议修复与回归：** 明确区分凭证已取得、资料待补齐和可用登录三个状态，允许资料重试或重新扫码，进程重启后继续恢复。用假服务返回 803 后分别模拟超时和取消，断言资料未齐时仍有恢复入口，补齐后身份和曲库一起生效。

### 3. 冷启动立即进入 KTV 可能删除原播放队列

**严重程度与结论：** P1，静态证据充分。

**位置与原因：** [PlaybackEngine.kt:202](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L202) 异步恢复普通播放快照，应用恢复结果要求 `songRevision == 0`。[进入 KTV](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L848) 会保存当时的普通队列，再清空和推进曲目版本。如果恢复仍未完成，当时的快照为空，[PlaybackSnapshotStore.kt:31](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackSnapshotStore.kt#L31) 将删除磁盘快照。旧的恢复结果随后因版本变化被丢弃。

**复现条件与证据：** 磁盘已有正常队列，测试用闸门挂起快照读取；在读完成前进入 KTV，待入口的保存和清空完成后释放读取，再退出 KTV。根据上述两个分支，原有队列、当前项和进度均无可恢复来源。

**建议修复与回归：** 让进入 KTV 等待初始恢复；加载未决时禁止空状态覆盖已有快照。用挂起的假快照存储断言进入并退出 KTV 后，队列、当前位置和磁盘快照均保留。

## P2：可见功能失败、状态错误或同步丢失

### 4. 改变播放模式会撤销“下一首播放”

**严重程度与结论：** P2，已在 Android TV 模拟器复现。

**位置与原因：** [PlaybackEngine.kt:298](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L298) 修改当前播放列表；[模式切换](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L676) 会从未同步更新的 `originalPlaylist` 重建顺序，覆盖用户编辑。

**复现与证据：** 随机模式下测试队列为 A、B、C。把 C 设为下一首，快照变为 A、C、B；切到单曲循环后，快照又变为 A、B、C。

**建议修复与回归：** 将用户显式插队写入可跨模式保留的队列状态，模式切换统一从该状态生成顺序。验证切换模式后手动按下一首仍选中 C。

### 5. 迟到的私人 FM 或心动模式结果覆盖较新的播放操作

**严重程度与结论：** P2，静态证据充分。

**位置与原因：** [普通选曲](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L286)、[私人 FM](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L356)、[心动模式](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L397) 和[模式切换](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L676) 并未统一更新 `requestedQueueRevision`。进入队列选曲、上一首、下一首或离开模式等路径可能让先前推荐请求仍有权提交。

**复现条件与证据：** 挂起推荐请求，先执行一次队列内手动选曲或离开推荐模式，再释放旧请求。旧请求通过原版本校验后仍可提交推荐队列，覆盖用户刚选择的播放意图。

**建议修复与回归：** 所有会改变播放目标或模式的入口统一推进意图版本并取消过时推荐。用可控挂起点逐一验证手动选曲、前后切歌、模式切换后的旧响应都无权写入。

### 6. 旧流报错会抢占正在进行的手动切源

**严重程度与结论：** P2，静态证据充分。

**位置与原因：** [手动切源](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L440) 开始解析新源时旧流仍可能发出错误。[新解析的版本和取消逻辑](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L489) 与[媒体错误恢复](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L566) 共用请求控制。旧流错误触发自动解析后可以推进版本并取消更晚发出的手动解析。

**复现条件与证据：** 让 A 正在播放，手动切到 B 并挂起 B 的解析，在 B 完成前注入 A 的 Media3 错误。错误回调会启动新一轮自动解析，原 B 请求不再具有提交资格。

**建议修复与回归：** 让错误回调绑定媒体项和播放请求代次；存在更新的手动请求时，旧流故障不得取消它。用假播放器及解析器验证 B 完成后正常接管，B 真正失败时才执行回退。

### 7. 网易云红心同步把缺少成功确认的响应当成已同步

**严重程度与结论：** P2，响应判定已在 JVM 复现，待同步条目删除由调用链确认。

**位置与原因：** [NcmRequest.kt:288](ncmapi/src/main/java/net/moriafly/ncm/NcmRequest.kt#L288) 在响应缺少业务码时默认按 200 处理；[收藏写接口](ncmapi/src/main/java/net/moriafly/ncm/NcmApi.kt#L52) 使用该检查，[FavoriteSyncWorker.kt:33](app/src/main/java/com/cutedino/xiangsuplayer/data/auth/FavoriteSyncWorker.kt#L33) 收到成功便删除待同步条目。

**复现与证据：** 给检查函数传入 HTTP 200 的 `{}` 和 `{"message":"upstream error"}`，两者均通过；显式业务码 301 被拒绝，200 通过。异常响应因而可使本地继续显示已收藏，而远端写入未经确认且不再重试。

**建议修复与回归：** 写接口按端点要求显式业务成功确认，未知响应保留队列并按现有策略退避。以假服务验证缺少业务码、错误码保留条目，明确成功才删除对应令牌。

### 8. 网易云 401 明确撤销凭证后仍显示已登录

**严重程度与结论：** P2，已在 JVM 复现。

**位置与原因：** [NcmRequest.kt:283](ncmapi/src/main/java/net/moriafly/ncm/NcmRequest.kt#L283) 在合并 Cookie 前就因非 2xx 抛错；[NcmSession.kt:55](ncmapi/src/main/java/net/moriafly/ncm/NcmSession.kt#L55) 仍用原有 `MUSIC_U` 判断登录。

**复现与证据：** 先存入登录 Cookie，再向响应处理函数传入 HTTP 401 和 `Set-Cookie: MUSIC_U=; Max-Age=0`。函数抛出 `NcmHttpException`，但 `isLogin` 仍为 `true`。

**建议修复与回归：** 对匹配当前会话版本的响应识别明确撤销凭证，清除本地账号并通知观察者。覆盖迟到的旧会话 401 和普通网络失败，保证两者不会注销当前账号。

### 9. 退出登录后，电视仍可能显示旧账号的私有列表

**严重程度与结论：** P2，静态证据充分。

**位置与原因：** [NcmModules.kt:184](ncmapi/src/main/java/net/moriafly/ncm/NcmModules.kt#L184) 先清本地会话，再发远程退出请求。[TvSecondaryScreens.kt:1165](app/src/main/java/com/cutedino/xiangsuplayer/ui/main/TvSecondaryScreens.kt#L1165) 在弹窗协程等待远程返回后才推进界面账号版本；[曲库加载](app/src/main/java/com/cutedino/xiangsuplayer/ui/main/TvSecondaryScreens.kt#L164) 依赖该版本触发。关闭弹窗可取消远程等待及后续通知，界面保留旧列表。

**复现条件与证据：** 先加载私有歌单，触发退出，假服务挂起远程请求。在确认本地会话清空后关闭弹窗；通知语句被取消，原列表不会因本地退出自动失效。

**建议修复与回归：** 本地退出即时发布状态，清除旧账号私有数据并使其在途请求失效；远程退出结果单独处理。用挂起的远程退出和迟到的列表响应验证退出后列表保持为空。

### 10. 星海首个无效播放地址会挡住可用备用地址

**严重程度与结论：** P2，已用合成网络响应复现。

**位置与原因：** [XingHaiSourceAdapter.kt:33](app/src/main/java/com/cutedino/xiangsuplayer/core/source/XingHaiSourceAdapter.kt#L33) 只用非空判断首个候选可用，直接返回；[SoundSourceRepository.kt:191](app/src/main/java/com/cutedino/xiangsuplayer/core/source/SoundSourceRepository.kt#L191) 随后以播放 URL 策略拒绝该候选，此时适配器的备用路径已经结束。

**复现与证据：** 首个接口返回非空的、策略禁止的 HTTP 地址，备用接口配置合法 HTTPS 地址。合成响应实验只观察到首个接口被调用，未尝试备用接口。

**建议修复与回归：** 每个候选先过同一套播放 URL 策略；首个被拒绝时继续备用接口，全部失败时保留恰当的错误类型。验证两个请求的调用顺序、最终地址和全失败时的分类。

### 11. 两部手机共用一个 KTV 二维码会相互使搜索结果失效

**严重程度与结论：** P2，已用真实本地 HTTP 服务和合成搜索结果复现。

**位置与原因：** [PhoneInputServer.kt:175](app/src/main/java/com/cutedino/xiangsuplayer/core/phone/PhoneInputServer.kt#L175) 与[搜索结果处理](app/src/main/java/com/cutedino/xiangsuplayer/core/phone/PhoneInputServer.kt#L186) 共用一份选择表，每次搜索覆盖整张表。[TvKaraokeScreen.kt:155](app/src/main/java/com/cutedino/xiangsuplayer/ui/player/TvKaraokeScreen.kt#L155) 将手机输入接到电视点歌流程。

**复现与证据：** A、B 两个客户端使用同一个会话，各自搜索，均收到 HTTP 200。B 搜索后，A 点选仍显示在页面上的结果收到 HTTP 409 `expired_choice`；B 的结果提交成功。

**建议修复与回归：** 按客户端或搜索批次保存候选，用独立结果 ID、过期时间和容量上限约束缓存。增加双客户端交错搜索、提交及过期测试。

### 12. KTV 搜索失败时手机提示“没有找到结果”

**严重程度与结论：** P2，静态证据充分。

**位置与原因：** [TvKaraokeScreen.kt:157](app/src/main/java/com/cutedino/xiangsuplayer/ui/player/TvKaraokeScreen.kt#L157) 把搜索失败转换为 `emptyList()`；[PhoneInputServer.kt:186](app/src/main/java/com/cutedino/xiangsuplayer/core/phone/PhoneInputServer.kt#L186) 按成功响应返回空列表，手机页面走普通无结果分支。

**复现条件与证据：** 让搜索仓库返回失败（如提供方全部断网）；与成功返回零条结果对照。两种输入经过上述路径得到相同的 HTTP 200 空列表，用户无法判断是否应重试。

**建议修复与回归：** 保留成功空结果与可重试失败两种响应。分别注入全失败、成功空列表和部分提供方返回有效结果，验证手机提示及可用结果。

### 13. 进入再退出 KTV 会截断超过 500 首的普通队列

**严重程度与结论：** P2，静态证据充分。

**位置与原因：** [PlaybackEngine.kt:809](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L809) 为磁盘续播限制快照长度；[进入 KTV](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L848) 复用该快照保存进程内的 `ordinaryQueue`，因此同一进程内退出 KTV 也只能恢复截断后的列表。

**复现条件与证据：** 从无同等上限的歌单详情载入 501 首歌，选中一首并播放一段，再进入、退出 KTV。用于恢复的对象在保存时已截断，无法恢复完整曲目顺序。

**建议修复与回归：** 内存模式切换保留完整队列和选中项，磁盘快照继续单独限额。验证 501 首及以上队列进出 KTV 前后 ID、顺序、当前项和位置一致。

## P3：局部或触发条件较窄的问题

### 14. 暂停时快速往返拖动进度，歌词可能停在错误行

**严重程度与结论：** P3，静态证据充分，已核对本地依赖中 LiveData 的发布行为。

**位置与原因：** [PlaybackEngine.kt:793](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L793) 比较当前歌词下标后调用异步 `postValue`；[PlayerActivity.kt:160](app/src/main/java/com/cutedino/xiangsuplayer/ui/player/PlayerActivity.kt#L160) 观察该下标。在主线程处理排队的发布前，第二次 seek 可能仍读到旧值，从而漏发最新下标。

**复现条件与证据：** 暂停在歌词行 A，在同一主线程轮次跳至 B 再跳回 A，最后执行待处理任务。第一次排入 B，第二次读取的仍是 A 并跳过发布；队列排空后，位置为 A、歌词为 B。

**建议修复与回归：** 主线程同步发布，或让位置、曲目代次和歌词行作为一个状态提交。用 Robolectric 暂停主 Looper，执行 A→B→A 后恢复 Looper，验证最终歌词对应当前位置。

### 15. 从未成功播放的曲目进入历史，挤掉真实播放记录

**严重程度与结论：** P3，静态证据充分。

**位置与原因：** [PlaybackEngine.kt:286](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L286) 在音源解析和播放成功前写历史；[SongDao.kt:65](app/src/main/java/com/cutedino/xiangsuplayer/data/db/SongDao.kt#L65) 在写入时裁剪至 200 条。解析失败不撤销历史。

**复现条件与证据：** 预置 200 条记录，选择一首所有音源都不可播的歌。写历史发生在解析失败前，新失败条目占一个名额，裁剪淘汰旧的已播放条目。

**建议修复与回归：** 当前请求首次实际开始播放时写历史，对同一请求的换源或恢复去重。验证失败不改变原 200 条，成功只写入一次。

### 16. 大体积续播快照可以写入，但冷启动读不回来

**严重程度与结论：** P3，静态证据充分。

**位置与原因：** [PlaybackSnapshotStore.kt:21](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackSnapshotStore.kt#L21) 读取上限为 2 MiB；[写入路径](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackSnapshotStore.kt#L31) 没有等价的编码字节上限。[引擎](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L809) 只限制队列项数。

**复现条件与证据：** 构造 500 首、每首标题约 5 KiB 的目录或测试曲目并写快照。即使条目数达标，序列化体积仍超过 2 MiB，重启后会被读取上限拒绝。

**建议修复与回归：** 写入前按实际 UTF-8 字节数限制快照，必要时从队列边缘裁剪，优先保留当前项和进度。验证上限两侧的快照都能往返读取。

### 17. “全平台”搜索遗漏咪咕

**严重程度与结论：** P3，静态证据充分。

**位置与原因：** [BuiltInSourceSearchApi.kt:87](app/src/main/java/com/cutedino/xiangsuplayer/core/source/builtin/BuiltInSourceSearchApi.kt#L87) 的聚合只启动网易云、QQ、酷狗和酷我。咪咕已有单平台搜索分支，并在 [README.md:24](README.md#L24) 的音源说明中列出。

**复现条件与证据：** 配置合成查询只在咪咕返回结果，对比单独搜索咪咕与全平台搜索。聚合没有调用咪咕的路径，单平台可找到的曲目在全平台会缺失。

**建议修复与回归：** 将咪咕加入聚合，保持其他平台在部分故障下仍可返回。验证只在咪咕存在的曲目可通过全平台检索，咪咕失败不丢弃其他结果。

## 待验证风险

以下三项没有计入确认的问题，需先用受控测试验证触发条件及实际影响。

| 潜在严重程度 | 位置与风险 | 可执行验证与处理条件 |
| --- | --- | --- |
| P2 | [BuiltInSourceSearchApi.kt:63](app/src/main/java/com/cutedino/xiangsuplayer/core/source/builtin/BuiltInSourceSearchApi.kt#L63)、[聚合分支](app/src/main/java/com/cutedino/xiangsuplayer/core/source/builtin/BuiltInSourceSearchApi.kt#L87)：聚合等待所有平台，尚未证明慢响应一定会超过用户可接受时长。 | 让一个模拟提供方持续发送少量字节，其他提供方立即返回，记录端到端时长及取消传播。若慢方可以长时间阻塞快方结果，增加每平台总期限并返回已完成的结果。 |
| P2 | [PlaybackEngine.kt:566](app/src/main/java/com/cutedino/xiangsuplayer/core/audio/PlaybackEngine.kt#L566)：首链解析和到达 READY 前的恢复受约 4.5 秒预算约束，尚未复现可用备用链因预算耗尽而无法尝试。 | 让首链在预算将尽时解析成功，随后在准备阶段延迟报错，第二链立即可播。若最终出现可恢复却未回退的失败，分别控制解析和媒体准备恢复的预算。 |
| P2 | [FavoriteSyncWorker.kt:28](app/src/main/java/com/cutedino/xiangsuplayer/data/auth/FavoriteSyncWorker.kt#L28)、[SongDao.kt:49](app/src/main/java/com/cutedino/xiangsuplayer/data/db/SongDao.kt#L49)：本地令牌可阻止旧响应删除新操作，不能单独保证远端提交顺序。远端是否会在客户端超时后继续提交未知。 | 用假服务模拟“加红心超时 → 取消红心成功 → 旧加红心迟到提交”。若真实协议允许这种次序并可形成远端错态，再考虑服务端幂等或顺序约束，或写后读回校正。 |

## 验证记录

- 双模块 JVM 测试重新执行通过：`:app:testDebugUnitTest` 64 项，`:ncmapi:testDebugUnitTest` 10 项。
- 双模块 Gradle Lint 完成：`:app:lintDebug :ncmapi:lintDebug`，0 个错误。
- Android TV 模拟器安装包与当前调试 APK 的 SHA-256 一致。系统媒体键、应用 D-pad 及队列快照用于两项实机式复现；完成后恢复了测试前的播放快照。
- 合成 HTTP 响应、JVM 会话和本地手机点歌 HTTP 服务用于隔离网络边界。上文的建议回归包含尚未执行的用例。真实平台账号、远端服务、物理电视未在本轮验证。
