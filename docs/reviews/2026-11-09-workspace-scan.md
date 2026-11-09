# 工作区扫描报告（2026-11-09）

> 扫描范围：`backend/`、`frontend/`、`docs/`（plans / reviews / devlog / service-catalog）、`scripts/`、git 工作区状态、关键端口。
> 口径：只登记**当前仓库仍存在**的缺口；已完成项不再重复登记。
> 上一份：[2026-11-07 工作区扫描](2026-11-07-workspace-scan.md)。

## 一、扫描证据

| 项 | 结果 |
|---|---|
| 拉取 | `git pull origin main` 成功，`main` 与 `origin/main` **0/0 同步** |
| HEAD | `17f70def`（2026-11-08 CRM M5 营销 + M6 分析 + M1-4 客户合并 + M1-9 CSV 导入导出） |
| 工作区 | **干净**，无已修改、无未跟踪文件；`git stash` 为空 |
| 文档门禁 | `scripts/check-doc-consistency.ps1` → **DOC-CONSISTENCY-PASS**（父 POM：Spring Boot 4.0.8 / Spring Cloud 2025.1.2 / SCA 2025.1.0.0） |
| 端口 | 3306 / 6379 / 8848 / 8080 / 9001 / 9002 / 9003 / 9004 / 9005 / 9010 **全部 CLOSED** |
| 代码标记 | 主代码无未实现 `TODO/FIXME`；检索到的 `TODO` 均为 PM 业务状态码（`TODO_STATUS_ID`），非待办 |
| 新增服务 | `whale-mail`、`whale-map` 已入 `backend/whale-services/pom.xml` 与网关 `lb://` 路由；权限迁移 `migration-5.89`–`migration-5.99` 均已入库 |

## 二、进度快照

| 波次 | 状态 |
|---|---|
| ERP MRP / CRP / 生产增强 / ERP↔MES | 已交付（V13–V16，517 单测） |
| 商城 Wave 5.94–5.98 | 已交付（V1–V6，M0 内核 + 支付闭环 + 售后 + 计价 + 物流） |
| OA M0–M6 | 代码已交付（V7–V15，权限 1600–1799，后端 234 单测 + 前端多页） |
| CRM M0–M6 | 已交付（V1–V12，前端 8113，329 单测） |
| PDM M0–M5 + 收口 | 无仓库内代码缺口，剩 6 项外部依赖 |
| MES M0–M5 + Step 6a/6b | 已交付，剩数据范围过滤与归档清理（依赖未就绪） |
| LMS M0–M6 | 代码已交付（27 表 / 92 端点），真实库与试点复盘待环境 |
| 党政工会 M-P0–M-P4 | 代码已交付（20 表 / 95 端点 / 前端 14 页 / 158 单测） |
| 邮箱 5.89/5.90、地图 5.91–5.93 | 已落地并入库，实机与 MTA 待环境 |

## 三、任务

### 3.1 代码已完成、待验收

| 事项 | 说明 |
|---|---|
| 真实库迁移 + 起服冒烟 | ERP V13–V18、OA V5–V15、Workflow V5/V6、Auth `5.82`–`5.99`、LMS V1–V9、Party V1–V6、Mall V1–V6、Mail/Map 均未在真实库重放 |
| 商城 5.95–5.98 实机冒烟 | 支付、售后、计价、物流仅单测 + 门禁通过 |
| CRM 联调 | 营销发送依赖 `whale-message` 投递回执；`snapshot-cron` / `action-plan-cron` 待验证触发与幂等；分析导出暂存 `MEDIUMTEXT`，待接 `whale-file`；客户合并对 `whale-contract` 的重写接口未接入 |
| ERP↔MES 链路 | 生产订单下达、完工/报工回传、对账页待实机 |
| LMS / 党政工会 | 真实库跑迁移、起服冒烟、试点支部与学员复盘 |
| 邮箱 MTA | Mailcow 部署、域名/PTR/SPF/DKIM/DMARC、Auth 建用户自动开户；方案文档仍标「待评审」 |

### 3.2 规划中、未开工

| 来源 | 事项 |
|---|---|
| [roadmap.md](../plans/roadmap.md) 8.1 / 8.5 | 文档治理 DOC-01–10；`whale-scene` M3 3D（需 F-09 解冻四条件） |
| roadmap 11.3 | OA 出差预算超限条件分支（依赖 Workflow 条件表达式运行期求值） |
| roadmap 13.10 | 新域动工前先完成 OA M1A-6 移动端适配与 OA 真实用户试点 |
| [五服务总账](../plans/2026-11-06-five-services-backlog-stepwise.md) | MES 数据范围上下文过滤（依赖 auth `data-scope`）、归档物理清理（依赖 D14）；ERP M3 剩余（计划订单转单/联产品副产品/MES 联动）、M2 批次追溯/财务扩展（待 D5） |
| PDM 计划 §十九 | CAD 插件、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调 |
| 商城 | 售后超时自动关闭、举证图片、券叠加/品类券、按件重算运费、报表口径拆分、退货物流回写/WMS、换货 |
| LMS / 工会 | 课件与封面接 `whale-file` 上传控件；工会会费逐笔流水与收缴率 |
| OA M6 | 安全/性能加固与试点部门验证（运营事项） |

## 四、阻塞

