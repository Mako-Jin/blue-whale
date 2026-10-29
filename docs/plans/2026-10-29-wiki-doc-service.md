# Wave 5.74（建议）在线文档 / Wiki 服务实施计划

> 状态：**M0–M5 全部完成**（2026-10-29；`whale-doc` 8095、`whale-agent` 8090 与前端 8110 可编译构建；B1-2~B1-8、F1-3~F1-8、M2-1~M2-7、M3-1~M3-6、F3-1、M4-1~M4-5 已完成；M1 65 项、M2 41 项、**M2b 42 项**、M3 31 项、M4 30 项、**M5 30 项** smoke 与备份恢复演练全部通过，共 **239 项**）
> 设计基线：[doc-service.md](../design/doc-service.md)
> 关联需求：REQ-FUNC-027、REQ-FUNC-028、REQ-ARCH-012、REQ-SEC-018
> 范围：新增核心服务 `whale-doc` + qiankun 子应用 `whale-doc`，复用现有认证、文件、Agent、消息、工作流、档案与审计能力。

---

## 一、结论与总体方案

1. **独立服务，不扩展文件服务**：新增 `whale-doc`（8095、`/doc/**`、`bluewhale_doc`、`com.bluewhale.doc`），拥有空间、页面树、正文、版本、评论、附件引用与权限；文件字节仍归 `whale-file`。
2. **前端独立子应用**：`frontend/platform-apps/whale-doc`（8110、base `/doc`），接入 qiankun 基座、菜单与权限；编辑器采用 TipTap（ProseMirror）JSON，不引入第二套富文本体系。
3. **分期交付**：
   - **M1（可用 Wiki）**：空间/成员、页面树、富文本编辑、自动保存、冲突检测、版本历史、评论、附件、搜索、收藏。
   - **M2（协作增强）**：实时多人协作（Yjs，可开关）、回收站、页面级权限、标签/反向链接、全文检索。
   - **M3（治理与生态）**：模板、导入导出、发布审批、消息通知、登录内分享、归档。
   - **M4（AI 知识）**：已发布公开页面进入 `whale-agent` 知识索引，支持引用与文档问答。
   - **M5（上线硬化）**：性能、安全、备份、可观测性与运维文档。
4. **一致性底线**：保存必须带 `baseRevision`，使用条件更新；冲突显式提示，不静默覆盖。
5. **不做**：不新建 MQ、不新建身份/文件存储、不做匿名公开站点、不做 Office/表格/白板、M1 不做实时协作。

---

## 二、目标、范围与成功标准

### 2.1 业务目标

让组织在平台内沉淀可检索、可协作、可治理的知识：空间承载团队知识，页面树组织内容，版本与评论保留演进过程，权限控制可见范围，Agent 让知识可问答。

### 2.2 用户角色

| 角色 | 场景 |
|---|---|
| 普通成员 | 浏览公开空间、评论、搜索、收藏；创建自己的空间 |
| 空间 OWNER/ADMIN | 管理空间、成员、页面归档与删除 |
| 空间 EDITOR | 新建/编辑/发布页面、上传附件、恢复版本 |
| 空间 READER | 阅读、评论、搜索、收藏 |
| 租户管理员 | 通过 `doc_manager` / `tenant_admin` 管理功能入口 |

### 2.3 成功标准（M1）

- 5 分钟内完成「建空间 → 建页面 → 编辑 → 发布 → 评论」闭环。
- 两个浏览器同时保存同一页面：后提交者收到冲突提示，服务端只接受一个 revision。
- 跨租户、跨空间、未授权附件访问均被拒绝；富文本脚本/事件属性不执行。
- 页面详情 P95 < 300ms；保存 P95 < 500ms；5,000 页空间树 < 1s。
- 后端单测 + 构建、前端 lint/build、网关 smoke、浏览器全流程均通过。

---

## 三、现状盘点与复用矩阵

