# 在线文档 / Wiki 服务运维手册（whale-doc）

> 适用版本：Wave 5.74（M1–M5）
> 关联设计：[doc-service.md](../design/doc-service.md)
> 关联计划：[2026-10-29-wiki-doc-service.md](../plans/2026-10-29-wiki-doc-service.md)
> 关联 SLO：[slo.md](slo.md)

---

## 一、服务坐标

| 项 | 取值 |
|---|---|
| 模块 | `whale-backend/whale-services/whale-doc` |
| 服务名 | `whale-doc` |
| 端口 | `8095`（`SERVER_PORT`） |
| 数据库 | `bluewhale_doc`（MySQL 8，`utf8mb4`） |
| 包根 | `com.bluewhale.doc` |
| 对外前缀 | `/doc/**`（网关不 StripPrefix） |
| 内网前缀 | `/doc/internal/**`（网关 `internal-deny` 统一 403） |
| 前端 | `whale-frontend/platform-apps/whale-doc`，qiankun base `/doc` |
| Redis | `whale:doc:*` |
| 权限码 | `sys:doc:*`（251–279） |
| 角色 | `doc_manager`（25101）、`doc_member`（25102） |

网关路由（`whale-backend/whale-services/whale-gateway/src/main/resources/application.yml`）：

- `whale-doc-ws`：`lb:ws://whale-doc`，`Path=/doc/ws`，`order=-1`（WS 升级先于 REST）。
- `whale-doc`：`lb://whale-doc`，`Path=/doc/**`。
- 白名单：`/doc/ws`（协作 WS 依赖 60 秒一次性 ticket，URL 不携带 JWT）。
- 内网屏蔽：`/doc/internal/**`。

---

## 二、构建、部署与启停

### 2.1 后端构建

```powershell
mvn -q -o -pl whale-services/whale-doc -am -DskipTests package
```

产物：`whale-backend/whale-services/whale-doc/target/whale-doc.jar`。

### 2.2 后端启动

```powershell
$env:MYSQL_PASSWORD='***'
$env:SPRING_DATA_REDIS_PASSWORD='***'   # 如 Redis 开启口令
java -jar whale-backend/whale-services/whale-doc/target/whale-doc.jar
```

必需外部依赖：MySQL、Redis、Nacos（8080 网关与 8081 auth 用于对外链路）。

### 2.3 容器构建

```powershell
docker build -f deploy/backend/whale-doc/Dockerfile -t bluewhale/whale-doc .
```

容器端口 `8095`；堆参数默认 `-XX:MaxRAMPercentage=70`。

### 2.4 前端构建

```powershell
cd frontend
pnpm --filter whale-doc lint
pnpm --filter whale-doc build
```

产物位于 `whale-frontend/platform-apps/whale-doc/dist`，按 qiankun 子应用部署到 `/doc`；
基座已注册 `whale-doc`（`qiankun/apps.ts`、`routes.tsx`、菜单与图标）。

### 2.5 健康检查

```powershell
curl http://127.0.0.1:8095/actuator/health
curl http://127.0.0.1:8095/actuator/prometheus
curl -H "Authorization: Bearer <token>" http://127.0.0.1:8080/doc/meta
```

`/doc/meta` 返回服务名、租户、用户、建设中的功能开关（审批/归档/协作/通知）。

---

## 三、配置项

所有配置前缀 `whale.doc.*`，环境变量前缀 `WHALE_DOC_*`。

### 3.1 内容与树

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `content-max-bytes` | `WHALE_DOC_CONTENT_MAX_BYTES` | 2097152 | 正文 JSON 上限 |
| `content-text-max-bytes` | `WHALE_DOC_CONTENT_TEXT_MAX_BYTES` | 1048576 | 纯文本投影上限 |
| `attachment-max-bytes` | `WHALE_DOC_ATTACHMENT_MAX_BYTES` | 52428800 | 单附件上限 |
| `max-depth` | `WHALE_DOC_MAX_DEPTH` | 10 | 页面树最大层级 |
| `auto-snapshot-minutes` | `WHALE_DOC_AUTO_SNAPSHOT_MINUTES` | 10 | 自动快照间隔 |
| `auto-snapshot-revisions` | `WHALE_DOC_AUTO_SNAPSHOT_REVISIONS` | 20 | 自动快照 revision 间隔 |

