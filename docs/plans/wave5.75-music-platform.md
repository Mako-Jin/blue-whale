# Wave 5.75 · 音乐平台（whale-music）实施计划

> 目标：在 BlueWhale 平台内新增音乐平台能力，覆盖「曲库 → 播放 → 歌单 → 发现 → 互动 → 运营」主线，并与既有认证、租户、网关、文件、消息、审计能力打通。
> 状态：**规划已确认；M0 完成，M1 后端 + 前端完成（单测 / Checkstyle / ArchUnit 全绿，三应用 build + lint 通过，建库与权限迁移已实机验证，8096 起服与网关路由冒烟通过），G1（`whale-file` HTTP Range）已完成，M2 完成（播放票据 + Range 流式 + 进度 / 收藏 + 全局播放器前端；后端 34/34 单测、前端 build + lint 通过，冒烟脚本 `SMOKE-ALL-PASS`），M3 完成（歌单与发现 + 播放计数回刷 + 前端发现 / 歌单 / 搜索页 + admin 官方歌单运营；后端 53/53 单测、前端 build + lint 通过，冒烟脚本 `SMOKE-ALL-PASS`），M4 完成（评论 / 审核 / 我的音乐 / 可选通知；后端 72/72 单测、前端 build + lint 通过，冒烟脚本 `SMOKE-ALL-PASS`）**（2026-10-31）｜ 关联需求：REQ-FUNC-029 / REQ-FUNC-030 / REQ-ARCH-013 / REQ-SEC-019
> 上游依据：[roadmap.md](roadmap.md)、[service-catalog.md](../service-catalog.md)、[naming-conventions.md](../naming-conventions.md)、[architecture.md](../architecture.md)、[backend.md](../backend.md)、[wave5.74 文档服务实施计划](2026-10-29-wiki-doc-service.md)

---

## 〇、结论摘要

| 项 | 结论 |
|---|---|
| 服务归属 | **新建 `backend/whale-services/whale-music`**（核心服务，不塞进 file / doc / pay） |
| 端口 / 前缀 / 库 | **8096** / **`/music/**`** / **`bluewhale_music`** |
| 包名 / 配置前缀 | `com.bluewhale.music` / `whale.music.*`；Redis key `whale:music:*` |
| 前端 | 新建 `frontend/platform-apps/whale-music`（qiankun 子应用，base `/music`，dev 端口 **8111**）；运营页面并入 `frontend/admin` |
| 分层 | DDD 四层：`interfaces / application / domain / infrastructure` + `architecture-test`（与 auth / file 同口径） |
| 复用的平台能力 | auth（登录 / RBAC / 租户）、gateway（JWT / 路由 / 限流 / `internal-deny`）、file（音频与封面字节）、message（可选通知）、audit（写操作与审核留痕）、job（可选计数回刷） |
| 关键依赖缺口 | ~~**`whale-file` 当前下载接口不支持 HTTP Range**~~ → **G1 已完成（2026-10-31）**：下载 / 预览 / 内网内容接口支持 206 / 416 + `Content-Range` + `ETag` + `If-Range`，音频拖动/续播可用 |
| 明确不做 | 公网匿名注册、第三方版权曲库接入、直播 / MV / KTV、离线 DRM、付费会员（首版不接 pay）、AI 推荐模型、音频转码（首版只接受浏览器可播格式） |
| 交付节奏 | M0 设计与骨架 → M1 曲库管理 → M2 播放与音频流 → M3 歌单与发现 → M4 互动与前端闭环 → M5 安全与上线硬化 |

---

## 一、现状盘点与差距

| 维度 | 现状 | 对本计划的影响 |
|---|---|---|
| 音乐域 | 全仓 **无** 音乐 / 歌曲 / 专辑 / 歌单相关服务、表、权限点、菜单 | 全新域，可按领域模型一次设计到位，无历史包袱 |
| 文件服务 | `whale-file` 已有上传、下载、预览、分片上传、对象存储插件（local / minio / oss / rustfs）、`/file/internal/upload` 与 `/file/internal/{id}/content` | 音频字节归 file，music 只存 `file_id`；**Range 缺口需补齐（G1）** |
| 认证与租户 | auth 已提供 JWT、RBAC、租户、网关身份头注入；租户行级隔离插件已落地 | 所有音乐表带 `tenant_id`，跨租户一律 404 |
| 网关 | 路由、JWT、黑名单、限流、熔断、`internal-deny`、TraceId 已就绪 | 新增 `/music/**` 路由；`/music/internal/**` 对外 403；`/music/stream/**` 需独立限流 |
| 审计 | `@AuditLog` + 四通道日志 + 脱敏已就绪 | 曲库写操作、审核、下架全部留痕；日志不记录歌词正文 |
| 消息 | `whale-message` 可发送站内信 / 邮件 / IM | M4 评论回复、审核结果通知可选接入，失败不回滚主事务 |
| Agent | `whale-agent` 已有知识索引与检索 | 音乐推荐 / 语义检索不在首版范围，仅预留 |
| 前端 | qiankun 基座 + 13 个子应用 + `@bluewhale/whale-ui` / http / theme / log-sdk | 直接按现有模板新建子应用；播放器为应用内全局组件 |
| 编号与端口 | 权限点已用至 **280**（279 doc 标签、280 `sys:file:manage`）；菜单已用至 **38**；后端 8095 之后空闲；前端 8110 之后空闲 | 音乐权限点从 **281** 起，使用 281–303；菜单从 **39** 起，使用 39–44；端口 **8096 / 8111** |
| 质量门禁 | Checkstyle `error`、JaCoCo、ArchUnit、CI 镜像矩阵 | 新服务同步纳入构建、检查清单、镜像矩阵与冒烟脚本 |

