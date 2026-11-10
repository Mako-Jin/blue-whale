# 音乐平台服务运维手册（whale-music）

> 适用版本：Wave 5.75（M0–M5）
> 关联计划：[wave5.75-music-platform.md](../plans/wave5.75-music-platform.md)
> 关联报告：[M5 检查报告](../reviews/2026-10-31-wave5.75-music-m5.md)
> 关联 SLO：[slo.md](slo.md)（SLO-6/7/8）
> 关联监控：[grafana/dashboards/bluewhale-music.json](../../deploy/monitoring/grafana/dashboards/bluewhale-music.json)

---

## 一、服务坐标

| 项 | 取值 |
|---|---|
| 模块 | `whale-backend/whale-services/whale-music` |
| 服务名 | `whale-music` |
| 端口 | `8096`（`SERVER_PORT`） |
| 数据库 | `bluewhale_music`（MySQL 8，`utf8mb4`） |
| 包根 | `com.bluewhale.music`（interfaces / application / domain / infrastructure 四层） |
| 对外前缀 | `/music/**`（网关不 StripPrefix） |
| 内网前缀 | `/music/internal/**`（网关 `internal-deny` 统一 403） |
| 前端 | `whale-frontend/platform-apps/whale-music`，qiankun base `/music`，本地 dev 端口 8111 |
| Redis | `whale:music:*` |
| 权限码 | `sys:music:*`（281–304，24 项）；申诉复核 `sys:music:appeal:audit`(304) |
| 角色 | `music_admin`（25103，含 304）、`music_member`（25104，不含 304 以防自审自批） |
| 菜单 | 45「版权申诉」（parent 39，path `/music/appeals`，component `music/appeals`） |
| 上游依赖 | `whale-file`（音频字节与文件元信息）、`whale-message`（可选通知，旁路） |
| 镜像 | 后端 `ghcr.io/<repo>/whale-music`；前端 `ghcr.io/<repo>/whale-frontend/whale-music` |

网关路由（`whale-backend/whale-services/whale-gateway/src/main/resources/application.yml`）：

- `whale-music`：`lb://whale-music`，`Path=/music/**`。
- 内网屏蔽：`/music/internal/**`（403；当前服务尚未暴露内网端点，规则先行为后续预留）。
- 限流（`rate-limit.path-rules`，超阈值 429 + `Result` 体）：

| 路径 | 阈值 | 窗口 | 环境变量（阈值 / 窗口） |
|---|---|---|---|
| `/music/stream/**` | 120 | 60s | `WHALE_GATEWAY_RATE_LIMIT_MUSIC_STREAM` / `..._WINDOW_SECONDS` |
| `/music/song/*/comment` | 20 | 60s | `WHALE_GATEWAY_RATE_LIMIT_MUSIC_COMMENT` / `..._WINDOW_SECONDS` |
| `/music/play/ticket` | 60 | 60s | `WHALE_GATEWAY_RATE_LIMIT_MUSIC_TICKET` / `..._WINDOW_SECONDS` |
| `/music/song/*/like` | 30 | 60s | `WHALE_GATEWAY_RATE_LIMIT_MUSIC_LIKE` / `..._WINDOW_SECONDS` |

---

## 二、构建、部署与启停

### 2.1 后端构建

```powershell
mvn -q -o -pl whale-services/whale-music -am -DskipTests package
```

产物：`whale-backend/whale-services/whale-music/target/whale-music.jar`。

### 2.2 后端启动

```powershell
$env:MYSQL_PASSWORD='***'
$env:SPRING_DATA_REDIS_PASSWORD='***'   # 如 Redis 开启口令
java -jar whale-backend/whale-services/whale-music/target/whale-music.jar
```

必需外部依赖：MySQL、Redis、Nacos；对外链路还需要网关（8080）与 auth（8081）；
音频流与文件校验还需要 `whale-file`（8085）。

### 2.3 容器构建（与 CI 一致）

后端复用参数化模板 `deploy/backend/sample/Dockerfile`：

```powershell
docker build -f deploy/backend/sample/Dockerfile \
  --build-arg MODULE_PATH=whale-services/whale-music \
  --build-arg SERVICE_PORT=8096 \
  -t bluewhale/whale-music .
```

