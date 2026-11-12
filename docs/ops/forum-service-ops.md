# 员工论坛服务运维手册（whale-forum / 鲸议台）

> 适用版本：Wave 6.47 / 论坛 M6（检索、计数对账、热榜双档、精华治理、经验账本全部落地）。
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
set -a && . infrastructure/docker/.env && . deploy/backend/.env && set +a   # 前者给 WHALE_INTERNAL_TOKEN
cd whale-backend/whale-services/whale-forum
MYSQL_HOST=127.0.0.1 SERVER_PORT=19011 \
WHALE_FORUM_FILE_BASE_URL=http://127.0.0.1:8085 \
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/bluewhale_forum?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_general_ci&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
java -jar target/whale-forum.jar
```

> `WHALE_FORUM_FILE_BASE_URL` 必补：`ForumProperties.fileBaseUrl` 默认 `http://whale-file:8085`
> 是容器 DNS，宿主机解析不到，申诉材料会整单失败（`300` 系列的参数错误，日志 `申诉材料元数据查询失败`）。

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
| `fm_hot_snapshot` | 热榜快照 | 按 `(tenant_id, hot_window)` 每 30 分钟分区覆盖，`hot_window=24h/7d` |
| `fm_count_reconcile` | 计数对账轨迹 | append-only，M4 新增 |
| `fm_mention` | @提及记录 | append-only，M5 新增；`post_id=0` 表示主帖，唯一键 `(tenant_id, thread_id, post_id, user_id)` |
| `fm_exp_ledger` | 论坛经验账本 | append-only，M6 新增；唯一键 `(tenant_id, user_id, event, ref_thread_id)`，`INSERT IGNORE` |
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
| V8 | `fm_mention`（@提及，append-only） |
| V9 | 热榜双档 `fm_hot_snapshot.hot_window` + 经验账本 `fm_exp_ledger` |

> **改历史脚本的注释也会让校验和变**，随后 `validate-on-migrate` 直接拒启。
> 真要改语义就加新版本；本地库只能重建（`db/migration` 全量重放）后再起服务。

### 3.3 权限与菜单（手工脚本，不归 Flyway）

`whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-6.43-forum.sql`

```bash
mysql -u<user> -p --default-character-set=utf8mb4 bluewhale_auth < migration-6.43-forum.sql
```

幂等（`NOT EXISTS`），可反复执行；脚本登记 12 个 `sys:forum:*` 权限、`forum_moderator` 角色、
授权与 3 个菜单。**预期：tenant_admin 12 项 / forum_moderator 9 项**（`manage:ops` 只授管理员）。

脚本 §5 是**存量库元数据校正**（幂等 `UPDATE`）：早期版本按设计稿把 `sys:forum:manage:*` 的 `path`
写成 `/forum/manage/**`，而实现落在 `/forum/admin/**`；由于插入走 `NOT EXISTS`，重跑会跳过，
错误的 `path`/`perm_name` 会永久留在库里，权限中心展示的接口路径与实际端点对不上。
校正后 `path` 与真实端点逐字一致（`manage:dispose` 的置顶/锁定端点其实不存在，已一并改为
加精/撤精/删帖/删层/举报处置/禁言/解禁/申诉复核）。鉴权只按 `perm_code` 判定（`PermissionInterceptor`），
所以这纯粹是展示元数据，**不影响任何授权结果**。自检：

```sql
-- 应为 0 行（菜单 path 不在 sys_permission 里）
SELECT perm_code FROM sys_permission WHERE perm_code LIKE 'sys:forum:%' AND path LIKE '%/forum/manage/%';
```

