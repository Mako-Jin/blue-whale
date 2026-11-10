# Wave 5.77 · 视频平台（短视频 / 视频 / 电影）实施计划

> 目标：在 BlueWhale 平台内新增视频平台能力，覆盖「短视频 → 视频 / 系列 → 电影」三条内容线，并与既有认证、租户、网关、文件、审计、消息、任务能力打通。
> 状态：**M0–M5 已完成（2026-11-01），实机验收待环境** ｜ 关联需求：REQ-FUNC-031 / REQ-FUNC-032 / REQ-FUNC-033 / REQ-ARCH-014 / REQ-SEC-025 ｜ M0 检查报告：[2026-11-01-wave5.77-video-m0](../reviews/2026-11-01-wave5.77-video-m0.md) ｜ M1：[m1](../reviews/2026-11-01-wave5.77-video-m1.md) ｜ M2：[m2](../reviews/2026-11-01-wave5.77-video-m2.md) ｜ M3：[m3](../reviews/2026-11-01-wave5.77-video-m3.md) ｜ M4：[m4](../reviews/2026-11-01-wave5.77-video-m4.md) ｜ M5：[m5](../reviews/2026-11-01-wave5.77-video-m5.md)
> 上游依据：[roadmap.md](roadmap.md)、[service-catalog.md](../service-catalog.md)、[naming-conventions.md](../naming-conventions.md)、[architecture.md](../architecture.md)、[backend.md](../backend.md)、[Wave 5.75 音乐平台实施计划](wave5.75-music-platform.md)、[安全基线实施计划](2026-10-31-security-baseline-config-flyway-password-rotation.md)

---

## 〇、结论摘要

| 项 | 结论 |
|---|---|
| 服务归属 | **新建 `whale-backend/whale-services/whale-video`**，一个服务承载「短视频 / 视频 / 电影」三个逻辑子域；**不拆三个微服务**，避免重复播放、互动、审核与检索底座 |
| 端口 / 前缀 / 库 | **8098** / **`/video/**`** / **`bluewhale_video`** |
| 包名 / 配置前缀 | `com.bluewhale.video` / `whale.video.*`；Redis key `whale:video:*` |
| 表前缀 | `vd_`（如 `vd_content`、`vd_movie`、`vd_comment`） |
| 前端 | 新建 `whale-frontend/platform-apps/whale-video`（qiankun 子应用，base `/video`，dev 端口 **8114**）；运营页面并入 `whale-frontend/admin` |
| 分层 | DDD 四层：`interfaces / application / domain / infrastructure` + `VideoArchitectureTest`（与 auth / file / doc / music 同口径） |
| 复用的平台能力 | auth（登录 / RBAC / 租户）、gateway（JWT / 路由 / 限流 / `internal-deny`）、file（视频与封面字节 + Range）、audit（写操作留痕）、message（可选通知）、job / 本地调度（计数回刷与清理） |
| 关键依赖缺口 | **`whale-file` 单文件上限 50MB、个人配额 1GB、分片完成前也校验 50MB** → 视频/电影必须先做 **G1 大文件上传策略**；Range 已具备（音乐 G1 已完成） |
| 数据库版本化 | 新服务**从 M0 起直接使用 Flyway**（REQ-SEC-022），`V1__baseline.sql` 建 `vd_*` 表；auth 侧权限/菜单种子用**新增版本化脚本**，不改已执行的 V1–V3 |
| 明确不做 | 公网匿名注册、直播、转码、HLS/DASH、DRM、CDN、付费会员、第三方片库、推荐模型、关注/粉丝关系、移动端原生 App |
| 交付节奏 | M0 设计与骨架 + G1 → M1 共享媒体底座 → M2 短视频闭环 → M3 视频 / 系列闭环 → M4 电影 / 发现 / 运营 → M5 安全与上线硬化 |
| 最小可上线切片 | **M0 + M1 + M2** 即可先上线「短视频」；M3 / M4 按增量交付，不阻塞短视频首发 |

---

## 一、现状盘点与差距

| 维度 | 现状 | 对本计划的影响 |
|---|---|---|
| 视频域 | 全仓 **无** 短视频 / 视频 / 电影 / 剧集 / 片单相关服务、表、权限点、菜单 | 全新域，可按领域模型一次设计到位；与音乐域保持并行，不互相依赖 |
| 文件服务 | `whale-file` 已有普通上传、分片上传（5MB/片，支持断点续传）、下载 / 预览 / 内网内容接口；内容接口支持 200 / 206 / 416 + `Content-Range` + `ETag` + `If-Range` | 视频字节归 file，video 只存 `file_id` 与元数据；**大文件上限与配额是硬缺口（G1）** |
| 认证与租户 | auth 已提供 JWT、RBAC、租户、网关身份头注入；租户行级隔离插件已落地 | 所有 `vd_*` 表带 `tenant_id`；跨租户一律 404，不泄露存在性 |
| 网关 | 路由、JWT、黑名单、限流、熔断、`internal-deny`、TraceId 已就绪；音乐 8096 已接入 | 新增 `/video/**` 路由；`/video/internal/**` 对外 403；`/video/stream/**` 独立限流；流式路由**不加入 3s 熔断名单** |
| 审计 | `@AuditLog` + 四通道日志 + 脱敏已就绪 | 内容发布 / 下架 / 版权下架 / 电影授权变更 / 评论审核全部留痕；日志不记录播放票据与评论正文 |
| 消息 | `whale-message` 可发送站内信 / 邮件 / IM | 评论回复、审核结果、电影授权到期提醒可选接入；失败只告警，不回滚主事务 |
| 任务 / 调度 | `whale-job` 可用；各服务也已有本地 `@Scheduled` + 分布式锁模式 | 播放计数回刷、观看历史清理、电影授权到期下线、每日统计可先本地调度 |
| 前端 | qiankun 基座 + 16 个前端应用（含 music 8111）+ `@bluewhale/whale-ui` / http / theme / log-sdk | 直接按现有模板新建 `whale-video` 子应用；admin 增加视频运营页 |
| 编号与端口 | 权限点已用至 **304**（音乐申诉）；菜单已用至 **45**（音乐申诉）；角色已用至 **25103 / 25104**（music_admin / music_member）；后端 8097 / 前端 8112 已由 Wave 5.77 场景与 CRM 规划登记占用；视频使用 8098 / 8114 | 视频权限从 **305** 起、菜单从 **46** 起、角色从 **25105 / 25106** 起；端口 **8098 / 8114** |
| 质量门禁 | Checkstyle `error`、JaCoCo、ArchUnit、CI 镜像矩阵、网关冒烟脚本 | 新服务同步纳入构建、检查清单、镜像矩阵、Nginx 前缀、冒烟与备份脚本 |
| 文档规范 | `service-catalog.md` / `naming-conventions.md` / `roadmap.md` / `requirements/register.md` 为事实源 | M0 先登记域词、端口、服务、库、前端、权限与需求，再落代码（REQ-DOC-002） |

**G1（阻塞项，M0 必须完成）**：`whale-file` 当前 `whale.file.max-size-bytes` 默认 50MB、`whale.file.quota-bytes` 默认 1GB，且分片 `init` 与 `complete` 都会按 50MB 校验；电影/长视频无法上传。方案见 §4.8：**按权限码的服务器端大文件上传策略**，而不是简单把全局上限放大。

---

## 二、定位、范围与边界

### 2.1 定位（待确认 Q1）

**平台内视频应用**：面向已登录的租户用户；所有登录用户可上传短视频与视频（上传前签署《内容合法与版权无争议声明》），电影仅由运营管理员入库与发布；用户可浏览、播放、点赞、收藏、评论、观看历史；不开放匿名注册，不接入第三方商业片库，不做公网分发。

> 若目标是「面向公众的视频 / 影视平台」，需额外确认：公网注册、版权采购、内容审核团队、CDN 与带宽预算、支付与会员体系、DRM。本计划的 M0–M5 结构可复用，但 M5 会显著扩大。

### 2.2 能力对标

| 能力 | 抖音 / 快手 | B 站 / YouTube | 爱奇艺 / Netflix | whale-video 首版方案 | 波次 |
|---|---|---|---|---|---|
| 短视频 | 竖屏滑播、推荐流 | Shorts | — | `vd_short_video` + 最新 / 热门 / 分类 feed，游标分页，无算法推荐 | M2 |
| 视频 | — | 长视频、频道、合集 | — | `vd_video` + 系列 / 集数 + 分类 + 断点续播 | M3 |
| 电影 | — | 影视区 | 电影库、片单、会员 | `vd_movie` + 管理端入库 + 授权来源 / 到期 + 电影片单 | M4 |
| 播放 | 秒开、滑播 | 进度、清晰度、字幕 | 多码率、DRM | 短时播放票据 + HTTP Range（200 / 206 / 416），单码率原始文件 | M1–M2 |
| 互动 | 点赞、评论、收藏 | 点赞、收藏、评论 | 评分、短评 | 点赞 / 收藏 / 评论 / 观看历史；不做用户评分 | M1 |
| 发现 | 推荐流 | 首页、搜索、排行 | 首页、搜索、榜单 | 首页聚合、分类 / 标签、热门榜、新片榜、站内搜索 | M1 / M4 |
| 运营 | 审核、下架 | 审核、分区 | 内容库、授权 | admin 内容管理、电影授权、评论审核、统计 | M1 / M4 |
| 会员 / 支付 | 直播 / 电商 | 大会员 | 会员 / 付费点播 | **不做**；预留 `whale-pay` 扩展点 | — |
| 转码 / CDN | 多码率 + CDN | 多码率 + CDN | 多码率 + DRM | **不做**；要求上传浏览器可播的 MP4 / WebM，生产后续接 MinIO + CDN | — |
| 推荐算法 | 强推荐 | 强推荐 | 强推荐 | **不做模型**；首版规则：最新 / 热度 / 分类 / 标签 | — |
| 直播 / 连麦 | 支持 | 支持 | — | **不做** | — |