**G1（阻塞项，已完成 2026-10-31）**：`FileController#download` / `#preview` / `FileInternalController#content` 已统一支持单段 Range（200 全量 / 206 分段 / 416 越界 + `Accept-Ranges` / `Content-Range` / 强 `ETag` / `If-Range`），M2 可直接实现 `/music/stream/**` 分段转发。

---

## 二、定位、范围与边界

### 2.1 定位（已确认 2026-10-31）

**平台内音乐应用**：面向已登录的租户用户；**所有登录用户均可上传歌曲**，上传前必须签署《版权无争议协议》，并可选择「公开」或「自听」；用户还可以播放、建歌单、收藏、评论；不开放匿名注册，不接入第三方商业曲库，不做公网分发。

> 若目标是「面向公众的音乐平台」，需额外确认：公网注册、版权授权、内容审核团队、CDN 与带宽预算、支付与会员体系。本计划的 M0–M5 结构可复用，但 M5 会显著扩大。

### 2.2 能力对标

| 能力 | 网易云 / QQ 音乐 | whale-music 首版方案 | 波次 |
|---|---|---|---|
| 曲库 | 歌曲 / 专辑 / 歌手 / 分类 | `mu_song` / `mu_album` / `mu_artist` / `mu_tag` | M1 |
| 内容运营 | 上架 / 下架 / 审核 | 所有人可上传（先签版权协议），可选公开 / 自听；`DRAFT → PUBLISHED → OFFLINE` + 版权下架 | M1 |
| 播放 | 流式播放 / 拖动 / 进度 | 短时播放票据 + Range 流式（206） | M2 |
| 我的音乐 | 收藏 / 最近播放 | `mu_song_like` / `mu_play_history` | M2 |
| 歌单 | 用户歌单 / 官方歌单 | `mu_playlist` + `mu_playlist_song`，公开 / 私有 | M3 |
| 发现 | 首页 / 排行榜 / 分类 | 首页聚合、热门榜、新歌榜、标签筛选、搜索 | M3 |
| 互动 | 评论 / 点赞 | `mu_song_comment` + 审核状态 + 点赞计数 | M4 |
| 运营后台 | 曲库 / 歌单 / 评论管理 | `frontend/admin` 五个音乐运营页 | M1/M3/M4 |
| 会员 / 支付 | VIP / 数字专辑 | **不做**（后续可接 `whale-pay`） | — |
| 直播 / MV / KTV | 视频与直播 | **不做** | — |
| 推荐算法 | 个性化推荐 | **不做模型**；首版用热度 / 新歌 / 标签规则 | — |
| 转码 / CDN | 多码率 + CDN | 首版仅接受 MP3/AAC，后续按需接转码与 CDN | — |

### 2.3 In Scope（首版 M1–M5）

1. 歌手、专辑、歌曲、标签的曲库管理；**所有登录用户可上传歌曲**（签署版权无争议协议，可选公开 / 自听），音频与封面上传到 `whale-file`；管理员可运营与下架。
2. 歌曲发布 / 下架 / 版权下架状态机；列表、详情、搜索、标签筛选。
3. 播放票据、Range 流式播放、最近播放与进度、收藏 / 点赞。
4. 用户歌单与官方歌单、歌单排序与容量约束、首页聚合、热门榜、新歌榜。
5. 歌曲评论、评论审核、我的音乐。
6. qiankun 子应用 `whale-music`（首页 / 搜索 / 歌手 / 专辑 / 歌单 / 我的音乐 / 播放器）+ admin 运营页。
7. 网关路由、权限点 / 菜单种子、租户隔离、审计、限流、指标与冒烟脚本。

### 2.4 Out of Scope（明确不做）

- 公网匿名注册、游客模式、第三方 OAuth。
- 第三方商业曲库接入、版权采购、DRM、离线下载。
- 付费会员、数字专辑、支付分账（后续按需接 `whale-pay`）。
- 直播、MV / 视频、KTV、电台节目。
- 音频转码、多码率、CDN 预热（首版由浏览器直接播放原始文件）。
- 个性化推荐模型、协同过滤、向量召回。
- 移动端原生 App。

---

## 三、领域建模（Tactical + 关键 Strategic 约束）

### 3.1 限界上下文与关系

| 上下文 | 归属 | 关系 |
|---|---|---|
| 音乐上下文 | `whale-music` | 拥有歌手、专辑、歌曲、标签、歌单、评论、播放历史；曲库是唯一内容源 |
| 认证上下文 | `whale-service-auth` | 上游：身份、租户、权限码；music **不持久化**用户资料 |
| 文件上下文 | `whale-file` | 下游：音频 / 封面字节；music 只保存 `file_id` 与元数据 |
| 审计上下文 | `whale-audit` | 下游：写操作与审核留痕 |
| 消息上下文 | `whale-message` | 下游（可选）：评论回复 / 审核结果通知 |
| 支付上下文 | `whale-pay` | 首版**不集成**，仅保留扩展点 |

> 不为音乐域引入 MQ、搜索引擎或独立对象存储；跨上下文调用全部在事务外，失败不回滚音乐主事务。

### 3.2 聚合与不变量

