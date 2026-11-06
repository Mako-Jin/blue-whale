# Wave 5.80 PDM 收口计划（完善与完成）

> 日期：2026-11-06　状态：**已完成**（后端 218 用例全绿、前端 lint/build 通过、文档一致性 PASS；已提交并推送 `origin/main`）
> 关联计划：[2026-11-04-wave5.80-pdm-panorama.md](../../docs/plans/2026-11-04-wave5.80-pdm-panorama.md)
> 关联路线图：[roadmap.md](../../docs/plans/roadmap.md) Phase 10

---

## 一、现状对账（只读审计结论）

- `whale-pdm` 保持 9003 / `/pdm/**` / `bluewhale_pdm` / 前端 8106；13 个 Flyway 版本、33 张业务表、259 个 Java 文件。
- 后端测试实跑：`mvn -o -pl backend/whale-apps/whale-pdm test` → 通过（197 用例基线）。
- 源码无 `TODO/FIXME/未实现` 残留；M0–M5 计划任务表中真实缺口仅以下两类。

### 1.1 文档与代码不一致（需收口）

| 位置 | 现状 | 问题 |
|---|---|---|
| PDM 计划 §5.4 第 141 行 | 仍写「未完成（M0-B 剩余）：预览任务、导出、下载短时票据、部门主体（DEPT）ACL」 | 与同文件 §5.5 及 M4/M5 状态自相矛盾；预览已在 M4-3 落地 |
| roadmap 10.1b | 勾选「预览任务 + 导出」 | 文档**列表导出**在后端不存在（只有 BOM Excel 导出） |
| 遗留项分散 | 散落在 §5.4/§10.1/§11.1/各评审报告 | 无统一清单，无法判断“是否完成” |

### 1.2 可落地的功能缺口（无外部依赖）

| 编号 | 缺口 | 依据 |
|---|---|---|
| C-1 | 图文档列表/版本导出（CSV） | roadmap 10.1b；权限号段 912 已预留未用 |
| C-2 | 集成同步失败告警接 `whale-message` | M5-6「告警到 whale-message」标注留待；`/message/internal/send` 契约已在 M2-8 打通 |

### 1.3 明确不做（外部依赖 / 需跨服务改造，另立项）

| 项 | 原因 |
|---|---|
| CAD 插件本体（SolidWorks/AutoCAD） | 外部桌面工程，仓库只交付契约（M5-1 已完成） |
| STEP/IGES 3D 渲染、Office→PDF、DWG 转换 | 需 FreeCAD/OCCT/LibreOffice/商业转换器，部署依赖 |
| MySQL ngram 全文索引、标题栏 OCR | 数据库插件与算法依赖，按 D-PDM-3 决策另行启用 |
| 下载短时票据 | 需网关对 `/pdm/document/download` 放行匿名票据，属网关鉴权改造 |
| DEPT 主体 ACL | 网关未注入部门头（`WebHeaders` 无 `X-Dept-Id`），需 auth+gateway+common 联合改造 |
| 真实 ERP/MES/OA 联调 | 需联调环境 |

---

## 二、实施范围

### 2.1 A 档：文档收口（无行为变更）

1. 修正 PDM 计划 §5.4 第 141 行为真实状态：预览已在 M4-3 落地；导出中 BOM 导出已在 M1B、文档列表导出在本次 C-1 补齐；票据与 DEPT ACL 标注“未启用 + 原因 + 归属”。
2. 在 PDM 计划追加「§十九 遗留与外部依赖清单」：统一列出 §1.3 各项的归属、触发条件与验收口径。
3. 修正 roadmap 10.1b 表述为「预览（M4-3）+ BOM 导出（M1B）+ 文档导出（本波）」，并在状态对账行注明剩余外部项。
4. 同步 `docs/service-catalog.md` PDM 行（端点数量/导出能力），确认 `requirements/register.md` 无需状态变更。