### 2.3 In Scope（首版 M0–M5）

1. **共享媒体底座**：统一内容注册、媒体资产引用、分类 / 标签、创作者资料、上传声明、发布状态机、播放票据、Range 流式、观看历史、点赞 / 收藏 / 评论。
2. **短视频**：登录用户上传（竖屏 / 横屏）、公开 / 私密、发布 / 下架、最新 / 热门 / 分类 feed、我的作品、详情、互动。
3. **视频**：登录用户上传长视频、分类、系列 / 集数、发布 / 下架、列表 / 详情 / 系列页、断点续播、搜索。
4. **电影**：运营管理员入库、电影元数据（导演 / 演员 / 地区 / 语言 / 上映日期 / 评分）、授权来源与到期、发布 / 下架 / 版权下架、电影片单、电影详情、授权到期自动下线。
5. **发现与运营**：首页聚合、分类 / 标签、热门榜 / 新片榜、站内搜索、评论审核、内容审核、播放统计、创作者管理。
6. **前端**：qiankun 子应用 `whale-video`（首页 / 短视频滑播 / 视频 / 电影 / 搜索 / 我的 / 上传中心 / 播放器）+ `whale-frontend/admin` 视频运营页。
7. **平台接入**：网关路由、权限点 / 菜单 / 角色种子、租户隔离、审计、限流、指标、Grafana 面板、冒烟与运维文档。

### 2.4 Out of Scope（明确不做）

- 公网匿名注册、游客模式、第三方 OAuth。
- 直播、连麦、实时弹幕、IM 聊天室。
- 视频转码、多码率、HLS / DASH、DRM、离线下载、CDN 预热。
- 付费会员、单片付费、数字藏品、支付分账（后续按需接 `whale-pay`）。
- 第三方商业片库接入、版权采购、内容审核外包。
- 个性化推荐模型、协同过滤、向量召回、关注 / 粉丝关系、用户评分 / 短评。
- Elasticsearch / 独立搜索集群；首版 MySQL `LIKE` + 标签过滤。
- 服务端抽帧 / 缩略图生成 / ffprobe 强校验；首版由客户端截帧或运营上传封面。
- 移动端原生 App、桌面客户端。

---

## 三、领域建模（Strategic + Tactical）

### 3.1 子域、限界上下文与上下文映射

| 子域 / 上下文 | 归属 | 关系 |
|---|---|---|
| 短视频子域 | `whale-video` | UGC 竖屏 / 横屏内容、feed、创作者；共享播放与互动 |
| 视频子域 | `whale-video` | 长视频、系列 / 集数、分类；共享播放与互动 |
| 电影子域 | `whale-video` | 运营入库、授权、电影元数据、片单；共享播放与互动 |
| 共享媒体子域 | `whale-video` | 内容注册、媒体资产引用、分类 / 标签、发布状态机、播放票据、搜索、首页聚合 |
| 互动子域 | `whale-video` | 点赞、收藏、评论、观看历史 |
| 认证上下文 | `whale-service-auth` | 上游：身份、租户、权限码；video **不持久化**用户资料 |
| 文件上下文 | `whale-file` | 下游：视频 / 封面 / 海报 / 背景图字节；video 只保存 `file_id` 与元数据 |
| 审计上下文 | `whale-audit` | 下游：发布、下架、版权下架、授权变更、评论审核留痕 |
| 消息上下文 | `whale-message` | 下游（可选）：评论回复、审核结果、授权到期提醒 |
| 支付上下文 | `whale-pay` | 首版**不集成**，仅保留扩展点 |
| Agent 上下文 | `whale-agent` | 首版**不集成**；后续可索引公开内容做语义检索 |

**为什么一个服务、一个限界上下文**：三条内容线共享同一套「内容 / 资产 / 发布 / 可见性 / 播放 / 审核 / 互动」语言与不变量；拆三个服务会复制播放票据、Range 代理、评论 / 点赞 / 收藏 / 历史表，并引入跨服务查询与一致性成本。**拆分触发条件**：三条线出现独立团队、独立扩缩容、电影付费 / DRM / 平台级片库授权等强边界时，再把电影拆为 `whale-movie`。

> 不为视频域引入 MQ、搜索引擎或独立对象存储；跨上下文调用全部在事务外，失败不回滚视频主事务。

### 3.2 聚合与不变量

| 聚合根 | 组成 | 关键不变量 |
|---|---|---|
| `ShortVideo` | 内容 ID、创作者、标题 / 文案、媒体资产、封面、时长、可见性、发布状态、上传声明、统计 | 上传者必须已签署当前版本声明；发布必须满足：资产存在、封面存在、`duration_ms>0` 且不超过短视频上限、标题非空；`PRIVATE` 仅上传者可见可播；公开内容先发后审，运营可下架 |
| `Video` | 内容 ID、创作者、标题、简介、分类、系列 / 集数、媒体资产、封面、时长、可见性、发布状态、上传声明 | 发布必须满足：资产存在、分类存在、`duration_ms>0` 且不超过长视频上限；同一系列内 `episode_no` 唯一；`PRIVATE` 仅上传者可见可播 |
| `Movie` | 内容 ID、片名 / 原名、导演、演员、地区、语言、上映日期、评分、简介、海报 / 背景图、媒体资产、时长、授权来源 / 编号 / 到期、发布状态 | **仅运营可创建 / 发布**；发布必须满足：海报存在、资产存在、`duration_ms>0`、授权来源必填、授权未过期；授权到期自动 `OFFLINE` 并拒绝播放；`TAKEDOWN` 立即强制下线 |
| `Series` | 标题、封面、简介、分类、状态、集数 | 同一系列内集数唯一；系列发布不要求所有集已发布，但系列页只展示已发布集；下架系列不自动下架单集，单集状态独立 |
| `Collection`（电影片单） | 标题、封面、简介、条目 | 同一片单同一电影不可重复；单白名单最多 200 部；仅运营可维护 |
| `Comment` | 内容、用户、父评论、正文、状态、点赞数 | 正文 ≤ 500 字、纯文本；仅 `VISIBLE` 对外展示；父评论必须属于同一内容且层级 ≤ 2；私密内容不开放评论 |
| `WatchProgress` | 用户、内容、进度、时长、来源、设备、观看时间 | 同一用户同一内容只保留一条最新进度；仅记录已发布且可见内容；按用户保留 180 天 |
| `UploadDeclaration` | 用户、声明版本、签署时间、客户端信息、内容来源 | 同一用户同一版本只记一次；未签署当前版本不能创建短视频 / 视频 |
| `CreatorProfile` | 用户、展示名、头像、简介、状态 | 同一租户内同一用户只有一份创作者资料；停用后不得发布新内容，存量内容不受影响 |
| `PlaybackTicket` | 租户、用户、内容、类型、签发时间、过期时间 | 只对「已发布 + 可见 + 授权有效」内容签发；绑定三要素；过期 / 换用户 / 换内容 / 下架 / 授权到期全部拒绝 |

### 3.3 关键命令、事件与规则

| 类型 | 内容 |
|---|---|
| 命令 | `SignUploadDeclaration`、`CreateShortVideo`、`PublishShortVideo`、`CreateVideo`、`PublishVideo`、`CreateSeries`、`CreateMovie`、`PublishMovie`、`OfflineContent`、`TakedownContent`、`CreatePlaylistCollection`、`AddCollectionItem`、`IssuePlaybackTicket`、`ReportProgress`、`LikeContent`、`FavoriteContent`、`SubmitComment`、`ModerateComment` |
| 领域事件 | `ShortVideoUploaded`、`ShortVideoPublished`、`VideoPublished`、`MoviePublished`、`ContentOfflined`、`ContentTakenDown`、`LicenseExpired`、`CommentSubmitted`、`CommentModerated`、`SeriesChanged`、`CollectionChanged` |
| 规则 R1 | 发布前置校验按内容类型分别执行；任一不满足返回领域异常，不进入 `PUBLISHED` |
| 规则 R2 | 短视频 / 视频创建前必须已签署当前版本上传声明；电影由运营创建，不走用户声明，但必须填写授权来源 |
| 规则 R3 | 可见性：`PRIVATE` 仅上传者本人可查 / 可播；`PUBLIC` 租户内可见；电影默认 `PUBLIC`，仅运营可发布 |
| 规则 R4 | 版权下架优先于发布状态：`TAKEDOWN` 强制 `OFFLINE`，已签发票据在下一次 Range 请求被拒绝；用户侧立即不可见 |
| 规则 R5 | 电影授权到期优先于发布状态：票据签发拒绝、流式拒绝、自动下线任务兜底；重新授权后可再次发布 |
| 规则 R6 | 点赞 / 收藏 / 片单加片幂等：唯一索引 + 重复请求返回现有状态，不报错、不重复计数 |
| 规则 R7 | 播放计数异步聚合：Redis 计数 + 定时回刷，避免 `UPDATE vd_content SET play_count` 行锁热点 |
| 规则 R8 | 评论默认先发后审（可配置先审后发）；隐藏后立即不可见；正文纯文本，不执行 HTML / 脚本 |
| 规则 R9 | 所有读接口先按 `tenant_id` 过滤，再按可见性 / 状态过滤；跨租户 ID 一律 404，不暴露存在性 |
| 规则 R10 | 媒体校验：只接受浏览器可播的 MP4 / WebM；封面 / 海报只接受 JPEG / PNG / WebP；大小、时长按类型上限；文件字节由 `whale-file` 保管 |
| 规则 R11 | 系列内集数唯一、片单内电影唯一；重复加集 / 加片返回现有状态；排序冲突由分布式锁串行化 |
| 规则 R12 | 公开响应不暴露 `video_file_id` / `cover_file_id`；封面、海报、背景图由 video 代理 URL 输出 |

