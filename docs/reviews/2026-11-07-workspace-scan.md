# 工作区扫描报告（2026-11-07）

> 扫描范围：`whale-backend/`、`whale-frontend/`、`ai/`、`docs/`（plans / specs / reviews / devlog）、`scripts/`、`deploy/`、git 工作区状态、关键端口。
> 口径：只登记**当前仓库仍存在**的缺口；已交付项不再重复登记（见 §六）。
> 上一份：[2026-11-06 工作区扫描](2026-11-06-workspace-scan.md)。

## 一、扫描证据

| 项 | 结果 |
|---|---|
| 分支 | `main` 与 `origin/main` 同步，HEAD `1bec98d2`（feat(oa): 考勤管理 M2 首批） |
| 工作区 | **无已修改未提交文件**；9 个未跟踪项（5 个 `.trae` 方案稿 + 报销半成品 3 目录 + `V8__expense_admin.sql`） |
| stash | 仍存 `pre-pull-local-edits-20261106`（**不得 pop**） |
| 端口 | MySQL 3306 **OPEN**；Redis 6379 / Nacos 8848 / 网关 8080 / 9001 / 9002 / 9004 / 9010 **全部 CLOSED** |
| 文档门禁 | `scripts/check-doc-consistency.ps1` → **DOC-CONSISTENCY-FAIL（2 项）**：`service-catalog.md` 缺 OA `V7__attendance.sql`、`V8__expense_admin.sql` 记录 |
| 代码标记 | 主代码无未实现 `TODO/FIXME`；`whale-games-core/src/engine.ts` 2 处 TODO 属 F-07 冻结项；PM 的 `TODO` 为业务状态码，非待办 |

## 二、进度快照

| 波次 | 状态 |
|---|---|
| ERP MRP / CRP / 生产增强 / ERP↔MES | ✅ 已交付（V13–V16，509 用例） |
| 商城 Wave 5.94–5.98 | ✅ M0–M1 + 售后、计价、物流已交付（V1–V6，205 用例） |
| OA M1A / M1B-1 / M1B-2 / M1B-2b | ✅ 已交付 |
| **OA M2-5a 考勤** | 🟡 后端已提交（V7 + 29 端点 + 权限 1680–1689），**前端未建、应用层测试仅 1 个类** |
| **OA M2-5b 报销** | 🔴 半成品未提交：domain/infrastructure/command/view 已写，**service/listener/controller/测试/权限脚本/前端全缺**；V8 只含报销 5 表 |
| OA M2-5c 用车/用章/采购、5d 前端与文档 | ⬜ 未开始 |
| LMS M0–M6 / 党政工会 M-P1–M-P4 | ✅ 代码交付，真实库与试点复盘待环境 |
| CRM 前端 8113 1a/1b | ✅ 已建（菜单 413–420） |
| PDM M0–M5 + 收口 | ✅ 无仓库内代码缺口，剩 6 项外部依赖 |

## 三、待办

### P0（当前批次内必须收口）

| 事项 | 依据 |
|---|---|
| 修复文档一致性门禁：`service-catalog.md` whale-oa 行补记 V7/V8 | 门禁实跑 FAIL |
| 完成 OA M2-5b 报销：`ExpenseAppService`+实现、`ExpenseApprovalResultListener`、`ExpenseController`、应用层测试、`migration-5.82-oa-m2-expense-admin.sql`（1690–1699 / 菜单 377–379） | `.trae/documents/oa-m2-step5-implementation.md` §5b |
| 补 OA M2-5a 考勤前端四页（打卡/日报/规则/补卡异常）+ `api/attendance.ts`，否则 29 个端点无入口 | 同上 §5d；`whale-oa/src/pages/` 仅 approval、hr |
| 补考勤应用层测试（现仅 `AttendanceDailyCalculatorTest` 1 个类，覆盖不到 29 端点） | 同上 §5a「测试」 |
| 提交并推送 OA M2 5a/5b（当前报销代码全部游离在版本库外，存在丢失风险） | `git status` |

### P1（下一批，按依赖排序）