容器内堆参数默认 `-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport`；端口 8096。

前端：

```powershell
docker build -f deploy/frontend/Dockerfile \
  --build-arg APP_NAME=whale-music \
  --build-arg APP_DIR=platform-apps/whale-music \
  --build-arg APP_BASE=/music \
  -t bluewhale/whale-fe-music .
```

> 同名冲突（后端服务也叫 `whale-music`）由 CI 命名空间规避：前端推送到
> `ghcr.io/<repo>/whale-frontend/whale-music`，后端推送到 `ghcr.io/<repo>/whale-music`，
> 缓存 scope 分别为 `fe-whale-music` / `be-whale-music`。
> 部署契约由 `scripts/ci/bw-deploy-contract-check.ps1` 在 `docker-deploy.yml` 的
> preflight job 中强制校验。脚本自收口轮起兼容 PowerShell 5.1 与 7：`${{ }}` 改为
> `$usd = [char]36` 拼接，文件以 **UTF-8 with BOM** 保存（无 BOM 的中文在 5.1 下按 ANSI 解码会解析失败）。

### 2.4 前端构建

```powershell
cd whale-frontend
pnpm --filter whale-music lint
pnpm --filter whale-music build
```

产物 `whale-frontend/platform-apps/whale-music/dist`，按 qiankun 子应用部署到 `/music`；
Nginx 侧由 `infrastructure/docker/nginx/conf.d/micro-frontend.conf` 的
`^/(...|music)(?=/|$)` 前缀转发到容器别名 `whale-fe-music:80`。

### 2.5 健康检查

```powershell
curl http://127.0.0.1:8096/actuator/health
curl http://127.0.0.1:8096/actuator/prometheus
curl -H "Authorization: Bearer <token>" http://127.0.0.1:8080/music/meta
curl -H "Authorization: Bearer <token>" http://127.0.0.1:8080/music/home
```

`/music/meta` 返回服务名、租户、用户与前端需要的上传策略（音频 / 封面 MIME 白名单、
单文件上限、协议版本），前端据此做上传前置校验。

---

## 三、配置项

所有业务配置前缀 `whale.music.*`，环境变量前缀 `WHALE_MUSIC_*`。

### 3.1 上传与版权

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `agreement-version` | `WHALE_MUSIC_AGREEMENT_VERSION` | `V1` | 当前《版权无争议协议》版本；上传必须签署该版本 |
| `agreement-title` | `WHALE_MUSIC_AGREEMENT_TITLE` | 音乐作品版权无争议协议 | 协议标题 |
| `upload-max-bytes` | `WHALE_MUSIC_UPLOAD_MAX_BYTES` | 104857600（100MB） | 单文件上限（音频 / 封面） |
| `audio-formats` | `WHALE_MUSIC_AUDIO_FORMATS` | `audio/mpeg,audio/aac,audio/flac` | 音频 MIME 白名单（逗号分隔） |
| `cover-formats` | `WHALE_MUSIC_COVER_FORMATS` | `image/jpeg,image/png,image/webp` | 封面 MIME 白名单 |

### 3.2 播放与流式

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `play-ticket-ttl-seconds` | `WHALE_MUSIC_PLAY_TICKET_TTL_SECONDS` | 120 | 票据 TTL；一次播放的多次 Range 复用同一票据 |
| `stream-chunk-bytes` | `WHALE_MUSIC_STREAM_CHUNK_BYTES` | 1048576 | 上游读取块大小 |
| `play-history-retention-days` | `WHALE_MUSIC_PLAY_HISTORY_RETENTION_DAYS` | 90 | 播放历史保留天数（清理任务读取） |

### 3.3 互动与集成

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `comment-pre-audit` | `WHALE_MUSIC_COMMENT_PRE_AUDIT` | false | false=先发后审 + 审核队列；true=先审后发 |
| `comment-notify-enabled` | `WHALE_MUSIC_COMMENT_NOTIFY_ENABLED` | true | 评论回复 / 审核结果通知（旁路，失败仅告警） |
| `playlist-max-songs` | `WHALE_MUSIC_PLAYLIST_MAX_SONGS` | 500 | 单歌单歌曲上限 |
| `file-service-name` | `WHALE_MUSIC_FILE_SERVICE` | `whale-file` | 文件服务 Nacos 名 |

