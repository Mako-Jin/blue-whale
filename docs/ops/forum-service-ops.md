# 员工论坛服务运维手册（whale-forum / 鲸议台）

> 适用版本：Wave 6.43 / 论坛 M4（检索、计数对账、热榜、治理全部落地）。
> 本文只写「上线后要做什么、出问题先看哪里」，业务规则见 `docs/design/forum-service.md`，
> 需求与验收判据见 `docs/requirements/2026-11-12-forum-2d-functional-spec-v1.3.md`。

## 一、服务坐标

| 项 | 值 |
|---|---|
| 服务名 | `whale-forum`（容器 `bw-forum`，镜像 `bluewhale/whale-forum:dev`） |
| 端口 | `9011`（例外登记：8081–8100 已满、8101–8199 归前端子应用段，见 service-catalog §七） |
| 数据库 | `bluewhale_forum`（MySQL 8.0+，utf8mb4） |
| 依赖 | `whale-file`（附件，`WHALE_FILE_URL`）、`whale-message`（站内信，`WHALE_FORUM_MESSAGE_BASE_URL`）、Nacos |
| 前端 | 子应用 `whale-frontend/platform-apps/whale-forum`（qiankun，`renderWithQiankun` 生命周期） |
| 监控 | Prometheus job `whale-forum`（`/actuator/prometheus`），SLO-12/13/14 见 `docs/ops/slo.md` |
| 权限号段 | `sys:forum:*` 2340–2351，角色 `forum_moderator` = 25139，菜单 510–512 |

## 二、构建、部署与启停

### 2.1 后端构建（单模块）

```bash
# 公共模块有改动时先 ./mvnw -pl whale-common/... install
MAVEN_OPTS=-Xmx1024m ./mvnw -B -ntp -s logs/maven-settings-aliyun.xml \
  -f whale-backend/pom.xml -pl whale-services/whale-forum package -DskipTests -Dcheckstyle.skip=true
```

> `target/` 下的 jar 不能作为验收依据：起服前必须重新 `package`。
> 改 yml / 迁移脚本 / 网关路由后必须**重建镜像**，`docker compose restart` 不会带上新代码。

### 2.2 本地直起（调试用）

```bash
set -a && . deploy/backend/.env && set +a
cd whale-backend/whale-services/whale-forum
MYSQL_HOST=127.0.0.1 SERVER_PORT=19011 \
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/bluewhale_forum?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_general_ci&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
java -jar target/whale-forum.jar
```

> 端口被旧进程占着时先停：`Get-NetTCPConnection -LocalPort 19011 -State Listen | %{ Stop-Process -Id $_.OwningProcess -Force }`。

### 2.3 容器编排

```bash
cd infrastructure/docker
docker compose -f ../backend/docker-compose.services.yml --profile biz up -d forum
docker logs -f bw-forum
```

### 2.4 健康检查

```bash
curl -s http://127.0.0.1:9011/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9011/forum/meta   # 期望 200
```

## 三、数据库与迁移

### 3.1 表清单

| 表 | 用途 | 备注 |
|---|---|---|
| `fm_board` | 版块（可见性、审核开关、发帖策略） | `uk_fm_board_code(tenant_id, code)` |
| `fm_thread` | 帖子（聚合根） | `next_floor` 原子分配；`thread_no` 软删改写为 `原编号#D{id}` |
| `fm_thread_edit_history` | 编辑前快照 | append-only |
| `fm_post` | 楼层（2 楼起，1 楼是楼主正文） | `uk_fm_post_floor(tenant_id, thread_id, floor)` |
| `fm_reaction` | 投花 / 藏帖 | 取消是**物理删除**，否则再点撞唯一键 |
| `fm_moderation_record` | 审核 / 处置轨迹 | append-only |
| `fm_report` | 举报工单 | 24h 去重窗口 |
| `fm_sensitive_word` / `fm_sensitive_hit` | 词库 / 命中留痕 | 留痕走 `REQUIRES_NEW`，不被业务回滚吃掉 |
| `fm_user_ban` | 禁言 / 封号 | 只有 ACTIVE 且未过期才生效 |
| `fm_notify_outbox` | 站内信 outbox | 先落库再投递，失败按 `retry_count` 退避 |
| `fm_thread_view` | 浏览去重窗口行 | `uk(tenant, thread, user)`，滚动 24h |
| `fm_hot_snapshot` | 热榜快照 | 每 30 分钟整表覆盖 |
| `fm_count_reconcile` | 计数对账轨迹 | append-only，M4 新增 |
| `api_log` / `audit_log` | 平台同构日志 | 缺失会表现为「刷栈但接口 200」/ 写接口 500 |

### 3.2 Flyway（`db/migration`，禁止改历史脚本）

