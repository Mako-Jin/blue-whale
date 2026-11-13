# 视频平台服务运维手册（whale-video）

> 适用版本：Wave 5.77（M0–M5）
> 关联计划：[wave5.77-video-platform.md](../plans/wave5.77-video-platform.md)
> 关联报告：[M5 检查报告](../reviews/2026-11-01-wave5.77-video-m5.md)
> 关联 SLO：[slo.md](slo.md)（SLO-9/10/11）
> 关联监控：[grafana/dashboards/bluewhale-video.json](../../deploy/monitoring/grafana/dashboards/bluewhale-video.json)
> 关联演练：[video-backup-restore.ps1](../../scripts/ops/video-backup-restore.ps1)

---

## 一、服务坐标

| 项 | 取值 |
|---|---|
| 模块 | `whale-backend/whale-services/whale-video` |
| 服务名 | `whale-video` |
| 端口 | `8098`（`SERVER_PORT`） |
| 数据库 | `bluewhale_video`（MySQL 8，`utf8mb4`；Flyway `V1`–`V4`，19 张核心表） |
| 包根 | `com.bluewhale.video`（interfaces / application / domain / infrastructure 四层） |
| 表前缀 | `vd_`（另含 `audit_log` / `api_log`） |
| 对外前缀 | `/video/**`（网关不 StripPrefix） |
| 内网前缀 | `/video/internal/**`（网关 `internal-deny` 统一 403） |
| 前端 | `whale-frontend/platform-apps/whale-video`，qiankun base `/video`，本地 dev 端口 8114 |
| Redis | `whale:video:*` |
| 权限码 | `sys:video:*`（ID 79–82 + 309–322，共 18 项；`sort_no` 仍为 305–322） |
| 角色 | `video_admin`（25105，按 `perm_code` 全量授权）、`video_member`（25106，314–318 + `sys:file:upload`(32)） |
| 菜单 | 46「视频中心」+ 47–52（短视频 / 视频 / 电影 / 分类系列片单 / 评论审核 / 播放统计） |
| 上游依赖 | `whale-file`（8085，视频字节与文件元信息）、`whale-message`（可选通知，暂未接入） |
| 镜像 | 后端 `ghcr.io/<repo>/whale-video`；前端 `ghcr.io/<repo>/whale-frontend/whale-video` |

网关路由（`whale-backend/whale-services/whale-service-gateway/src/main/resources/application.yml`）：

- `whale-video`：`lb://whale-video`，`Path=/video/**`，**不配 `StripPrefix`**。
- 内网屏蔽：`/video/internal/**`（403）。
- 匿名流白名单：网关 `whale.gateway.white-list` 含 `/video/stream/**`（与 `/music/stream/**` 同理），
  `<video>` 标签无法携带 JWT，鉴权改由 `POST /video/play/ticket` 签发的一次性票据在服务侧校验。
- **不加入** `circuit-breaker.apply-to-routes`：一次播放会跨多个 Range 请求，3s 超时熔断会截断长视频。
- 限流（`rate-limit.path-rules`，超阈值 429 + `Result` 体）：

| 路径 | 阈值 | 窗口 | 环境变量（阈值 / 窗口） |
|---|---|---|---|
| `/video/stream/**` | 120 | 60s | `WHALE_GATEWAY_RATE_LIMIT_VIDEO_STREAM` / `..._WINDOW_SECONDS` |
| `/video/play/ticket` | 60 | 60s | `WHALE_GATEWAY_RATE_LIMIT_VIDEO_TICKET` / `..._WINDOW_SECONDS` |
| `/video/content/*/comment` | 20 | 60s | `WHALE_GATEWAY_RATE_LIMIT_VIDEO_COMMENT` / `..._WINDOW_SECONDS` |
| `/video/content/*/like` | 30 | 60s | `WHALE_GATEWAY_RATE_LIMIT_VIDEO_LIKE` / `..._WINDOW_SECONDS` |
| `/video/content/*/favorite` | 30 | 60s | 复用 `WHALE_GATEWAY_RATE_LIMIT_VIDEO_LIKE` / `..._WINDOW_SECONDS` |
| `/video/upload/**` | 20 | 60s | `WHALE_GATEWAY_RATE_LIMIT_VIDEO_UPLOAD` / `..._WINDOW_SECONDS` |

> 限流过滤器 Order = `HIGHEST_PRECEDENCE + 10`，先于 JWT 鉴权；限流键含客户端维度
> （`X-Forwarded-For` 首段），多实例共享 Redis 固定窗口。

---

## 二、构建、部署与启停

### 2.1 后端构建

```powershell
mvn -q -o -pl whale-backend/whale-services/whale-video -am -DskipTests package
```

产物：`whale-backend/whale-services/whale-video/target/whale-video.jar`（`finalName=${project.artifactId}`）。

### 2.2 后端启动

```powershell
$env:MYSQL_PASSWORD='***'
$env:SPRING_DATA_REDIS_PASSWORD='***'   # 如 Redis 开启口令
java -jar whale-backend/whale-services/whale-video/target/whale-video.jar
```

