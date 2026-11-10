# Wave 5.53 规划：档案服务 + 文件知识库

> 制定日期：2026-10-24 ｜ 状态：**M0–M5 已完成（含 A13 真机联调通过）**（2026-10-25）
> 关联服务：`whale-workflow`（8083）、`whale-form`（8084）、`whale-file`（8085）、`whale-agent`（8090）、`whale-gateway`（8080）、`whale-auth`（8081）、`whale-audit`（9101）
> 关联需求：REQ-FUNC-014（档案服务）、REQ-FUNC-015（文件知识库）、REQ-SEC-008（档案与知识库可见性）

---

## 一、目标与边界

### 1.1 两个任务

| 任务 | 目标 | 交付物 |
|---|---|---|
| **任务一：档案服务** | 凡经过工作流审批**通过**的文档，均可归档为不可篡改的档案条目；支持门类、档号、保管期限、密级、附件、审批轨迹、检索与下载 | 新服务 `whale-archive`（8093，`bluewhale_archive`，`/archive/**`）+ admin 档案管理页 |
| **任务二：文件知识库** | `whale-file` 中**全部业务文件**自动构成可检索知识库，供 `whale-agent` 问答、引用、追溯 | `whale-agent` 新增文件采集/解析/索引通道 + `agent_knowledge_file` 表 + 知识库管理接口 |

### 1.2 非目标（一期明确不做）

- 档案借阅申请、销毁审批流（二期复用 `whale-workflow` 自身实现）。
- 图片 OCR、音视频转写（无 OCR/ASR 能力，图片/音视频一期不入知识库）。
- 档案原件 PDF 渲染/版式固化（一期快照为不可变 JSON + 结构化 HTML）。
- 文件级 ACL 全量改造（一期按「桶 → 知识类型 + 权限码」映射，二期再细化到文件/部门）。
- 多租户知识隔离（现有索引为全局，档案按密级 + 权限码过滤）。

---

## 二、已确认决策（默认口径，评审时可改）