**执行记录**：2026-11-12 实机冒烟时在开发库执行过一次（此前一直是「脚本就绪、库内 0 行」）；
同日执行 §5 校正，5 行 `path` 各更新 1 条，脏路径归零，授权仍为 12/9。
验证方法：重新登录后 `GET /auth/v1/me`，`permissions` 应比执行前多 12 条并含全部 `sys:forum:*`，
菜单树出现「论坛 / 论坛管理」。无 mysql 客户端时可用仓库内 JDBC 小工具按文件执行（见 devlog 6.63）。

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
# 热榜只读快照（空则实时兜底）；window=24h|7d，缺省/非法按 7d
curl -s "http://127.0.0.1:9011/forum/hot?window=24h&limit=20" -H "X-Permission-Codes: sys:forum:browse" ...
# 手工刷新（sys:forum:hot:refresh）：24h/7d 两档一起重算，返回两档写入合计
curl -s -X POST "http://127.0.0.1:9011/forum/hot/refresh" -H "X-Permission-Codes: sys:forum:hot:refresh" ...
# 我的论坛经验（只读）
curl -s "http://127.0.0.1:9011/forum/exp/me" -H "X-Permission-Codes: sys:forum:browse" ...
# 补偿投递待发通知（sys:forum:manage:board）
curl -s -X POST "http://127.0.0.1:9011/forum/admin/notify/flush?limit=100" ...
```

- 热榜任务 `forum.hot.snapshot`，按 `(tenant_id, hot_window)` 先删后插，多实例同时跑不会写出两份
  （最多浪费一次聚合）；24h 档在刷新 SQL 里按 `EXP(-发帖小时数/τ)` 衰减，τ=`hot-decay-half-life-hours`（默认 24）。
- 题金榜/撤精走 `POST /forum/admin/thread/{id}/essence|unessence`（理由必填），落
  `ESSENCE`/`UNESSENCE` 轨迹并通知作者；重复操作幂等；撤精不回滚已发经验。
- 经验账本只增不改：同一人同一事件同一帖子只记一次（唯一键 + `INSERT IGNORE`），
  匿名帖与无作者不入账；`fm_exp_ledger` 写失败只 `warn`，不影响发帖/投花。
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
| @了人但对方没收到 | `fm_mention` 是否有行；`fm_notify_outbox` 里 `biz_type=MENTION` | 三种最常见的：① 昵称写错（不做模糊匹配，解析不到直接跳过）；② 通讯录不可达/超时（日志 `提及解析失败`，连接 800ms、读 1500ms）；③ 自己 @ 自己、待审帖、同层重复都不发 |
| @提及全都不生效 | 日志 `通讯录返回非成功` / `通讯录未精确命中` | 检查 `whale.forum.auth-base-url` 与 `X-Tenant-Id`；auth 通讯录实际路径是 **`/user/internal/directory`（无版本段）**，带 `/v1` 恒 401；返回的 `id` 是字符串，代码必须同时认 String 与 Number |
| 第二个帖子 @ 同一个人没有通知 | `fm_mention` 唯一键、outbox 幂等键 | 唯一键须为 `(tenant_id, thread_id, post_id, user_id)`、幂等键须带 `threadId`；缺 `thread_id` 时主帖（`post_id=0`）会跨帖互相顶掉 |
| 引用块显示「该楼层已删除」但楼层还在 | 被引楼层的 `status` 与 `deleted` | 引用摘要只认 `PUBLISHED`；待审/已删/跨租户都按删除占位（不回正文，避免成为绕过删帖的通道） |
| 楼层倒序翻页有跳号 | 请求是否带 `order=desc` | 排序必须下推服务端 `ORDER BY`；前端本地反转单页会导致页边界不连续 |

## 九、日常巡检

- [ ] `/actuator/health` 与 `/forum/meta` 均为 200
- [ ] `/forum/search/status` 的 `pendingBackfill` 为 0
- [ ] 昨日 `forum.count.reconcile` 执行流水存在，`diff` 数量在下降（不是每天都修同一帖）
- [ ] `fm_notify_outbox` 无 `FAILED` 堆积
- [ ] `fm_report` 待受理工单没有超过 48h 未处理
- [ ] `fm_sensitive_hit` 中 BLOCK 命中量与舆情/投诉量对得上

## 十、实机冒烟（`scripts/forum-smoke.py`）

服务起好后跑两条命令，报告落 `.workbuddy/tmp/forum-smoke-report.json` / `forum-smoke-rate-report.json`：

```bash
python scripts/forum-smoke.py          # 全链路 188 例（基线/版块/发帖/回帖/互动/热榜/检索/治理/申诉/结契/对账/越权）
python scripts/forum-smoke.py --rate   # 限流专项 6 例（默认配置下才有意义）
```

口径与前置：

- 默认直打本机 `19011`（`FORUM_SMOKE_BASE` 可改），文件服务 `8085`（`FORUM_SMOKE_FILE_BASE` 可改）。
  与 `mall-smoke.py` / `file-smoke.py` 一致：**绕过网关**，用 `X-User-Id / X-Tenant-Id / X-Permission-Codes`
  注入可信身份，因此不会因网关权限头问题假失败。
- **全链路用例不依赖限流回退**：每条写路径都用本轮专属新账号（`95<RUN><序号>`），
  所以**不要**再为跑冒烟把 `WHALE_FORUM_POST_INTERVAL_SECONDS` 改成 0——默认 60s / 10s 下也全绿。
- 本地直起必须补 `WHALE_FORUM_FILE_BASE_URL=http://127.0.0.1:8085`
  （`ForumProperties` 默认 `http://whale-file:8085` 是容器 DNS，宿主机解析不到，申诉材料会整单失败）。