必需外部依赖：MySQL、Redis、Nacos；对外链路还需要网关（8080）与 auth（8081）；
上传、封面代理与 `/video/stream/**` 两跳链路还需要 `whale-file`（8085）。

### 2.3 容器构建（与 CI 一致）

后端复用参数化模板 `deploy/backend/sample/Dockerfile`：

```powershell
docker build -f deploy/backend/sample/Dockerfile `
  --build-arg MODULE_PATH=whale-services/whale-video `
  --build-arg SERVICE_PORT=8098 `
  -t bluewhale/whale-video .
```

前端：

```powershell
docker build -f deploy/frontend/Dockerfile `
  --build-arg APP_NAME=whale-video `
  --build-arg APP_DIR=platform-apps/whale-video `
  --build-arg APP_BASE=/video `
  -t bluewhale/whale-fe-video .
```

> 同名冲突（后端服务也叫 `whale-video`）由 CI 命名空间规避：前端推送
> `ghcr.io/<repo>/whale-frontend/whale-video`，后端推送 `ghcr.io/<repo>/whale-video`。
> CI 矩阵已包含：`.github/workflows/docker-deploy.yml` 后端 `whale-video`(8098) 与
> 前端 `whale-video`(base `/video`, namespace `frontend`)；
> Nginx `infrastructure/docker/nginx/conf.d/micro-frontend.conf` 的
> `^/(...|video)(?=/|$)` 转发到容器别名 `whale-fe-video:80`。

### 2.4 前端构建

```powershell
cd whale-frontend
pnpm --filter whale-video lint
pnpm --filter whale-video build
```

产物 `whale-frontend/platform-apps/whale-video/dist`，按 qiankun 子应用部署到 `/video`。

### 2.5 健康检查

```powershell
curl http://127.0.0.1:8098/actuator/health
curl http://127.0.0.1:8098/actuator/prometheus
curl -H "Authorization: Bearer <token>" http://127.0.0.1:8080/video/meta
curl -H "Authorization: Bearer <token>" http://127.0.0.1:8080/video/home
```

`/video/meta` 返回服务名、租户、用户、声明版本与前端需要的上传策略（视频 / 图片 MIME
白名单、三种内容类型的时长与大小上限），前端据此做上传前置校验。

---

## 三、配置项

业务配置前缀 `whale.video.*`，环境变量前缀 `WHALE_VIDEO_*`。

### 3.1 上传、声明与配额

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `declaration-version` | `WHALE_VIDEO_DECLARATION_VERSION` | `V1` | 当前《内容合法与版权无争议声明》版本，上传必须签署该版本 |
| `declaration-title` | `WHALE_VIDEO_DECLARATION_TITLE` | 内容合法与版权无争议声明 | 声明标题 |
| `short-max-duration-seconds` | `WHALE_VIDEO_SHORT_MAX_DURATION_SECONDS` | 300 | 短视频时长上限（5 分钟） |
| `video-max-duration-seconds` | `WHALE_VIDEO_VIDEO_MAX_DURATION_SECONDS` | 7200 | 视频时长上限（2 小时） |
| `movie-max-duration-seconds` | `WHALE_VIDEO_MOVIE_MAX_DURATION_SECONDS` | 14400 | 电影时长上限（4 小时） |
| `short-max-bytes` | `WHALE_VIDEO_SHORT_MAX_BYTES` | 314572800（300MB） | 短视频单文件上限 |
| `video-max-bytes` | `WHALE_VIDEO_VIDEO_MAX_BYTES` | 2147483648（2GB） | 视频单文件上限 |
| `movie-max-bytes` | `WHALE_VIDEO_MOVIE_MAX_BYTES` | 4294967296（4GB） | 电影单文件上限 |
| `video-formats` | `WHALE_VIDEO_VIDEO_FORMATS` | `video/mp4, video/webm` | 视频 MIME 白名单（逗号分隔） |
| `image-formats` | `WHALE_VIDEO_IMAGE_FORMATS` | `image/jpeg, image/png, image/webp` | 封面 / 海报 MIME 白名单 |

**大文件落库链路**：视频字节统一归 `whale-file`，配额由文件服务按权限码（G1）判定：

| 权限码 | 单文件上限 | 单用户配额 | 环境变量 |
|---|---|---|---|
| `sys:video:upload`（上传短视频 / 视频） | 2GB | 8GB | `WHALE_FILE_UPLOAD_POLICY_VIDEO_MAX_SIZE_BYTES` / `..._QUOTA_BYTES` |
| `sys:video:movie:manage`（运营入库电影） | 4GB | 不限（0） | `WHALE_FILE_UPLOAD_POLICY_MOVIE_MAX_SIZE_BYTES` / `..._QUOTA_BYTES` |

> `whale-file` 的 multipart 单请求上限仍为 50MB（`max-file-size`），因此 2GB / 4GB 视频
> **必须走分片上传**（`/file/upload/chunk/*`）；前端上传中心已按分片实现，改变阈值时
> 需同步 `WHALE_VIDEO_*_MAX_BYTES`、`WHALE_FILE_UPLOAD_POLICY_*` 与前端提示。

### 3.2 播放与流式

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `play-ticket-ttl-seconds` | `WHALE_VIDEO_PLAY_TICKET_TTL_SECONDS` | 300 | 票据 TTL；一次播放的多次 Range 复用同一票据 |
| `play-ticket-refresh-seconds` | `WHALE_VIDEO_PLAY_TICKET_REFRESH_SECONDS` | 240 | 客户端主动刷新间隔（提前于 TTL 60s） |
| `playable-cache-seconds` | `WHALE_VIDEO_PLAYABLE_CACHE_SECONDS` | 30 | 可播状态缓存；下架 / 版权下架 / 授权到期主动删除 |
| `stream-chunk-bytes` | `WHALE_VIDEO_STREAM_CHUNK_BYTES` | 1048576 | 上游读取块大小（1MB） |

流式响应约定：`200` / `206` / `416`；`Accept-Ranges: bytes`、`Cache-Control: private, no-store`、
`X-Content-Type-Options: nosniff`；`If-Range` / `Range` 透传；416 返回
`Content-Range: bytes */<total>` 且 `Content-Length: 0`。

### 3.3 互动、缓存与清理

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `comment-pre-audit` | `WHALE_VIDEO_COMMENT_PRE_AUDIT` | false | false=先发后审 + 审核队列；true=先审后发 |
| `history-retention-days` | `WHALE_VIDEO_HISTORY_RETENTION_DAYS` | 180 | 观看历史保留天数（每日 04:40 清理） |
| `content-cache-seconds` | `WHALE_VIDEO_CONTENT_CACHE_SECONDS` | 60 | 首页 / feed / 详情缓存（含 0–10s 抖动） |
| `file-service-name` | `WHALE_VIDEO_FILE_SERVICE` | `whale-file` | 文件服务 Nacos 名 |
| `file-bucket` | `WHALE_VIDEO_FILE_BUCKET` | `whale-video` | 视频文件桶（生产建议 MinIO 独立桶） |

### 3.4 基础设施与观测

| 项 | 默认 | 说明 |
|---|---|---|
| `SERVER_PORT` | 8098 | 服务端口 |
| `DB_POOL_MAX_SIZE` | 5 | Hikari 上限（按实例数换算，见 §9.3） |
| `DB_POOL_LEAK_DETECTION_MS` | 60000 | 连接泄漏检测门槛 |
| `REDIS_HOST` / `REDIS_PORT` | 127.0.0.1:6379 | 票据 / 可播状态 / 播放计数 / 首页缓存 |
| `NACOS_SERVER` | 127.0.0.1:8848 | 注册与配置中心 |
| `WHALE_FLYWAY_ENABLED` | true | 启动时自动执行 `V1`–`V4` |
| `management.endpoints.web.exposure.include` | health,info,prometheus | 仅暴露三项，不含 env/beans |
| `management.metrics.distribution.percentiles-histogram.http.server.requests` | true | **SLO-9/10/11 依赖该直方图桶**，不可关闭 |
| `logging.level.com.bluewhale` | debug | **生产必须改为 `info`**，落地方式见 §6.4 |

---

## 四、数据库与迁移

### 4.1 表清单（19 张核心表）

| 来源 | 表 |
|---|---|
| `V1__baseline.sql`（16） | `vd_content`、`vd_short_video`、`vd_video`、`vd_movie`、`vd_category`、`vd_tag`、`vd_content_tag`、`vd_creator`、`vd_upload_declaration`、`vd_like`、`vd_favorite`、`vd_watch_history`、`vd_comment`、`vd_comment_like`、`audit_log`、`api_log` |
| `V2__series.sql`（1） | `vd_series` |
| `V3__movie_collection.sql`（2） | `vd_collection`、`vd_collection_item` |
| `V4__watch_history_cleanup_index.sql`（索引） | `idx_vd_history_watched(tenant_id, watched_at)` |

关键索引（不可删除）：

- `idx_vd_content_feed(tenant_id, content_type, publish_status, visibility, published_at)`：feed / 列表。
- `idx_vd_content_hot(tenant_id, content_type, publish_status, hot_score)`：热门榜。
- `idx_vd_movie_license(tenant_id, license_expire_at)`：授权到期扫描。
- `idx_vd_history_watched(tenant_id, watched_at)`：观看历史保留期清理（V4）。

### 4.2 迁移执行顺序

1. 建库：`CREATE DATABASE IF NOT EXISTS bluewhale_video CHARACTER SET utf8mb4;`
2. 启动 `whale-video`，Flyway 自动执行 `V1` → `V4`（`validate-on-migrate=true`、
   `clean-disabled=true`、`baseline-version=1`）。存量空库首启即以 V1 为基线。
3. `bluewhale_auth` 执行 `migration-5.77-video.sql`（幂等）：权限 ID 79–82 + 309–322（`sort_no`
   仍为 305–322；305–308 已被消息公告占用）、角色 25105/25106、按 `perm_code` 授权
   （admin/tenant_admin 全量；video_member 314–318 + `sys:file:upload`）、
   所有启用用户授予 `video_member`、菜单 46–52、M4 接口路径增量。
   注意：`sys_permission.path` 需先扩宽到 `VARCHAR(2048)`（`migration-5.87-permission-path-width.sql`），
   否则逗号分隔的多端点路径会因 `Data too long` 失败。
4. 校验：

```sql
SELECT COUNT(*) FROM information_schema.tables
 WHERE table_schema='bluewhale_video' AND table_name IN
 ('vd_content','vd_short_video','vd_video','vd_movie','vd_category','vd_tag','vd_content_tag',
  'vd_creator','vd_upload_declaration','vd_like','vd_favorite','vd_watch_history','vd_comment',
  'vd_comment_like','vd_series','vd_collection','vd_collection_item','audit_log','api_log');  -- 期望 19

SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema='bluewhale_video' AND table_name='vd_watch_history'
   AND index_name='idx_vd_history_watched';                                        -- 期望 1

SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema='bluewhale_video' AND table_name='vd_content'
   AND column_name IN ('copyright_status','takedown_reason','takedown_at');         -- 期望 3

SELECT COUNT(*) FROM sys_permission
 WHERE perm_code LIKE 'sys:video:%' AND deleted = 0;                                -- 期望 18
SELECT COUNT(*) FROM sys_menu WHERE id BETWEEN 46 AND 52;                          -- 期望 7
```

> Flyway 版本记录在 `flyway_schema_history`；回滚不删索引 / 不删表（旧版本不读取新增列，
> 保持兼容，见 §十二）。

---

## 五、备份与恢复

### 5.1 备份与恢复演练脚本

```powershell
# 备份 + 导入独立校验库 + 断言核心表 / 索引 / 版权列 / 片单列 / 行数一致性
$env:MYSQL_PASSWORD='***'
.\scripts\ops\video-backup-restore.ps1
```

参数（均有默认值）：

| 参数 | 默认 | 说明 |
|---|---|---|
| `-Database` | `bluewhale_video` | 源库 |
| `-VerifyDatabase` | `bluewhale_video_restore_check` | 校验库（默认演练结束删除；禁止与源库同名） |
| `-BackupDir` | `logs/backup` | 备份文件目录 |
| `-MysqlBin` | `D:\mysql8\bin`（可用 `MYSQL_BIN_DIR` 覆盖） | `mysqldump` / `mysql` 所在目录 |
| `-KeepRestoreDatabase` | 关 | 保留校验库供人工核对 |

断言项：

1. 核心表 **19** 张齐全；
2. `idx_vd_history_watched` 存在（否则观看历史清理退化为全表扫描）；
3. `vd_content` 含 `copyright_status / takedown_reason / takedown_at` 三列（版权下架留痕）；
4. `vd_collection_item` 含 `collection_id / content_id / sort_no` 三列；
5. `vd_content / vd_comment / vd_watch_history / vd_collection_item` 源库与校验库行数一致。

成功输出 `RESULT: PASSED`（退出码 0），失败输出 `RESULT: FAILED - <原因>`（退出码 1）。

手工等价命令：

```powershell
mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF `
  -uroot -p"$env:MYSQL_PASSWORD" bluewhale_video `
  > logs/backup/bluewhale_video-$(Get-Date -Format 'yyyyMMdd-HHmmss').sql