### 2.2 B 档：代码补齐

#### C-1 图文档导出

| 项 | 内容 |
|---|---|
| 端点 | `GET /pdm/document/export`，查询参数与 `PdmDocDocumentQueryRequest` 一致，返回 `text/csv;charset=UTF-8`（UTF-8 BOM） |
| 列 | 文档编号、标题、类型、状态、所属目录、零件号、设计师、当前版本、创建时间、更新时间（固定七列→十列，不含人员敏感字段） |
| 规则 | 复用现有 ACL 过滤（DENY 目录子树/DENY 文档不计入）；上限 5000 行；超限截断并在响应头 `X-Export-Truncated: true`；`@AuditLog(OperateType.EXPORT)` |
| 权限 | 新增 `sys:pdm:doc:export`（912）；`tenant_admin`/`pdm_manager`/`pdm_doc_manager` 授予，`pdm_member` 不授予 |
| 后端文件 | `PdmDocDocumentService(+Impl)` 增加 `export`；`PdmDocDocumentController` 增加端点；抽 `buildWrapper` 复用分页条件（避免复制查询逻辑） |
| 迁移 | `migration-5.80-pdm-doc-export.sql`（幂等，912 + 角色授权） |
| 前端 | `pages/document/index.tsx` 增加「导出」按钮（权限控制），Blob 下载文件名 `pdm-documents-{yyyyMMdd}.csv` |
| 单测 | 表头与 BOM、字段转义（逗号/引号/换行）、5000 行截断、ACL 过滤生效 |

#### C-2 集成失败告警

| 项 | 内容 |
|---|---|
| 端口 | `integration/client/PdmIntAlertClient` + Impl，复用 `InternalTokenSupport.restClient()` 调 `POST {whale-message}/message/internal/send` |
| 触发 | `PdmIntSyncServiceImpl.record` 落库为 `FAILED` 且“新建或错误信息变化”时异步告警；`retry` 转成功不告警 |
| 幂等键 | `PDM:ALERT:{channel}:{idempotencyKey}:{status}` |
| 接收人 | 配置 `whale.pdm.alert.user-ids`（默认空 → 跳过并 debug 日志）；渠道默认 `INBOX` |
| 隔离 | 告警异常全部捕获只记 `warn`，绝不影响同步事务与返回 |
| 单测 | 失败触发一次、重复记录不重复告警、成功不告警、空接收人跳过、告警异常不外抛 |

---

## 三、验收与验证

| 检查 | 命令/口径 |
|---|---|
| 后端测试 | `$env:JAVA_HOME='D:\java'; mvn -o -pl backend/whale-apps/whale-pdm test`（基线 197，新增 ≥9） |
| Checkstyle | 随 Maven `checkstyle-gate`，0 违规 |
| ArchUnit | 随测试执行，模块边界与分层不得新增违规 |
| 前端 | `pnpm --filter whale-pdm lint`、`build`（cwd `frontend`） |
| 文档一致性 | `powershell -ExecutionPolicy Bypass -File scripts/check-doc-consistency.ps1` → `DOC-CONSISTENCY-PASS` |
| 文档回写 | PDM 计划状态/遗留清单、roadmap、service-catalog、devlog、review 报告 |

---

## 四、交付流程

1. 批准后实施 B 档代码 + A 档文档，全部验证通过。
2. 仅提交本波涉及文件（`backend/whale-apps/whale-pdm/**`、`frontend/platform-apps/whale-pdm/**`、PDM 相关 auth 迁移、PDM 文档）；**不得触碰** 5.89 邮箱与 5.91 地图的未提交内容（`whale-services/pom.xml`、`whale-gateway/application.yml`、`prometheus.yml`、`01-init-databases.sql`、`whale-map/`、`migration-5.91-map-m0.sql` 等）。
3. 提交并推送 `origin/main`，输出四段式报告（需求项 / 完成项 / 待做项 / 已修正问题项）。