- 冒烟会真实落库（版块/帖子/标签/申诉/举报/经验/通知），脚本用 `RUN` 后缀隔离数据，
  可反复执行；`--rate` 用 `91<RUN>` / `92<RUN>` 两个独立账号，避免上一轮的 60 秒间隔污染结论。
- 断言口径：HTTP 200 且业务码匹配；负向用例钉死业务码（30001/30020/30031/30041/30042/30060/30062/403）。
  失败清单直接打印在末尾，`rc=1`。

冒烟覆盖到、但日常巡检容易漏的三处真实依赖：

1. **权限点必须先在 `bluewhale_auth` 播种**（§3.3）。库里 0 条 `sys:forum:*` 时服务本身能起、
   直连注入头也能过，但走网关的真实用户一律 `{"code":403}`——2026-11-12 实机冒烟就是这样抓到的。
2. **网关镜像要跟着 `application.yml` 里的 `Path=/forum/**` 重建**。只 `package` 不重建镜像时
   `/forum/**` 会 404「服务不可用」（旧网关注册的是 `/whale-forum/**` 自动发现路由），
   而 `/forum/internal/**` 仍会 403，容易误判成「路由没问题」。
3. **`fm_notify_outbox` 的降级与补投**：`bw-message` 挂掉时通知落 `PENDING` + `retry_count`，
   恢复后 `POST /forum/admin/notify/flush?limit=200` 可一次性补投（实测 161 条 PENDING → 171 SENT，无卡单）。

### 10.1 界面实机（申诉材料缩略图 / 放大）

API 层由 `forum-smoke.py` 覆盖，渲染层另有两条脚本（M7-8 验收）：

```bash
# 1) 造一条待审工单（临时禁言用户 + 96x96 实色 PNG + txt 材料），结果落
#    .workbuddy/tmp/forum-ui-seed-latest.json
python scripts/forum-ui-seed-appeal.py

# 2) 起论坛子应用 dev（8123），再跑浏览器检查（系统 Edge 当内核，无头）
cd whale-frontend && pnpm --filter whale-forum dev &
cd whale-frontend/e2e && node tools/forum-material-ui-check.mjs
#   断言：缩略图渲染（blob URL）/ 非图片材料降级下载链接 / 点击进全屏预览，截图 3 张
```

口径说明：

- 检查脚本**不上基座**：拦截 `8123` 的 `/forum/**` 接口补 `X-User-Id / X-Tenant-Id /
  X-Permission-Codes`（与 `forum-smoke.py` 同款注入式身份），验的是论坛自己的渲染逻辑。
- **不要尝试经基座（8000）走 qiankun 验证**：dev 下 qiankun 激活与子应用容器存在竞态
  （`Target container with #subapp-container not existed`），是框架层问题，与论坛无关；
  生产构建无 StrictMode 不受影响。
- 本机没装 Playwright 自带 Chromium（CDN 不可达），脚本用 `channel` 外的
  `executablePath` 指向系统 Edge（`C:/Program Files (x86)\Microsoft\Edge\...\msedge.exe`）。

历史上挡住这套验证的两处 dev 配置缺陷（2026-11-12 修复）：

1. `whale-forum/vite.config.ts` 的代理写成 `^/forum(/|$)`，把子应用自己的入口 HTML
   （base=`/forum`）也转发到了网关 → `GET /forum/` 401，基座加载与独立运行都白屏。
   已改为只匹配论坛真实接口段（meta|board|thread|post|...）。
2. `whale-platform/vite.config.ts` 的 `GATEWAY_PREFIXES` 缺 `/forum` → 基座内嵌时论坛
   接口落到前端 SPA 回退。已补（文档导航由 `gatewayBypass` 旁路，不影响页面路由）。

生产 nginx（`micro-frontend.conf` 的 `fe_app` 正则与 `whale-fe-*` 容器矩阵）同样没有
`forum`/`mall`/`twin`/`agent`，属**前端部署矩阵未接线**，与网关路由是两回事——接线时记得
一起补（见 devlog 6.64 遗留）。