```

### 5.2 生产恢复步骤

1. 停 `whale-video` 实例（网关摘流或直接停进程），避免恢复期间写入。
2. 新建 `bluewhale_video_new` 导入备份，执行 §4.2 校验 SQL。
3. 校验 19 表齐全、`idx_vd_history_watched` 存在、版权列与片单列存在、auth 侧权限 18 项
   存在后切换库名。
4. 启动服务并检查 `/actuator/health`、`/video/meta`、首页与详情、一次票据签发与 Range 流式。
5. Redis 数据全部可重建，**无需恢复**：
   - `whale:video:play:ticket:*`（300s）、`whale:video:playable:*`（30s）、
     `whale:video:play:count:*`（待回刷增量）、`whale:video:home:*` / `whale:video:cache:feed:*`（30–60s）。
   - 丢失的播放计数增量最多影响 5 分钟窗口，不影响可播状态；
     丢失票据只让进行中的播放重新取票。
6. 视频字节在 `whale-file`（local 磁盘 / MinIO 桶 `whale-video`），需与该库同批次备份；
   `vd_content.video_file_id` / `cover_file_id` 与文件服务元数据不一致时表现为 40006 / 图片 404。

---

## 六、监控、告警与 SLO

### 6.1 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `http_server_requests_seconds_*` | Timer + 直方图桶 | 全部 REST 接口；SLO-9/10/11 数据源 |
| `hikaricp_connections_*` | Gauge | 连接池 active/idle/pending/max |
| `jvm_memory_used_bytes` / `jvm_gc_*` | Gauge / Counter | 堆与 GC |
| `up{job="whale-video"}` | Gauge | 抓取存活 |

> 本服务**没有自定义业务指标**（播放计数在 Redis，榜单为实时聚合），排障以 HTTP 直方图、
> Hikari、JVM + 业务日志为主。

Prometheus：

- `deploy/monitoring/prometheus.yml`：`job_name: whale-video`，`127.0.0.1:8098`。
- `infrastructure/prometheus/prometheus-docker.yml`：`host.docker.internal:8098`。

Grafana 面板（12 个 panel，含 3 个行分组，uid `bluewhale-video`）：

- `deploy/monitoring/grafana/dashboards/bluewhale-video.json`（数据源 uid `prometheus`）
- `infrastructure/prometheus/grafana/dashboards/bluewhale-video.json`（数据源 uid `bluewhale-prometheus`，Docker 侧副本）

面板内容：首页 / 详情 / 流式首字节 P95（SLO-9/10/11）、`up`、端点 QPS 与 P95 趋势、
5xx 占比、Hikari（active/idle/pending/max）、JVM 堆使用率。

> 直方图桶仅在 `uri` 归一化后可用：`/video/content/{id}`、`/video/stream/{contentId}`
> （`http_server_requests_seconds_bucket` 的 `uri` 标签为模板化路径，不是真实 ID）。

### 6.2 SLO（PromQL 见 [slo.md](slo.md)）

| ID | 目标 | PromQL 核心 |
|---|---|---|
| SLO-9 | 首页 P95 < 300ms | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/video/home",application="whale-video"}[5m])))` |
| SLO-10 | 内容详情 P95 < 250ms | 同上，`uri="/video/content/{id}"` |
| SLO-11 | 流式首字节 P95 < 400ms | 同上，`uri="/video/stream/{contentId}"` |