| 版本 | 内容 |
|---|---|
| V1 | 基线：`api_log` / `audit_log` |
| V2 | 论坛核心 8 表（版块/帖子/编辑历史/楼层/互动/处置/举报/敏感词命中） |
| V3 | 开发版块种子（1001 鲸聚贤堂 等） |
| V4 | `thread_no` 扩到 64（软删改写值最长 38 字符，32 装不下） |
| V5 | 治理 3 表 + 违规词种子（`fm_sensitive_word` / `fm_user_ban` / `fm_notify_outbox`） |
| V6 | 浏览去重 `fm_thread_view` + 热榜快照 `fm_hot_snapshot` |
| V7 | 检索纯文本 `fm_thread.content_text` + `FULLTEXT(title, content_text) WITH PARSER ngram` + `fm_count_reconcile` |

> **改历史脚本的注释也会让校验和变**，随后 `validate-on-migrate` 直接拒启。
> 真要改语义就加新版本；本地库只能重建（`db/migration` 全量重放）后再起服务。

### 3.3 权限与菜单（手工脚本，不归 Flyway）

`whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-6.43-forum.sql`

```bash
mysql -u<user> -p --default-character-set=utf8mb4 bluewhale_auth < migration-6.43-forum.sql
```

幂等（`NOT EXISTS`），可反复执行；脚本登记 12 个 `sys:forum:*` 权限、`forum_moderator` 角色、
授权与 3 个菜单。**预期：tenant_admin 12 项 / forum_moderator 9 项**（`manage:ops` 只授管理员）。

## 四、检索（M4）

### 4.1 口径

- 索引建在 `fm_thread(title, content_text)`，`content_text` 是**写入时由消毒后 HTML 提取的纯文本**
  （上限 `search-text-length`，默认 4000 字符）。不直接索引 `content`：富文本源码里搜 `p`/`div`/`href`
  会命中几乎全部帖子。
- `ngram_token_size` 默认 2：关键词长度 ≥ 2 走 `MATCH ... AGAINST`，长度 1 回退 `LIKE`。
  **单字检索慢、结果依赖全表扫是分词器口径，不是缺陷**。
- 排序：标题整串命中优先 → 全文相关度 → 发布时间倒序。

### 4.2 自查

```bash
curl -s "http://127.0.0.1:9011/forum/search/status" \
  -H "X-User-Id: 9001" -H "X-Tenant-Id: 1" -H "X-Permission-Codes: sys:forum:browse"
# {"engine":"mysql-ngram","indexName":"ft_fm_thread_search","ngramTokenSize":2,
#  "minKeywordLength":2,"likeFallback":true,"totalThreads":"6","indexedThreads":"6","pendingBackfill":"0"}
```

`pendingBackfill > 0` 就是「有帖子还没回填纯文本」——这些帖子只能被标题搜到，正文搜不到。

### 4.3 何时重建

| 场景 | 动作 |
|---|---|
| 首次上线 / 从 V6 升到 V7 | `POST /forum/search/reindex`（分批回填，可重复执行） |
| 历史帖正文被直接 SQL 改过 | 同上；`content_text` 已非 NULL 的行不会被重算，必要时先置 NULL |
| 排查「正文搜不到」 | 先看 `status`，再 `reindex`，最后再怀疑分词长度与停用词 |

```bash
curl -s -X POST "http://127.0.0.1:9011/forum/search/reindex?limit=1000" \
  -H "X-User-Id: 9001" -H "X-Tenant-Id: 1" -H "X-Permission-Codes: sys:forum:manage:ops"
```

> 需要 `sys:forum:manage:ops`（无权限返回 `{"code":403}`）。单次上限 5000 条，
> 默认按 `search-reindex-batch`（200）分批，避免一次 UPDATE 锁住整表。

## 五、计数对账（M4）

### 5.1 口径

- 展示计数（`reply_count` / `like_count` / `collect_count`）走增量维护，**会漂**：漏一次回滚、
  多一次补偿、人工改数都对不上明细。
- 对账以明细 `COUNT` 为准：回复 = 已发布楼层数，投花/收藏 = 互动表有效行数。
- **浏览数不纳入对账**：`fm_thread_view` 是去重窗口判定行，不是浏览流水，
  按它重算会把「同一人 24h 看 5 次」压成 1，越对越错。

### 5.2 手工演练

```bash
# 只报告（默认，不改数据）
curl -s -X POST "http://127.0.0.1:9011/forum/admin/count/reconcile" \
  -H "X-User-Id: 9001" -H "X-Tenant-Id: 1" -H "X-Permission-Codes: sys:forum:manage:ops"

# 报告并修正
curl -s -X POST "http://127.0.0.1:9011/forum/admin/count/reconcile?fix=true" ...同上
```