| 现有能力 | 复用方式 | 新增工作 |
|---|---|---|
| `whale-service-auth` | 登录、租户、RBAC、用户/部门；网关注入 `X-User-Id` / `X-Tenant-Id` / `X-Permission-Codes` | 权限码 251–270、角色 `doc_member/doc_manager`、菜单 36–37 |
| `whale-gateway` | JWT、限流、熔断、TraceId、`internal-deny` | `/doc/**` 路由；M2 `/doc/ws` 路由与白名单 |
| `whale-file` | `POST /file/internal/upload`、`GET /file/internal/{id}/content`；桶 `whale-doc` | 无（已有内部上传/读取） |
| `whale-agent` | Lucene 索引、切块、脱敏、检索、引用卡片 | M4 新增 DOC 知识源/同步与权限过滤 |
| `whale-message` | `POST /message/internal/send`、幂等、投递记录 | M3 评论提及/审批通知模板 |
| `whale-workflow` | 审批实例、用户任务、回调 | M3 发布审批流程定义与回调（默认关闭） |
| `whale-archive` | `POST /archive/internal/items`，按来源幂等 | M3 页面快照入档（`sourceType=DOC_PAGE`） |
| `whale-audit` | `@AuditLog` 汇聚与查询 | 各写接口审计（不记录正文） |
| starter | 租户拦截、`Result`、异常、雪花 ID、限流/锁、i18n | 文档域错误码、限流键 |
| 前端体系 | qiankun、`@bluewhale/whale-ui`、`@bluewhale/http`、主题、日志 SDK | 新子应用、编辑器、页面与交互 |

---

## 四、落点与对外契约

| 项 | 取值 |
|---|---|
| 后端模块 | `backend/whale-services/whale-doc`（单模块，DDD 四层包） |
| 服务名 / Nacos | `whale-doc` |
| 端口 / 库 | `8095` / `bluewhale_doc` |
| 包根 | `com.bluewhale.doc` |
| 对外前缀 | `/doc/**`（网关不 StripPrefix） |
| 内网前缀 | `/doc/internal/**`（`internal-deny` 403） |
| 前端 | `frontend/platform-apps/whale-doc`，8110，base `/doc` |
| 权限前缀 | `sys:doc:*`（251–277 预占，M1 用 251–270） |
| 菜单 | 36 文档中心（DIR `/doc`）、37 文档空间（`/doc/spaces`） |
| 表前缀 / Redis | `doc_*` / `whale:doc:*` |
| 环境变量 | `WHALE_DOC_*`（如 `WHALE_DOC_COLLAB_ENABLED`、`WHALE_DOC_APPROVAL_ENABLED`） |

> 编码前必须完成：`naming-conventions.md` 域词登记、`service-catalog.md` 端口/路由登记、auth 迁移脚本编号登记。

---

## 五、里程碑与任务拆分

### M0 设计冻结与登记（3 人日）

| ID | 任务 | 交付物 | 验收 |
|---|---|---|---|
| M0-1 | 设计评审与定稿 | 本文 + [doc-service.md](../design/doc-service.md) | 架构/安全/前端评审通过；开放问题 Q1–Q8 有结论 |
| M0-2 | 需求与命名登记 | `requirements/register.md`、`naming-conventions.md`、`service-catalog.md` | 服务/库/包/端口/前端端口/权限码/菜单号唯一 |
| M0-3 | API 与错误码冻结 | OpenAPI 或 Markdown 合同；文档域错误码表 | 保存冲突、越权、超限、环、最后 OWNER 等错误码确定 |
| M0-4 | 前端低保真流程 | 空间列表、工作台、编辑页、版本/评论/附件抽屉的线框与交互说明 | 产品/设计确认；移动端仅保证可用不专门适配 |
| M0-5 | 测试与上线清单 | `scripts/smoke/bw-doc-smoke.ps1` 大纲；安全/性能用例 | 覆盖权限矩阵、并发保存、跨租户、XSS、附件 IDOR |