### 3.4 值对象、领域服务与端口

- 值对象：`MediaAsset(fileId, format, sizeBytes, durationMs, width, height, checksum)`、`CoverAsset`、`Visibility(PUBLIC/PRIVATE)`、`PublishState(DRAFT/PENDING_REVIEW/PUBLISHED/OFFLINE)`、`CopyrightState(AUTHORIZED/TAKEDOWN)`、`MovieLicense(source, licenseNo, expireAt)`、`PlaybackTicket(ticket, tenantId, userId, contentId, expiresAt)`、`EpisodeNo`、`HotScore`。
- 聚合根实现 `Entity`，值对象实现 `ValueObject`，领域事件继承 `DomainEvent`（与 auth / file / doc / music 基座契约一致）。
- 领域层不依赖 Spring / MyBatis / Redis；文件读取通过 `MediaContentPort`，票据通过 `PlayTicketPort`，缓存通过 `ContentCachePort`，消息通过 `ContentMessagePort`，媒体探测通过 `MediaProbePort`。
- 领域服务：`ContentPublishPolicy`、`PlaybackAuthorizationPolicy`、`MediaAssetPolicy`、`ContentAccessPolicy`、`FeedRankingPolicy`。
- 分层由 `VideoArchitectureTest`（ArchUnit）固化；领域层仅依赖 JDK。

### 3.5 统一语言

| 术语 | 含义 |
|---|---|
| 短视频 | 竖屏 / 横屏 UGC 短片，默认 ≤ 5 分钟，滑播消费 |
| 视频 | 长视频 / PGC / 创作者内容，可挂系列与集数 |
| 电影 | 运营维护的影视内容，含授权来源、到期、片单 |
| 系列 | 视频的剧集 / 节目集合，按集数排序 |
| 片单 | 运营维护的电影集合，用于首页 / 专题推荐 |
| 播放票据 | 短期有效的播放授权凭证，绑定租户 + 用户 + 内容 |
| 观看进度 | 用户对某内容的最后播放位置，支持断点续播 |
| 上架 / 下架 | `PUBLISHED` / `OFFLINE`，控制用户可见与可播 |
| 版权下架 | `TAKEDOWN`，因授权 / 合规问题强制下线，优先级最高 |
| 授权来源 | 电影内容的权利来源：自有 / 已授权 / 公有领域 |
| 上传声明 | 上传者对内容合法性与版权无争议的声明，按版本留痕 |
| 公开 / 私密 | `PUBLIC` 租户内可见；`PRIVATE` 仅上传者可见 |
| 审核队列 | 待审核评论 / 待复核内容集合，运营可批量通过 / 隐藏 / 下架 |
| 创作者 | 上传短视频 / 视频的租户用户；电影不要求创作者 |

---

## 四、总体设计

### 4.1 服务落位与命名（REQ-ARCH-004 / REQ-ARCH-005）

| 项 | 值 | 落点 |
|---|---|---|
| Maven 模块 | `whale-backend/whale-services/whale-video` | `whale-services/pom.xml` 增加 `<module>whale-video</module>` |
| artifactId / 服务名 | `whale-video` / `whale-video` | `pom.xml`、`application.yml`、Nacos 服务名 |
| 端口 | `${SERVER_PORT:8098}` | `application.yml` |
| 对外前缀 | `/video/**`（服务自持，网关**不剥离**） | Controller `@RequestMapping("/video/...")` |
| 数据库 | `bluewhale_video` | Flyway `V1__baseline.sql` + 幂等增量脚本 |
| Java 包 | `com.bluewhale.video` | 四层分包 |
| 配置前缀 | `whale.video.*` | 如 `whale.video.play.ticket-ttl-seconds`、`whale.video.comment.pre-audit` |
| Redis key | `whale:video:<用途>:<标识>` | 票据 / 可播状态 / 计数 / 榜单 / 缓存 |
| 前端子应用 | `whale-frontend/platform-apps/whale-video`，base `/video`，端口 8114 | `vite.config.ts`、qiankun/apps.ts |
| 运营页面 | `whale-frontend/admin/src/pages/video` | 复用 admin 布局、ProTable、权限组件 |
| 生产文件桶 | `whale-video`（建议） | `whale-file` 的 `bucket` 参数；本地可用默认桶 |

### 4.2 分层与依赖

```
interfaces  → application  → domain
                    ↑            ↑
              infrastructure ────┘（实现 domain 端口）
```

- 单模块 `whale-video` + 四层分包（与 `whale-doc` / `whale-music` 同口径），避免为骨架引入 5 个子模块与聚合 POM。
- `interfaces`：REST 控制器、DTO / VO、参数校验、权限注解。
- `application`：用例编排、事务边界、领域事件发布、缓存 / 计数适配；按子域拆 `ShortVideoAppService`、`VideoAppService`、`MovieAppService`、`PlaybackAppService`、`InteractionAppService`、`CatalogAppService`、`ModerationAppService`、`DiscoverAppService`。
- `domain`：聚合、值对象、领域服务、端口接口；仅依赖 JDK。
- `infrastructure`：MyBatis-Plus、仓储实现、`whale-file` 客户端、Redis 适配、调度任务。
- `architecture-test`：`VideoArchitectureTest` 固化依赖方向与包名；领域层不得出现 Spring / MyBatis / Redis 依赖。

**事务边界约定**：
- 上传文件字节、创建内容元数据分开：文件上传在事务外完成，内容创建只包本地聚合写入；内容创建失败由孤儿文件清理任务兜底。
- 发布 / 下架 / 版权下架：本地事务完成后失效缓存；无外部 HTTP 调用留在事务内。
- 播放票据：先读 DB 校验可播，再写 Redis；Redis 失败则 fail-closed，不允许无票放行。
- 评论：本地事务写评论 + 计数；可选消息在 `afterCommit` 发送，失败只告警。

### 4.3 数据模型（`bluewhale_video`）

> 通用列：`id BIGINT`（雪花，`ASSIGN_ID`）、`tenant_id BIGINT NOT NULL`、`create_time`、`update_time`、`deleted TINYINT`；所有业务表带租户过滤。M1 基线 **14 张业务表**，M3 +1、M4 +2、M5 +2，最多 19 张；另 `audit_log` / `api_log` 随 starter 复用，不计业务表。

