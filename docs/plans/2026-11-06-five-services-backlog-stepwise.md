# PDM / MES / OA / CRM / ERP 待办总账与分步实施计划

> 日期：2026-11-06　状态：**已批准，执行中**（Step 1 → Step 7 逐批实施，每批独立验证、提交、推送）
> 进度：**Step 1a 已完成**（2026-11-06，CRM 前端 8113 客户域）；Step 1b 与 Step 2–7 未开始。
> 依据：[roadmap.md](roadmap.md) 状态对账（2026-11-06）、[PDM 计划 §十九](2026-11-04-wave5.80-pdm-panorama.md)、[MES 计划](2026-11-04-wave5.79-mes-panorama.md)、[OA 计划](2026-11-04-wave5.82-oa-panorama.md)、[CRM 计划](2026-11-04-wave5.83-crm-panorama.md)、[ERP 计划](2026-11-03-wave5.78-erp-panorama.md)
> 纪律：每批动工前先做一次代码对账（文档标注 ⏸ 的项可能已被后续波次补齐），只做确认缺失的；只提交本批文件；每批结束输出四段式报告。

---

## 一、结论

| 服务 | 已完成 | 剩余主体 |
|---|---|---|
| PDM | M0–M5 全量 + 2026-11-06 收口（图文档导出、集成告警） | **无仓库内代码缺口**；6 项外部依赖（CAD 插件、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调）见 PDM §十九 |
| ERP | M0–M6（含 M2 七期、M3 一阶段、M4 一阶段、M5 一期） | M3 剩余 **MRP/CRP/预测增强/生产增强**；M2 剩余 **批次追溯/财务扩展**；ERP↔MES 工单下发与完工回传 |
| MES | M0–M5 全量（70 表、370+ 文件） | 文档标 ⏸ 的增强项：有限产能排程、数据范围上下文过滤、采集推送通道（SSE/WS）、谱系冲销、线边领料联动、设备维度追溯、开工门禁强制、归档物理清理 |
| OA | M0 + M1A 全量 + M1B-1/M1B-2 | M1B 预算超限条件分支；**M2–M6 全部未开始**（考勤/报销/用车/用章/采购申请、协同、公文档案、文化投票、报表试点）；M1A-6 移动端适配 |
| CRM | M0 + M1 + M2 **后端** | **前端 8113 完全未建**（仓库无 `frontend/platform-apps/whale-crm`）；M1-4 客户合并、M1-9 导入导出未做；M3–M6 全部未开始 |

> 结论：真正“有后端无界面”的只有 CRM；真正“里程碑级空白”的是 OA M2–M6、CRM M3–M6、ERP M3 剩余。PDM 已无代码可做，MES 只剩增强项。

---

## 二、分步顺序（依赖优先，不插队）

| 步骤 | 主题 | 范围 | 依赖 | 预估批量 |
|---|---|---|---|---|
| **Step 1** | CRM 前端 8113 | 脚手架（8113、qiankun 注册、登录/布局/路由）、客户/联系人/跟进/公海、线索/转化/商机/阶段/漏斗、报价/预测/目标共 10 页 + API 层 | 后端 M1/M2 已完成，仅缺前端 | 1 波（可拆 1a 客户域 / 1b 销售域）——**1a 已完成，1b 待做** |
| **Step 2** | ERP MRP | BOM 展开（取 PDM `/pdm/internal/bom/**` 快照）、计划订单生成、净需求与需求追溯、MRP 运算 Job 与结果页 | PDM 快照契约已交付 | 1 波 |
| **Step 3** | ERP CRP + 生产增强 | 工作中心/产能负荷（取 PDM 工艺路线）、排程与甘特；工序报工、倒冲领料、退料/报废 | PDM M3 工艺已交付 | 1–2 波 |
| **Step 4** | ERP ↔ MES 打通 | ERP 生产订单下发 MES、MES 完工/报工回传 ERP、双向幂等与对账页 | ERP M3、MES M1–M2 已完成 | 1 波 |
| **Step 5** | OA M2 首批 | 考勤 + 报销 + 用车/用章/采购申请的领域化（复用 Workflow/Form）；M1B 预算超限条件分支 | Workflow 条件表达式运行期求值 | 2–3 波 |
| **Step 6** | MES 遗留增强 | 有限产能排程、采集推送通道（SSE/WS）、数据范围上下文过滤、谱系冲销/线边联动/设备追溯/开工门禁、归档清理 | auth `data-scope` 就绪（第 3 项） | 2 波 |
| **Step 7** | OA M3–M6 + CRM M3–M6 + ERP 批次/财务扩展 | 协同办公、公文档案、文化投票、报表试点；CRM 合同联动/售后/营销/分析；ERP 批次追溯与财务四切片（D5 定稿后） | 业务决策 D5/D6/D8/D11、真实联调环境 | 多波，按决策推进 |

**默认执行顺序**：Step 1 → Step 2 → Step 3 → Step 4 → Step 5 → Step 6 → Step 7。若用户指定某一步优先，按指定执行。

---

## 三、每步的通用验收与交付

| 检查 | 口径 |
|---|---|
| 后端 | 对应模块 `mvn -o -pl <module> test` 全绿；Checkstyle 无新增违规；ArchUnit 通过 |
| 前端 | 对应应用 `pnpm --filter <app> lint` / `build`（cwd `frontend`） |
| 迁移 | 新增 Flyway 只追加不改历史；真实库可重放 |
| 文档 | 计划/roadmap/service-catalog/devlog/review 同步；`check-doc-consistency.ps1` → PASS |
| 交付 | 仅提交本批文件；推送 `origin/main`；四段式报告（需求项/完成项/待做项/已修正问题项） |

---

## 四、工作区红线（每批必须遵守）

- 5.89 邮箱与 5.91 地图未提交内容不得 `git add`：`backend/whale-services/pom.xml`、`whale-gateway/application.yml`、`deploy/monitoring/prometheus.yml`、`infrastructure/docker/mysql/init/01-init-databases.sql`、`backend/whale-services/whale-map/`、`migration-5.91-map-m0.sql`。
- `.trae/documents/crm-frontend-8113-implementation.md` 与 `docs/plans/2026-06-06-mail-server-and-map-service.md` 不提交；stash `pre-pull-local-edits-20261106` 不得 pop。
- 新功能一律先文档（计划/ADR/权限号段）后代码；权限号段与菜单号段先查 `naming-conventions`，避免与 5.89/5.91 冲突。

---

## 五、Step 1 启动清单（1a 已完成，1b 待做）

1. ~~对账：读 CRM 后端 10 个 Controller 的端点和 DTO，冻结 `api/crm.ts` 契约。~~（1a 已冻结客户域契约）
2. ~~脚手架：`frontend/platform-apps/whale-crm`（8113、base `/crm`），注册进基座 `apps.ts` 与菜单。~~（已完成）
3. 页面：客户（列表/详情/联系人/跟进/公海）~~已完成~~；线索/转化、商机/阶段/漏斗、报价、预测、目标 **待 1b**。
4. 权限：CRM 已有 1800–1899，按页面按钮补齐 `list/detail/add/edit/delete/export` 缺失项（只追加）。1a 未新增权限号，仅登记菜单 413–416。
5. 验证：前端 lint/build 已通过（1a）；1b 完成后同样执行。