### 6.3 告警

复用通用告警：`ServiceDown`（`up{job="whale-video"} == 0`）、`Http5xxRateHigh`、`JvmHeapHigh`。
建议补充：

```yaml
- alert: VideoStreamP95Slow
  expr: histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="whale-video",uri="/video/stream/{contentId}"}[10m]))) > 0.4
  for: 10m
- alert: VideoDetailP95Slow
  expr: histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="whale-video",uri="/video/content/{id}"}[10m]))) > 0.25
  for: 10m
- alert: VideoHikariSaturated
  expr: hikaricp_connections_pending{application="whale-video"} > 0
  for: 5m
```

### 6.4 日志

- **生产日志级别落地方式**（三选一，作用等价）：
  1. 环境变量：`LOGGING_LEVEL_COM_BLUEWHALE=info`（Spring Boot relaxed binding 映射为
     `logging.level.com.bluewhale`，容器 / systemd / Windows 服务均可用）。
  2. 命令行属性：`--logging.level.com.bluewhale=info`（无法透传环境变量的场景使用）。
  3. （可选改造）将 `application.yml` 改为
     `logging.level.com.bluewhale: ${LOGGING_LEVEL_COM_BLUEWHALE:info}`。
     当前仓库默认仍为 `debug`（本地排障便利），生产部署须用 1 或 2 显式覆盖。
- **日志不含敏感内容**：播放票据、`fileId`、视频字节、评论正文、声明全文均不入日志；
  审计上报只带资源 ID / 标题 / 状态 / 操作人。
- 流式只记录内容 ID、Range 区间、状态码（200/206/416），不打印字节内容。
- 定时任务日志关键字（巡检可直接 grep）：
  `视频播放计数回刷完成:`、`电影授权到期自动下线完成:`、`观看历史清理完成:`、`观看历史清理失败:`。

---

## 七、安全硬化（M5）

| 项 | 措施 | 验收 |
|---|---|---|
| 越权矩阵 | 跨租户 / 私密内容 / 片单 / 评论 / 封面与海报代理 / 附件 `fileId` 统一「存在性不泄露」：不可见一律 404（40401/40404/40405/40406/40407），可见但非本人写操作 403（40301） | `bw-video-m5-smoke.ps1` §5 断言 |
| 上传安全 | MIME 白名单 → 大小上限 → magic bytes（MP4 偏移 4 `ftyp` / WebM `1A45DFA3`）→ 时长上限；`webm 头 + 声明 mp4` → 40003，图片非白名单 → 40004，文件不可用 → 40006，超时长 → 40002 | `bw-video-m5-smoke.ps1` §2 |
| 文件 IDOR | 公开视图不返回 `videoFileId` / `coverFileId` / `posterFileId` / `backdropFileId` / `avatarFileId`；封面与海报全部走代理 | 视图断言 + 既有 M1–M4 用例 |
| 内容安全与版权 | 上传必须签署当前声明版本（`vd_upload_declaration` 记版本与时间）；来源必填（`ORIGINAL/AUTHORIZED/ROYALTY_FREE`）；版权下架原因必填 ≤500（40017）；电影授权来源 / 编号 / 到期必填（40011）；`@AuditLog` 留痕 | 未签声明 40007、无原因 40017、票据与流式拒绝 |
| 播放票据 | 绑定 `tenantId + userId + contentId`，TTL 300s、refresh 240s；每次开流复核可播态；缺票 / 过期 / 跨用户 / 跨内容全部 40302；不可播 40303 | `bw-video-m5-smoke.ps1` §3/§5 |
| 限流防刷 | 网关六条路径阈值（§一）；超阈值 429 + `Result` 体 | `bw-video-gateway-rate-limit-smoke.ps1`（§7.1） |
| 异常用户封禁 | 复用 auth `PUT /user/{id}/status`（`sys:user:status`）：停用即撤销存量令牌，视频侧接口立即 401/403 | 复用既有能力 |
| 内网通道 | `/video/internal/**` 网关 403；`whale-file` 内容接口需内网令牌头 | 网关脚本断言 |
| 流式响应头 | `X-Content-Type-Options: nosniff`、`Cache-Control: private, no-store`，避免媒体被中间缓存或嗅探为 HTML | `4.nosniff` / `4.cacheControl` |
| 审计 | 内容发布 / 下架 / 版权下架 / 声明签署 / 电影入库 / 评论审核 / 创作者停用均 `@AuditLog`（不含正文） | audit_log / whale-audit |

