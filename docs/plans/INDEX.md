# docs/plans/ 分类索引

> `plans/` 目录按项目约定「只增不删、历史文件保留原位」（2026-10-22 平台评审 P0-4 决议），
> 因此文件按时间沉淀会显得多。本索引把 90+ 份文件按「主控 / 现行 / 主题 / 历史归档」四层组织，
> **不移动任何文件**（历史评审报告中的相对链接依赖现路径）。
> 想知道「现在做到哪了、接下来做什么」：先读 [bluewhale-blueprint-2026Q4.md](bluewhale-blueprint-2026Q4.md)，
> 再按需查 [roadmap.md](roadmap.md) 对应 Phase 的台账与本文「现行主题计划」。

---

## 一、主控文档（常读常更，只有 4 份）

| 文档 | 定位 |
|---|---|
| [roadmap.md](roadmap.md) | **历史波次总台账**（Phase 1–15 全勾选状态，含状态对账批注；每个 Phase 头部链到对应执行计划） |
| [bluewhale-blueprint-2026Q4.md](bluewhale-blueprint-2026Q4.md) | **现状与收线蓝图**（家底 / 三线收敛 / M1–M4 阶段与准入门槛 / 技术债台账 / 不做什么） |
| [2026-11-06-five-services-backlog-stepwise.md](2026-11-06-five-services-backlog-stepwise.md) | PDM / MES / OA / CRM / ERP 五服务待办总账（Step 1–7 状态，含剩余主体结论） |
| [whale-form-expansion-roadmap.md](whale-form-expansion-roadmap.md) | whale-form 扩展评审处置台账（批次 1–5 状态与后续批次排期） |

## 二、现行主题计划（2026-11，仍在收口或有后续批次）

### 平台工程（Wave 6.x）

| 文档 | 主题 |
|---|---|
| [wave6-iot-twin-ai-assistant-plan.md](wave6-iot-twin-ai-assistant-plan.md) | IoT / 孪生 / AI 助手三平台总纲 |
| [wave6.03-twin.md](wave6.03-twin.md)（及 6.01/6.02/6.04–6.17） | 孪生告警体系、知识助手、内网边界门禁、权限映射门禁等（详见 roadmap Phase 14） |
| [2026-11-12-auth-permission-center-expansion.md](2026-11-12-auth-permission-center-expansion.md) | 权限中心扩展：品牌配置 / OAuth2.0 / 三方登录 / 用户档案与导入导出（M0–M4 分里程碑） |
| [whale-job-scheduled-migration.md](whale-job-scheduled-migration.md) | 各服务 @Scheduled 迁移 whale-job 调度中心 |

### 服务能力扩展（分批次交付）

| 文档 | 主题 |
|---|---|
| [whale-file-extension-batch1.md](whale-file-extension-batch1.md) … [batch7.md](whale-file-extension-batch7.md) | whale-file 七批次扩展（storageType 路由 / 回收站 / 对账 / 分享 / 批量打包 / 魔数校验 / 配额，批次 7 在途） |
| [whale-form-security-hardening.md](whale-form-security-hardening.md) | whale-form 部署安全约束（X-Username 可信边界 / DDL 权限收敛） |
| [whale-form-tenant-table-strategy.md](whale-form-tenant-table-strategy.md) | whale-form 多租户物理表策略（单表 + tenant_id 列） |

### 新服务波次（2026-11-06 批量立项）

| 文档 | 服务 |
|---|---|
| [2026-11-06-mail-server-and-map-service.md](2026-11-06-mail-server-and-map-service.md) | 自建邮件系统与地图服务决策方案 |
| [2026-11-06-wave5.89-mail-service-m0.md](2026-11-06-wave5.89-mail-service-m0.md) | whale-mail M0 |
| [2026-11-06-wave5.91-map-service-m0.md](2026-11-06-wave5.91-map-service-m0.md) / [wave5.92](2026-11-06-wave5.92-map-fence-m1.md) / [wave5.93](2026-11-06-wave5.93-map-frontend-kit.md) | whale-map M0 / 电子围栏 M1 / 前端 kit |
| [2026-11-06-wave5.94-mall-service-m0.md](2026-11-06-wave5.94-mall-service-m0.md) → [2026-11-07-wave5.98-mall-logistics.md](2026-11-07-wave5.98-mall-logistics.md) | whale-mall M0–M4（五种形态一套内核 / 支付切片 / 售后 / 定价券运费 / 物流） |
| [2026-11-06-wave5.87-lms-and-party-union-panorama.md](2026-11-06-wave5.87-lms-and-party-union-panorama.md) / [wave5.88](2026-11-06-wave5.88-party-union-service.md) | LMS 与党政工会（Phase 13 执行计划） |
| [2026-11-03-wave5.77-scene-and-crm.md](2026-11-03-wave5.77-scene-and-crm.md) | 城池场景 + CRM 立项（Phase 8 执行计划） |
| [2026-11-03-wave5.78-erp-panorama.md](2026-11-03-wave5.78-erp-panorama.md) ～ [2026-11-04-wave5.83-crm-panorama.md](2026-11-04-wave5.83-crm-panorama.md) | ERP / MES / PDM / OA / CRM 五张全景设计执行计划（Phase 9–12） |