### 3.2 功能开关

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `collab-enabled` | `WHALE_DOC_COLLAB_ENABLED` | false | Yjs 实时协作；关闭时 REST 自动保存 |
| `approval-enabled` | `WHALE_DOC_APPROVAL_ENABLED` | false | 发布审批；开启必须配置 `approval-definition-id` |
| `approval-definition-id` | `WHALE_DOC_APPROVAL_DEFINITION_ID` | 空 | 工作流定义 ID |
| `approval-sync-minutes` | `WHALE_DOC_APPROVAL_SYNC_MINUTES` | 5 | 回调缺失时的补偿轮询 |
| `notify-enabled` | `WHALE_DOC_NOTIFY_ENABLED` | false | 站内通知（评论@/审批/成员） |
| `notify-channels` | `WHALE_DOC_NOTIFY_CHANNELS` | INAPP | 消息渠道 |
| `archive-enabled` | `WHALE_DOC_ARCHIVE_ENABLED` | false | 归档快照入档 |
| `archive-definition-code` | `WHALE_DOC_ARCHIVE_DEFINITION_CODE` | DOC_PAGE | 档案业务定义 |

> 开关默认关闭；开启后外部依赖失败只告警降级，不回滚文档事务。上线顺序建议：
> 审批/通知 → 归档 → 协作，每步观察 1 个工作日。

### 3.3 回收站、清理与协作

| 配置 | 环境变量 | 默认 |
|---|---|---|
| `recycle-retention-days` | `WHALE_DOC_RECYCLE_RETENTION_DAYS` | 30 |
| `recycle-cleanup-enabled` | `WHALE_DOC_RECYCLE_CLEANUP_ENABLED` | true |
| `recycle-cleanup-cron` | `WHALE_DOC_RECYCLE_CLEANUP_CRON` | `0 30 3 * * *` |
| `recycle-cleanup-batch-size` | `WHALE_DOC_RECYCLE_CLEANUP_BATCH_SIZE` | 200 |
| `orphan-cleanup-enabled` | `WHALE_DOC_ORPHAN_CLEANUP_ENABLED` | true |
| `orphan-cleanup-cron` | `WHALE_DOC_ORPHAN_CLEANUP_CRON` | `0 30 4 * * *` |
| `orphan-attachment-retention-hours` | `WHALE_DOC_ORPHAN_ATTACHMENT_RETENTION_HOURS` | 24 |
| `orphan-cleanup-batch-size` | `WHALE_DOC_ORPHAN_CLEANUP_BATCH_SIZE` | 500 |
| `share-default-expire-days` | `WHALE_DOC_SHARE_DEFAULT_EXPIRE_DAYS` | 7 |
| `share-max-expire-days` | `WHALE_DOC_SHARE_MAX_EXPIRE_DAYS` | 365 |
| `collab-*` | `WHALE_DOC_COLLAB_*` | 见 `application.yml` |

---

## 四、数据库与迁移

### 4.1 表清单

M1：`doc_space`、`doc_space_member`、`doc_page`、`doc_page_version`、`doc_page_attachment`、`doc_comment`、`doc_page_star`。

M2：`doc_page_acl`、`doc_tag`、`doc_page_tag`、`doc_page_link`、`doc_page_activity`、`doc_page_recent`、`doc_comment_read`；`doc_page` 增 `deleted_at/deleted_by/trash_root_id/is_restricted` 与 ngram 全文索引。

M3：`doc_template`、`doc_page_share`；`doc_page` 增审批/归档列。

### 4.2 迁移执行顺序

1. `whale-doc`：`db/init.sql`（新库）或 `migration-5.74-doc.sql`、`migration-5.74-doc-m2.sql`、`migration-5.75-doc-m2b.sql`、`migration-5.75-doc-m3.sql`（存量库）。
2. `whale-service-auth`：`migration-5.74-doc.sql`、`migration-5.75-doc-m2b.sql`、`migration-5.75-doc-m3.sql`（权限/角色/菜单）。
3. 校验：

```sql
SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema='bluewhale_doc' AND table_name='doc_page' AND column_name='is_restricted';
SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema='bluewhale_doc' AND table_name='doc_page' AND index_name='ft_doc_page_text';
SELECT COUNT(*) FROM information_schema.tables
 WHERE table_schema='bluewhale_doc' AND table_name IN
 ('doc_page_acl','doc_tag','doc_page_tag','doc_page_link','doc_page_activity','doc_page_recent','doc_comment_read','doc_template','doc_page_share');
```

> 迁移脚本均幂等：先查 `information_schema` 再 `PREPARE` 加列/索引，或 `CREATE TABLE IF NOT EXISTS`。

---

## 五、备份与恢复

### 5.1 一键演练（推荐）