**Gate**：M0 未通过，不创建模块/不写迁移脚本。

### M1 可用 Wiki（30 人日，后端 16 / 前端 14）

#### 后端

| ID | 任务 | 关键交付 |
|---|---|---|
| B1-1 | 服务骨架与基础设施 | `whale-doc/pom.xml`、`WhaleDocApplication`、`application.yml`、MyBatis/租户/Redis/健康检查、`db/init.sql`、根 POM/`whale-services/pom.xml` 注册、启动脚本 8095 |
| B1-2 | 空间与成员 | `doc_space`/`doc_space_member`、CRUD、成员角色、最后 OWNER 保护、`DocAccessGuard` |
| B1-3 | 页面与树 | `doc_page`、CRUD、树、移动/排序、slug、层级/环校验、发布/归档、`revision` 条件更新、JSON→`content_text` 服务端提取 |
| B1-4 | 版本历史 | `doc_page_version`、快照策略、列表/详情/恢复、内容哈希去重 |
| B1-5 | 附件 | 调 `file/internal/upload`（桶 `whale-doc`）、引用登记、解除引用、内容代理（inline/attachment + nosniff） |
| B1-6 | 评论 | 评论/回复、块锚点、解决/删除、作者/管理员权限 |
| B1-7 | 搜索与收藏 | M1 `LIKE` 标题/纯文本 + 可见性过滤 + 片段；收藏/取消收藏；最近访问（M1 可前端本地） |
| B1-8 | 权限、审计与内网接口 | auth 迁移 `migration-5.74-doc.sql`（权限 251–270、`doc_manager/doc_member` 角色、`tenant_admin` 授权、菜单 36–37、批量授予说明）；`@AuditLog`；`/doc/internal/page/changes` 雏形（M4 完整） |
| B1-9 | 网关与配置 | `/doc/**` 路由、`/doc/internal/**` internal-deny、限流/熔断接入、Prometheus 抓取；**`whale-agent` 的 `WHALE_AGENT_KNOWLEDGE_EXCLUDED_BUCKETS` 加入 `whale-doc`，防止私有附件被文件知识同步误索引** |

#### 前端

| ID | 任务 | 关键交付 |
|---|---|---|
| F1-1 | 子应用骨架 | `whale-doc` 包、Vite 8110、qiankun 生命周期、base `/doc`、路由、登录态/权限守卫 |
| F1-2 | 基座接入 | `apps.ts` 注册、菜单路由、图标、构建脚本、Nginx/部署清单 |
| F1-3 | 空间列表 | 我的空间/公开空间、创建/编辑、成员抽屉、空态/错误态 |
| F1-4 | 空间工作台 | 左侧页面树、新建/重命名/移动/删除、面包屑、页面打开 |
| F1-5 | 编辑器 | TipTap 扩展（标题/列表/任务/表格/代码块/引用/链接/图片/提及/页面引用）、工具栏、斜杠菜单、快捷键、字数/大纲；仅用开源免费扩展，不引入 Pro 依赖 |
| F1-6 | 自动保存与冲突 | 1.5s debounce（提交 `title + contentJson + baseRevision`）、保存状态、`localStorage` 崩溃恢复、409 冲突对话框（加载最新/覆盖二次确认/对比） |
| F1-7 | 信息面板 | 版本抽屉（手动存版本/预览/恢复）、评论面板（轮询刷新）、附件面板、大纲 |
| F1-8 | 搜索与收藏 | 全局搜索页/命令面板、结果高亮与跳转、收藏列表 |
| F1-9 | 交付验证 | `pnpm --filter whale-doc lint build`、浏览器全流程、权限矩阵走查 |

**M1 Gate**：REQ 验收项全部通过；冒烟脚本 PASS；无 P0/P1 缺陷；完成检查报告与 devlog。

### M2 协作增强（18 人日）