已知取舍：

- **magic bytes 回落**：文件头无法识别（`detectFormat` 返回 null）时按声明 MIME 回落，
  由单测 `shouldFallbackToMimeWhenHeadUnknown` 固化。影响：纯文本伪装成 `video/mp4` 可被
  文件服务接收，但该文件不可解码、无法通过前端播放；真正的「头与声明不一致」仍拒绝
  （如 WebM 头 + `video/mp4` → 40003）。后续可加 ffprobe 深度校验。
- **举报入口（M5-3 可选）**：本期未实现，错误码 40018 已预留；运营侧以「评论审核 +
  下架 / 版权下架」闭环替代。
- **图片与流式网关鉴权**：`<img>` 与 `<video>` 无法携带自定义头，当前依赖网关 JWT 与
  部署层（同源 / 签名 URL / 票据）方案，待部署确认。

### 7.1 429 限流验证方法

```powershell
# 直连网关 8080；限流先于 JWT，无需真实令牌
$env:GATEWAY_BASE='http://127.0.0.1:8080'
.\scripts\smoke\bw-video-gateway-rate-limit-smoke.ps1
```

要点：

- 每条规则使用独立客户端 IP（`X-Forwarded-For`，脚本随机 `10.a.b.101..105` 网段），
  避免与真实客户端 / 上一次运行的窗口串扰，并验证 `pathPattern` 不串键。
- 每条规则发起 `limit + 1` 次请求，断言「阈值内无 429、第 `limit+1` 次 429」。
- 429 body 必须是 `{"code":429,"message":"请求过于频繁，请稍后重试","data":null}` 且
  `Content-Type: application/json`（前端据此提示，而非吞成 500）。
- 网关 `actuator/health` 不可用时脚本以退出码 2 结束，不误判为通过。

---

## 八、日常运维

### 8.1 每日巡检

1. `up{job="whale-video"}`、5xx 占比、SLO-9/10/11。
2. `hikaricp_connections_pending` 是否长期 > 0（连接池 5 不够或有慢 SQL）。
3. 定时任务日志：播放计数回刷（每 5 分钟）、授权到期下线（03:10）、观看历史清理（04:40）。
4. 上传失败分布：40003 / 40004 / 40005 / 40006 的占比（前端编码规范或 `whale-file` 配额问题）。
5. `whale-file` 存储增长（视频字节按上传量线性增长）；`vd_watch_history`、`vd_comment`、
   `audit_log` 增长；磁盘余量。
6. 票据 40302 比例（客户端刷新逻辑或 TTL 偏短）。

### 8.2 定时任务

| 任务 | 调度 | 行为 | 幂等 / 多实例 |
|---|---|---|---|
| 播放计数回刷 | `0 */5 * * * ?` | Redis 增量 → `vd_content.play_count = play_count + delta` | 增量 `GETDEL` 不重复取；多实例竞争同一 key 只会少计数，不会重复加 |
| 电影授权到期下线 | `0 10 3 * * ?` | 扫已发布且 `license_expire_at <= now` 的电影（单批 200）→ `OFFLINE` + 删可播缓存 | 幂等；单条失败只 `log.warn` |
| 观看历史清理 | `0 40 4 * * ?` | `watched_at < now - historyRetentionDays` 分批删除（500/批，最多 20 批/次） | 幂等；剩余数据次日继续；异常只 `log.warn` |

> 三个任务都在无租户上下文的后台线程执行，租户插件对空上下文不加条件，
> 因此一次覆盖全部租户。**多实例会重复执行**，但均为幂等设计；
> 若需严格单实例，可在部署层只在一个实例设置 `SPRING_PROFILES_ACTIVE` 差异开关（当前未要求）。

### 8.3 Redis Key