### 3.4 基础设施与观测

| 项 | 默认 | 说明 |
|---|---|---|
| `SERVER_PORT` | 8096 | 服务端口 |
| `DB_POOL_MAX_SIZE` | 5 | Hikari 上限（按实例数换算，见 §9.4） |
| `DB_POOL_LEAK_DETECTION_MS` | 60000 | 连接泄漏检测门槛 |
| `REDIS_HOST` / `REDIS_PORT` | 127.0.0.1:6379 | 票据 / 计数 / 首页缓存 |
| `NACOS_SERVER` | 127.0.0.1:8848 | 注册与配置中心 |
| `management.endpoints.web.exposure.include` | health,info,prometheus | 仅暴露三项，不含 env/beans |
| `management.metrics.distribution.percentiles-histogram.http.server.requests` | true | **SLO-6/7/8 依赖该直方图桶**，不可关闭 |
| `logging.level.com.bluewhale` | debug | **生产必须改为 `info`**：debug 会打印请求参数，既放大部分日志正文也增加 IO 开销。落地方式见 §6.4 |

---

## 四、数据库与迁移

### 4.1 表清单

`whale-backend/whale-services/whale-music/src/main/resources/db/init.sql` 建 14 张表：

| 分组 | 表 |
|---|---|
| 曲库 | `mu_artist`、`mu_album`、`mu_song`、`mu_tag`、`mu_song_tag` |
| 版权 | `mu_copyright_agreement`、`mu_song_appeal` |
| 歌单 | `mu_playlist`、`mu_playlist_song` |
| 互动 | `mu_song_like`、`mu_play_history`、`mu_song_comment` |
| 审计 | `audit_log`、`api_log` |

`mu_play_history` 的 `idx_mu_history_played(played_at)` 支撑「近 7 天热榜」聚合，不可删除。

### 4.2 迁移执行顺序

1. 建库：`CREATE DATABASE IF NOT EXISTS bluewhale_music CHARACTER SET utf8mb4;`
2. `whale-music`：新库执行 `db/init.sql`；存量库依次执行
   `db/migration-5.75-m5.sql`（幂等加列 `mu_song.copyright_source`）与
   `db/migration-5.75-m5-appeal.sql`（建 `mu_song_appeal` + `idx_mu_history_played`，幂等）。
3. `whale-service-auth`：`db/migration-5.75-music.sql`（权限 281–303、角色
   `music_admin`/`music_member`、菜单与授权；幂等 `WHERE NOT EXISTS`），再执行
   `db/migration-5.75-m5-appeal.sql`（权限 304、`music_admin` 授权、菜单 45；幂等）。
4. 校验：

```sql
SELECT COUNT(*) FROM information_schema.tables
 WHERE table_schema='bluewhale_music' AND table_name IN
 ('mu_artist','mu_album','mu_song','mu_copyright_agreement','mu_tag','mu_song_tag',
  'mu_playlist','mu_playlist_song','mu_song_like','mu_play_history','mu_song_comment',
  'mu_song_appeal','audit_log','api_log');            -- 期望 14

SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema='bluewhale_music' AND table_name='mu_song' AND column_name='copyright_source';

SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema='bluewhale_music' AND table_name='mu_song_appeal'
   AND column_name IN ('status','review_opinion','reviewer_id');   -- 期望 3

SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
 WHERE table_schema='bluewhale_music' AND table_name='mu_play_history'
   AND index_name='idx_mu_history_played';             -- 期望 1

SELECT COUNT(*) FROM sys_permission WHERE id BETWEEN 281 AND 304 AND deleted = 0;  -- 期望 24
```

> 申诉状态机：`PENDING → APPROVED | REJECTED`；复核通过仅把 `copyright_status` 恢复为
> `VALID` 并清除 `takedown_reason`，`publish_status` 保持 `OFFLINE`，**必须由运营重新上架
> 才会复播**。

---

## 五、备份与恢复

### 5.1 备份与恢复演练脚本

```powershell
# 备份 + 导入独立校验库 + 断言核心表 / 索引 / 申诉列 / 行数一致性
$env:MYSQL_PASSWORD='***'
.\scripts\ops\music-backup-restore.ps1
```