1. **实机验证全阻塞**：本机 MySQL/Redis/Nacos/网关/业务端口实测全部关闭，所有「真实库 + 起服」动作无法执行。
2. **真实库迁移未执行（累计）**：见 §3.1 首行，覆盖 8 条产品线。
3. **决策阻塞**：D5（ERP 批次/财务扩展）、D2（CRM→ERP 客户主数据映射）、Workflow 条件表达式求值、auth `data-scope`、D14（分区/对象存储）。
4. **外部依赖**：PDM 六项；生产 compose 覆盖层缺 Let's Encrypt 证书（`pending-issues` 中 **V-09 唯一未闭环项**）；Mailcow 需公网 25/465/587 与域名 PTR。
5. **CI 门禁阻塞**：`whale-oa` Checkstyle 325 条 error，`checkstyle:check` 实测失败。

## 五、BUG 与风险

| 级别 | 问题 | 证据 | 建议 |
|---|---|---|---|
| **高** | `whale-oa` Checkstyle **325 条 error**，门禁无法转绿；集中于 `collab` / `administrative` / `document` / `culture` 的 `MissingJavadocMethod` 与少量 `UnusedImports` | 本机 `mvnw -pl backend/whale-apps/whale-oa checkstyle:check` → BUILD FAILURE | 单独批次补 Javadoc 与清理 import，恢复 `checkstyle:check` 全绿 |
| **中** | CRM 客户列表「负责人」筛选静默失效：`CustomerPageParams` 无 `ownerId`，前端传参被丢弃 | `whale-crm/.../interfaces/support/CustomerPageParams.java` 仅 keyword/status/level/source/industry/createdFrom/To | 补 `ownerId` 并绑定到 `CustomerQuery` |
| **中** | OA 审批通知同步发送：`ApprovalNotificationListener` 三处 `@EventListener`，消息服务抖动直接拉长审批接口耗时 | `whale-oa/.../approval/application/listener/ApprovalNotificationListener.java` | 改 `@TransactionalEventListener` + 异步投递，失败仅告警 |
| 中 | OA 附件仅登记 `fileId`，未校验上传者与租户，存在跨用户引用 | 11-07 扫描登记，本次未复核 | `whale-file` 补上传者/租户校验，OA 下载走代理 |
| 中 | JaCoCo「零测试模块即跳过」盲区：`whale-common-core` / `whale-common-domain` / `whale-auth-sdk` | `pending-issues` N-11 观察项 | 补最小测试或加测试类存在性校验 |
| 低 | `/auth/refresh` 返回 HTTP 200 + 业务码 401，属接口约定差异 | 2026-11-06 扫描登记，未变更 | 先定口径再改，勿单独改动 |
| 观察 | 权限/菜单号段靠人工核对：5.89 邮箱菜单曾撞工会 481，被 `WHERE NOT EXISTS` 静默吞掉 | Wave 5.88 M-P3 / 5.89 §7 | 脚本化扫描全仓号段占用，纳入改动迁移的批次 |

## 六、待办（文档与工程卫生）

1. **roadmap 复选框陈旧**：8.7 / 8.8 仍为未勾选，但 CRM 后端与前端 8113 均已完成。
2. **五服务总账 §四红线过期**：仍写「5.89 邮箱 / 5.91 地图未提交不得 `git add`」，实际已在 pom、网关与 `migration-5.89/5.91` 中。
3. **5.89 文档自相矛盾**：§8 写「不支持附件发送/接收（M1）」，§10 又把「附件收发」划为已完成。
4. **Wave 5.98 无 devlog 章节**，mall 端点与测试总数未回填 `service-catalog.md`。
5. **OA V12 迁移**：若本地库留有失败记录，执行前需 `flyway repair` 或清理 `flyway_schema_history` 失败行。
6. **上线必检**：`docs/design/network-boundary.md`（直连业务端口收口）；prod compose 证书（V-09）。

## 七、本轮核实已闭环（从旧清单移除）

- `service-catalog.md` V7/V8/Mall V6 补记：门禁已 PASS。
- OA M2 报销/考勤/用车/用章/采购后端与前端 10 页：已提交（`8b113470`、`037b39f7`、`fb1efd55`）。
- CRM M5 营销、M6 分析、M1-4 客户合并、M1-9 导入导出：已交付（`17f70def`）。
- 邮箱 / 地图服务与前端子应用：已入库并注册网关路由。
- `pending-issues-2026-10-27.md` 的 T/S/F/V/N 系列：全部闭环，仅 V-09 待证书。
- 工作区未跟踪文件与 stash 红线：已消除（本次 `git status` 与 `git stash list` 均为空）。

## 八、建议执行顺序

1. 修 `whale-oa` Checkstyle 325 条 → 全量 `checkstyle:check` 转绿（唯一实测红灯）。
2. 修 CRM `ownerId` 筛选 + OA 通知改事务后异步（两处小改，收益明确）。
3. 订正 5 处文档失真（roadmap 8.7/8.8、五服务 §四、5.89 §8、5.98 devlog、catalog 数字）。
4. 起 MySQL/Redis/Nacos + 按 §3.1 清单跑迁移与冒烟，解除实机阻塞。
5. 再开功能缺口：ERP M3 剩余 / MES 两项 / Mall 售后增强。