| # | 决策项 | 结论 | 理由 |
|---|---|---|---|
| D1 | 档案服务形态 | **独立微服务 `whale-archive`**，不并入 `whale-file` | 档案有自己的生命周期（整理→归档→保管→销毁）、权限与检索语义，属独立限界上下文；文件实体仍由 `whale-file` 存储 |
| D2 | 归档对象 | `process_instance.status = 1`（**已通过**）的实例；驳回（2）、撤销（3）、运行中（0）**不归档** | 对应「经过审批的文档」；未通过不具备归档资格 |
| D3 | 归档内容 | 实例元数据 + 表单数据快照 + 表单字段标签 + 审批轨迹 + 表单附件（attachment 控件中的 `fileId`，含子表递归） | 现有 [FormRenderer](file:///f:/blue-whale/whale-frontend/elemental/whale-ui/src/components/FormRenderer/index.tsx#L210-L212) 的 attachment 字段以文件 ID 数组承载，可精确提取 |
| D4 | 归档触发 | **定时增量拉取候选 + 归档规则自动归档 / 人工确认归档**；不依赖 MQ | 本机 RabbitMQ 未运行；以 `(source_type, source_id)` 唯一索引保证幂等，轮询可重放 |
| D5 | 快照与防篡改 | 归档时生成不可变 JSON 快照（含实例/表单/轨迹），上传 `whale-file` 独立桶 `whale-archive`；快照与附件在 `file_info` 置 `status=2`（归档锁定，禁止删除） | 档案要求内容可复现；锁定避免原始文件被误删 |
| D6 | 知识库归属 | **扩展现有 `whale-agent`**（Lucene + BM25 + 向量 + 引用体系），不新建知识服务 | 复用 8090 的索引、切分、脱敏、问答与引用校验能力，避免第二套 RAG |
| D7 | 知识库范围 | 允许 MIME：`pdf`、`docx`、`xlsx`、`pptx`、`txt`、`md`、`html`、`csv`；单文件 ≤ 20MB、抽取 ≤ 100 万字符；可配置 | 覆盖业务文档；扫描件/图片需 OCR，一期排除并记录 `SKIPPED` |
| D8 | 知识权限 | 普通文件知识沿用 `sys:agent:chat`；档案快照知识额外要求 `sys:archive:item:read` + 密级过滤；索引块新增 `permission_code` 字段 | 档案可能涉密，不能进入全体可检索池 |
| D8a | 文件入库范围（W5.75 补丁） | **桶白名单 default-deny**：只有 `whale.agent.knowledge.included-buckets`（默认 `bluewhale,whale-archive`）命中的桶才采集；`excluded-buckets` 优先排除 | 原实现仅排除 `whale-doc`，其余业务/附件桶默认进入租户级公开知识池，个人或部门文件可能被全员检索 |
| D9 | 前端落点 | 档案管理进 `whale-admin`（8001）；知识库管理进 `whale-agent` 工作台（8004）；**不新建前端应用** | 控制交付面积，避免新增端口与应用 |
| D10 | 数据同步 | `whale-file` / `whale-workflow` / `whale-form` 新增 `/internal/**` 只读接口，网关统一 403 屏蔽 | 与现有 `/file/internal/upload` 模式一致；服务间 Nacos 直连，不经网关 |

### 2.1 待评审确认项

| 项 | 默认方案 | 若改判影响 |
|---|---|---|
| 是否自动归档 | 规则命中自动归档，否则进「待整理」人工确认 | 影响 `archive_item.status` 流转与前端页面 |
| 档案密级体系 | 1 公开 / 2 内部 / 3 秘密 | 影响权限过滤与知识库可见性 |
| 知识库是否纳入 IM 附件/头像 | **已定案（W5.75）**：桶白名单 default-deny，`whale-im` 等桶默认不采集，需显式加入 `included-buckets` | 影响噪声与索引量；改判需调整白名单配置 |
| 是否生成 HTML 快照 | 一期 JSON 快照 + 可选 HTML | 影响 Tika/模板依赖 |

---

## 三、总体架构

```text
                     ┌──────────────────────────────┐
                     │ whale-admin 8001 档案管理页   │
                     └──────────────┬───────────────┘
                                    │ /archive/**
┌───────────────────────────────────▼───────────────────────────────────┐
│ whale-gateway 8080：JWT / 权限码头 / 限流 / TraceId / internal-deny    │
│  ├─ /archive/**  → lb://whale-archive                                  │
│  ├─ /agent/**    → lb://whale-agent（既有）                            │
│  └─ /file/**、/workflow/**、/form/**（既有）                           │
└───────┬───────────────────────────────────────────────────────────────┘
        │
┌───────▼───────────────┐   internal HTTP   ┌──────────────────────────┐
│ whale-archive 8093    │ ────────────────> │ whale-workflow 8083      │
│ bluewhale_archive     │  已通过实例+轨迹   │ /internal/instance/**    │
│ 门类/规则/条目/档号   │ ────────────────> │ whale-form 8084          │
│ 快照/附件/密级/期限   │  表单数据+字段定义 │ /internal/data/**        │
│ 定时增量同步 + 幂等   │ ────────────────> │ whale-file 8085          │
└───────────┬───────────┘  快照上传/锁定/流 │ /internal/**             │
            │                               └──────────────────────────┘
            │ 快照与附件进 `whale-archive` 桶
            ▼
┌───────────────────────┐   internal changes   ┌──────────────────────┐
│ whale-file 8085       │ <─────────────────── │ whale-agent 8090     │
│ file_info             │  /file/internal/...  │ Lucene 索引 + 知识块 │
└───────────────────────┘                      │ agent_knowledge_file │
                                               └──────────────────────┘
```

**核心原则**：档案与知识库都**不复制业务事实源**，通过只读内网接口拉取；写操作只在各自服务库内完成；权限与审计统一走现有 `whale-auth` / starter 能力。

---

## 四、服务与端口登记（M1 同步 `service-catalog.md`）

| 服务 | 端口 | 对外前缀 | 库 | 说明 |
|---|---|---|---|---|
| `whale-archive`（Java/Spring Boot） | **8093** | `/archive/**` | **`bluewhale_archive`** | 档案门类、规则、条目、档号、快照、检索、同步 |

- 端口落在「8081–8099 核心服务」区段（8087/8088/8089 空置，8093 连续可用）。
- 命名遵循 REQ-ARCH-004：库 `bluewhale_archive`、包 `com.bluewhale.archive`、Nacos 名 `whale-archive`。
- 网关新增 1 条路由，`internal-deny` 扩为 `/archive/internal/**`、`/workflow/internal/**`、`/form/internal/**`、`/file/internal/**`。

---

## 五、档案服务设计

### 5.1 数据模型（`bluewhale_archive`，5 表）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `archive_category` | `id, code, name, parent_id, level, retention_years, security_level, sort, status` | 档案门类/分类树；`code` 业务唯一由服务层保证 |
| `archive_rule` | `id, definition_code, category_id, retention_years, security_level, auto_archive, enabled` | 工作流定义编码 → 归档门类映射；`definition_code` 唯一 |
| `archive_item` | `id, archive_no, title, category_id, category_path, fonds_code, year, retention_years, expire_at, security_level, source_type, source_id, definition_code, business_key, initiator, finished_at, item_status, summary, keywords, snapshot_file_id, snapshot_hash, archived_by, archived_at` | `archive_no` 唯一；`(source_type, source_id)` 唯一；状态 `0 待整理 / 1 已归档 / 2 已销毁` |
| `archive_file` | `id, item_id, file_id, file_role, original_name, file_size, content_type, md5, sort` | `file_role`：`ORIGINAL / ATTACHMENT / SNAPSHOT`；文件实体仍在 `whale-file` |
| `archive_source_record` | `id, source_type, source_id, sync_status, retry_count, last_error, last_sync_at` | 同步幂等与重试台账；`(source_type, source_id)` 唯一 |

配套：

- `archive_sequence(category_code, year, current_value)`：按「门类 + 年度」原子自增，生成档号。
- 档号格式：`BW-{categoryCode}-{year}-{6 位流水}`，如 `BW-GL-2026-000001`。
- `file_info.status` 语义扩展：`1 正常 / 0 禁用 / 2 归档锁定`（`whale-file` 迁移脚本）。

### 5.2 接口（外部）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/archive/category/tree` | `sys:archive:category:list` | 门类树 |
| POST/PUT/DELETE | `/archive/category`、`/archive/category/{id}` | `sys:archive:category:manage` | 门类维护（含保管期限、密级） |
| GET | `/archive/rule/page` | `sys:archive:rule:list` | 归档规则分页 |
| POST/PUT/DELETE | `/archive/rule`、`/archive/rule/{id}` | `sys:archive:rule:manage` | 规则维护 |
| GET | `/archive/item/page` | `sys:archive:item:list` | 条目分页（门类/年度/密级/状态/关键词） |
| GET | `/archive/item/{id}` | `sys:archive:item:read` | 条目详情（含附件与轨迹） |
| PUT | `/archive/item/{id}` | `sys:archive:item:edit` | 整理（门类、标题、密级、期限、摘要、关键词） |
| POST | `/archive/item/{id}/confirm` | `sys:archive:item:archive` | 确认归档：分配档号、置 `1` |
| POST | `/archive/item/{id}/destroy` | `sys:archive:item:destroy` | 销毁登记（二期接审批流） |
| GET | `/archive/item/{id}/files/{fileId}/download` | `sys:archive:item:download` | 经档案权限校验后代理流式下载 |
| POST | `/archive/sync/run` | `sys:archive:sync:run` | 手动触发增量同步 |
| GET | `/archive/sync/status` | `sys:archive:item:list` | 同步游标与失败统计 |
| GET | `/archive/stats` | `sys:archive:item:list` | 门类/年度/密级统计 |

### 5.3 接口（内网，网关 403）

| 服务 | 方法 | 路径 | 说明 |
|---|---|---|---|
| workflow | GET | `/workflow/internal/instance/approved?updatedAfter=&updatedBefore=&current=&size=` | 已通过实例增量列表（按 `update_time,id` 游标） |
| workflow | GET | `/workflow/internal/instance/{id}/detail` | 实例 + 定义快照 + 任务轨迹 |
| form | GET | `/form/internal/data/{id}` | 表单数据 + 字段定义（含 `widgetType`/子表结构） |
| file | GET | `/file/internal/changes?since=&current=&size=` | 文件变更流（id/md5/status/contentType/originalName/size/uploaderId/bucket/updateTime） |
| file | GET | `/file/internal/{id}` | 文件元数据 |
| file | GET | `/file/internal/{id}/content` | 文件内容流（知识抽取用） |
| file | POST | `/file/internal/{id}/lock` / `/unlock` | 归档锁定/解锁（`status=2`） |
| archive | GET | `/archive/internal/refs?fileId=` | 文件是否被档案引用（删除前置校验）；**M2 决策：以 `/file/internal/{id}/lock`（`status=2`）拦截删除，本接口暂不启用** |
| archive | POST | `/archive/internal/items` | **业务系统快照入档（2026-10-26 新增）**：接收合同等业务终态快照，上传快照文件并落「待整理」条目；`(source_type, source_id)` 幂等，仅 `CONTRACT` 白名单 |

### 5.4 归档主流程

1. `@Scheduled`（默认 5 分钟，可配）调用 workflow 增量接口，按 `update_time` 游标翻页。
2. 对每个实例：查 `archive_source_record`，已存在则跳过（幂等）。
3. 读取实例详情与任务轨迹；按 `business_key` 读取表单数据与字段定义。
4. 递归提取 attachment 字段（含子表）中的 `fileId`，读取文件元数据。
5. 生成快照 JSON（实例 + 定义 + 表单值/标签 + 轨迹 + 文件清单 + `sha256`），上传至 `whale-archive` 桶。
6. 对快照与附件调用 `/file/internal/{id}/lock`。
7. 命中规则且 `auto_archive=true` → 直接分配档号、状态 `1`；否则状态 `0 待整理`。
8. 写 `archive_item` / `archive_file` / `archive_source_record`（同一本地事务）；失败记录 `retry_count/last_error`，下次重试。
9. 已归档条目不随源实例变更而改写（快照不可变）；源实例后续驳回/撤回不删档，仅在详情保留来源状态。

### 5.5 权限与审计

- 新增权限码：`sys:archive:category:list/manage`、`sys:archive:rule:list/manage`、`sys:archive:item:list/read/edit/archive/download/destroy`、`sys:archive:sync:run`。
- admin 菜单新增「档案管理」：门类、规则、待整理、档案检索、同步状态。
- 归档、整理、确认、销毁、下载均加 `@AuditLog`；审计落 `whale-audit`。

---

## 六、文件知识库设计

### 6.1 数据模型（`bluewhale_agent` 新增 1 表）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `agent_knowledge_file` | `id, file_id, bucket, original_name, content_type, file_size, md5, uploader_id, source(FILE/ARCHIVE), index_status(PENDING/INDEXING/INDEXED/SKIPPED/FAILED), chunk_count, attempts, last_error, indexed_at, enabled` | `file_id` 唯一；`md5` 变化触发重建；记录失败与重试 |

### 6.2 采集与索引流程

1. **变更拉取**：`@Scheduled`（默认 10 分钟）调用 `/file/internal/changes?since=`，按 `(updateTime,id)` 游标翻页（每页 200）。
2. **变更判定**：`agent_knowledge_file` 不存在或 `md5` 变化 → 置 `PENDING`；文件已删/禁用 → 删除 `path=file:<id>` 索引，置 `SKIPPED`。
3. **解析**：单线程/双线程受限执行器拉取 `/file/internal/{id}/content`，用 **Apache Tika** 抽取文本；限制 MIME 白名单、20MB、100 万字符、30s 超时；禁用外部实体（防 XXE/zip bomb）。
4. **脱敏**：复用 `SensitiveContentMasker.mask`。
5. **切分**：新增 `FileChunker`（段落/标题优先，块 ≤ 1200 字、重叠 150；表格按行块；PPT 按页）。
6. **索引**：`AgentIndexService.addFileChunks` 写入 Lucene，`kind=file|archive`、`path=file:<id>`、`title=originalName`、`module=bucket`，新增存储字段 `file_id`、`permission_code`。
7. **提交**：`commitAndRefresh`；更新 `agent_knowledge_file` 状态与块数；失败重试 ≤ 3 次，指数退避。
8. **档案快照**：`whale-archive` 桶的文件映射 `kind=archive`、`permission_code=sys:archive:item:read`；普通桶 `kind=file`、`permission_code` 为空（公开）。

### 6.3 知识库接口

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/agent/knowledge/files/page` | `sys:agent:knowledge:list` | 已入库文件分页（状态/类型/来源/关键词） |
| POST | `/agent/knowledge/files/{fileId}/reindex` | `sys:agent:knowledge:manage` | 单文件重建 |
| PUT | `/agent/knowledge/files/{fileId}/enabled` | `sys:agent:knowledge:manage` | 启用/停用（停用即删索引） |
| POST | `/agent/knowledge/rebuild` | `sys:agent:knowledge:manage` | 文件知识全量重建 |
| GET | `/agent/knowledge/stats` | `sys:agent:knowledge:list` | 文件数/块数/失败数/最近同步 |

- 问答范围开关新增 `file`、`archive` 两类；引用仍走现有 `[Sx]` 与 `CitationValidator`，证据展示文件名 + `file:<id>` + 可下载链接。
- 检索过滤：`permission_code` 为空或属于当前用户权限码集合；档案密级 3（秘密）仅 `sys:archive:item:read` + 指定角色可见。

### 6.4 与档案的耦合

- 档案快照同样是 `whale-file` 中的文件，自动进入知识库（无需额外通道）。
- 实现上通过**桶映射**区分 `kind`，避免档案服务反向依赖 `whale-agent`；如需覆盖分类，预留 `POST /agent/internal/knowledge/file-meta`（二选一，评审确认）。

---

## 七、改动清单

| 模块 | 改动 | 里程碑 |
|---|---|---|
| `whale-backend/whale-services/whale-archive`（新建） | 实体/Mapper/Service/Controller/配置/DDL/单测 | M1–M3 |
| `whale-workflow` | `/internal/instance/**` 只读接口；**办结时间取 `process_instance.update_time`**（不改既有库加 `finished_at` 列，审批通过时状态与更新时间同事务写入） | M2 |
| `whale-form` | `/internal/data/{id}`（数据 + 字段定义） | M2 |
| `whale-file` | `/internal/changes`、`/internal/{id}`、`/internal/{id}/content`、`/internal/{id}/lock|unlock`；`file_info.status=2` | M2、M4 |
| `whale-agent` | Tika 依赖、`agent_knowledge_file`、文件采集器/`FileChunker`、知识接口、检索权限字段 | M4–M5 |
| `whale-gateway` | `/archive/**` 路由；internal-deny 扩 4 组前缀 | M1 |
| `whale-auth` | 权限种子 + admin 菜单（幂等 migration） | M1、M3 |
| `whale-frontend/admin` | 门类、规则、待整理、档案检索、同步状态五页 | M3 |
| `whale-frontend/platform-apps/whale-agent` | 文件知识管理视图 + 问答范围开关 | M5 |

---

## 八、里程碑与任务拆解

### M0 设计冻结（本次完成）

- [x] 现状核对：`file_info` / `process_instance` / `form_data` / Lucene 索引与存储插件能力。
- [x] 决策 D1–D10 与待确认项。
- [x] 需求登记（REQ-FUNC-014/015、REQ-SEC-008）。
- [x] 服务目录/端口/库登记（M1 第一项）。
- [ ] 评审确认 §2.1。

### M1 档案底座（A1–A5）——**已完成（2026-10-24）**

- [x] A1 模块骨架 `whale-archive`（8093）+ 数据源 + Nacos + Checkstyle/Javadoc 合规。
- [x] A2 DDL 5 表 + `archive_sequence` + `bluewhale_archive` 建库脚本。
- [x] A3 门类树 CRUD + 规则 CRUD + 档号生成（数据库原子自增 + 唯一索引兜底）。
- [x] A4 权限种子 + 网关路由 + internal-deny + service-catalog 登记。
- [x] A5 单测（门类/规则/档号并发）+ Checkstyle 0 + 构建。

### M2 工作流归档接入（A6–A10）——**已完成（2026-10-24）**

- [x] A6 `whale-workflow` 内网接口：`/workflow/internal/instance/approved`（`status=1`，`update_time,id` 游标，`updateTime` 即办结时间）+ `/{id}/detail`（实例 + 定义快照 JSON + 任务轨迹）。
- [x] A7 `whale-form` 内网接口：`/form/internal/data/{id}` 返回表单值（结构化 `fields`）+ 字段定义（含 `attachment`/`subtable`）。
- [x] A8 `whale-file` 内网接口：`/file/internal/changes|{id}|{id}/content|{id}/lock|{id}/unlock`；`status=2` 归档锁定，锁定文件删除返回参数错误。
- [x] A9 同步调度 + 幂等台账 + 快照生成上传 + 附件递归提取 + 规则命中：`archive_sync_state` 持久化游标；`archive_source_record` 记录成功/失败与重试次数；快照写入 `whale-archive` 桶并返回 SHA-256；附件（含子表）全部锁定；未命中规则进待整理。
- [x] A10 单测：幂等重放、驳回/撤销不归档、附件含子表、锁定后删除冲突、失败重试、快照确定性与命名；Checkstyle 0、受影响模块构建成功。

### M3 档案检索与前端（A11–A13）

- [x] A11 条目分页/详情/整理/确认/下载/统计 + 审计埋点。
- [x] A12 admin 五页（门类/规则/待整理/档案检索/同步状态）+ 权限按钮（真机联调归 A13）。
- [x] A13 验收：审批通过 → 5 分钟内可见；档号唯一；下载与权限矩阵。（2026-10-25 直连模式实测：办结 → 可见 **28 秒**，定时同步 cron 默认 `0 */5 * * * *`；`BW-A13TEST-2026-000001/000002/000003` 唯一；下载 200 + `attachment` + RFC 5987 + `nosniff`；无权限码/错码 = `code=403`，正确 = `code=0`；重跑同步 `pulled=0` 无重复；`file_info.status=2`；`audit_log` 7 行）

### M4 文件知识库（K1–K5，可与 M1–M3 并行）——**已完成（2026-10-25）**

- [x] K1 `agent_knowledge_file` DDL + 变更游标 + 状态机：`init.sql` / `migration-5.53.sql` 同构建表；`file_id` 唯一；`source_update_time` 取最大值为游标（不额外建状态表）；状态 `PENDING/INDEXING/INDEXED/SKIPPED/FAILED` + `attempts/last_error/next_retry_at/indexed_at/md5`。
- [x] K2 Tika 解析器（白名单/20MB/100 万字符/30s 超时/线程池隔离）+ `FileChunker`（≤1200 字、重叠 150、表格重复表头、`\f` 分页）+ 脱敏，异常分 `retryable` 与不可重试两类。
- [x] K3 索引写入/删除 + 增量与删除同步 + 失败重试：新增 `file_id`/`permission_code` 存储字段；`FileKnowledgeClient` 对接 `/file/internal/changes|{id}|{id}/content`；`whale-file` 变更流纳入逻辑删除行（`deleted`）；md5 未变不重建；删除/停用即删索引；失败指数退避 ≤3 次。
- [x] K4 知识库管理接口 + 权限码 + 审计：`GET /agent/knowledge/files/page`、`POST /files/{fileId}/reindex`、`PUT /files/{fileId}/enabled`、`POST /rebuild`、`GET /stats`；权限 `sys:agent:knowledge:list/manage`（106/107）；重索引/启停/重建均 `@AuditLog`。
- [x] K5 单测：6 类文档（txt/md/html/csv/docx/xlsx/pptx/pdf）、损坏/超大/不支持、删除即消失、md5 未变不重建、失败退避；`whale-agent` 全量 56 例、`whale-file` 56 例全绿，Checkstyle 0。

### M5 档案知识融合与权限（K6–K8）——**已完成（2026-10-25）**

- [x] K6 `whale-archive` 桶 → `kind=archive` + 档案权限码：`AgentProperties.Knowledge` 新增 `archiveBucket/archiveUrl/archivePermissionCode/archiveSecretPermissionCode`；`FileDocumentParser` 白名单补 `application/json`、`text/json` 并按 UTF-8 直读快照；`FileChunker` 增加 kind 重载；`ArchiveFileMapper.selectSecurityByFileId`（同文件多条目取最严）+ `GET /archive/internal/files/{fileId}/security`（网关 internal-deny 屏蔽）；agent 新增 `ArchiveSecurityClient`，密级不可用即 `SKIPPED`（fail-closed）；同步按「档案桶 或 `status=2` 归档锁定」写 `kind=archive`、`source=ARCHIVE`，密级 3 → `sys:archive:secret:read`，1/2 → `sys:archive:item:read`；原件/附件归档后同样受密级保护，解锁后自动降级为 `kind=file`。
- [x] K7 检索 `permission_code` 过滤 + 密级；问答范围开关 `file/archive`；证据展示文件名/下载：索引受限块写 `permission_restricted` 标记 + `permission_code`（StringField + SortedDocValues）；BM25/KNN 均加权限过滤（无约束块恒可见，受限块须命中用户权限码之一）；`KnowledgePermissionContext` 线程内透传 `X-Permission-Codes`（未设置按空集合处理）；`AgentConversationService`/前端范围新增 `file/archive`；`SearchHit`/`Citation`/`CitationVO` 带 `fileId` 与 `downloadUrl`，证据面板支持下载。
- [x] K8 验收：档案快照可检索、无权限用户不可见、敏感内容不入索引；新增权限码 `sys:archive:secret:read`（151，auth `init.sql` + `migration-5.53.sql`）；单测覆盖 JSON 解析、archive 切块、档案密级权限码、密级缺失 fail-closed、检索权限过滤（匿名/条目权限/秘密级权限）。

---

## 九、验收口径

| # | 验收项 | 口径 |
|---|---|---|
| 1 | 归档触发 | 审批通过（status=1）5 分钟内进入待整理/已归档；驳回、撤销永不进入 |
| 2 | 幂等 | 同一实例重复同步 N 次，条目数恒为 1，档号不重复 |
| 3 | 快照完整性 | 快照含实例、表单值、字段标签、审批轨迹、附件清单；`sha256` 稳定可复算 |
| 4 | 附件保护 | 表单 attachment（含子表）全部收集；锁定文件删除返回冲突 |
| 5 | 档号 | 并发 20 次确认归档，档号唯一且连续 |
| 6 | 权限 | 无 `sys:archive:*` 403；下载需 `sys:archive:item:download`；审计可查 |
| 7 | 文件入库 | 6 类文档入库；引用含文件名与 `file:<id>`；删除/停用后检索不可见 |
| 8 | 解析容错 | 损坏/超大/不支持文件置 `SKIPPED/FAILED`，不阻塞队列、不崩溃 |
| 9 | 增量 | 未变更文件不重复抽取；md5 变更触发重建 |
| 10 | 档案知识权限 | 密级 3 档案内容仅授权用户检索命中；普通用户不可见 |
| 11 | 质量门禁 | 新模块单测全绿 + Checkstyle 0；全模块构建成功；前端 lint/build EXIT=0 |
| 12 | 实机 | 本机 MySQL/Redis/Nacos + 网关链路完成端到端脚本验收（本机 Docker 不可用，沿用原生中间件） |

---

## 十、风险与处置

| 风险 | 影响 | 处置 |
|---|---|---|
| 无 MQ，跨服务一致性依赖轮询 | 归档延迟、重复拉取 | 游标 + 唯一索引幂等 + 手动触发 + 失败重试；二期接 RabbitMQ 事件 |
| Tika 解析不可信文档 | 内存溢出、XXE/zip bomb | MIME 白名单、大小/字符/超时上限、禁用外部实体、隔离线程池 |
| 索引体积增长 | 磁盘与重建耗时 | 单文件上限、块上限、`enabled` 停用、按需重建、保留统计 |
| 档案涉密内容进入全局索引 | 越权检索 | `permission_code` 过滤优先于召回；密级 3 仅授权角色；验收第 10 项 |
| 原始文件被删除 | 档案失效 | `status=2` 归档锁定 + 删除前置引用校验 |
| 表单附件 id 精度丢失 | 附件漏收 | 全链路字符串承载（现有实现已注意），解析时按字符串处理 |
| 现有 `form_data` 历史数据无 attachment 字段定义 | 提取不到附件 | 按 `widget_type=attachment` 匹配；缺失定义时仅归档数据快照，并记录告警 |

---

## 十一、需求项 / 完成项 / 待做项 / 已修正问题项

### 11.1 需求项

| 编号 | 需求 | 优先级 |
|---|---|---|
| REQ-FUNC-014 | 档案服务：审批通过文档归档（门类/档号/期限/密级/快照/附件/检索/下载） | 高 |
| REQ-FUNC-015 | 文件知识库：文件服务全部业务文件自动解析、切分、索引，供问答引用 | 高 |
| REQ-SEC-008 | 档案与知识库可见性：档案权限码 + 密级过滤 + 索引块权限字段 + 归档锁定 | 高 |

### 11.2 完成项

- 现状核对：归档对象、附件承载方式、索引能力、内网接口缺口。
- 决策 D1–D10、服务/端口/库登记方案、数据模型、接口清单、同步流程、里程碑与验收口径。
- 需求登记（登记册 §五）。
- M1：`whale-archive` 模块骨架、DDL、门类/规则 CRUD、档号并发生成、权限种子、网关路由与 internal-deny、17 用例与门禁。
- M2：workflow/form/file 三组内网接口；同步调度（游标持久化 + 失败重试 + 手动触发）、规则命中、不可变快照（SHA-256）、附件递归提取与锁定、幂等台账；新增 28/3/6 用例并通过 Checkstyle 0。
- M3：档案条目分页/详情/整理/确认/销毁/下载/统计接口与审计；admin 五页与动态路由；A13 直连真机验收通过（28 秒可见、档号唯一、下载与权限矩阵）。
- M4：`agent_knowledge_file` 采集台账与状态机；Tika 解析（白名单/大小/字符/超时/隔离线程池）；`FileChunker` 与脱敏；`FileKnowledgeClient` 增量拉取、md5 去重、删除/停用下架、失败指数退避；Lucene `file_id`/`permission_code` 字段；知识库管理接口、权限种子与审计；新增 17 用例全绿。
- M5：档案桶 `kind=archive` + 归档 JSON 快照直读；`/archive/internal/files/{fileId}/security` 密级查询（同文件取最严）；同步侧密级 → 权限码映射与 fail-closed；BM25/KNN 权限过滤（受限标记 + 权限码，普通块恒可见）；问答/检索线程透传幂等权限上下文；范围开关 `file/archive`；证据 `fileId` + 下载；`sys:archive:secret:read`（151）权限种子；新增 5 类用例。

### 11.3 待做项

- 评审确认 §2.1 四项待定决策。
- 部署前置：执行 `bluewhale_agent` 的 `migration-5.53.sql`（`agent_knowledge_file`）、`whale-auth` 的 `migration-5.53.sql`（权限 106/107/151）与 `bluewhale_archive` 建库/建表脚本（本机 MySQL8 root 口令未提供，脚本已就绪待执行）。
- 真机补测：档案快照入索引后，用无权限/条目权限/秘密级权限三种码头验证检索命中差异。

### 11.4 已修正问题项

- 澄清「归档 = 复制文件」不成立：文件实体仍由 `whale-file` 保管，档案只持有引用 + 不可变快照 + 锁定。
- 澄清知识库不新建服务：复用 `whale-agent` 的 Lucene/向量/引用体系，仅新增采集与权限字段。
- 澄清「所有文件」需排除图片/音视频（无 OCR/ASR），并以 MIME 白名单与大小上限控制风险。
- 澄清无 MQ 条件下的一致性方案：游标轮询 + 幂等唯一键，而非事件驱动。
- 办结时间不新增 `finished_at` 列：沿用 `process_instance.update_time`，避免对既有工作流库做破坏性 DDL。
- 快照 JSON 时间统一为 ISO-8601 字符串，不依赖 JSON 映射器的时间模块配置，保证跨环境摘要可复算。