参数（均有默认值）：

| 参数 | 默认 | 说明 |
|---|---|---|
| `-Database` | `bluewhale_music` | 源库 |
| `-VerifyDatabase` | `bluewhale_music_restore_check` | 校验库（默认演练结束删除） |
| `-BackupDir` | `logs/backup` | 备份文件目录 |
| `-MysqlBin` | `D:\mysql8\bin`（可用 `MYSQL_BIN_DIR` 覆盖） | `mysqldump` / `mysql` 所在目录 |
| `-KeepRestoreDatabase` | 关 | 保留校验库供人工核对 |

断言项：核心表 **14** 张齐全；`idx_mu_history_played` 存在；`mu_song_appeal` 含
`status / review_opinion / reviewer_id` 三列；`mu_song` 等核心表源库与校验库行数一致。
成功输出 `RESULT: PASSED`。

手工等价命令：

```powershell
mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF `
  -uroot -p"$env:MYSQL_PASSWORD" bluewhale_music `
  > logs/backup/bluewhale_music-$(Get-Date -Format 'yyyyMMdd-HHmmss').sql
```

### 5.2 生产恢复步骤

1. 停 `whale-music` 实例（网关摘流或直接停进程），避免恢复期间写入。
2. 新建 `bluewhale_music_new` 导入备份，执行 §4.2 校验 SQL。
3. 校验 14 表齐全、`mu_song.copyright_source` 列存在、`mu_song_appeal` 三列存在、
   auth 侧权限 24 项存在后切换库名。
4. 启动服务并检查 `/actuator/health`、`/music/meta`、首页与详情。
5. Redis 数据全部可重建，**无需恢复**：
   - `whale:music:play:ticket:*`（120s）、`whale:music:play:count:*`（待回刷增量）、
     `whale:music:home:{tenantId}`（60s 缓存）。
   - 丢失的播放计数增量会丢少量计数（最多 5 分钟窗口），不影响可播状态。

---

## 六、监控、告警与 SLO

### 6.1 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `http_server_requests_seconds_*` | Timer + 直方图桶 | 全部 REST 接口；SLO-6/7/8 的数据源 |
| `hikaricp_connections_*` | Gauge | 连接池 active/idle/pending/max |
| `jvm_memory_used_bytes` / `jvm_gc_*` | Gauge / Counter | 堆与 GC |
| `up{job="whale-music"}` | Gauge | 抓取存活 |

> 本服务**没有自定义业务指标**（播放计数在 Redis，热榜为 `mu_play_history` 近 7 天实时聚合），排障以 HTTP 直方图、Hikari、JVM + 业务日志为主。

Prometheus：`deploy/monitoring/prometheus.yml` 的 `whale-music` job（`127.0.0.1:8096`，
`metrics_path=/actuator/prometheus`）。

Grafana 面板（12 个 panel，uid `bluewhale-music`）：

- `deploy/monitoring/grafana/dashboards/bluewhale-music.json`（数据源 uid `prometheus`）
- `infrastructure/prometheus/grafana/dashboards/bluewhale-music.json`（数据源 uid `bluewhale-prometheus`，Docker 侧副本）

面板内容：首页 / 详情 / 流式首字节 P95（SLO-6/7/8）、`up`、端点 QPS 与 P95 趋势、5xx 占比、
Hikari（active/idle/pending/max）、JVM 堆使用率。

### 6.2 SLO（PromQL 见 [slo.md](slo.md)）

| ID | 目标 | PromQL 核心 |
|---|---|---|
| SLO-6 | 首页 P95 < 300ms | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/music/home",application="whale-music"}[5m])))` |
| SLO-7 | 详情 P95 < 200ms | 同上，`uri="/music/public/song/{id}"` |
| SLO-8 | 流式首字节 P95 < 300ms | 同上，`uri="/music/stream/{songId}"` |

### 6.3 告警

复用通用告警：`ServiceDown`（`up{job="whale-music"} == 0`）、`Http5xxRateHigh`、`JvmHeapHigh`。
建议补充：

```yaml
- alert: MusicStreamP95Slow
  expr: histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="whale-music",uri="/music/stream/{songId}"}[10m]))) > 0.3
  for: 10m
- alert: MusicHikariSaturated
  expr: hikaricp_connections_pending{application="whale-music"} > 0
  for: 5m
```