```powershell
$env:MYSQL_PASSWORD='***'
pwsh -File scripts/ops/doc-backup-restore.ps1
# 保留校验库供人工核对：
pwsh -File scripts/ops/doc-backup-restore.ps1 -KeepRestoreDatabase
```

脚本行为：

1. `mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF` 生成一致性快照到 `logs/backup/bluewhale_doc-<时间>.sql`。
2. 创建独立校验库 `bluewhale_doc_restore_check` 并导入。
3. 校验核心表 14 张、`ft_doc_page_text` 全文索引、`doc_page.is_restricted` 列、`doc_page` 行数与源库一致。
4. 默认删除校验库，保留备份文件；失败时返回非 0。

> 恢复校验库名禁止等于源库名，脚本会直接拒绝，防止误覆盖。

### 5.2 生产恢复步骤

1. 停止 `whale-doc` 实例（网关摘流或直接停进程）。
2. 新库恢复：`CREATE DATABASE bluewhale_doc_new ...`，导入备份。
3. 校验 5.1 的 4 项断言后改名切换，或按组织备份策略原地恢复。
4. 启动服务，检查 `/actuator/health`、`/doc/meta`、页面树与搜索。
5. 协作开启时，Redis 更新日志无需恢复：客户端重连会发送全量状态重建。

### 5.3 Redis 数据

- `whale:doc:collab:updates:*`（更新日志，TTL 2h）、`presence` Hash（TTL 180s）、leader 锁（TTL 30s）、WS ticket（60s）。
- 均为可重建缓存：Redis 丢失只影响进行中的协作会话，不影响已保存文档；无需备份。

---

## 六、监控、告警与 SLO

### 6.1 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `http_server_requests_seconds_*` | Timer | 全部 REST 接口；已开启直方图桶 |
| `doc_page_save_conflict_total` | Counter | 保存版本冲突次数 |
| `doc_search_seconds`（`mode=fulltext/like`） | Timer | 搜索耗时 |
| `doc_recycle_purged_total` | Counter | 回收站到期清除子树数 |
| `doc_orphan_attachment_removed_total` | Counter | 孤儿附件引用清理行数 |
| `doc_orphan_attachment_failed_total` | Counter | 清理失败批次 |

Prometheus 抓取：`deploy/monitoring/prometheus.yml` 的 `whale-doc` job（`127.0.0.1:8095`）。

SLO：

- SLO-4 保存 P95 < 500ms。
- SLO-5 搜索 P95 < 1000ms。

告警：`DocSaveConflictSpike`、`DocSearchP95Slow`、`DocOrphanCleanupFailed`；通用 `ServiceDown` / `Http5xxRateHigh` / `JvmHeapHigh` 覆盖本服务。

Grafana：`bluewhale-overview` 新增「文档服务（Wave 5.74）」行，含保存冲突速率与搜索 P95。

### 6.2 建议新增告警（按需）

```yaml
- alert: DocCollabRedisDown
  expr: up{job="whale-doc"} == 1 and absent(redis_commands_total{job="whale-doc"})
```

> 协作依赖 Redis；Redis 不可用时服务自动降级为 REST 自动保存，前端提示协作不可用（40903）。

---

## 七、安全硬化（M5）

| 项 | 措施 |
|---|---|
| 导出 XSS | HTML/Markdown 导出对文本转义；链接协议白名单 `http/https/mailto/相对路径/#`，`javascript:`/`data:`/`vbscript:` 降级纯文本；图片仅允许 `http/https` 与站内路径 |
| 附件下载 | SVG 等非位图强制 `Content-Disposition: attachment`；全部响应 `X-Content-Type-Options: nosniff`；仅 png/jpeg/gif/webp/bmp inline |
| 附件越权 | 读取必须同时校验页面可读与附件归属；跨页 `fileId` 返回 40401 |
| 受限页 | 树/搜索/评论/收藏/分享/最近访问/内网快照统一走 `DocAccessGuard`；受限页不进入 Agent 知识索引 |
| 内网接口 | `/doc/internal/**` 网关 403；运维触发接口仅限内网直连 |
| 协作 WS | 60s 一次性 ticket，URL 不携带 JWT；origin 白名单 `collab-allowed-origins` |
| 越权写 | 全写接口 `@RequirePermission` + 空间角色 + ACL 三层校验；审计不记录正文 |

生产建议：

- 网关统一附加 CSP（`default-src 'self'`，图片/字体按需放行）与 `Referrer-Policy`。
- `collab-allowed-origins` 收敛到前端域名，不要保留 `*`。
- 分享 token 为 32 字节 `SecureRandom` Base64URL；撤销即改写，不接受旧 token。