| 事项 | 依据 |
|---|---|
| OA M2-5c 用车/用章/采购申请 + ERP `ErpInternalOaController`、Contract `ContractInternalSealController` 内网端点 | 计划 §5c |
| 真实库执行各波迁移并起服冒烟（清单见 §四.2） | 各波 devlog「待做」 |
| CRM 遗留：负责人筛选接线、跟进记录编辑/删除、公海释放免手填 ID、日期字段改 DatePicker | 1a/1b 评审「遗留」 |
| ERP M3 剩余：计划订单转采购/生产、MPS 自动取数、在途与安全库存占用、联产品/副产品、用料调整查询与成本报表下钻 | devlog 2026-11-07 §3b「待做」 |
| MES 遗留增强 7 项（有限产能排程、SSE/WS 推送、数据范围过滤、谱系冲销、线边领料联动、设备维度追溯、开工门禁、归档物理清理）+ D1–D16 业务口径现场复盘 | 五服务计划 §一 |
| LMS / 党政工会：真实库跑 V1–V9、起服冒烟、试点支部复盘；课件/封面/附件接 `whale-file` 上传控件；工会会费逐笔流水与收缴率 | Wave 5.87/5.88 各节「待做」 |
| 商城：退货物流回写/WMS、换货、售后超时关闭、举证图片、按运费与优惠分摊退款、券叠加/品类券、按件重计运费、报表口径拆分、5.95–5.98 实机冒烟 | Wave 5.96–5.98「待做」 |

### P2（储备，不插队）

| 事项 | 依据 |
|---|---|
| OA M3–M6（协同/公文档案/文化投票/报表试点）、M1A-6 移动端适配 | roadmap 11.5–11.8 |
| CRM M3 商务 / M4 售后 / M5 营销 / M6 分析 | roadmap 12.2–12.7 |
| PDM 6 项外部依赖（CAD 插件、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调） | PDM 计划 §十九 |
| 文档治理 DOC-01–DOC-10、`whale-scene` M3 3D（需 F-09 解冻四条件） | roadmap 8.1 / 8.5 |
| 商城买家侧视角拆分、基座菜单树路由联动实机点检、企业微信/物流商等外部对接 | Wave 5.94 前端「待做」 |

## 四、阻塞

1. **实机验证全阻塞**：本机仅 MySQL 3306 可连，Redis/Nacos/网关与全部业务服务未启动，所有「真实库 + 起服冒烟」无法执行。
2. **真实库迁移未执行（累计）**
   - ERP：V13–V16 + `migration-5.78-erp-mrp|crp|production-enhance|mes.sql`
   - OA：V5–V8 + `migration-5.82-oa-m1b*`、`migration-5.82-oa-m2-attendance.sql`
   - Workflow：V5/V6；Auth：`5.82-oa-m1b*`、`5.87-lms-*`、`5.88-party-*`、`5.94/5.95/5.96/5.97/5.98-mall*`
   - LMS：V1–V9；Party：V1–V6；Mall：V1–V6；Pay：V1–V2
3. **跨服务契约阻塞**：加班费月汇总推 ERP 依赖 M2 薪资契约；报销冲抵预支依赖 M2；PDM 库存可用/预占口径依赖 ERP 预占模型；OA 报销推送 ERP 与用章授权校验依赖 §三 P1 的内网端点先落地。
4. **部署阻塞**：prod compose 覆盖层实跑缺 Let's Encrypt 证书（V-09 唯一未闭环项）。
5. **范围性不做（已决策）**：CA/TSA、真实实名与意愿、真实短信/邮件、合同供应商沙箱、Gitee Go 均按 mock / 不做口径。

## 五、BUG 与风险

| 级别 | 问题 | 证据 | 建议 |
|---|---|---|---|
| **高** | **文档一致性门禁红灯进主干**：`1bec98d2` 提交考勤 V7 后未登记 `service-catalog.md`，本轮实跑 FAIL 2 项 | `scripts/check-doc-consistency.ps1` 输出 | 立即补记 V7/V8（V8 随 5b 一起），并把该脚本纳入 CI 阻断 |
| **中高** | **报销半成品游离在版本库外**：`expense/` 三目录 + `V8` 全为未跟踪，且 V8 已建 5 张表但无对应服务代码；一旦误提交会造成「有表无接口」，若真实库先跑 V8 则留下空表 | `git status --porcelain` | 5b 收口前不 `git add` 不完整代码；先补齐 service/controller 再一次性提交 |
| 中 | **CRM 客户列表负责人筛选静默失效**：`CustomerPageParams` 无 `ownerId` 字段，而 `CustomerQuery` 有，前端传参被丢弃 | `whale-crm/.../interfaces/support/CustomerPageParams.java` 仅 keyword/status/level/source/industry/createdFrom/To | 补字段并绑定到 `CustomerQuery` |
| 中 | **OA 附件无归属校验**：请假/报销仅登记 `fileId`，未校验上传者与租户，存在跨用户引用 | `whale-oa/hr` 下无 owner/tenant 校验代码 | `whale-file` 补上传者/租户校验，OA 下载走代理 |
| 中 | **OA 审批通知同步发送**：`ApprovalNotificationListener` 用 `@EventListener`（非 `@TransactionalEventListener` + 异步），消息服务抖动直接拉长审批接口耗时 | 监听器三个 `@EventListener` | 改事务提交后异步投递，失败仅告警 |
| 中 | **考勤模块测试覆盖过薄**：29 个端点仅 1 个领域测试类，迟到/早退/补卡月上限/日汇总重算等核心规则无应用层用例 | `src/test` 下 attendance 仅 1 文件 | 随 5b 一并补 6–8 个用例，避免覆盖率门禁回退 |
| 低 | `/auth/refresh` 返回 HTTP 200 + 业务码 401，属接口约定差异（遗留） | 2026-11-06 扫描登记，未变更 | 先定口径再改，勿单独改动 |
| 低 | JaCoCo「零测试模块即跳过」盲区：`whale-common-core` / `whale-common-domain` / `whale-auth-sdk` | N-11 观察项 | 补最小测试或加测试类存在性校验 |
| 观察 | 权限/菜单号段靠人工核对：5.89 邮箱菜单曾撞工会 481，靠 `WHERE NOT EXISTS` 静默吞掉 | Wave 5.88 M-P3「已修正问题」 | 建议脚本化扫描全仓号段占用，纳入动了迁移的批次 |
| 红线 | stash `pre-pull-local-edits-20261106` 含已回滚的抄送截断缺陷，**不可 pop** | 2026-11-06 扫描 §五.1 | 确认后丢弃 |