### 6.4 日志

- **生产日志级别落地方式**（三选一，作用等价）：
  1. 环境变量：`LOGGING_LEVEL_COM_BLUEWHALE=info`（Spring Boot relaxed binding 映射为
     `logging.level.com.bluewhale`，容器 / systemd / Windows 服务均可用）。
  2. 命令行属性：`--logging.level.com.bluewhale=info`（WMI、`Start-Process` 等无法透传
     环境变量的场景使用）。
  3. （可选改造）将 `application.yml` 改为
     `logging.level.com.bluewhale: ${LOGGING_LEVEL_COM_BLUEWHALE:info}`，让默认值即生产值。
     当前仓库默认仍为 `debug`（本地排障便利），生产部署须用 1 或 2 显式覆盖。
- 验证：启动参数中确认生效（日志启动 banner 或 `--debug`），再用一次请求确认输出中无
  `DEBUG` 行；本机脚本 `scripts/dev/run-music.ps1` 默认即 `info`
  （`/actuator/loggers` 未暴露，仅开放 health/info/prometheus）。
- `debug` 仅用于本地排障（打印请求参数，既放大日志正文也增加 IO 开销）。
- **日志不含正文**：评论内容、歌词正文、协议全文均不入日志；审计上报只带资源 ID / 标题 / 状态。
- 媒体流不打印字节内容；仅记录歌曲 ID、Range 区间、状态码。

---

## 七、安全硬化（M5）

| 项 | 措施 | 验收 |
|---|---|---|
| 越权矩阵 | 跨租户、私有歌单、自听作品、他人评论、附件 `fileId` 统一走「存在性不泄露」判定：不可见一律 404（40403/40405/40406），越权写 403 | `bw-music-m5-smoke.ps1` 全部断言 |
| 上传白名单 | `UploadPolicy` 校验 MIME ∈ `audio-formats` / `cover-formats`，不可用文件 40019，超上限 40018 | 非法类型 / 超限 400 |
| 文件 IDOR | 详情/公开视图不返回 `audioFileId` / `lyricFileId`；取文件元信息必须同租户 | `1.noAudioFileId` 断言 |
| 版权合规 | 上传必须签署当前协议版本（`mu_copyright_agreement` 记版本与签署时间）；`copyright_source` 必填（ORIGINAL / LICENSED / PUBLIC_DOMAIN 等）；`TAKEDOWN` 需原因并强制 `OFFLINE` + 审计留痕 | 未签署不可上传、无原因 40005、下架后票据失效 |
| 版权申诉 | 上传者对 `TAKEDOWN` 作品提交理由（`POST /music/song/{id}/appeal`，`sys:music:upload`）；运营复核（`sys:music:appeal:audit`）通过 → 版权 `VALID` + 清空 `takedown_reason` + 保持 `OFFLINE`（需重新上架复播）；驳回保持 `TAKEDOWN` | `bw-music-m5-smoke.ps1` §6.1/§6.2 断言 |
| 播放票据 | 绑定 `tenantId|userId|songId`，TTL 120s，**每次流式请求复核可播态**；缺票 / 换用户 / 换歌曲 / 下架全部拒绝 | 5./6. 断言 |
| 限流防刷 | 网关四类路径阈值（§一）；超阈值 429 + `Result` 体 | `bw-gateway-rate-limit-smoke.ps1` 实机验证（§7.1） |
| 异常用户封禁 | 复用 auth `PUT /user/{id}/status`（`sys:user:status`）：停用即撤销存量令牌，音乐侧所有接口立即 401/403 | 复用既有能力 |
| 内网通道 | `/music/internal/**` 网关 403；`whale-file` 内容接口需内网令牌头 | `4.internalDeny` |
| 审计 | 曲库写操作、协议签署、下架、评论审核均 `@AuditLog` 留痕（不含正文） | audit_log / whale-audit |

生产建议：

- `whale.music.comment-notify-enabled`、`comment-pre-audit` 按运营策略固定，不随请求可变。
- 单文件上限与前端提示保持一致（`/music/meta` 已下发），避免「前端放行、后端 40018」。
- 协议版本升级（如 `V2`）时旧签署不自动继承，需重新签署；下架与申诉记录长期保留
  （`mu_song_appeal` + `@AuditLog`），复核意见一并落库。