| 表 | 波次 | 用途 | 关键字段 / 索引 |
|---|---|---|---|
| `vd_content` | M1 | 共享内容注册与生命周期 | `content_type`（SHORT_VIDEO/VIDEO/MOVIE）、`title`、`subtitle`、`description`、`cover_file_id`、`video_file_id`、`duration_ms`、`width`、`height`、`format`、`size_bytes`、`uploader_id`、`creator_id`、`category_id`、`series_id`、`episode_no`、`visibility`、`publish_status`、`copyright_status`、`copyright_source`、`copyright_expire_at`、`takedown_reason`、`takedown_at`、`play_count`、`like_count`、`favorite_count`、`comment_count`、`hot_score`、`sort_weight`、`published_at`；`idx(tenant_id,content_type,publish_status,visibility,published_at)`、`idx(tenant_id,content_type,publish_status,hot_score)`、`idx(tenant_id,creator_id,published_at)`、`idx(tenant_id,category_id,publish_status)`、`idx(tenant_id,series_id,episode_no)` |
| `vd_short_video` | M1 | 短视频子类型 | `content_id` 主键、`caption`、`aspect_ratio`、`cover_frame_ms`、`feed_weight` |
| `vd_video` | M1 | 长视频子类型 | `content_id` 主键、`video_kind`、`allow_comment`、`allow_download`、`source_note` |
| `vd_movie` | M1 | 电影子类型 | `content_id` 主键、`original_title`、`director`、`actors`、`region`、`language`、`release_date`、`rating`、`license_source`、`license_no`、`license_expire_at`、`poster_file_id`、`backdrop_file_id`；`idx(tenant_id,license_expire_at)` |
| `vd_category` | M1 | 分类树 | `parent_id`、`name`、`code`、`content_type_scope`、`sort`、`status`；`uk(tenant_id,parent_id,name,deleted)` |
| `vd_tag` | M1 | 标签 | `name`、`type`（TOPIC/GENRE/LANGUAGE）、`sort`；`uk(tenant_id,name,type,deleted)` |
| `vd_content_tag` | M1 | 内容标签关系 | 复合主键 `(content_id,tag_id)`；`idx(tag_id)` |
| `vd_creator` | M1 | 创作者资料 | `user_id`、`display_name`、`avatar_file_id`、`bio`、`status`；`uk(tenant_id,user_id)` |
| `vd_upload_declaration` | M1 | 上传声明签署 | `user_id`、`declaration_version`、`accepted_at`、`client_ip`、`user_agent`、`content_source`；`uk(tenant_id,user_id,declaration_version)` |
| `vd_like` | M1 | 内容点赞 | `user_id`、`content_id`；`uk(tenant_id,user_id,content_id)` |
| `vd_favorite` | M1 | 内容收藏 | `user_id`、`content_id`；`uk(tenant_id,user_id,content_id)` |
| `vd_watch_history` | M1 | 观看进度 / 最近观看 | `user_id`、`content_id`、`progress_ms`、`duration_ms`、`source`、`device`、`watched_at`；`uk(tenant_id,user_id,content_id)`、`idx(tenant_id,user_id,watched_at)` |
| `vd_comment` | M1 | 评论 | `content_id`、`user_id`、`parent_id`、`content`（≤500）、`status`、`like_count`；`idx(tenant_id,content_id,status,create_time)` |
| `vd_comment_like` | M1 | 评论点赞 | `comment_id`、`user_id`；`uk(tenant_id,comment_id,user_id)` |
| `vd_series` | M3 | 视频系列 | `title`、`cover_file_id`、`description`、`category_id`、`status`、`episode_count`、`sort`；`idx(tenant_id,status)` |
| `vd_collection` | M4 | 电影片单 | `title`、`cover_file_id`、`description`、`collection_type`（MOVIE_LIST）、`status`、`item_count` |
| `vd_collection_item` | M4 | 片单条目 | `collection_id`、`content_id`、`sort_no`、`added_by`；`uk(collection_id,content_id)`、`idx(collection_id,sort_no)` |
| `vd_play_stat_daily` | M5 可选 | 每日播放统计 | `stat_date`、`content_id`、`play_count`、`unique_users`、`watch_seconds`；`uk(tenant_id,stat_date,content_id)` |
| `vd_report` | M5 可选 | 用户举报 | `content_id`、`reporter_id`、`reason_type`、`reason_text`、`status`、`handled_by`、`handled_at`；`idx(tenant_id,status,create_time)` |

**Flyway 约定**（REQ-SEC-022）：
- 新服务 M0 直接建 `src/main/resources/db/migration/V1__baseline.sql`，不复制音乐的历史 `init.sql` 模式。
- `baseline-on-migrate=true`、`clean-disabled=true`、`validate-on-migrate=true`；脚本禁止 `DROP` / `TRUNCATE`；种子使用 `INSERT ... WHERE NOT EXISTS` 或 `INSERT IGNORE`。
- auth 侧权限 / 菜单 / 角色种子使用新增版本化脚本（建议 `V4__video_permissions.sql`），不修改已执行的 V1–V3；若当前 auth Flyway 尚未覆盖历史种子，与 `migration-5.77-video.sql` 保持同一内容、只选一种执行路径。

### 4.4 接口清单（首版约 40 个端点）

**共享 / 用户侧**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/video/home` | 首页聚合：短视频推荐、视频、电影、分类、片单 |
| GET | `/video/search` | 站内搜索：标题 / 简介 / 标签，按类型过滤 |
| GET | `/video/categories`、`/video/tags` | 分类树 / 标签列表 |
| GET | `/video/content/{id}` | 统一内容详情（按类型返回 VO） |
| GET | `/video/content/{id}/cover`、`/poster`、`/backdrop` | 封面 / 海报 / 背景图代理，不暴露 `file_id` |
| POST | `/video/play/ticket` | 签发单个播放票据 |
| POST | `/video/play/tickets` | 批量签发（短视频 feed 预取下一批） |
| GET | `/video/stream/{contentId}?ticket=...` | Range 流式播放（200 / 206 / 416） |
| POST | `/video/play/progress` | 上报观看进度 |
| GET | `/video/history`、`DELETE /video/history/{contentId}` | 最近观看 / 删除记录 |
| POST / DELETE | `/video/content/{id}/like` | 点赞 / 取消 |
| POST / DELETE | `/video/content/{id}/favorite` | 收藏 / 取消 |
| GET | `/video/my/likes`、`/video/my/favorites` | 我的点赞 / 收藏 |
| POST / DELETE | `/video/comment/{id}/like` | 评论点赞 / 取消 |
| GET | `/video/declaration/current` | 当前上传声明版本与正文 |
| POST | `/video/declaration/accept` | 签署上传声明（版本 + 勾选） |

**短视频**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/video/shorts/feed` | 最新 / 热门 / 分类 feed，游标分页 |
| GET | `/video/shorts/{id}` | 短视频详情 |
| POST / PUT / DELETE | `/video/user/shorts`、`/video/user/shorts/{id}` | 上传 / 编辑 / 删除自己的短视频 |
| GET | `/video/my/shorts` | 我的短视频（含私密） |
| POST | `/video/user/shorts/{id}/publish`、`/offline` | 发布 / 下架自己的短视频 |
| POST / GET | `/video/content/{id}/comment`、`/video/content/{id}/comment/page` | 发表评论 / 评论分页 |

**视频**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/video/videos/page`、`/video/videos/{id}` | 视频列表 / 详情 |
| GET | `/video/series/page` | 系列分页（仅已上架，供上传 / 编辑选择所属系列） |
| GET | `/video/series/{id}` | 系列详情与集数列表 |
| POST / PUT / DELETE | `/video/user/videos`、`/video/user/videos/{id}` | 上传 / 编辑 / 删除自己的视频 |
| GET | `/video/my/videos` | 我的视频（含私密） |
| POST | `/video/user/videos/{id}/publish`、`/offline` | 发布 / 下架自己的视频 |

**电影**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/video/movies/page`、`/video/movies/{id}` | 电影列表 / 详情 |
| GET | `/video/collections/{id}` | 电影片单详情（M4） |