## 六、本轮核实已闭环（从旧清单移除）

- 党政工会 M-P2 党建内容、M-P3 工会五域、M-P4 看板与提醒：已交付（单测 158）。
- CRM 前端 8113 Step 1a 客户域 + 1b 销售域：已建并登记菜单 413–420。
- PDM 收口（图文档导出 + 集成失败告警）：已交付。
- OA M1B-2b 出差报告未交提醒、M1A-4 通知/对账/附件上传：已交付。
- 商城 Wave 5.95–5.98（支付闭环、售后、计价、物流）：已交付。
- `pending-issues-2026-10-27.md` 的 T/S/F/V/N 系列：全部闭环，仅 V-09 生产 compose 实跑待证书。

## 七、建议执行顺序

1. 补 `service-catalog.md` V7/V8 → 门禁转 PASS（10 分钟，先止血）。
2. 收口 5b 报销（service/controller/listener/测试/权限脚本）→ 与 5a 一起补前端四页。
3. 5a/5b 一次性提交并推送，同步 devlog 2026-11-07、OA 全景 §11.4、五服务计划 Step 5 状态（当前仍写「Step 5–7 未开始」，已失真）。
4. 起 Redis/Nacos + 相关服务，按 §四.2 清单执行迁移与冒烟。
5. 再开 5c（用车/用章/采购 + 两条内网契约）。

## 八、补记（同日 12:56 复核：本报告结论的时效边界）

本报告扫描时点为 HEAD `1bec98d2`。此后同一工作区有并行开发推进，**下列结论已被后续提交取代**，保留原文以便对照：

| 原结论（章节） | 现状 | 依据 |
|---|---|---|
| 门禁 FAIL 2 项（§一、§五 高） | ✅ 已修复：`service-catalog.md` 补记 OA `V7`/`V8`，复跑另发现并补齐 Mall `V6__mall_shipment.sql`，现 **DOC-CONSISTENCY-PASS** | `fb1efd55` 收录 catalog 改动 |
| 报销半成品游离在版本库外（§二、§五 中高） | ✅ 已收口：报销 14 端点（申请 8 + 类目 6）、行政 33 端点（用车 18 / 用章 8 / 采购 7）全部提交 | `8b113470`、`037b39f7` |
| 考勤/报销前端未建（§二、§三） | ✅ 5d 已交付 **10 页**（考勤 4：打卡/日汇总/规则/补卡；报销 3：申请/类目/台账；行政 3：用车/用章/采购） | `fb1efd55` |
| 文档失真（§三 P0 末项） | ✅ 已回填：devlog、OA 全景 §11.4、五服务计划 Step 5 状态、roadmap | `fb1efd55` |
| 建议顺序 1–3（§七） | ✅ 已完成；下一步为 §四.2 的真实库迁移与起服冒烟 | `efa02b6f`、`50dac457`（MES Step 6a/6b） |

**仍然有效**：§四 全部阻塞（仅 MySQL 3306 可连，Redis/Nacos/网关/业务服务未启动）、§五 中 CRM `ownerId`、OA 附件归属、审批通知同步发送、JaCoCo 盲区、号段人工核对、stash 红线。另新增遗留：Wave 5.98 无 devlog 章节、mall 端点与测试总数未回填（catalog 已就地注明）。