### 7.1 429 限流验证方法

```powershell
# 直连网关 8080，无需 JWT（限流过滤器 ORDER = HIGHEST_PRECEDENCE + 10，先于 JWT）
$env:GATEWAY_BASE='http://127.0.0.1:8080'
.\scripts\smoke\bw-gateway-rate-limit-smoke.ps1
```

要点：

- `clientId` 取 `X-Forwarded-For` 首段，脚本每轮随机网段（`10.a.b.101..105`）隔离计数，
  避免与真实客户端 / 上一次运行的窗口串扰。
- 每条规则发起 `limit + 1` 次请求，断言「阈值内无 429、第 `limit+1` 次 429」。
- 窗口为 Redis 固定窗口（60s），脚本在窗口剩余 < 25s 时等待下一窗口，整批未命中则换窗口重试一次。
- 429 body 必须是 `{"code":429,"message":"请求过于频繁，请稍后重试","data":null}` 且
  `Content-Type: application/json`（前端据此提示，而非吞成 500）。
- 隔离规则：四条规则的 clientIp 互不相同，验证 pathPattern 不会串键。

---

## 八、日常运维

### 8.1 每日巡检

1. `up{job="whale-music"}`、5xx 占比、SLO-6/7/8。
2. `hikaricp_connections_pending` 是否长期 > 0（连接池 5 不够或有慢 SQL）。
3. 播放历史清理日志：`播放历史清理完成: retentionDays=90 ...`。
4. 播放计数回刷日志：`播放计数回刷完成: songs=..., total=...`（每 5 分钟）。
5. MySQL 磁盘；`mu_play_history`、`mu_song_comment`、`audit_log` 增长。
6. `whale-file` 存储目录增长（音频 / 封面按上传量线性增长）。

### 8.2 定时任务

| 任务 | 调度 | 行为 | 幂等 |
|---|---|---|---|
| 播放计数回刷 | `0 */5 * * * ?` | `GETDEL` 取增量 → `play_count = play_count + delta` | 是（增量不重复取） |
| 播放历史清理 | `0 0 3 * * ?` | 删除 `createTime < now - retentionDays`（默认 90 天） | 是（可重复执行） |

> 两个任务都在无租户上下文的后台线程执行，租户插件对空上下文不加条件，因此一次覆盖全部租户。
> 「榜单 7 天」：`GET /music/chart/hot` 按 `mu_play_history.played_at >= now()-7d` 聚合播放次数
> （JOIN `mu_song` 过滤 `deleted=0 / PUBLISHED / PUBLIC / 非 TAKEDOWN`），窗口内无数据时回落
> 累计 `play_count` 榜；新歌榜仍按 `published_at` 倒序 + 60s 缓存。**无 ZSET，不产生额外 Redis 键**。

### 8.3 Redis Key

| Key | 用途 | TTL |
|---|---|---|
| `whale:music:play:ticket:{ticket}` | 播放票据（`tenantId|userId|songId`） | 120s |
| `whale:music:play:count:{songId}` | 播放计数增量 | 待 5 分钟回刷 |
| `whale:music:home:{tenantId}` | 首页聚合缓存（JSON） | 60s + 0–10s 抖动 |

全部可重建，Redis 重启只影响进行中的播放票据与部分计数增量。

### 8.4 常见排障