| Key | 用途 | TTL |
|---|---|---|
| `whale:video:play:ticket:{ticket}` | 播放票据（`tenantId|userId|contentId`） | 300s |
| `whale:video:playable:{tenantId}:{contentId}` | 可播状态缓存 | 30s |
| `whale:video:play:count:{contentId}` | 播放计数增量 | 待 5 分钟回刷 |
| `whale:video:home:{tenantId}:{section}` | 首页 / 榜单聚合缓存（JSON） | 60s + 0–10s 抖动 |
| `whale:video:cache:feed:{tenantId}:{section}` | feed 首屏缓存（按类型 / 排序分区） | 30s |

全部可重建，Redis 重启只影响进行中的播放票据与部分计数增量。

### 8.4 常见排障

| 现象 | 排查方向 |
|---|---|
| 播放 40302「票据无效或已过期」 | 票据 300s 过期未刷新、跨用户 / 跨内容复用、服务重启后 Redis 被清 |
| 播放 40303「内容当前不可播放」 | 未发布 / 私密他人 / 版权下架 / 电影授权到期；复核可播状态缓存是否已失效 |
| 上传 40003 | MIME 不在白名单，或 magic bytes 与声明不一致（WebM 头 + mp4 声明）；核对文件真实编码 |
| 上传 40004 | 封面 / 海报非 JPEG / PNG / WebP |
| 上传 40005 / 40006 | 超类型大小上限；`fileId` 不可用 / 已删除 / 跨租户；确认 `whale-file` 已提交分片 |
| 上传 40007 / 40008 | 未签声明；声明版本升级后需重新签署（`declaration-version`） |
| 上传 40009 / 40010 | 未声明内容来源；来源取值非法 |
| 电影 40011 | 授权来源 / 编号 / 到期缺失或到期不晚于当前；编辑需重新提交授权 |
| 下架 40017 | 版权下架未填原因（前端抽屉必填） |
| 详情 / 封面 404 | 私有内容非本人、未发布、已删除、跨租户、片单草稿、无封面（统一 404，不区分原因） |
| 流式 416 | 前端 Range 超出文件长度；核对 `whale-file` 返回的 `Content-Length` 与 `Content-Range` |
| 流式被截断 | 网关是否误把 `/video/stream/**` 加入熔断名单；Nginx `proxy_read_timeout`；客户端整体超时 |
| 流式首字节变慢 | `whale-file` 磁盘 / 对象存储 IO、本机并发；对比 `/video/stream/{contentId}` 与 `file/internal` 直方图 |
| 首页 / 详情 P95 抖动 | 缓存被发布 / 下架频繁失效；核对发布事件频率与 `content-cache-seconds` |
| 评论 40014 / 40015 | 正文空或 > 500 字；父评论不存在 / 不可见 |
| 限流 429 | 命中网关六条阈值；按业务量调整 `WHALE_GATEWAY_RATE_LIMIT_VIDEO_*` 并用 §7.1 脚本复验 |
| 电影到期后仍可播 | 票据 300s 未过期前的边界；核对可播缓存 30s、授权到期任务是否执行 |
| 定时任务未生效 | 检查 `@EnableScheduling`、实例时区（cron 为 Asia/Shanghai）、日志关键字 |

---

## 九、容量与性能

### 9.1 验收阈值与脚本

`scripts/smoke/bw-video-m5-load.ps1`（本机直连 8098，不经过网关）：

| 路径 | 指标 | 阈值（环境变量可覆盖） |
|---|---|---|
| `/video/meta` | 顺序 P95 | 200ms（`META_P95_MS`） |
| `/video/home` | 顺序 + 50 并发 P95 | 300ms（`HOME_P95_MS`） |
| `/video/content/{id}` | 顺序 + 50 并发 P95 | 250ms（`DETAIL_P95_MS`） |
| `/video/stream/{contentId}` | 50 并发首字节 P95 | 400ms（`STREAM_TTFB_P95_MS`） |

其他参数：`CONCURRENCY=50`、`PERF_ITERATIONS=20`、`PERF_ROUNDS=3`（并发取三轮 P95 中位数）。

> **实机数据待环境**：本轮（2026-11-01）因 MySQL / Redis / Nacos / `whale-file` 与本机
> 凭据不可用，未产生实测数字；验收时以脚本输出与 Grafana 直方图为准。

### 9.2 测量方法论（勿踩）

脚本内嵌 C# 探针：**共享 HttpClient + `TaskCompletionSource` 异步栅栏 + 每请求独立
Stopwatch + `ThreadPool.SetMinThreads`**，`ResponseHeadersRead` 到响应头即停表
（流式等价首字节）。另需：每路径先跑一轮预热并丢弃、轮间留 1 秒、多轮取 P95 中位数。
早期用 Runspace / 每线程新建 HttpClient 会把 50 并发抬到数百毫秒，属客户端自身开销
（用 `/actuator/health` 对照即可证伪）。

### 9.3 扩容建议