| 聚合根 | 组成 | 关键不变量 |
|---|---|---|
| `Artist` | 艺名、头像、地区、简介、状态 | 艺名租户内唯一；下架艺人不得新增可发布歌曲 |
| `Album` | 标题、封面、歌手、发行日期、类型、状态 | 封面必填；发行日期不得晚于当前日期 + 1 年（防误填）；仅 `PUBLISHED` 可被用户检索 |
| `Song` | 标题、副标题、上传者、可见性（PUBLIC / PRIVATE）、音频资产、封面、时长、歌词、语言、标签、发布状态、版权状态 | 上传必须已签署版权无争议协议；发布必须满足：音频资产存在、歌手存在且启用、版权状态有效、`duration_ms>0`；`PRIVATE` 仅上传者可见可播；`TAKEDOWN` 立即强制 `OFFLINE` 并拒绝播放；公开作品允许运营复核与下架 |
| `CopyrightAgreement` | 用户、协议版本、签署时间、客户端信息 | 同一用户同一版本只记一次；未签署当前版本不能创建歌曲 |
| `Playlist` | 标题、封面、所有者、类型（USER/OFFICIAL）、可见性、歌曲项 | 私有歌单仅所有者可见；单歌单最多 500 首；同一歌曲不可重复加入；官方歌单仅运营可建 |
| `SongComment` | 歌曲、用户、父评论、正文、状态、点赞数 | 正文 ≤ 500 字、纯文本；状态 `PENDING / VISIBLE / HIDDEN`；仅 `VISIBLE` 对外展示 |
| `PlayHistory` | 用户、歌曲、进度、来源、播放时间 | 仅记录已发布歌曲；按用户保留 90 天，超期清理 |

### 3.3 关键命令、事件与规则

| 类型 | 内容 |
|---|---|
| 命令 | `CreateArtist`、`PublishAlbum`、`UploadSong`、`PublishSong`、`OfflineSong`、`TakedownSong`、`SignCopyrightAgreement`、`CreatePlaylist`、`AddSongToPlaylist`、`ReorderPlaylist`、`LikeSong`、`ReportPlayback`、`SubmitComment`、`ModerateComment` |
| 领域事件 | `SongUploaded`、`SongPublished`、`SongOfflined`、`SongTakenDown`、`PlaylistChanged`、`CommentSubmitted`、`CommentModerated` |
| 规则 R1 | 歌曲发布前置校验（资产 / 歌手 / 版权 / 时长）全部通过，否则 `DomainException` |
| 规则 R2 | 版权下架优先于发布状态：`TAKEDOWN` 后任何播放票据签发失败，已签发票据在缓存失效后不可续用 |
| 规则 R3 | 歌单可见性：`PRIVATE` 仅所有者；`PUBLIC` 租户内可见；官方歌单只读 |
| 规则 R4 | 点赞与加入歌单幂等：唯一索引 + 重复请求返回现有状态，不报错、不重复计数 |
| 规则 R5 | 播放计数异步聚合：Redis 计数 + 定时回刷，避免 `UPDATE mu_song SET play_count` 行锁热点 |
| 规则 R6 | 评论先审后发或先发后审由配置决定（默认先发后审 + 审核队列），隐藏后立即不可见 |
| 规则 R7 | 所有读接口先按 `tenant_id` 过滤，再按可见性过滤；跨租户 ID 一律 404，不暴露存在性 |
| 规则 R8 | 上传前置：上传者已签署当前版本《版权无争议协议》，未签署直接 `DomainException` |
| 规则 R9 | 可见性：`PRIVATE` 歌曲仅上传者本人可查 / 可播；`PUBLIC` 租户内可见；运营可对公开作品下架 |

### 3.4 值对象与领域事件落地约定

- 值对象：`SongTitle`、`AudioAsset(fileId, format, sizeBytes, durationMs)`、`CoverAsset`、`TagName`、`PlaylistVisibility`、`PublishState`、`CopyrightState`、`Lyric`。
- 聚合根实现 `Entity`，值对象实现 `ValueObject`，领域事件继承 `DomainEvent`（与 auth / file 改造后的基座契约一致）。
- 领域层不依赖 Spring / MyBatis / Redis；文件读取通过 `AudioStoragePort`，缓存与计数通过应用层端口下沉。
- 分层由 `whale-music-architecture-test` 的 ArchUnit 规则固化。

### 3.5 统一语言

| 术语 | 含义 |
|---|---|
| 曲库 | 歌手、专辑、歌曲、标签的集合 |
| 上架 / 下架 | `PUBLISHED` / `OFFLINE`，控制用户可见与可播 |
| 版权下架 | `TAKEDOWN`，因授权问题强制下线，优先级最高 |
| 播放票据 | 短期有效的播放授权凭证，绑定租户 + 用户 + 歌曲 |
| 官方歌单 | 运营维护、用户只读的歌单 |
| 审核队列 | 待审核评论集合，运营可批量通过 / 隐藏 |
| 自听 | 歌曲 `visibility=PRIVATE`，仅上传者本人可见可播 |
| 版权无争议协议 | 上传者声明拥有版权或已获授权、无第三方权利争议的协议 |

---

## 四、总体设计

### 4.1 服务落位与命名（REQ-ARCH-004 / REQ-ARCH-005）

| 项 | 值 | 落点 |
|---|---|---|
| Maven 模块 | `backend/whale-services/whale-music` | `whale-services/pom.xml` 增加 `<module>whale-music</module>` |
| artifactId / 服务名 | `whale-music` / `whale-music` | `pom.xml`、`application.yml`、Nacos 服务名 |
| 端口 | `${SERVER_PORT:8096}` | `application.yml` |
| 对外前缀 | `/music/**`（服务自持，网关**不剥离**） | Controller `@RequestMapping("/music/...")` |
| 数据库 | `bluewhale_music` | `db/init.sql` + 幂等迁移脚本 |
| Java 包 | `com.bluewhale.music` | 四层分包 |
| 配置前缀 | `whale.music.*` | 如 `whale.music.play.ticket-ttl`、`whale.music.comment.pre-audit` |
| Redis key | `whale:music:<用途>:<标识>` | 票据 / 计数 / 榜单 / 缓存 |
| 前端子应用 | `frontend/platform-apps/whale-music`，base `/music`，端口 8111 | `vite.config.ts`、`qiankun/apps.ts` |
| 运营页面 | `frontend/admin/src/pages/music` | 复用 admin 既有布局与权限组件 |

### 4.2 分层与依赖

```
interfaces  → application  → domain
                    ↑            ↑
              infrastructure ────┘（实现 domain 端口）
```