| 现象 | 排查方向 |
|---|---|
| 播放返回 403xx / 无票据 | 票据 120s 过期、跨用户复用、歌已下架（`copyrightState=TAKEDOWN`） |
| 流式 416 | 前端 Range 超出文件长度；核对 `whale-file` 返回的 `Content-Length` |
| 流式首字节变慢 | `whale-file` 磁盘 IO、本机并发、`/music/stream` 直方图 vs `file/internal` 直方图对比 |
| 上传 40018 / 40019 | 超 `upload-max-bytes`；MIME 不在白名单；`fileId` 不可用或跨租户 |
| 上传提示未签协议 | 协议版本不一致（`agreement-version` vs 用户已签版本） |
| 首页 P95 抖动 | 缓存被发布事件频繁失效；核对发布 / 下架操作频率 |
| 详情 404 | 私有作品非本人、未发布、已删除、跨租户（均为 404，不区分原因） |
| 评论 40014 / 40015 | 正文空或 > 500 字；父评论不存在 / 不同歌曲 / 已隐藏 |
| 申诉 40022 / 40902 / 40903 | 理由为空或 > 500 字；同一作品已有 `PENDING` 申诉；作品不处于 `TAKEDOWN` |
| 申诉 40403 / 40301 | 非上传者：作品对当前用户不可见（跨租户 / 私有 / 下架）→ 40403；可见但非本人 → 40301 |
| 申诉通过后仍不能播放 | 预期行为：申诉通过只恢复版权为 `VALID`，作品仍 `OFFLINE`，需运营在曲库重新上架 |
| 限流 429 | 命中网关四类阈值；按业务量调整 `WHALE_GATEWAY_RATE_LIMIT_MUSIC_*`；用 §7.1 脚本复验 |

---

## 九、容量与性能

### 9.1 实测（2026-10-31，本机 8 核 + 6 JVM + MySQL + Redis + Nacos）

| 场景 | 方式 | 实测 P95 | 阈值 |
|---|---|---|---|
| 首页聚合 | 顺序 20 次 | 4.8–8.6ms（复验 6.8ms） | < 300ms |
| 歌曲详情 | 顺序 20 次 | 5.3–8.9ms（复验 8.9ms） | < 200ms |
| 流式首字节 | 50 并发 × 20 次 × 5 轮，取轮 P95 中位数 | 181.9–220.5ms（最差轮 296.1ms） | < 300ms |

- 15 轮并发样本全部 HTTP 200，`errors=0`；三次运行（含收尾复验）均通过三条 URI 直方图桶断言。
- 服务端两跳实测（同一时刻）：`whale-file` `/file/internal/{id}/content` 50 请求 sum 0.95s（mean 19.0ms）；
  `whale-music` `/music/stream/{songId}` 50 请求 sum 2.97s（mean 59.5ms）——客户端观测的 ~220ms
  大部分来自本机客户端排队，服务端未饱和。

### 9.2 测量方法论（勿踩）

`scripts/smoke/bw-music-m5-load.ps1` 内嵌 C# 探针：**共享 HttpClient + `TaskCompletionSource`
异步栅栏 + 每请求独立 Stopwatch + `ThreadPool.SetMinThreads(concurrency+20, 100)`**。

早期版本用 Runspace / 每线程新建 HttpClient / 顺序 `SendAsync`，客户端自身发起开销把 50 并发
抬到 400–700ms（用 `/actuator/health` 对照组同样 300ms 即可证伪）。另需：每路径先跑一轮预热并丢弃、
多轮取 P95 中位数。线上口径以 Prometheus 直方图 + Grafana 为准。

### 9.3 扩容建议

- 先纵向（容器内存 70–75% 给堆），再横向；服务无会话粘性，可任意多实例。
- 连接池 5：实测 `hikaricp_connections_acquire_seconds_max ≈ 0.023s`（2894 次获取），非瓶颈；
  实例数 × 5 控制在 MySQL `max_connections` 80% 以内。
- 流式为两跳直传，带宽 = 并发 × 码率；生产建议音频走对象存储（MinIO）+ CDN，并开启 Range 缓存。

---

## 十、审计与数据保留

| 数据 | 保留 | 说明 |
|---|---|---|
| 播放历史 `mu_play_history` | 90 天 | 每日 03:00 清理（`play-history-retention-days`，可回滚为更短/更长） |
| 评论 `mu_song_comment` | 长期 | 逻辑删除；隐藏为状态流转，不物理删除（审核可追溯） |
| 协议签署 `mu_copyright_agreement` | 长期 | 版本 + 签署时间可追溯，不随用户/歌曲删除 |
| 下架记录 | 长期 | `copyright_state=TAKEDOWN` + `takedown_reason` + `@AuditLog` |
| 申诉记录 `mu_song_appeal` | 长期 | 理由 / 状态 / 复核人 / 复核意见 / 复核时间；复核通过后 `takedown_reason` 清空，但申诉行保留可追溯 |
| 播放票据 / 计数 / 首页缓存 | 120s / 5min / 60s | Redis，可重建 |
| `audit_log` / `api_log` | 由 `whale-audit` 统一策略 | 本服务不单独清理 |