| ID | 任务 | 关键交付 |
|---|---|---|
| M2-1 | 实时协作基础 | `/doc/ws-ticket`（60s Redis ticket）、网关 `lb:ws://whale-doc`、`whale.doc.collab.enabled` 开关、鉴权与断线重连 |
| M2-2 | Yjs 协同 | TipTap Collaboration + CollaborationCaret；Java WS 中继；Redis pub/sub 多实例；协作期间暂停 REST 自动保存；leader 客户端 10–15s 经 **WS 控制消息**提交快照（Redis 锁 + 哈希去重）；掉线重连/重放；会话结束恢复 revision；WS 故障回退 M1 |
| M2-3 | 回收站 | 页面 `deleted_at/deleted_by`；子树删除/恢复；slug 冲突处理；30 天清理任务 |
| M2-4 | 页面级权限 | `doc_page_acl`、`is_restricted`；与空间角色叠加；搜索/附件/评论统一走 `DocAccessGuard` |
| M2-5 | 知识组织 | 标签、页面引用/反向链接、页面动态、最近访问（服务端） |
| M2-6 | 检索升级 | `FULLTEXT ... WITH PARSER ngram`、标题权重、片段高亮、空间/标签过滤 |
| M2-7 | 评论增强 | 评论回复/提及、轮询或 WS 刷新、未读提示（站内，不依赖 IM） |
| F2-1 | 协作 UI | 在线头像、光标、选区、连接状态、离线降级提示 |
| F2-2 | 回收站/ACL/标签 | 回收站页、页面权限抽屉、标签/反向链接/动态面板 |
| F2-3 | 验证 | 双浏览器协作、断网重连、20 并发保存、全文搜索性能 |

**M2 Gate**：协作功能默认关闭时 M1 行为不变；开启后双人编辑无丢失、断线可恢复；权限升级回归通过。

> 本轮进度（2026-10-29）：**M2-1~M2-7 与 F2-1、F2-2 已完成**。M2-4 页面级权限（`doc_page_acl` + `is_restricted`，空间角色叠加、搜索/树/附件/评论统一走 `DocAccessGuard`）；M2-5 标签/反向链接/页面动态/最近访问；M2-6 `FULLTEXT WITH PARSER ngram` + 空间/标签过滤 + 前端高亮；M2-7 评论未读/标记已读与 @提及输入；F2-2 页面权限抽屉、标签/反链/动态页签、最近访问入口。M4 内网快照与变更流同步排除受限页。
> 配置：`whale.doc.collab-enabled`（默认 `false`，冒烟需 `WHALE_DOC_COLLAB_ENABLED=true` + Redis）；回收站 `recycle-retention-days=30`、`recycle-cleanup-cron=0 30 3 * * *`。
> 验证脚本：`scripts/smoke/bw-doc-m2-smoke.ps1`（回收站子树/恢复/清除 + WS ticket/单次消费/leader 快照/40902/断线恢复，41 项断言）；`scripts/smoke/bw-doc-m2b-smoke.ps1`（ACL/标签/反向链接/动态/最近访问/评论未读/ngram 检索/内网受限页下线，42 项断言）。
> 一键回归：`logs/doc-m2b-verify.ps1`（迁移校验 + M1/M2b/M2/M3/M4 顺序执行，209 项断言 `PASSED`；日志 `logs/doc-m2b-run.log`）。

### M3 治理与生态（14 人日）

| ID | 任务 | 关键交付 |
|---|---|---|
| M3-1 | 模板与复制 | `doc_template`、模板管理页、按模板/页面复制；复制时重映射图片/附件引用，避免跨页 IDOR |
| M3-2 | 导入导出 | Markdown/HTML 导入导出；Word/PDF 作为可选扩展（需单独确认） |
| M3-3 | 发布审批 | `whale-workflow` 流程定义、`businessKey`、`PENDING_APPROVAL`、回调幂等；默认关闭 |
| M3-4 | 消息通知 | 评论提及、审批待办、成员变更 → `message/internal/send`；after-commit、失败告警、幂等键 |
| M3-5 | 登录内分享 | `doc_page_share` token、过期、可读校验；不做匿名公开 |
| M3-6 | 归档 | 页面快照上传 → `archive/internal/items`（`DOC_PAGE`）；**档案服务白名单增加 `DOC_PAGE` + `definitionCode=DOC`**；`snapshotJson` ≤1MB、完整快照走 `originalFileId`；归档幂等、可追溯 |
| F3-1 | 前端页面 | 模板页、导入导出入口、审批状态、分享抽屉、归档提示 |