- 实施口径（2026-10-31 M0 落地）：单模块 `whale-music` + 四层分包
  （`interfaces / application / domain / infrastructure`，与 `whale-doc` 同口径），
  避免为骨架引入 5 个子模块与聚合 POM；后续若需独立复用可按层拆分。
- `interfaces`：REST 控制器、DTO / VO、参数校验、权限注解。
- `application`：用例编排、事务边界、领域事件发布、缓存 / 计数适配。
- `domain`：聚合、值对象、领域服务、端口接口；仅依赖 JDK，不依赖 Spring / MyBatis / Redis。
- `infrastructure`：MyBatis-Plus、仓储实现、`whale-file` 客户端、Redis 适配。
- `architecture-test`：`MusicArchitectureTest`（ArchUnit）固化依赖方向与包名。

### 4.3 数据模型（`bluewhale_music`）

> 通用列：`id BIGINT`（雪花，倾向 `ASSIGN_ID`）、`tenant_id BIGINT NOT NULL`、`create_time`、`update_time`、`deleted TINYINT`；所有业务表带租户过滤。

| 表 | 用途 | 关键字段 / 索引 |
|---|---|---|
| `mu_artist` | 歌手 | `name`（租户内唯一）、`avatar_file_id`、`region`、`bio`、`status`；`uk(tenant_id,name,deleted)` |
| `mu_album` | 专辑 | `artist_id`、`title`、`cover_file_id`、`release_date`、`album_type`、`publish_status`；`idx(tenant_id,publish_status)`、`idx(artist_id)` |
| `mu_song` | 歌曲 | `uploader_id`、`visibility`（PUBLIC / PRIVATE）、`album_id`、`artist_id`、`title`、`subtitle`、`audio_file_id`、`cover_file_id`、`duration_ms`、`lyric_file_id`、`lyric_text`、`language`、`publish_status`、`copyright_status`、`play_count`、`like_count`、`comment_count`、`sort_weight`；`idx(tenant_id,publish_status,visibility)`、`idx(uploader_id)`、`idx(album_id)`、`idx(artist_id)` |
| `mu_copyright_agreement` | 版权协议签署 | `user_id`、`agreement_version`、`accepted_at`、`client_ip`、`user_agent`；`uk(tenant_id,user_id,agreement_version)` |
| `mu_tag` | 标签 | `name`、`type`（GENRE / LANGUAGE / MOOD）、`sort`；`uk(tenant_id,name,type,deleted)` |
| `mu_song_tag` | 歌曲标签关系 | 复合主键 `(song_id,tag_id)`；`idx(tag_id)` |
| `mu_playlist` | 歌单 | `owner_id`（官方歌单为空）、`title`、`cover_file_id`、`description`、`playlist_type`、`visibility`、`song_count`、`play_count`、`status`；`idx(tenant_id,visibility)`、`idx(owner_id)` |
| `mu_playlist_song` | 歌单歌曲 | `playlist_id`、`song_id`、`sort_no`、`added_by`；`uk(playlist_id,song_id)`、`idx(playlist_id,sort_no)` |
| `mu_song_like` | 歌曲收藏 | `user_id`、`song_id`；`uk(tenant_id,user_id,song_id)` |
| `mu_play_history` | 播放历史 | `user_id`、`song_id`、`progress_ms`、`source`、`device`、`played_at`；`idx(tenant_id,user_id,played_at)`；保留 90 天 |
| `mu_song_comment` | 歌曲评论 | `song_id`、`user_id`、`parent_id`、`content`、`status`、`like_count`；`idx(tenant_id,song_id,status,create_time)` |
| `mu_song_stat_daily` | 每日播放统计（M5 可选） | `stat_date`、`song_id`、`play_count`、`unique_users`；`uk(tenant_id,stat_date,song_id)` |
| `audit_log` | 审计（随服务库） | 复用 starter 审计表 |

### 4.4 接口清单（首版约 30 个端点）

**运营 / 曲库（权限码保护）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST / PUT / DELETE | `/music/artist`、`/music/artist/{id}` | 歌手增改删（删为逻辑删除） |
| GET | `/music/artist/page`、`/music/artist/{id}` | 运营分页 / 详情 |
| POST / PUT / DELETE | `/music/album`、`/music/album/{id}` | 专辑增改删 |
| POST | `/music/album/{id}/publish` | 专辑上架 |
| GET | `/music/album/page` | 运营分页 |
| POST / PUT / DELETE | `/music/song`、`/music/song/{id}` | 歌曲增改删 |
| POST | `/music/song/{id}/publish`、`/music/song/{id}/offline`、`/music/song/{id}/takedown` | 发布 / 下架 / 版权下架 |
| GET | `/music/song/page`、`/music/song/{id}` | 运营分页 / 详情 |
| POST / PUT / DELETE / GET | `/music/tag`、`/music/tag/list` | 标签维护 |
| POST / PUT / DELETE / GET | `/music/playlist/official`、`/music/playlist/official/{id}` | 官方歌单运营 |
| GET | `/music/comment/page` | 评论审核分页 |
| POST | `/music/comment/{id}/approve`、`/music/comment/{id}/hide` | 评论通过 / 隐藏 |
| GET | `/music/stat/overview` | 曲库与播放概览 |