**运营 / 管理（权限码保护）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/video/admin/content/page` | 内容分页（类型 / 状态 / 分类 / 创作者） |
| POST | `/video/admin/content/{id}/publish`、`/offline`、`/takedown` | 发布 / 下架 / 版权下架 |
| POST / PUT / DELETE | `/video/admin/movies`、`/video/admin/movies/{id}` | 电影入库 / 编辑 / 逻辑删除 |
| GET | `/video/admin/movies/page` | 电影运营分页 |
| POST / PUT / DELETE | `/video/admin/series`、`/video/admin/series/{id}` | 系列维护 |
| POST / PUT / DELETE | `/video/admin/collections`、`/video/admin/collections/{id}` | 电影片单维护 |
| POST | `/video/admin/collections/{id}/item`、`DELETE .../item/{contentId}` | 加片 / 移除 |
| POST / PUT / DELETE | `/video/admin/categories`、`/video/admin/tags` | 分类 / 标签维护 |
| GET | `/video/admin/comment/page` | 评论审核分页 |
| POST | `/video/admin/comment/{id}/approve`、`/hide` | 评论通过 / 隐藏 |
| GET | `/video/admin/stat/overview` | 内容 / 播放 / 审核概览 |
| GET / PUT | `/video/admin/creator/page`、`/video/admin/creator/{id}` | 创作者查询 / 停用 |

> 内部接口（后续 Agent 检索 / 消息回查）统一落 `/video/internal/**`，由网关 `internal-deny` 对外 403。所有公开 VO **不返回** `video_file_id` / `cover_file_id` / `poster_file_id`；管理端 VO 可按权限返回文件 ID。

### 4.5 权限点 / 菜单 / 角色种子（305–322 / 46–52 / 25105–25106）

> 编号顺延：权限点已用至 304，菜单已用至 45，角色已用至 25103 / 25104；执行 M0 时若被其他波次占用，按实际空闲号重排并同步文档。

| 权限码 | ID | 用途 |
|---|---|---|
| `sys:video:content:list/add/edit/delete` | 305–308 | 内容查询与维护 |
| `sys:video:content:publish/offline/takedown` | 309–311 | 发布 / 下架 / 版权下架 |
| `sys:video:comment:audit` | 312 | 评论审核 |
| `sys:video:stat:view` | 313 | 播放与内容概览 |
| `sys:video:browse` | 314 | 用户侧浏览 / 搜索 / 详情 |
| `sys:video:upload` | 315 | 上传短视频 / 视频（含声明签署） |
| `sys:video:like` | 316 | 点赞 / 取消 |
| `sys:video:favorite` | 317 | 收藏 / 取消 |
| `sys:video:comment:create` | 318 | 发表评论 |
| `sys:video:category:manage` | 319 | 分类 / 标签维护 |
| `sys:video:series:manage` | 320 | 系列维护 |
| `sys:video:movie:manage` | 321 | 电影入库 / 授权 / 片单 |
| `sys:video:creator:manage` | 322 | 创作者查询 / 停用 |

| 菜单 ID | 名称 | 落点 |
|---|---|---|
| 46 | 视频中心（DIR） | qiankun 子应用入口 `/video` |
| 47 | 短视频管理 | admin `video/content-list`（`contentType=SHORT_VIDEO`） |
| 48 | 视频管理 | admin `video/content-list`（`contentType=VIDEO`） |
| 49 | 电影管理 | admin `video/movie-list` |
| 50 | 分类 / 系列 / 片单 | admin `video/catalog` |
| 51 | 评论审核 | admin `video/comment-audit` |
| 52 | 播放统计 | admin `video/stat` |

**角色**：
- `video_admin`（**25105**）：305–322 全量；运营可发布 / 下架 / 版权下架 / 电影入库 / 评论审核 / 统计。
- `video_member`（**25106**）：314–318（浏览 / 上传 / 点赞 / 收藏 / 评论）；迁移脚本幂等授予全部启用用户（若 Q4 确认“所有登录用户可上传”）。
- `tenant_admin`：全部 305–322；与现有业务角色模板一致。
- 电影入库 / 发布**仅** `video_admin` / `tenant_admin`；`video_member` 不能创建电影。

### 4.6 网关与内网安全

- 新增路由 `whale-video`：`Path=/video/**` → `lb://whale-video`，**不配 `StripPrefix`**。
- `/video/internal/**` 由 `InternalApiDenyGlobalFilter` 对外 403；服务侧内网令牌校验复用 starter。
- 白名单不放行 `/video/**`；所有播放必须登录，播放票据由登录态签发。
- 限流（网关 `rate-limit.path-rules`，阈值可配置）：
  - `/video/stream/**`：120 次/分钟/IP（一次播放会发多个 Range 请求）。
  - `/video/play/ticket`、`/video/play/tickets`：60 次/分钟。
  - `/video/content/*/comment`：20 次/分钟。
  - `/video/content/*/like`、`/favorite`：30 次/分钟。
  - `/video/upload/**`（若走网关代理）：20 次/分钟。
- `/video/stream/**` **不加入** `circuit-breaker.apply-to-routes`，避免长连接被 3s 超时误熔断；流式超时与首字节由服务侧与负载测试验证。
- JWT 身份头 / 租户头 / 权限码头由网关注入；服务端 `whale.permission.header.enabled=true`，Controller 使用 `@RequirePermission`。

### 4.7 播放与流式设计（关键决策）

```
前端                     whale-video                  whale-file
 |  POST /video/play/ticket   |                        |
 |-------------------------->| 校验发布/可见/授权/租户   |
 |                           | 写 Redis 票据 (TTL)     |
 |<--------------------------| {ticket, expiresAt}    |
 |  GET /video/stream/{id}?ticket=... + Range        |
 |-------------------------->| 校验票据 → 校验可播态   |
 |                           |------------------------>| 内部内容接口（Range）
 |<--------------------------|<------------------------| 200/206/416 + ETag
```

| 决策 | 方案 | 理由 |
|---|---|---|
| D1 播放授权 | 短时票据，默认 **300s**，绑定 `tenantId + userId + contentId`；客户端在 240s 主动刷新，流式 401/403 时重取票据并从当前 `currentTime` 续播 | 长视频一次播放会跨多个 Range 请求，不能做一次性；300s 兼顾撤销与长片体验 |
| D2 批量票据 | 短视频 feed 提供 `POST /video/play/tickets` 批量签发，前端预取当前 + 下 2 条 | 避免滑播时 N+1 票据请求 |
| D3 音频/视频读取 | `whale-video` 不直接读存储；通过 `whale-file` 内部内容接口读取并透传 Range | 文件字节归 file，video 不持存储凭据 |
| D4 可播状态 | 票据签发查 DB；流式每次校验票据 + `whale:video:cache:playable:{tenantId}:{contentId}`（30s），下架 / 版权下架 / 授权到期主动删除缓存 | 50 并发 Range 不压垮 DB，同时保证下架后下一次请求即拒绝 |
| D5 票据失效 | 票据过期、换用户、换内容、私密内容、未发布、版权下架、授权到期一律拒绝；不依赖客户端 | 下架立即阻断，已有票据不能续用 |
| D6 响应头 | `Cache-Control: private, no-store` + `ETag` + `Accept-Ranges` + `Content-Range` + `X-Content-Type-Options: nosniff` | 防止共享缓存泄漏票据 URL |
| D7 不暴露 file_id | 公开 VO 只返回代理 URL；流式与封面代理均不带 `file_id` | 防止绕过票据直连文件服务枚举 |
| D8 长视频超时 | 视频路由不进熔断名单；`FileContentClient` 使用流式 HTTP 客户端、无整体响应超时；网关只做限流 | 避免 2 小时电影被 3s 熔断或缓冲截断 |

**播放票据 Key**：`whale:video:play:ticket:{ticket}`，值 `tenantId|userId|contentId|expiresAt`，TTL 300s；`whale:video:cache:playable:{tenantId}:{contentId}` 30s。

### 4.8 上传与 `whale-file` 大文件策略（G1）

**G1 目标**：在不放宽全平台默认 50MB 的前提下，让短视频 / 视频 / 电影按权限使用更大的单文件上限与配额。

| 方案 | 内容 | 评价 |
|---|---|---|
| A. 全局放大 | 设置 `WHALE_FILE_MAX_SIZE_BYTES=4GB`、`WHALE_FILE_QUOTA_BYTES=0` | 最快，但所有服务都能传 4GB，配额与存储滥用风险高 |
| B. 按权限码策略（推荐） | `whale-file` 新增 `whale.file.upload-policy.rules`：按网关注入的 `X-Permission-Codes` 选择生效上限与配额 | 只对持有 `sys:video:upload` / `sys:video:movie:manage` 的请求放宽；默认仍 50MB / 1GB |
| C. purpose 参数 | 分片 init/complete 增加 `purpose`，服务端映射上限 | 需要防止客户端伪造 purpose；不如权限码直接 |

**推荐 B**：
- `whale-file` 新增 `FileUploadPolicy` 领域服务：解析 `X-Permission-Codes`，按配置规则取 `maxSizeBytes` / `quotaBytes`，`initChunkUpload`、`completeChunkUpload`、普通上传、极速上传统一使用；分片元数据记录生效策略，complete 复用同一策略，防止 init/complete 绕过。
- 规则示例（可配置，部署侧按实际调整）：
  - `sys:video:upload`：单文件 **2GB**，个人配额 **8GB**。
  - `sys:video:movie:manage`：单文件 **4GB**，个人配额 **不限**（仅运营）。
  - `sys:music:upload`：单文件 **100MB**（对齐音乐现有上限）。
  - 默认：单文件 **50MB**，个人配额 **1GB**。
- `whale-file` 测试：权限命中 / 未命中 / 伪造头被网关剥离 / init-complete 策略一致 / 配额超限；既有下载、预览、Range 用例不回归。
- 前端上传走 `whale-file` 分片接口（5MB/片，支持断点续传）；短视频 / 视频 / 电影均使用分片上传，不使用普通 50MB 上传。

**视频上传编排**：
1. 用户签署当前版本上传声明（短视频 / 视频）。
2. 前端调用 `whale-file` 分片 init（文件名、contentType、总大小），记录 `uploadId`；支持断点续传。
3. 逐片上传 5MB 分片；完成后 complete 得到 `fileId`。
4. 客户端截帧或运营上传封面 / 海报 / 背景图，得到 `coverFileId`。
5. 调用 `POST /video/user/shorts|videos` 或 `POST /video/admin/movies`，提交 `fileId` 与元数据。
6. 服务端通过 `FileAssetClient` 校验文件大小 / contentType，并通过 Range `0–31` 读取文件头做 **magic bytes** 校验（MP4 `ftyp`、WebM EBML）；不通过返回格式错误。
7. 发布时校验时长 / 大小 / 封面 / 声明 / 授权；通过后 `PUBLISHED`。

**默认上限（可配置，待 Q8 确认）**：

| 类型 | 时长上限 | 单文件上限 | 封面 / 海报 |
|---|---|---|---|
| 短视频 | 5 分钟 | 300MB | 10MB |
| 视频 | 2 小时 | 2GB | 10MB |
| 电影 | 4 小时 | 4GB | 20MB |
| 评论 | — | 500 字纯文本 | — |

> 首版不服务端转码 / 抽帧；要求上传 **H.264 + AAC 的 MP4（moov 前置 / faststart）** 或浏览器可播 WebM。非 faststart 文件仍可 Range 播放，但首屏可能变慢；M5 可评估 `ffprobe` 只做校验不做转码。

### 4.9 缓存、计数与定时任务

| Key / 任务 | 用途 | TTL / 策略 |
|---|---|---|
| `whale:video:play:ticket:{ticket}` | 播放票据 | 300s，值绑定三要素 |
| `whale:video:cache:playable:{tenantId}:{contentId}` | 流式可播状态 | 30s；发布 / 下架 / 版权下架 / 授权到期主动删除 |
| `whale:video:play:count:{tenantId}:{contentId}` | 播放计数 | Redis INCR，5 分钟一批回刷 |
| `whale:video:cache:home:{tenantId}` | 首页聚合 | 60s + 抖动；发布 / 下架事件主动失效 |
| `whale:video:cache:content:{tenantId}:{contentId}` | 内容详情 | 300s；状态变更主动失效 |
| `whale:video:cache:feed:{tenantId}:{type}:{sort}` | feed 首屏 | 30s；发布 / 下架事件失效 |
| `whale:video:lock:series:{id}` / `lock:collection:{id}` | 集数 / 片单排序 | Redisson 分布式锁 |
| `PlayCountFlushScheduler` | 播放计数回刷 | 每 5 分钟；多实例加锁 |
| `WatchHistoryCleanupScheduler` | 观看历史清理 | 每日；保留 180 天 |
| `MovieLicenseExpiryJob` | 电影授权到期自动下线 | 每日；`license_expire_at < now` 且 `PUBLISHED` → `OFFLINE`，原因 `LICENSE_EXPIRED`，审计 + 失效缓存 |
| `PlayStatDailyRollup`（M5 可选） | 每日播放统计 | 每日凌晨；按 `vd_watch_history` / 计数汇总 |
| `OrphanFileCleanup`（M5 可选） | 未引用文件清理 | 每周；仅清理超过 30 天且无内容引用的文件，调用 file 内部接口前需人工确认口径 |

- 缓存复用既有 `CacheConsistencyTemplate`（空值哨兵 + TTL 抖动 + 延迟双删）。
- 播放计数采用 Redis 计数 + 定时回刷，**不采用高并发行更新**；榜单与 DB `play_count` 允许最终一致。

### 4.10 前端设计

**`whale-frontend/platform-apps/whale-video`（qiankun 子应用，8114，base `/video`）**

| 页面 / 组件 | 说明 |
|---|---|
| 首页 / 发现 | 短视频推荐、视频、电影、片单、分类入口；空态与加载态 |
| 短视频滑播 | 全屏 / 卡片滑播，`IntersectionObserver` 播放当前、暂停其他；预取当前 + 下 2 条票据；静音自动播放 + 点击解除静音 |
| 短视频详情 | 作者、文案、点赞 / 收藏 / 评论、相关推荐 |
| 视频列表 / 详情 | 分类筛选、系列 / 集数、简介、评论、相关推荐 |
| 系列详情 | 封面、简介、集数列表、继续观看 |
| 电影列表 / 详情 | 海报、背景图、导演 / 演员 / 地区 / 语言 / 上映日期 / 评分、片单、播放 |
| 搜索 | 关键词 + 类型 Tab（短视频 / 视频 / 电影 / 创作者） |
| 我的 | 最近观看、点赞、收藏、我的短视频、我的视频 |
| 上传中心 | 选择类型、分片上传（断点续传）、客户端截帧 / 上传封面、填写元数据、签署声明、选择公开 / 私密、保存草稿 / 发布 |
| `VideoPlayer` | HTML5 `<video>` 全局播放器：播放 / 暂停、进度拖动、音量、全屏、键盘快捷键、断点续播、票据自动刷新 |
| `ShortVideoFeed` | 滑播容器：一屏一条、预加载、离开暂停、进度记忆 |
| `ContentCard` / `CommentList` / `FavoriteButton` | 复用列表、评论、互动组件 |

- API 层 `src/api/video.ts` 冻结契约，统一走 `@bluewhale/http`（Cookie 刷新 + CSRF）。
- 播放器状态使用应用内 React Context / Reducer，不新增状态管理依赖；封面 / 海报 / 背景图只使用 video 代理 URL。
- 长视频播放：进入详情先拉 `progress`，有进度时弹「继续观看 / 从头播放」；每 15s / 暂停 / 结束上报进度。
- 票据刷新：`usePlaybackTicket` 在 240s 或流式 401/403 时重取，刷新后从当前 `currentTime` 继续；失败给出可重试提示。
- 上传：短视频 / 视频默认公开，可选私密；电影入口仅 admin，不在用户端出现。

**`whale-frontend/admin` 运营页**

- `pages/video/ContentList`：短视频 / 视频分页、筛选、发布 / 下架 / 版权下架、二次确认、审计提示。
- `pages/video/MovieList`、`MovieEdit`：电影入库、元数据、授权来源 / 到期、海报 / 背景图、发布 / 下架。
- `pages/video/Catalog`：分类、标签、系列、电影片单维护。
- `pages/video/CommentAudit`：评论审核队列、通过 / 隐藏。
- `pages/video/Stat`：内容数、播放量、互动量、审核积压、存储占用。
- `pages/video/CreatorList`：创作者查询 / 停用。
- 所有操作按钮受对应权限码控制；下架 / 版权下架 / 授权变更二次确认并留审计。
- 菜单组件键需在 admin `router/dynamicRoutes.tsx` 注册：`video/content-list`、`video/movie-list`、`video/catalog`、`video/comment-audit`、`video/stat`、`video/creator-list`。

### 4.11 可观测性

- Prometheus 抓取 `whale-video:8098`；指标：
  - `video_play_ticket_issued_total` / `video_play_ticket_failed_total`
  - `video_stream_requests_total{status="200|206|416"}`、`video_stream_ttfb_seconds`
  - `video_feed_cache_hit_ratio`、`video_home_cache_hit_ratio`
  - `video_comment_pending_count`、`video_content_pending_count`
  - `video_upload_bytes_total{type="SHORT_VIDEO|VIDEO|MOVIE"}`
  - `video_license_expired_offline_total`
- Grafana「视频平台（Wave 5.77）」行：播放 QPS、流式 P95 / 首字节、票据失败率、审核积压、授权到期下线数。
- SLO 建议：内容详情 P95 < 250ms；首页 / feed P95 < 300ms；流式首字节 P95 < 400ms；50 并发流式无 5xx。
- 日志只记录 ID 与状态；**不记录**播放票据、视频标题 / 简介全文、评论正文；访问日志对 `ticket` 查询参数脱敏。

### 4.12 构建、CI 与运维

- 后端：`whale-video` 纳入 `whale-backend/whale-services/pom.xml`、根聚合、Backend CI 矩阵与 Docker 镜像矩阵。
- 文件服务：G1 改动纳入 file 服务单测与 CI；既有下载 / 预览 / Range 用例不回归。
- 前端：`whale-video` 纳入 pnpm workspace、Turborepo、build / lint 矩阵；`whale-admin` 同步。
- Nginx：新增 `/video/` 前缀与子应用静态资源；环境变量 `VITE_SUBAPP_ENTRY_VIDEO`。
- 冒烟脚本：`scripts/smoke/bw-video-m1-smoke.ps1` … `bw-video-m5-smoke.ps1`、`bw-video-m5-load.ps1`，统一输出 `SMOKE-ALL-PASS`，兼容 PowerShell 5.1。
- 备份 / 恢复：`scripts/ops/video-backup-restore.ps1`（演练 `RESULT: PASSED`）+ [视频服务运维手册](../ops/video-service-ops.md)。
- 配置项（示例）：
  - `WHALE_VIDEO_DECLARATION_VERSION=V1`
  - `WHALE_VIDEO_SHORT_MAX_DURATION_SECONDS=300`
  - `WHALE_VIDEO_VIDEO_MAX_DURATION_SECONDS=7200`
  - `WHALE_VIDEO_MOVIE_MAX_DURATION_SECONDS=14400`
  - `WHALE_VIDEO_SHORT_MAX_BYTES=314572800`
  - `WHALE_VIDEO_VIDEO_MAX_BYTES=2147483648`
  - `WHALE_VIDEO_MOVIE_MAX_BYTES=4294967296`
  - `WHALE_VIDEO_PLAY_TICKET_TTL_SECONDS=300`
  - `WHALE_VIDEO_PLAY_TICKET_REFRESH_SECONDS=240`
  - `WHALE_VIDEO_COMMENT_PRE_AUDIT=false`
  - `WHALE_VIDEO_HISTORY_RETENTION_DAYS=180`
  - `WHALE_VIDEO_FILE_BUCKET=whale-video`

---

## 五、里程碑与任务

> 每个里程碑收尾必须产出包含「需求项 / 完成项 / 待做项 / 已修正问题项」四类信息的检查报告（REQ-DOC-001 / REQ-QA-002），并更新 devlog 与 roadmap。

### M0 设计与骨架（依赖：Q1–Q4 确认；可与 G1 并行）

| ID | 任务 | 验收 |
|---|---|---|
| M0-1 | 需求登记 REQ-FUNC-031/032/033、REQ-ARCH-014、REQ-SEC-025；计划评审；更新 roadmap / service-catalog / naming-conventions | 需求、计划、服务目录、域词表可追溯 |
| M0-2 | 新建 `whale-video` 模块（POM、启动类、`application.yml`、hikari 基线、Nacos） | 8098 可启动、Nacos 注册、`/actuator/health` UP |
| M0-3 | 建库 `bluewhale_video` + Flyway `V1__baseline.sql`（14 张 `vd_*` 表 + 索引 + 种子） | 空库启动自动建表；租户列 / 索引齐全；脚本无 `DROP` / `TRUNCATE` |
| M0-4 | DDD 四层骨架 + `VideoArchitectureTest` + `VideoErrorCode` + `package-info` | ArchUnit 通过，领域层无 Spring / MyBatis / Redis 依赖 |
| M0-5 | 网关 `/video/**` 路由、`internal-deny`、流式限流占位；auth 权限 305–322、菜单 46–52、角色 25105/25106 | 经网关健康检查 `code=0`；无权限 403；权限 / 菜单可追溯 |
| M0-6 | 前端子应用 `whale-video`（8114、base `/video`）壳 + 基座 `apps.ts` 注册 + admin 路由占位 | `pnpm --filter whale-video build` 通过；基座可加载 |
| M0-7 | **G1** `whale-file` 按权限码大文件上传策略 + 单测 + 既有 Range 回归 | `sys:video:upload` 可 init 2GB 分片；无权限仍 50MB；伪造头被剥离；既有用例不回归 |
| M0-8 | API 契约冻结（`src/api/video.ts` + DTO / VO）、冒烟脚本骨架、CI / Docker / Nginx 矩阵登记 | 前后端契约一致；CI 列表包含 video；文档与代码一致 |

### M1 共享媒体底座

| ID | 任务 | 验收 |
|---|---|---|
| M1-1 | `vd_content` 共享内容聚合、发布状态机、版权状态、分类 / 标签 / 创作者、上传声明 | `DRAFT → PUBLISHED → OFFLINE` 与 `TAKEDOWN` 幂等；未签声明不能创建短视频 / 视频 |
| M1-2 | 上传编排：分片上传、`FileAssetClient`、文件大小 / contentType / magic bytes 校验、封面 | 非法格式 / 超限 400；合法 MP4 / WebM 可创建草稿 |
| M1-3 | 播放票据（单个 + 批量）、`/video/stream/{contentId}` Range 透传、可播状态缓存 | 200 / 206 / 416 正确；过期 / 换用户 / 换内容 / 下架全部拒绝；不暴露 `file_id` |
| M1-4 | 观看进度 / 最近观看、点赞 / 收藏 / 评论点赞，唯一索引 + 幂等计数 | 重复请求不重复计数；跨租户 404；历史保留策略可配置 |
| M1-5 | 评论发表 / 分页 / 父子回复、审核队列、可选通知 | 正文 ≤500 纯文本；隐藏后立即不可见；通知失败不回滚 |
| M1-6 | 首页聚合、分类 / 标签、站内搜索、feed 基础查询 + 缓存 | 缓存 60s + 抖动；发布 / 下架失效；空结果稳定 |
| M1-7 | admin 内容分页、发布 / 下架 / 版权下架、审计 | 权限码保护；`@AuditLog` 留痕；下架立即不可见 |
| M1-8 | 单测 + ArchUnit + Checkstyle + `bw-video-m1-smoke.ps1` | 模块用例全绿；冒烟 `SMOKE-ALL-PASS` |

> **状态（2026-11-01）**：M1-1 ～ M1-8 代码与静态验证完成——编译通过、Checkstyle 0 违规、ArchUnit 5/5、单测 64/64、冒烟脚本强断言化且语法校验通过；实机冒烟待环境（见 [M1 检查报告](../reviews/2026-11-01-wave5.77-video-m1.md)）。

### M2 短视频闭环

| ID | 任务 | 验收 |
|---|---|---|
| M2-1 | 短视频上传 / 编辑 / 删除 / 我的作品；公开 / 私密；发布 / 下架 | 私密仅本人可见可播；公开租户内可见；删除为逻辑删除 |
| M2-2 | 短视频 feed（最新 / 热门 / 分类，游标分页）+ 批量票据 + 首屏缓存 | 游标稳定、无重复 / 漏项；feed 预取票据可用 |
| M2-3 | 短视频详情、互动、评论、观看进度 | 点赞 / 收藏 / 评论 / 进度幂等；私密内容不开放评论 |
| M2-4 | 前端短视频滑播、详情、上传中心、我的作品、全局播放器 | 滑播暂停 / 预取 / 票据刷新可用；`build` + `lint` 通过 |
| M2-5 | admin 短视频管理、下架 / 版权下架、评论审核入口 | 新增 → 发布 → feed 可见 → 下架不可见闭环 |
| M2-6 | 网关短视频限流、takedown 后票据拒绝、缓存失效 | 下次 Range 请求拒绝；超阈值 429 |
| M2-7 | `bw-video-m2-smoke.ps1` + 浏览器 E2E：登录 → feed → 滑播 → 点赞 → 收藏 → 评论 → 审核 | 全链路 PASS；浏览器依赖测试账号（与音乐 B-09 同类风险） |

> **状态（2026-11-01）**：M2-1 ～ M2-6 代码与静态验证完成——编译通过、Checkstyle 0 违规、单测 73/73（含 feed 游标 9 例）、前端 `pnpm build` + `pnpm lint` 通过；M2-7 冒烟脚本强断言化且语法校验通过，实机冒烟与浏览器 E2E 待环境（见 [M2 检查报告](../reviews/2026-11-01-wave5.77-video-m2.md)）。

### M3 视频 / 系列闭环

| ID | 任务 | 验收 |
|---|---|---|
| M3-1 | `vd_series` + 系列 / 集数聚合与排序；集数唯一 | 同系列重复集数拒绝；系列页只展示已发布集 |
| M3-2 | 视频上传 / 编辑 / 删除 / 我的视频；分类；发布 / 下架 | 发布前置校验全通过；私密仅本人可见 |
| M3-3 | 视频列表 / 详情 / 系列页 / 搜索 | 分类筛选、系列集数、关键词搜索可用 |
| M3-4 | 播放器断点续播、全屏、键盘快捷键、进度上报 | 退出再进可续播；进度 15s / 暂停 / 结束上报 |
| M3-5 | admin 视频 / 系列管理 | 建系列 → 加集 → 发布 → 用户可见闭环 |
| M3-6 | `bw-video-m3-smoke.ps1` + 前端 build / lint | 冒烟 PASS；前端构建通过 |
| M3-7 | 系列排序 / 加集幂等 / 缓存失效 | 并发排序无重复；发布 / 下架缓存即时失效 |

> **状态（2026-11-01）**：M3-1 ～ M3-7 代码与静态验证完成——编译通过、Checkstyle 0 违规、单测 96/96（含 `SeriesAppServiceImplTest` 14 例）、`whale-video` 与 `whale-frontend/admin` 的 `pnpm build` + `pnpm lint` 通过；`bw-video-m3-smoke.ps1` 由 M0 PENDING 骨架升级为全段强断言且语法校验通过，实机冒烟与浏览器联调待环境（见 [M3 检查报告](../reviews/2026-11-01-wave5.77-video-m3.md)）。用户侧契约新增 `GET /video/series/page`（仅已上架），auth 314 权限 GET 路径同步。

### M4 电影 / 发现 / 运营

| ID | 任务 | 验收 |
|---|---|---|
| M4-1 | 电影聚合、管理端入库、导演 / 演员 / 地区 / 语言 / 上映日期 / 评分、海报 / 背景图 | 仅运营可创建；元数据必填校验；逻辑删除 |
| M4-2 | 电影授权来源 / 编号 / 到期、发布 / 下架 / 版权下架、授权到期自动下线 | 授权过期拒绝播放并自动 `OFFLINE`；重新授权可再发布 |
| M4-3 | 电影列表 / 详情 / 片单、海报 / 背景图代理 | 片单加片幂等；公开 VO 不含 `file_id` |
| M4-4 | 首页聚合、热门榜 / 新片榜、跨类型搜索 | 缓存 60s + 抖动；榜单规则可配置；空结果稳定 |
| M4-5 | 评论审核页、统计页、创作者管理页 | 审核 / 统计 / 停用均受权限码保护并留审计 |
| M4-6 | admin 电影 / 片单 / 分类页面 | 电影入库 → 授权 → 发布 → 用户可见闭环 |
| M4-7 | `bw-video-m4-smoke.ps1` + 前端全量 build / lint | 冒烟 PASS；`whale-video` + `whale-admin` 构建通过 |
| M4-8 | 电影授权到期提醒（可选消息） | 到期前提醒运营；失败只告警 |

> **状态（2026-11-01）**：M4-1 ～ M4-7 代码与静态验证完成——编译通过、Checkstyle 0 违规、单测 **146/146**（新增 6 个测试类）、`whale-video` 与 `whale-frontend/admin` 的 `tsc -b` + `vite build` + `eslint --max-warnings 0` 全部通过；`bw-video-m4-smoke.ps1` 升级为全段强断言且语法校验 `parseErrors=0`，实机冒烟（需 `SMOKE_VIDEO_FILE_ID`）与浏览器联调待环境。M4-8 口径调整为**运营统计页临期提醒 + 每日 03:10 授权到期自动下线兜底**，本期**不接消息中心**（后续可复用 `whale-message`）。详见 [M4 检查报告](../reviews/2026-11-01-wave5.77-video-m4.md)。

### M5 安全与上线硬化

| ID | 任务 | 验收 |
|---|---|---|
| M5-1 | 越权矩阵：跨租户 / 私密内容 / 片单 / 评论 / 封面代理 / 附件 IDOR | 全部 404 / 403，不泄露存在性 |
| M5-2 | 上传安全：MIME + magic bytes + 大小 + 时长 + 配额；伪造 content-type 拒绝 | 非法文件 / 超限 400；普通上传不能绕过 |
| M5-3 | 内容安全与版权：上传声明版本可追溯、电影授权必填、下架原因必填、审计可追溯、举报入口（可选） | 未签声明不可上传；无授权电影不可发布；下架 / 举报留痕 |
| M5-4 | 限流与防刷：播放票据、流式、评论、点赞、上传 | 超阈值 429（8080 实机验证）；异常用户可封禁 |
| M5-5 | 指标 / 日志 / Grafana / SLO | 抓取 UP、面板可读、日志无票据与正文 |
| M5-6 | 性能与容量：50 并发流式、首页 / 详情 P95、长视频票据刷新 | 达到 §4.11 SLO；无 5xx、无连接泄漏 |
| M5-7 | 清理任务：观看历史 180 天、播放计数回刷、孤儿文件（可选）、审计保留期 | 定时任务幂等、可回滚、多实例安全 |
| M5-8 | 验收报告、运维手册、备份恢复、CI / Nginx / Docker 矩阵、浏览器 E2E | 文档齐全、流水线通过；唯一可阻塞项为浏览器测试账号 |

> **状态（2026-11-01）**：M5-1 ～ M5-8 代码 / 脚本 / 文档完成——`requireOwned` 越权口径固定为「不可见 40401 / 可见非本人 40301」，新增 `WatchHistoryCleanupScheduler`（每日 04:40、500/批 × ≤20 批）+ `V4__watch_history_cleanup_index.sql`；单测 **151/151**、Checkstyle 0、ArchUnit 5/5；新增 `bw-video-m5-smoke.ps1`（强断言：上传安全 / 票据 300+240 / Range 206+416 / 越权矩阵 / 互动 / 直方图 / 可选网关）、重写 `bw-video-m5-load.ps1`（50 并发 × 3 轮）、新增 `bw-video-gateway-rate-limit-smoke.ps1` 与 `scripts/ops/video-backup-restore.ps1`（19 表校验）；`docs/ops/slo.md` 增 SLO-9/10/11，新增 Grafana `bluewhale-video`（部署侧 + Docker 侧副本）与运维手册 `docs/ops/video-service-ops.md`。**举报入口与孤儿文件清理为可选项，本期不做**（40018 已预留）。实机项（建库迁移、起服、M1–M5 冒烟、429 实测、授权到期任务、备份恢复演练、8114 浏览器 E2E、图片与流式网关鉴权）待环境。详见 [M5 检查报告](../reviews/2026-11-01-wave5.77-video-m5.md)。

---

## 六、验收标准

### 6.1 功能验收

1. 创作者可完成「签署声明 → 分片上传短视频 → 截帧封面 → 保存草稿 → 发布 → feed 可见 → 滑播 / 拖动 → 点赞 / 收藏 / 评论」闭环。
2. 创作者可完成「上传长视频 → 建系列 → 加集 → 发布 → 系列页可见 → 断点续播」闭环。
3. 运营可完成「电影入库 → 填授权来源 / 到期 → 上传海报 / 背景图 → 发布 → 用户可见 → 片单推荐」闭环。
4. 版权下架 / 电影授权到期后：用户侧立即不可见、已签发票据在下一次 Range 请求被拒绝、再次申请票据失败。
5. 私密短视频 / 视频、跨租户资源、未授权评论 / 点赞 / 收藏操作全部拒绝且返回一致错误码。
6. 评论审核通过 / 隐藏后用户侧可见性即时变化，操作有审计记录。

### 6.2 非功能验收

| 项 | 目标 |
|---|---|
| 内容详情 P95 | < 250ms |
| 首页 / feed P95 | < 300ms |
| 流式首字节 P95 | < 400ms |
| 50 并发流式 | 无 5xx、无连接泄漏 |
| 2GB / 4GB 分片上传 | 断点续传成功；完成校验一致；超限明确拒绝 |
| 播放计数 | 峰值不产生 `vd_content` 行锁等待；5 分钟内回刷 |
| 电影授权到期 | 票据签发拒绝 + 自动下线任务兜底；重新授权可恢复 |
| 安全 | 跨租户 404、无 `file_id` 直链、票据不可跨用户复用、伪造 MIME 拒绝 |

### 6.3 质量门禁

- 后端：`mvn -o -pl whale-services/whale-video -am test` 全绿；全量 `mvn -o clean install -T 1C` BUILD SUCCESS；Checkstyle 0 违规；ArchUnit 0 违规。
- 文件服务改动：`mvn -o -pl whale-services/whale-service-file -am test` 全绿，既有下载 / 预览 / Range 用例不回归。
- 前端：`pnpm --filter whale-video build`、`pnpm --filter whale-admin build`、`pnpm run lint` 通过。
- 运行时：网关冒烟脚本 `scripts/smoke/bw-video-*.ps1` 全 PASS；浏览器 E2E 通过。
- CI：`whale-video` 纳入 Backend CI、前端矩阵、Docker 镜像矩阵与 Nginx 前缀矩阵。

---

## 七、风险与待确认决策

| # | 决策 / 风险 | 影响 | 建议默认 |
|---|---|---|---|
| Q1 | 平台定位：企业内部视频库 / 面向公众 / 演示 | 决定注册、版权、审核、CDN、支付范围 | **企业内部视频库**（登录用户，运营维护电影） |
| Q2 | 服务拆分：一个 `whale-video` / 三个服务 | 影响部署、团队、跨服务一致性 | **一个服务 + 三个逻辑子域**；出现独立团队 / 独立扩缩容 / 电影付费 DRM 再拆 |
| Q3 | 电影是否平台级共享片库（跨租户） | 影响租户隔离、授权与查询模型 | **首版租户内电影库**；平台级片库需 `vd_movie_grant` 与租户拦截特例，后置 |
| Q4 | 短视频 / 视频是否所有登录用户可上传 | 影响审核、存储、配额 | **是**；必须签署上传声明；电影仅运营 |
| Q5 | 是否做会员 / 付费点播 | 需接 `whale-pay` 与订单体系 | **首版不做**，保留 `access_level` / `pay_product_id` 扩展点 |
| Q6 | 转码 / HLS / CDN | 浏览器兼容性、清晰度、带宽 | **不做转码 / HLS / CDN**；要求上传 H.264 + AAC MP4（faststart）或 WebM |
| Q7 | 审核策略 | 影响审核工作量与可见时效 | **短视频 / 视频先发后审 + 审核队列；电影运营入库后先审后发** |
| Q8 | 时长 / 大小上限 | 存储、带宽、上传体验 | 短视频 5 分钟 / 300MB；视频 2 小时 / 2GB；电影 4 小时 / 4GB；全部可配置 |
| Q9 | 播放票据 TTL 与刷新 | 长视频播放稳定性与撤销时效 | **300s + 240s 主动刷新 + 流式失败重取**；下架靠可播状态缓存即时阻断 |
| Q10 | 搜索 / 推荐 | 首页与发现体验 | **MySQL LIKE + 标签 + 规则排序**；不做 ES / 推荐模型 |
| R1 | `whale-file` 大文件策略 | 直接影响视频 / 电影能否上传 | **权限码策略（G1）**；全局放大仅作临时回退 |
| R2 | 浏览器编码兼容 / faststart | 首屏与拖动体验 | 上传前客户端预检；运营侧提供编码规范；M5 评估 ffprobe 只校验 |
| R3 | 存储与带宽 | 本机磁盘 / 生产成本 | 本机 local；生产 MinIO 独立桶 `whale-video`；CDN 后置 |
| R4 | 网关流式超时 / 熔断 | 长视频被截断 | 视频路由**不加入** 3s 熔断；流式客户端无整体超时 |
| R5 | 审核工作量 | 内容合规 | 先发后审 + 举报 + 运营下架；电影先审后发 |
| R6 | 电影授权到期 | 合规风险 | 票据 / 流式双重校验 + 每日自动下线 + 可选到期提醒 |
| R7 | 个人配额 / 租户配额 | 存储滥用 | G1 按权限配额；M5 评估租户级配额与孤儿文件清理 |
| R8 | `file_id` 直链泄漏 | 绕过票据 | 公开 VO 不含 `file_id`；封面 / 海报 / 流式全部走代理 |

---

## 八、登记与后续

- 需求登记：`REQ-FUNC-031`（共享媒体底座与短视频）、`REQ-FUNC-032`（视频 / 系列与电影）、`REQ-FUNC-033`（发现 / 互动 / 运营）、`REQ-ARCH-014`（服务架构与平台集成）、`REQ-SEC-025`（内容安全与版权合规）。
- 文档更新：`service-catalog.md`（服务 / 端口 / 路由 / 前端 / 库）、`naming-conventions.md` §八、`roadmap.md` Phase 4、`requirements/register.md`。
- 代码入口：M0 完成后创建模块与骨架；M1 起按里程碑逐步提交，提交信息采用 `feat(video): ...` 风格。
- 每个里程碑完成即产出 `docs/reviews/<日期>-wave5.77-video-<里程碑>.md`，并更新 `docs/devlog/`。
- 运维文档：`docs/ops/video-service-ops.md`（启动、限流、配额、备份恢复、授权到期处理、常见故障）。
- **下一步**：M0 / M1 / M2 / M3 / M4 / M5 已完成（见 [M0](../reviews/2026-11-01-wave5.77-video-m0.md) / [M1](../reviews/2026-11-01-wave5.77-video-m1.md) / [M2](../reviews/2026-11-01-wave5.77-video-m2.md) / [M3](../reviews/2026-11-01-wave5.77-video-m3.md) / [M4](../reviews/2026-11-01-wave5.77-video-m4.md) / [M5](../reviews/2026-11-01-wave5.77-video-m5.md) 检查报告），代码、脚本与文档收口；**待环境实机验收**：按 [运维手册 §十一](../ops/video-service-ops.md) 清单执行建库 + Flyway V1–V4、auth 权限迁移、8098 / 8085 / 8080 / 8081 起服、M1–M5 冒烟、`bw-video-m5-load.ps1` 性能实测、`bw-video-gateway-rate-limit-smoke.ps1` 429 取证、授权到期任务、`video-backup-restore.ps1` 演练、8114 浏览器 E2E；部署层确认图片与 `/video/stream/**` 鉴权方案；可选能力（举报入口、孤儿文件清理、ffprobe 深度校验、先审后发切换）按运营需要排期。