**M3 Gate**：关闭审批/通知/归档开关时无外部副作用；开启后流程闭环、幂等、审计完整。

> 本轮进度（2026-10-29）：**M3-1~M3-6 与 F3-1 已完成**。模板管理与按模板建页、页面/子树复制（附件引用重映射 + IDOR 防护）、Markdown/HTML 导入导出、发布审批（`PENDING_APPROVAL` + 工作流回调幂等 + 读取补偿同步）、事务后消息通知（评论 @/审批/成员变更，幂等键 + 开关 + 失败降级）、登录内分享（`doc_page_share` token/有效期/撤销即失效）、页面归档快照（`DOC_PAGE` 白名单 + ≤1MB + 幂等）均已落地。
> 配置：`whale.doc.approval-enabled`、`whale.doc.notify-enabled`、`whale.doc.archive-enabled` 默认 `false`；外部服务名为 `message-service-name`/`workflow-service-name`/`archive-service-name`。
> 验证脚本：`scripts/smoke/bw-doc-m3-smoke.ps1`（31 项断言，覆盖模板/复制/导出/导入/分享/审批开关两态/回调幂等/通知降级/归档开关关闭）；`logs/doc-m3-verify.ps1`（`logs/` 不入库）执行迁移校验并跑 M1 + M3 smoke。
> 已验证：M3 迁移 6 列 + 2 表、auth 权限 271–274；M1 回归 65 项、M2 回归 41 项、M3 31 项全部 `PASSED`。

### M4 AI 知识（10 人日）

| ID | 任务 | 关键交付 |
|---|---|---|
| M4-1 | 文档变更流 | `/doc/internal/page/changes` 完整游标 + 墓碑；`/doc/internal/page/{id}` 快照；租户隔离 |
| M4-2 | Agent DOC 源 | `DocKnowledgeClient` + 同步调度；复用切块/脱敏/索引；台账 `source=DOC`；只采 `PUBLISHED + PUBLIC + 未限制`；**不采集 `whale-doc` 桶附件** |
| M4-3 | 检索与引用 | 权限码 `sys:doc:page:read`；结果附页面 URL；删除/归档/转私有即下线索引 |
| M4-4 | 文档 AI 面板 | 「问这篇文档/生成摘要」入口，经 `whale-agent`，权限由 doc 服务校验；M4 可选 |
| M4-5 | 评测 | 复用 `docs/agent/golden-set.json` 增补文档题；索引状态/失败重试可观测 |

**M4 Gate**：私有/成员空间页面不出现在通用知识检索；公开页面引用链接可跳转；下线后索引可清理。