### 3D 体验层（2026-11 新线）

| 文档 | 主题 |
|---|---|
| [2026-11-18-3d-showcase-scene.md](2026-11-18-3d-showcase-scene.md) | `whale-showcase` 美术样板间（河谷渡口，S0–S23 视觉迭代，不接后端） |
| [2026-11-19-3d-capital-master-plan.md](2026-11-19-3d-capital-master-plan.md) | **三维王城总纲 v2（3D 全量承载）**：31 个服务空间映射（皇城 9 殿 / 内城 12 坊 / 外城 9 处）+ WebGL/CSS3D/HUD 三层渲染 + 120 个细分任务（S0 验证 → S8 打磨）+ M0–M5 里程碑。**进度：S0/S1/S2/S3/S4 已完成；S5 进行中（T5.1 数枢殿总控 + T5.2 枢密殿 + T5.3 度支殿 + T5.4 藏书殿 + T5.5 时辰殿已完成），下一步 T5.6 政事殿** |
| [2026-11-19-s0-spike-report.md](2026-11-19-s0-spike-report.md) | **S0 技术验证报告**：两层零漂移（0.00px）、深度遮挡可行、antd 页面可复用（弹层需全局 `getPopupContainer`）；IME 与性能待真机 |

## 三、服务台账（backlog，与 roadmap 互补）

| 文档 | 覆盖 |
|---|---|
| [whale-auth-backlog.md](whale-auth-backlog.md) | 权限服务残余与增强项 |
| [whale-gateway-backlog.md](whale-gateway-backlog.md) | 网关残余（GW-n 台账） |
| [whale-workflow-backlog.md](whale-workflow-backlog.md) | 工作流残余（5b 分组泳道等） |

## 四、历史文档（结论已沉淀进 roadmap / blueprint，仅作追溯）

| 文档 | 说明 |
|---|---|
| [next-steps.md](next-steps.md) | **Wave 5.6 时期快照**，头部已标注「不得作为待办依据」 |
| [task-breakdown.md](task-breakdown.md) | 早期任务分解（Wave 5 时代，已被 roadmap 取代） |
| [pending-issues-2026-10-27.md](pending-issues-2026-10-27.md) | 2026-10-27 问题清单（多数已销账） |
| [priority-execution-ledger-2026-10-25.md](priority-execution-ledger-2026-10-25.md) | P0/P1/P2/P3 优先级台账（2026-10-25 批次已收口） |
| [2026-10-22-platform-remediation.md](2026-10-22-platform-remediation.md) | 平台评审整改（P0-1…P2-4，均已闭环，见 roadmap Phase 5） |
| [2026-10-28-crm-srm-and-message-service-decisions.md](2026-10-28-crm-srm-and-message-service-decisions.md) | CRM/SRM 与消息服务早期决策（后续被全景计划细化） |
| [2026-10-29-wiki-doc-service.md](2026-10-29-wiki-doc-service.md) | 在线文档/Wiki 服务（M1–M5 已交付） |
| [2026-10-31-security-baseline-config-flyway-password-rotation.md](2026-10-31-security-baseline-config-flyway-password-rotation.md) | 安全基线配置化 / Flyway / 口令轮换（已落地） |
| `wave5.34/35/46/47/51/52/53/55/61/75/77-*.md` | Wave 5.34–5.77 各波次执行计划（结论已勾进 roadmap 对应 Phase） |
| `wave6.01/6.02/6.04–6.17-*.md` | Wave 6.x 早期波次（结论已勾进 roadmap Phase 14） |
| [archive/](archive/) | 更早的历史归档 |

## 五、使用约定

1. **新计划**：新建文件命名 `YYYY-MM-DD-<主题>.md`（跨波次大主题用 `wave<N>.<M>-<主题>.md`），建好后在本索引「二、现行主题计划」登记一行。
2. **收口**：波次全部完成后，把结论勾进 `roadmap.md` 对应 Phase，并将本索引中该条目移到「四、历史文档」。
3. **禁止**移动或重命名本目录既有文件——历史评审报告（`docs/reviews/` 124 份）含指向本目录的相对链接。