**用户侧（登录 + 浏览权限）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/music/home` | 首页聚合（热门歌 / 新歌 / 推荐歌单 / 标签） |
| GET | `/music/search` | 关键词搜索（歌曲 / 歌手 / 专辑 / 歌单） |
| GET | `/music/song/{id}`、`/music/album/{id}`、`/music/artist/{id}` | 公开详情 |
| GET | `/music/song/page` | 用户侧筛选分页（标签 / 歌手 / 专辑 / 排序） |
| GET | `/music/chart/hot`、`/music/chart/new` | 热门榜 / 新歌榜 |
| GET | `/music/copyright/agreement/current` | 当前版权协议版本与正文 |
| POST | `/music/copyright/agreement/accept` | 签署版权无争议协议（版本 + 勾选） |
| POST / PUT / DELETE | `/music/user/song`、`/music/user/song/{id}` | 用户上传 / 编辑 / 删除自己的作品（创建时选 PUBLIC / PRIVATE） |
| GET | `/music/my/songs` | 我的作品（含自听作品） |
| POST | `/music/play/ticket` | 签发播放票据（校验发布状态、版权、可见性、租户） |
| GET | `/music/stream/{songId}?ticket=...` | Range 流式播放（200 / 206 / 416） |
| POST | `/music/play/progress` | 上报播放进度与历史 |
| GET | `/music/play/history` | 最近播放 |
| POST / DELETE | `/music/song/{id}/like` | 收藏 / 取消 |
| GET | `/music/my/likes` | 我的收藏 |
| POST / PUT / DELETE | `/music/playlist`、`/music/playlist/{id}` | 用户歌单增改删 |
| GET | `/music/playlist/page`、`/music/playlist/{id}` | 歌单分页 / 详情 |
| POST / DELETE / PUT | `/music/playlist/{id}/song`、`/music/playlist/{id}/song/{songId}`、`/music/playlist/{id}/sort` | 加歌 / 移除 / 排序 |
| POST / GET | `/music/song/{id}/comment`、`/music/song/{id}/comment/page` | 发表评论 / 评论分页 |

> 内部接口（如后续 Agent 检索需要）统一落 `/music/internal/**`，由网关 `internal-deny` 对外 403。

### 4.5 权限点 / 菜单种子（281–303 / 39–44）

> 权限编号顺延：279 为 doc 标签管理，280 已被 `sys:file:manage`（init.sql / 5.76）占用，音乐从 281 起。

| 权限码 | ID | 用途 |
|---|---|---|
| `sys:music:artist:list/add/edit/delete` | 281–284 | 歌手查询与维护 |
| `sys:music:album:list/add/edit/delete/publish` | 285–289 | 专辑维护与上下架 |
| `sys:music:song:list/add/edit/delete/publish` | 290–294 | 歌曲维护与发布 / 下架 / 版权下架 |
| `sys:music:tag:manage` | 295 | 标签维护 |
| `sys:music:playlist:manage` | 296 | 官方歌单运营 |
| `sys:music:comment:audit` | 297 | 评论审核 |
| `sys:music:stat:view` | 298 | 播放概览 |
| `sys:music:browse` | 299 | 用户侧浏览 / 搜索 / 详情 |
| `sys:music:playlist:own` | 300 | 用户自建歌单 |
| `sys:music:comment:create` | 301 | 发表评论 |
| `sys:music:like` | 302 | 收藏 / 点赞 |
| `sys:music:upload` | 303 | 上传 / 管理自己的作品（含协议签署） |

| 菜单 ID | 名称 | 落点 |
|---|---|---|
| 39 | 音乐中心（DIR） | qiankun 子应用入口 `/music` |
| 40 | 音乐曲库 | admin 歌曲管理 |
| 41 | 歌手与专辑 | admin 歌手 / 专辑管理 |
| 42 | 歌单与标签 | admin 官方歌单 / 标签管理 |
| 43 | 评论审核 | admin 评论审核 |
| 44 | 播放统计 | admin 播放概览 |

> 角色：`music_admin`（25103，全部 281–303）、`music_member`（25104，用户侧 299–303）。
> 「所有人可上传」：迁移脚本将 `music_member` 幂等授予全部启用用户，`tenant_admin` 亦获得全部权限。

### 4.6 网关与内网安全

- 新增路由 `whale-music`：`Path=/music/**` → `lb://whale-music`，**不配 `StripPrefix`**。
- `/music/stream/**` 在网关按路径限流（独立阈值，例如 120 次/分钟/IP）；`/music/comment` 按用户限流。
- `/music/internal/**` 由 `InternalApiDenyGlobalFilter` 对外 403；服务侧内网令牌校验复用 starter。
- 白名单不放行 `/music/**`，播放必须登录；播放票据由登录态签发。

### 4.7 音频流设计（关键决策）

```
前端                 whale-music                  whale-file
 |  POST /music/play/ticket  |                        |
 |-------------------------->| 校验歌曲发布/版权/租户   |
 |                           | 写 Redis 票据 (TTL)     |
 |<--------------------------| {ticket}               |
 |  GET /music/stream/{id}?ticket=... + Range        |
 |-------------------------->| 校验票据 → 透传 Range   |
 |                           |------------------------>| 内部内容接口（支持 Range）
 |<--------------------------|<------------------------| 206 + Content-Range + ETag
```

| 决策 | 方案 | 理由 |
|---|---|---|
| D1 播放授权 | 短时票据（默认 120s，绑定 `tenantId + userId + songId`），允许多次 Range 请求 | 浏览器一次播放会发多个 Range 请求，不能做成严格一次性 |
| D2 音频读取 | `whale-music` 不直接读存储；通过 `whale-file` 内部内容接口读取并透传 Range | 文件字节归 file，music 不持存储凭据 |
| D3 Range 缺口 | M2 前置任务：`whale-file` 内部内容接口支持 `Range`，返回 `206 / 416`、`Accept-Ranges`、`Content-Range`、`Content-Length`、`ETag` | 当前 `InputStreamResource` 全量流不可 seek |
| D4 票据失效 | Redis 删除票据 + 版权下架事件触发批量失效；票据校验每次查 Redis | 下架立即阻断，不依赖客户端 |
| D5 响应头 | `Cache-Control: private, no-store` + `ETag` + `X-Content-Type-Options: nosniff` | 防止共享缓存泄漏票据 URL |
| D6 对象存储 | 首版 local / minio 均可；生产建议 MinIO + 后续 CDN；**不向前端暴露 `file_id`** | 防止绕过票据直连文件接口枚举 |

### 4.8 缓存与计数

| Key | 用途 | TTL / 策略 |
|---|---|---|
| `whale:music:play:ticket:{ticket}` | 播放票据 | 120s，值为 `tenantId|userId|songId` |
| `whale:music:play:count:{tenantId}:{songId}` | 播放计数 | 定时回刷，5 分钟一批 |
| `whale:music:chart:hot:{tenantId}:{yyyyMMdd}` | 热门榜 ZSET | 按日滚动，保留 7 天 |
| `whale:music:cache:home:{tenantId}` | 首页聚合 | 60s + 抖动；发布 / 下架事件主动失效 |
| `whale:music:cache:song:{tenantId}:{songId}` | 歌曲详情 | 300s；发布状态变更主动失效 |
| `whale:music:lock:playlist:{id}` | 歌单排序 / 加歌互斥 | Redisson 分布式锁 |

- 缓存复用既有 `CacheConsistencyTemplate`（空值哨兵 + TTL 抖动 + 延迟双删）。
- 播放计数采用 Redis 计数 + 定时回刷，**不采用高并发行更新**；榜单与 DB `play_count` 允许最终一致。

### 4.9 前端设计

**`frontend/platform-apps/whale-music`（qiankun 子应用，8111，base `/music`）**

| 页面 / 组件 | 说明 |
|---|---|
| 首页 / 发现 | 热门歌曲、新歌、推荐歌单、标签入口；空态与加载态 |
| 搜索 | 关键词搜索歌曲 / 歌手 / 专辑 / 歌单，分类 Tab |
| 歌手详情 | 歌手信息 + 代表歌曲 + 专辑 |
| 专辑详情 | 封面、发行信息、曲目列表 |
| 歌单详情 | 歌单信息、歌曲列表、播放全部 |
| 我的音乐 | 我的收藏、最近播放、我的歌单、我的作品 |
| 上传音乐 | 选音频 / 封面、填写元数据、签署版权协议、选择公开 / 自听 |
| 我的作品 | 本人上传的公开与自听作品，可编辑 / 删除 |
| `PlayerBar` | 全局播放器：播放 / 暂停、进度拖动、上一首 / 下一首、音量、播放队列、歌词抽屉 |
| `SongList` / `PlaylistPicker` | 复用列表与「加入歌单」弹层 |

- API 层 `src/api/music.ts` 冻结契约，统一走 `@bluewhale/http`（Cookie 刷新 + CSRF）。
- 播放器状态使用应用内 React Context / Reducer，不新增状态管理依赖；歌词按 LRC 时间戳在前端解析。
- 封面与音频均来自 `whale-file`，前端只拿接口返回的代理 URL，不持有 `file_id` 直链。

**`frontend/admin` 运营页**

- `pages/music/SongList`、`ArtistList`、`AlbumList`、`PlaylistManage`、`CommentAudit`。
- 上传音频 / 封面复用 admin 既有文件上传能力，提交时只传 `fileId`。
- 所有操作按钮受对应权限码控制；下架 / 版权下架二次确认并留审计。
- 用户上传页复用同一套上传组件，创建时强制勾选版权协议并选择公开 / 自听。

### 4.10 可观测性

- Prometheus 抓取 `whale-music:8096`；指标：播放票据签发量 / 失败率、流式请求 200/206/416 比例、首字节耗时、热门榜缓存命中率、评论审核积压量。
- Grafana「音乐平台（Wave 5.75）」行：播放 QPS、流式 P95、票据失败率、审核积压。
- SLO 建议：歌曲详情 P95 < 200ms；流式首字节 P95 < 300ms；首页聚合 P95 < 300ms。
- 日志仅记录 ID 与状态，不记录歌词正文、评论正文与完整票据。

---

## 五、里程碑与任务

> 每个里程碑收尾必须产出包含「需求项 / 完成项 / 待做项 / 已修正问题项」四类信息的检查报告（REQ-DOC-001 / REQ-QA-002），并更新 devlog 与 roadmap。

### M0 设计与骨架（依赖：用户确认 2.1 定位与第七节决策点）

| ID | 任务 | 验收 |
|---|---|---|
| M0-1 | 需求登记 REQ-FUNC-029/030、REQ-ARCH-013、REQ-SEC-019；计划评审 | 登记册与计划链接可追溯 |
| M0-2 | 新建 `whale-music` 模块（POM、启动类、`application.yml`、hikari 基线） | 可启动、Nacos 注册、`/actuator/health` UP |
| M0-3 | `bluewhale_music` 建库 + `mu_*` 表 DDL + 幂等迁移脚本 | 11 张表建成，租户列与索引齐全 |
| M0-4 | DDD 四层骨架 + `architecture-test` ArchUnit | 分层规则用例通过，领域层无 Spring 依赖 |
| M0-5 | 网关 `/music/**` 路由、`internal-deny`、权限点 281–303 与菜单 39–44 种子 | 经网关健康检查 `code=0`，无权限 403 |
| M0-6 | 服务登记：service-catalog / naming-conventions / 构建与镜像矩阵 | 文档与代码一致 |

### M1 曲库内容管理

| ID | 任务 | 验收 |
|---|---|---|
| M1-1 | 歌手 / 专辑 / 标签聚合与 CRUD | 租户内唯一、逻辑删除、分页上限 200 |
| M1-2 | 歌曲聚合与 CRUD；音频 / 封面上传接 `whale-file` | 发布前置校验 R1 全通过才可上架 |
| M1-3 | 发布 / 下架 / 版权下架状态机 | `TAKEDOWN` 强制 `OFFLINE`，重复操作幂等 |
| M1-4 | 运营分页与详情、标签筛选 | 权限码保护，写操作 `@AuditLog` |
| M1-5 | admin 曲库 / 歌手 / 专辑页 | 新增→发布→列表可见→下架不可见闭环 |
| M1-6 | 单测 + ArchUnit + Checkstyle | 模块用例全绿、0 违规 |
| M1-7 | 用户上传与协议签署：协议版本 / 签署记录 / 未签署拦截 | 未签署不能上传；签署记录不可篡改、可按版本追溯 |
| M1-8 | 用户作品管理：公开 / 自听、我的作品、编辑 / 删除 | 自听作品仅本人可见可播；公开作品租户内可见 |
| M1-9 | 前端上传页 / 协议弹窗 / 我的作品 | 签署→上传→选择公开 / 自听→列表可见闭环 |

### M2 播放与音频流（前置：G1 Range 补齐）

> ✅ **已完成（2026-10-31）**：票据（Redis / TTL 120s / 三要素绑定，每次流式复核可播态）、`/music/stream/{songId}` 透传 Range（200 / 206 / 416，不暴露 `file_id`）、进度与收藏幂等、90 天清理任务、全局播放器（播放条 / 队列 / 歌词抽屉）、网关 `/music/stream/**` 限流配置；冒烟 `bw-music-m2-smoke.ps1` 输出 `SMOKE-ALL-PASS`。详见 [M2 检查报告](reviews/2026-10-31-wave5.75-music-m2.md)。

| ID | 任务 | 验收 |
|---|---|---|
| M2-1 | `whale-file` 内部内容接口 Range 支持（206 / 416 / ETag） | 单测覆盖首段 / 中段 / 越界 / 全量；不破坏既有下载 |
| M2-2 | 播放票据签发与校验（Redis、TTL、绑定三要素） | 过期 / 换用户 / 换歌曲 / 下架全部拒绝 |
| M2-3 | `/music/stream/{songId}` 透传 Range | 200 / 206 / 416 正确；不暴露 `file_id` |
| M2-4 | 播放进度、最近播放、收藏 / 点赞 | 幂等、租户隔离、历史保留 90 天 |
| M2-5 | `PlayerBar` 与播放队列、歌词抽屉 | 拖动进度、切歌、续播可用 |
| M2-6 | 网关 `/music/stream/**` 限流 | 超阈值 429 + `Result` 体 |
| M2-7 | 冒烟脚本：票据 → Range → 206 → 416 → 下架拒绝 | `SMOKE-ALL-PASS` |

### M3 歌单与发现

> ✅ **已完成（2026-10-31）**：用户 / 官方歌单（500 首上限、私有可见性、加歌幂等、重排一致性校验）、首页聚合（Redis 缓存 60s + 抖动）、热门 / 新歌榜、站内搜索（歌曲 / 歌手 / 专辑 / 歌单，空结果稳定）、播放计数 Redis INCR + 5 分钟定时回刷；前端发现页 / 搜索页 / 歌单列表与详情页、admin 官方歌单运营页；冒烟 `bw-music-m3-smoke.ps1` 输出 `SMOKE-ALL-PASS`。详见 [M3 检查报告](reviews/2026-10-31-wave5.75-music-m3.md)。

| ID | 任务 | 验收 |
|---|---|---|
| M3-1 | 用户歌单 / 官方歌单、加歌 / 移除 / 排序 | 500 首上限、私有可见性、排序锁 |
| M3-2 | 首页聚合、热门榜、新歌榜、标签筛选 | 缓存 60s + 抖动，发布事件失效 |
| M3-3 | 搜索（歌曲 / 歌手 / 专辑 / 歌单） | 关键词与租户过滤，空结果稳定 |
| M3-4 | 播放计数 Redis + 定时回刷 | 高并发不产生行锁热点，最终一致 |
| M3-5 | admin 官方歌单运营页 | 建歌单→加歌→排序→公开可见 |
| M3-6 | 发现页 / 歌单页 / 搜索页前端 | 列表、分页、筛选、空态可用 |

### M4 互动与前端闭环

> ✅ **已完成（2026-10-31）**：评论发表 / 分页 / 父子回复（纯文本 ≤500，XSS 原样存储不执行，40014 / 40015 / 40403）、审核队列与通过 / 隐藏（状态机 + `GREATEST` 原子计数，幂等，隐藏即不可见，`@AuditLog` 留痕）、我的音乐（收藏 / 最近播放 / 我的歌单三页签）、可选通知（`CommentNotificationPort` + `whale-message`，事务提交后发送，失败仅告警）；前端评论区、我的音乐页、admin 评论审核页；冒烟 `bw-music-m4-smoke.ps1` 输出 `SMOKE-ALL-PASS`。M4-5 浏览器 E2E 依赖测试账号记入待做。详见 [M4 检查报告](reviews/2026-10-31-wave5.75-music-m4.md)。

| ID | 任务 | 验收 |
|---|---|---|
| M4-1 | 评论发表 / 分页 / 父子回复；纯文本与长度限制 | XSS 不执行、租户隔离 |
| M4-2 | 评论审核（通过 / 隐藏）与审核队列 | 隐藏后立即不可见，操作留审计 |
| M4-3 | 我的音乐（收藏 / 最近播放 / 我的歌单） | 三者分页与空态完整 |
| M4-4 | 可选通知：评论回复 / 审核结果走 `whale-message` | 失败仅告警，不回滚主事务 |
| M4-5 | 浏览器 E2E：登录 → 首页 → 搜索 → 播放 → 收藏 → 建歌单 → 评论 → 审核 | 全链路 PASS |

### M5 安全与上线硬化

| ID | 任务 | 验收 |
|---|---|---|
| M5-1 | 越权矩阵：跨租户 / 私有歌单 / 自听作品 / 评论 / 附件 IDOR | 全部 404 / 403，不泄露存在性 |
| M5-2 | 上传白名单：音频 `audio/mpeg`、`audio/aac`、`audio/flac`；封面图片；大小上限 | 非法类型 / 超限 400 |
| M5-3 | 版权合规：签署协议版本可追溯、上传来源必填、下架与申诉留痕、审计可追溯 | 未签署协议不可上传；无授权素材可下架 |
| M5-4 | 限流与防刷：播放票据、流式、评论、点赞 | 超阈值 429，异常用户可封禁 |
| M5-5 | 指标 / 日志 / Grafana / SLO | 抓取 UP、面板可读、无敏感正文 |
| M5-6 | 性能与容量：50 并发流式稳定、首页 P95、详情 P95 | 达到 4.10 SLO 建议值 |
| M5-7 | 清理任务：播放历史 90 天、榜单 7 天、审计保留期 | 定时任务幂等、可回滚 |
| M5-8 | 验收报告、运维手册、CI 镜像矩阵与冒烟脚本 | 文档齐全、流水线通过 |

---

## 六、验收标准

### 6.1 功能验收

1. 运营可完成「建歌手 → 建专辑 → 上传音频 → 建歌曲 → 发布 → 用户可见」闭环。
2. 用户可完成「登录 → 首页 → 搜索 → 播放（含拖动）→ 收藏 → 建歌单 → 评论」闭环。
3. 版权下架后：用户侧立即不可见、已签发票据不可继续播放、再次申请票据失败。
4. 私有歌单、跨租户资源、未授权评论操作全部拒绝且返回一致错误码。
5. 评论审核通过 / 隐藏后用户侧可见性即时变化，操作有审计记录。

### 6.2 非功能验收

| 项 | 目标 |
|---|---|
| 歌曲详情 P95 | < 200ms |
| 流式首字节 P95 | < 300ms |
| 首页聚合 P95 | < 300ms |
| 50 并发流式 | 无 5xx、无连接泄漏 |
| 播放计数 | 峰值不产生 `mu_song` 行锁等待；5 分钟内回刷 |
| 安全 | 跨租户 404、无 `file_id` 直链、票据不可跨用户复用 |

### 6.3 质量门禁

- 后端：`mvn -o -pl whale-services/whale-music -am test` 全绿；全量 `mvn -o clean install -T 1C` BUILD SUCCESS；Checkstyle 0 违规；ArchUnit 0 违规。
- 文件服务改动：`mvn -o -pl whale-services/whale-service-file -am test` 全绿，既有下载用例不回归。
- 前端：`pnpm --filter whale-music build`、`pnpm --filter whale-admin build`、`pnpm run lint` 通过。
- 运行时：网关冒烟脚本 `scripts/smoke/bw-music-*.ps1` 全 PASS；浏览器 E2E 通过。
- CI：`whale-music` 纳入 Backend CI 与 Docker 镜像矩阵。

---

## 七、风险与待确认决策

| # | 决策 / 风险 | 影响 | 建议默认 |
|---|---|---|---|
| Q1 | 平台定位：企业内部音乐库 / 面向公众 / 演示 | 决定注册、版权、审核、CDN、支付范围 | **企业内部音乐库**（登录用户，管理员维护曲库） |
| Q2 | 内容来源与版权 | 无授权曲库不得上线 | **已确认**：仅自有 / 授权 / 免版权素材；上传必须签署《版权无争议协议》并记录版本 |
| Q3 | 是否允许用户上传 / 创作者入口 | 审核与存储成本上升 | **已确认（2026-10-31）**：所有登录用户可上传；签协议；可选公开 / 自听；运营可复核下架 |
| Q4 | 是否做会员 / 付费 | 需接 `whale-pay` 与订单体系 | 首版**不做**，保留扩展点 |
| Q5 | 音频格式与转码 | 浏览器兼容性与存储成本 | 首版 MP3/AAC，**不做转码**；FLAC 仅登记 |
| Q6 | 存储与分发 | 公网播放需 CDN | 本机 local；生产 MinIO；CDN 后置 |
| Q7 | 推荐能力 | 影响首页与榜单设计 | 首版规则推荐（热度 / 新歌 / 标签），不做模型 |
| Q8 | 评论审核策略 | 影响审核工作量 | 默认先发后审 + 审核队列，可配置为先审后发 |
| Q9 | 用户上传的公开作品是否先审后发 | 影响可见时效与审核成本 | **已确认**：不设上传前置审核，公开即见，运营事后复核并可下架 |
| R1 | `whale-file` Range 改造范围 | 影响文件服务既有契约 | 只增强内部内容接口，保持下载 / 预览行为不变 |
| R2 | 播放计数热点 | 影响 `mu_song` 写性能 | Redis 计数 + 定时回刷，榜单允许最终一致 |
| R3 | 大文件与带宽 | 影响本机存储与网络 | 单文件上限 100MB；生产独立桶 `whale-music` |

---

## 八、登记与后续

- 需求登记：`REQ-FUNC-029`（曲库与播放）、`REQ-FUNC-030`（歌单发现与互动）、`REQ-ARCH-013`（服务架构与文件 Range）、`REQ-SEC-019`（内容安全与版权）。
- 文档更新：`service-catalog.md`（服务 / 端口 / 路由 / 前端）、`naming-conventions.md` §八、`roadmap.md` Phase 4。
- 代码入口：M0 完成后创建模块与骨架；M1 起按里程碑逐步提交，提交信息采用 `feat(music): ...` 风格。
- 每个里程碑完成即产出 `docs/reviews/<日期>-wave5.75-music-<里程碑>.md`，并更新 `docs/devlog/`。
- 下一步：用户确认第七节 Q1–Q8 后，执行 M0（需求登记已完成，可直接进入骨架与建库）。