> 本轮进度（2026-10-29）：**M4-1~M4-5 已完成**。
> - M4-1：`changes` 改为返回租户内**全部页面**（LEFT JOIN 空间、不过滤状态），附 `spaceVisibility/spaceActive` 与删除墓碑；游标改为闭区间（`update_time >= since`），规避秒级时间戳在同一秒内漏掉发布/归档/恢复；`detail` 仅返回 `PUBLISHED + PUBLIC + 空间启用 + page_type=PAGE` 页面并返回 `spaceVisibility/spaceActive`，保证撤销发布/归档/删除/转私有/停用空间都会触发下线，且单页重建可复用同一采集判定。
> - M4-2：`whale-agent` 新增 `DocKnowledgeClient` + `DocKnowledgeSyncService` + `agent_knowledge_doc` 台账（`page_id` 唯一、`revision/content_hash`、`source_update_time` 作游标、失败退避与上限）；复用 `FileChunker`/`SensitiveContentMasker`/`AgentIndexService`；索引 kind=`doc_page`、路径 `docpage:{pageId}`；`whale-doc` 桶默认加入 `excluded-buckets`。
> - M4-3：文档知识块权限码 `sys:doc:page:read`；Lucene 存储 `doc_page_id/space_id/url`，`/agent/search` 与问答引用 `CitationVO` 均返回 `url=/doc/space/{spaceId}?pageId={pageId}`；归档/删除/私有/停用即下线。
> - M4-4：前端文档编辑器新增「AI 问答」抽屉（`scope=doc_page` SSE），快捷摘要/要点问题，引用可回跳页面；按钮受 `sys:agent:chat` 权限控制。
> - M4-5：`/agent/knowledge/stats` 增加文档 `pending/indexing/indexed/skipped/failed/chunkCount/cursor/lastRun/lastResult`；`/agent/knowledge/doc/page` 台账分页；手动同步/单页重建/启停/全量重建接口；`docs/agent/golden-set-wikidoc.json` 增补文档题。
> - 验证：`scripts/smoke/bw-agent-doc-m4-smoke.ps1` 30 项断言；`logs/doc-m4-verify.ps1` 执行 doc M3 6 列 + agent 台账校验，并跑 **M1 回归 65 项 + M4 冒烟 30 项全部 `PASSED`**（含归档下线、重新发布恢复、停用/启用重建、私有空间不采集）。

### M5 上线硬化（4 人日）

> - 性能：保存/详情/树/搜索时延在 M5 冒烟中实测（tree 79ms、detail 90ms、search 73ms）；20 路并发同页保存仅 1 个成功、19 个 `40901`。
> - 安全：导出 HTML/Markdown 链接协议白名单（`http/https/mailto/相对路径/#`），`javascript:`/`data:`/`vbscript:` 降级纯文本；图片仅允许 `http/https` 与站内路径；SVG 下载强制 attachment + nosniff；跨页附件 IDOR 返回 `40401`。
> - 备份/恢复：新增 `scripts/ops/doc-backup-restore.ps1`（一致性备份 → 独立校验库恢复 → 14 张核心表/全文索引/受限列/页面行数断言），演练通过。
> - 孤儿附件：新增 `DocOrphanAttachmentCleanupJob`（默认每天 04:30，保留 24h，批 500）与内网手工触发 `POST /doc/internal/ops/orphan-attachment-cleanup`；仅删除无主引用，不删文件字节。
> - 可观测：`DocMetrics` 暴露 `doc_page_save_conflict_total`、`doc_search_seconds{mode}`、`doc_recycle_purged_total`、`doc_orphan_attachment_removed_total` 与失败计数；开启 HTTP 直方图桶（SLO-4）。
> - 运维：新增 [doc-service-ops.md](../ops/doc-service-ops.md)；Prometheus 抓取 8095、3 条文档告警、Grafana 文档行；SLO-4/5。
> - 前端：编辑器工具栏补齐 `aria-label`、新增 ≤768px 基础适配样式；空态/错误态沿用既有页面。
> - 部署：新增 `deploy/backend/whale-doc/Dockerfile`；Nginx 子应用按 `/doc` 通用模板部署。
> - 验证：`scripts/smoke/bw-doc-m5-smoke.ps1` 30 项；`logs/doc-m5-verify.ps1` 顺序执行 **M1 65 + M2b 42 + M2 41 + M3 31 + M4 30 + M5 30 = 239 项 PASSED**，并完成备份/恢复演练。

---

## 六、验收与测试策略

### 6.1 分层测试