> 审计上报不含正文（评论内容、歌词、协议全文均不记录）；仅记录资源 ID / 标题 / 状态 / 操作人。

---

## 十一、上线检查清单

- [x] `bluewhale_music` 建库 + `init.sql`（或存量库 `migration-5.75-m5.sql`、`migration-5.75-m5-appeal.sql`）执行，§4.2 校验 SQL 通过（14 表 / 申诉 3 列 / `idx_mu_history_played`）。
- [x] auth `migration-5.75-music.sql` + `migration-5.75-m5-appeal.sql` 执行：权限 281–304（24 项）、角色 25103/25104、菜单 45 与授权（25103 含 304，25104 不含）。
- [x] 网关 `whale-music` 路由、`/music/internal/**` 403、四类限流规则生效，并用 §7.1 脚本实测 429。
- [x] `whale-file` 内网内容接口可用（音流两跳链路），内网令牌头已配置。
- [ ] 前端 `/music` 子应用可加载；`/music/meta` 下发的白名单与上限和前端一致；「我的作品」可见申诉入口，管理端「版权申诉」菜单可打开。（**部分验证**：`tsc`/`build`/`lint` 与 `/music/meta` 接口已通过；浏览器逐页点检受 B-09 阻塞）
- [x] `mvn -o -pl whale-services/whale-music -am test` 全绿（117/117）；Checkstyle 0；ArchUnit 5/5。
- [x] `scripts/smoke/bw-music-m2/m3/m4/m5-smoke.ps1` 全 `SMOKE-ALL-PASS`（m5 含申诉闭环）。
- [x] `scripts/smoke/bw-music-m5-load.ps1` 全绿（首页 / 详情 / 50 并发流式）。
- [x] Prometheus 抓取 `whale-music` UP，Grafana `bluewhale-music` 面板有数据，SLO-6/7/8 可查。
- [x] `logging.level.com.bluewhale` 生产置 `info`（`LOGGING_LEVEL_COM_BLUEWHALE=info` 或 `--logging.level.com.bluewhale=info`，见 §6.4）；确认日志无正文。
- [ ] Docker 部署契约 preflight 通过（`bw-deploy-contract-check.ps1`，PS 5.1 / 7 均可）：后端 23 / 前端 16 / 同名冲突 10 / nginx 10 前缀。（**部分验证**：本机以等价静态核对通过；脚本含 GitHub Actions 表达式，PS 5.1 无法解析，CI 由 pwsh 执行）
- [x] 备份策略与恢复演练（`scripts/ops/music-backup-restore.ps1` → `RESULT: PASSED`，§5.1）。
- [ ] 浏览器人工回归：登录 → 首页 → 搜索 → 播放（含拖动）→ 收藏 → 建歌单 → 评论 → 审核 → 申诉 → 下架（本机账号与登录态已验证，见 M5 报告 §4.9；联调口令禁止用于生产）。（**阻塞**：缺浏览器工具 B-09）

---

## 十二、回滚

1. **功能降级优先**：`WHALE_MUSIC_COMMENT_PRE_AUDIT=false`、`WHALE_MUSIC_COMMENT_NOTIFY_ENABLED=false`
   等开关可即时降级，无需回滚数据库。
2. **应用回滚**：回退到上一镜像 / 上一 jar；`mu_song.copyright_source`、`mu_song_appeal` 表
   与 `idx_mu_history_played` 均为 M5 新增，旧版本不读取，保持兼容（**不要**在回滚时删列 / 删表）。
   注意：回滚后热榜会退回旧口径（累计 `play_count`），申诉接口不可用（前端入口会 404）。
3. **限流回滚**：调高 `WHALE_GATEWAY_RATE_LIMIT_MUSIC_*` 或临时下线对应 `path-rules` 条目（需网关重启）。
4. **数据回滚**：仅在确认无新版本写入后按 §5.2 恢复；Redis 无需回滚（可重建）。
5. **协议版本回滚**：把 `agreement-version` 改回旧版本即可恢复旧签署可用性；已记版本留痕不删。