---

## 八、日常运维

### 8.1 每日巡检

1. `up{job="whale-doc"}`、5xx、SLO-4/5。
2. `doc_page_save_conflict_total` 是否异常升高（多人编辑或客户端未刷新 revision）。
3. 回收站清理任务日志：`回收站到期清理完成`。
4. 孤儿附件清理日志：`孤儿附件引用清理完成`。
5. MySQL 磁盘、`doc_page` 与 `doc_page_version` 增长。

### 8.2 手工触发孤儿附件清理

```powershell
curl -X POST http://127.0.0.1:8095/doc/internal/ops/orphan-attachment-cleanup
```

返回 `{"code":0,"data":{"removed":n}}`。清理范围：页面行已物理不存在、且 `update_time` 早于
`orphan-attachment-retention-hours` 的附件引用行；**不删除 whale-file 文件字节**。

### 8.3 回收站

- 页面删除进入回收站，默认保留 30 天。
- 到期任务 03:30 清除根节点；彻底清除同步物理删除 ACL/标签/引用/最近访问/评论已读/动态。
- 恢复要求：根节点、空间 ADMIN、原父级存活且同空间、深度校验。

### 8.4 协作排障

| 现象 | 排查 |
|---|---|
| 前端提示“协作不可用”（40903） | `collab-enabled=false` 或 Redis/Nacos 不可用 |
| WS 握手 401/403 | ticket 过期（60s）、ticket 已使用、origin 不在白名单 |
| 协作期 REST 保存 40902 | 正常保护：协作活跃时禁用 REST 覆盖 |
| 多实例不同步 | 检查 Pub/Sub 频道 `whale:doc:collab:channel:{tenant}:{page}` 与 Redis 连通性 |

---

## 九、容量与性能

| 场景 | 目标 | 备注 |
|---|---|---|
| 页面详情 | P95 < 300ms | 单页正文 ≤ 2MB |
| 保存 | P95 < 500ms | 条件更新 + 同事务版本快照 |
| 空间树 | 5,000 页 < 1s | 树一次性查询 + 内存组装 |
| 搜索 | P95 < 1s | ngram 全文索引；<2 字回退 LIKE |
| 协作 | 单页 20 人 | 更新日志 2000 条 / 2h |

扩容建议：先纵向（JVM 70% 容器内存），再横向；协作依赖 Redis Pub/Sub，多实例无需粘性会话。
大空间（> 10,000 页）建议拆分空间，避免单次树查询过大。

---

## 十、审计与数据保留

- 写操作经 `@AuditLog` 上报 `whale-audit`，仅记录页面 ID/标题/版本号，不记录正文。
- 版本历史 append-only，随页面彻底清除删除。
- 动态 `doc_page_activity` append-only，随页面彻底清除删除。
- 回收站保留 30 天；到期物理清除。
- 分享默认 7 天、上限 365 天；归档页不再允许分享访问。
- 审计保留周期由 `whale-audit` 统一策略决定，本服务不单独清理。

---

## 十一、上线检查清单

- [ ] `bluewhale_doc` 迁移全部执行，5.1 校验 SQL 通过。
- [ ] auth 权限 251–279、角色 25101/25102、菜单 36–38 已下发。
- [ ] 网关 `whale-doc` / `whale-doc-ws` 路由与 `/doc/internal/**` 403 生效。
- [ ] 前端 qiankun 子应用 `/doc` 可加载，菜单与权限码一致。
- [ ] `mvn package`、`pnpm --filter whale-doc lint/build` 通过。
- [ ] `scripts/smoke/bw-doc-m1/m2/m2b/m3/m4/m5` 全部 PASSED。
- [ ] `scripts/ops/doc-backup-restore.ps1` 演练 PASSED。
- [ ] Prometheus 抓取 `whale-doc`，Grafana 文档行有数据。
- [ ] 协作/审批/通知/归档开关按灰度计划设置。
- [ ] `collab-allowed-origins` 已收敛，生产 CSP 已配置。
- [ ] 浏览器人工回归：双人协作、断网重连、20 并发保存、受限页 ACL 与分享/审批/归档组合。

---

## 十二、回滚

1. 关闭新能力开关（`WHALE_DOC_COLLAB_ENABLED=false` 等）先降级，无需回滚数据库。
2. 回滚应用版本到上一构建；M2/M3 新增列与表保持兼容（旧版本不读取新列）。
3. 仅在确认无新版本写入后，才可按备份恢复数据库；恢复步骤见 5.2。