| 层 | 内容 |
|---|---|
| 领域单测 | 空间最后 OWNER、角色层级、页面移动环、层级上限、状态机、revision 冲突、slug 唯一、版本恢复 |
| 应用/接口测试 | 空间/页面/评论/附件/搜索权限矩阵；租户隔离；分页上限；参数校验 |
| 前端单测/校验 | 编辑器保存状态、冲突对话框、权限渲染、树操作；`lint` + `build` |
| 冒烟脚本 | `scripts/smoke/bw-doc-smoke.ps1`：登录 → 建空间 → 建页面 → 保存 → 冲突 → 发布 → 评论 → 搜索 → 越权 403 |
| 浏览器 E2E | 双用户空间协作、成员邀请、版本恢复、附件上传预览、移动端基础 |
| 安全用例 | `<script>` / `onerror` / `javascript:` 链接 / SVG inline / 跨页面 fileId / 跨租户 ID |
| 性能用例 | 5k 页树、2MB 正文保存、20 并发同页保存、2 万页搜索 |

### 6.2 构建与门禁

```powershell
# 后端
mvn -pl whale-services/whale-doc -am test
# 前端
pnpm --filter whale-doc lint
pnpm --filter whale-doc build
# 网关/服务冒烟（Nacos、MySQL、Redis 就绪后）
powershell -File scripts/smoke/bw-doc-smoke.ps1
```

提交前必须产出 `docs/reviews/YYYY-MM-DD-wave5.74-doc-m1.md` 检查报告，并在 `docs/devlog/` 记录需求/完成/待做/修正四类信息。

---

## 七、风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 实时协作复杂（Yjs 快照/多实例/断线） | 高 | M2 才做，默认开关关闭；先做 M1 乐观锁；M2 原型验证后再定 Java 中继或 Hocuspocus |
| 正文 JSON 过大/MySQL 包大小 | 中 | 应用层 2MB 上限；DB `max_allowed_packet` 复核；超限提示拆分；后续可外置对象存储 |
| 权限模型膨胀（平台码 + 空间角色 + 页面 ACL） | 高 | 统一 `DocAccessGuard`；禁止控制器散落判断；权限矩阵测试固定 |
| 附件 IDOR / 文件服务权限绕过 | 高 | 附件内容必须经 doc 代理并校验 `pageId + attachmentId`；`doc_member` 不默认授予 `sys:file:download`；M2 评估文件服务按桶/来源 ACL（现有文件服务无桶级 ACL，属平台风险） |
| XSS / 恶意链接 / SVG | 高 | 只存 TipTap JSON；协议白名单；SVG 禁止 inline；导出统一清洗；CSP 兜底 |
| 中文搜索效果差 | 中 | M1 有限 LIKE；M2 FULLTEXT ngram；M4 Agent 语义检索 |
| AI 索引泄漏私有文档 | 高 | 只索引 `PUBLISHED + PUBLIC + 未限制`；成员空间/回收站/页面 ACL 一律排除；索引下线有墓碑 |
| 跨服务依赖（file/message/workflow/archive）不可用 | 中 | 附件上传失败不回滚正文；通知/归档失败只告警；开关默认关闭；补偿任务 |
| 编辑体验/依赖兼容（TipTap + React 19） | 中 | 锁定版本；先做编辑器 spike；构建与浏览器验证纳入 M1 Gate |
| 范围膨胀（白板/表格/Office/匿名站点） | 中 | 严格按非目标裁剪；新增需求重新走 REQ 与设计 |

---

## 八、工作量与排期建议

| 阶段 | 人日 | 主要角色 |
|---|---:|---|
| M0 设计冻结 | 3 | 架构/产品/前端 |
| M1 可用 Wiki | 30 | 后端 2 + 前端 1 |
| M2 协作增强 | 18 | 后端 2 + 前端 1 |
| M3 治理与生态 | 14 | 后端 2 + 前端 1 |
| M4 AI 知识 | 10 | 后端 1–2 + Agent |
| M5 上线硬化 | 4 | 后端/运维/QA |
| **合计** | **79** | 按 2 后端 + 1 前端 + 0.5 QA，约 **6–7 周**（含 20% 缓冲约 8 周） |