返回：`diffCount`（差异条目数）、`fixedCount`（实际修正条目数）、`batchNo`、`items`（前 20 条）。

### 5.3 轨迹与定时任务

- 每条差异落一行 `fm_count_reconcile`：`value_before` / `value_actual` / `fixed` / `batch_no` / `operator_id`。
  「只报告未修」也留痕，否则事后分不清「没差异」和「有差异但没修」。
- 受管任务 `forum.count.reconcile`（默认 `0 30 3 * * ?`，`whale.forum.count-reconcile-cron` 可改）：
  按租户遍历、`fix=true`。单租户失败只记日志，不拖累其他租户。
- 指标 `forum_count_reconcile_diff_total`：连续多日同帖同项漂移，说明对应写路径漏了计数维护，
  别靠对账长期兜（SLO-14）。

## 六、热榜与通知

```bash
# 热榜只读快照（空则实时兜底）
curl -s "http://127.0.0.1:9011/forum/hot?limit=20" -H "X-Permission-Codes: sys:forum:browse" ...
# 手工刷新（sys:forum:hot:refresh）
curl -s -X POST "http://127.0.0.1:9011/forum/hot/refresh" -H "X-Permission-Codes: sys:forum:hot:refresh" ...
# 补偿投递待发通知（sys:forum:manage:board）
curl -s -X POST "http://127.0.0.1:9011/forum/admin/notify/flush?limit=100" ...
```

- 热榜任务 `forum.hot.snapshot`，整表物理覆盖，多实例同时跑不会写出两份（最多浪费一次聚合）。
- `fm_notify_outbox` 里 `status=FAILED` 且 `retry_count>=notify-max-retry` 的记录需人工看一眼
  `whale-message` 是否在线；恢复后用 `notify/flush` 补投，幂等键保证不重复发。

## 七、备份与恢复

```bash
# 备份（保留字符集，中文才不会变 ?）
mysqldump -u<user> -p --default-character-set=utf8mb4 --single-transaction \
  --routines --triggers bluewhale_forum > forum-$(date +%F).sql

# 恢复到校验库
mysql -u<user> -p --default-character-set=utf8mb4 -e "CREATE DATABASE bluewhale_forum_bak DEFAULT CHARSET utf8mb4"
mysql -u<user> -p --default-character-set=utf8mb4 bluewhale_forum_bak < forum-2026-11-12.sql
```

- 恢复后先 `SELECT COUNT(*) FROM fm_thread / fm_post / fm_reaction` 与备份前比对，再改连接串。
- `fm_count_reconcile` / `fm_moderation_record` / `fm_sensitive_hit` 是 append-only 证据表，
  恢复时**不要**做「只留最近 N 天」的裁剪，否则审计链断裂。

## 八、故障排查

| 现象 | 先看什么 | 常见原因 |
|---|---|---|
| `{"code":30001}` | 帖子是否存在、是否跨租户、是否已软删 | 服务端对未发布/已删内容一律按「不存在」返回，属预期 |
| `{"code":30031}` | `fm_sensitive_hit` 最近记录 | 命中 BLOCK 词；留痕独立事务，接口报错也会留 |
| `{"code":30041}` | `fm_user_ban` 中 ACTIVE 记录 | 禁言中，报文里有到期时间与原因 |
| 写接口返回 `{"code":403}` | 网关注入的 `X-Permission-Codes` 是否含 `sys:forum:*` | 权限点未播种 / 角色未授权（`migration-6.43-forum.sql`） |
| 正文搜不到 | `/forum/search/status` 的 `pendingBackfill` | 未回填 `content_text`，跑 reindex |
| 单字搜不到 / 很慢 | `ngram_token_size` | 长度 < 2 走 LIKE，慢是预期的 |
| 帖子计数不对 | `fm_count_reconcile` 最近批次 | 跑一次 `count/reconcile?fix=true`，再查对应写路径 |
| 起服 `Flyway validate failed` | 是否改过历史脚本 | 加新版本或重建库，别改 V1–V7 |
| 站内信收不到 | `fm_notify_outbox.status`、`bw-message` 健康 | 投递失败会重试；超过 `notify-max-retry` 需人工补投 |

## 九、日常巡检

- [ ] `/actuator/health` 与 `/forum/meta` 均为 200
- [ ] `/forum/search/status` 的 `pendingBackfill` 为 0
- [ ] 昨日 `forum.count.reconcile` 执行流水存在，`diff` 数量在下降（不是每天都修同一帖）
- [ ] `fm_notify_outbox` 无 `FAILED` 堆积
- [ ] `fm_report` 待受理工单没有超过 48h 未处理
- [ ] `fm_sensitive_hit` 中 BLOCK 命中量与舆情/投诉量对得上