- 先纵向（容器内存 70–75% 给堆），再横向；服务无会话粘性，可任意多实例。
- 连接池 5：实例数 × 5 控制在 MySQL `max_connections` 80% 以内。
- 视频为两跳直传（`whale-video` → `whale-file`），带宽 = 并发 × 码率；生产建议视频走
  MinIO 独立桶 + CDN，并开启 Range 缓存。
- 流式不参与熔断，长视频依赖客户端多次 Range；网关限流按「播放会话」估算阈值
  （120 次/分钟 ≈ 多段拖动 + 重连余量）。

---

## 十、审计与数据保留

| 数据 | 保留 | 说明 |
|---|---|---|
| 观看历史 `vd_watch_history` | 180 天 | 每日 04:40 分批清理（`history-retention-days` 可调） |
| 评论 `vd_comment` | 长期 | 逻辑删除 / 状态流转（审核可追溯），不物理删除 |
| 上传声明 `vd_upload_declaration` | 长期 | 版本 + 签署时间可追溯，不随内容删除 |
| 版权下架记录 | 长期 | `copyright_status=TAKEDOWN` + `takedown_reason` + `takedown_at` + `@AuditLog` |
| 电影授权 `vd_movie` | 长期 | 授权来源 / 编号 / 到期；到期自动下线但记录保留 |
| 播放票据 / 可播缓存 / 计数 / 首页与 feed 缓存 | 300s / 30s / 5min / 30–60s | Redis，可重建 |
| `audit_log` / `api_log` | 由 `whale-audit` 统一策略 | 本服务不单独清理 |
| 视频字节与封面 / 海报 | 与业务数据同批次备份 | `whale-file`（local 磁盘或 MinIO `whale-video` 桶） |

> 审计上报不含正文：仅资源 ID / 标题 / 状态 / 操作人；票据与 `fileId` 不入日志。

---

## 十一、上线检查清单

- [x] 代码与静态验证：`mvn -o -pl whale-backend/whale-services/whale-video test` **151/151**、Checkstyle **0**、ArchUnit **5/5**。
- [x] Flyway `V1__baseline.sql` / `V2__series.sql` / `V3__movie_collection.sql` / `V4__watch_history_cleanup_index.sql` 就绪。
- [x] auth `migration-5.77-video.sql`（权限 305–322、角色 25105/25106、菜单 46–52）就绪且幂等。
- [x] 网关：`/video/**` 路由、`/video/internal/**` 403、六条限流规则、流式不进熔断名单。
- [x] `whale-file` G1 上传策略（`sys:video:upload` 2GB/8GB、`sys:video:movie:manage` 4GB/不限）。
- [x] 监控：Prometheus `whale-video` job（部署侧 + Docker 侧）、Grafana `bluewhale-video`（两份副本）、SLO-9/10/11 写入 `slo.md`。
- [x] 备份恢复演练脚本 `scripts/ops/video-backup-restore.ps1`（19 表 / V4 索引 / 版权列 / 片单列 / 行数）。
- [x] 冒烟脚本就绪并通过 PowerShell 5.1 语法校验：`bw-video-m1`～`m5`、`bw-video-m5-load`、`bw-video-gateway-rate-limit-smoke`。
- [x] CI 矩阵：Backend CI、前端矩阵、Docker 镜像矩阵、Nginx 前缀矩阵均含 `video`。
- [x] 前端 `pnpm --filter whale-video lint` / `build` 通过（M4 收口验证）。
- [ ] **实机项（环境不具备）**：建库 + Flyway 实跑、`migration-5.77-video.sql` 实跑、8098 / 8085 起服、M1–M5 冒烟、`bw-video-m5-load` 实测、网关 429 实测、授权到期任务实测、备份恢复演练实跑、8114 浏览器 E2E。
- [ ] 部署层确认：图片代理（封面 / 海报 / 背景 / 头像）与 `/video/stream/**` 的网关鉴权 / 签名方案。
- [ ] `logging.level.com.bluewhale` 生产置 `info` 并确认日志无票据、无正文。

---

## 十二、回滚

1. **功能降级优先**：`WHALE_VIDEO_COMMENT_PRE_AUDIT=false`、调高限流阈值、调低
   `WHALE_VIDEO_HISTORY_RETENTION_DAYS` 等开关可即时降级，无需回滚数据库。
2. **应用回滚**：回退到上一镜像 / 上一 jar。`V4` 索引、`vd_series`、`vd_collection(_item)`
   与版权列均为新增，旧版本不读取，**不要**在回滚时删索引 / 删表。
   注意：回滚后系列 / 电影 / 片单相关接口不可用（前端入口会 404）。
3. **限流回滚**：调整 `WHALE_GATEWAY_RATE_LIMIT_VIDEO_*` 或临时下线对应
   `path-rules` 条目（需网关重启）。
4. **数据回滚**：仅在确认无新版本写入后按 §5.2 恢复；Redis 无需回滚（可重建）；
   `whale-file` 的视频字节需与数据库同批次回滚，否则出现 40006 / 图片 404。
5. **声明版本回滚**：把 `declaration-version` 改回旧版本即恢复旧签署可用性；
   已记版本留痕不删（新版本签署仍需重新提交）。