> 估算用于排期，不作为交付承诺；M2–M4 可按业务优先级裁剪或并行。

---

## 九、依赖与待确认事项

### 9.1 前置依赖

1. auth 迁移脚本编号与权限/菜单区间确认（当前权限最大 250、菜单最大 35）。
2. 网关 8095/8110 端口与 `/doc/**` 路由确认；M2 WS 路由与白名单确认。
3. 文件服务 `whale-doc` 桶创建/配额确认（内部上传已具备）。
4. M3 工作流流程定义、消息模板、档案来源类型登记。
5. M4 Agent 索引目录/租户清单/评测集更新。

### 9.2 待确认（详见设计文档 §十一）

- Q1 M1 是否必须实时协作（默认否）
- Q2 是否需要页面级私密/部门可见（默认 M2 页面级 ACL）
- Q3 普通成员是否可建空间（默认是）
- Q4 是否必须 Word/PDF 导入导出（默认 M3 Markdown/HTML）
- Q5 正文/附件上限（默认 2MB / 50MB）
- Q6 AI 是否只索引公开已发布页面（默认是）
- Q7 是否允许匿名公开分享（默认否）
- Q8 发布是否必须审批（默认关闭，M3 可配置）

---

## 十、交付物清单（Definition of Done）

- [x] `docs/design/doc-service.md` 评审定稿
- [x] `docs/requirements/register.md` REQ-FUNC-027/028、REQ-ARCH-012、REQ-SEC-018
- [x] `docs/naming-conventions.md`、`docs/service-catalog.md` 登记
- [x] `backend/whale-services/whale-doc`：代码、`db/init.sql`、单测
- [x] auth `migration-5.74-doc.sql`：权限 251–270、角色、菜单
- [x] auth `migration-5.75-doc-m3.sql`：权限 271–274、授权、菜单 38；`whale-doc` `migration-5.75-doc-m3.sql` 与 `init.sql` 同步
- [x] gateway `/doc/**`、internal-deny、M2 `/doc/ws`
- [x] `frontend/platform-apps/whale-doc`：页面、编辑器、lint/build（M2 协作编辑器与回收站抽屉已通过 `lint`/`build`）
- [x] qiankun 注册、菜单/图标、部署与 Nginx 配置（M5 新增 `deploy/backend/whale-doc/Dockerfile`，Nginx 走 `/doc` 通用子应用模板）
- [x] `scripts/smoke/bw-doc-smoke.ps1`、`bw-doc-m1-smoke.ps1`、`bw-doc-m2-smoke.ps1`、`bw-doc-m2b-smoke.ps1`、`bw-doc-m3-smoke.ps1`、`bw-agent-doc-m4-smoke.ps1`、`bw-doc-m5-smoke.ps1` 与执行记录（`logs/doc-m5-verify.ps1`）
- [x] `docs/reviews/2026-10-29-wave5.74-doc-m5.md`
- [x] `docs/devlog/2026-10-29.md` 需求/完成/待做/修正记录
- [x] Prometheus 指标/日志/告警与运维说明（[doc-service-ops.md](../ops/doc-service-ops.md)、[slo.md](../ops/slo.md)）

---

## 十一、下一步

1. 上线前完成人工浏览器回归：双人实时协作、断网重连、20 并发保存提示、受限页 ACL 与分享/审批/归档组合场景。
2. 按灰度顺序开启 `WHALE_DOC_APPROVAL_ENABLED` → `WHALE_DOC_NOTIFY_ENABLED` → `WHALE_DOC_ARCHIVE_ENABLED` → `WHALE_DOC_COLLAB_ENABLED`，每步观察 1 个工作日。
3. M3/M4 生产量联调：审批/通知/归档真实工作流，Agent 索引规模与受限页权限细化；M5 指标与告警接入生产 Prometheus/Grafana。
