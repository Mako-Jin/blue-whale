# 客户关系服务设计（whale-crm，需求门禁骨架）

> 状态：**门禁骨架已落地（仅 `/crm/meta`）**；M4/M5 业务编码未启动
> 关联需求：REQ-FUNC-035、REQ-ARCH-017、REQ-SEC-027
> 关联规划：[Wave 5.77 场景与 CRM 规划](../plans/2026-11-03-wave5.77-scene-and-crm.md) §3.2 / §9.2
> 关联决策：[CRM/SRM 与消息服务边界决策](../plans/2026-10-28-crm-srm-and-message-service-decisions.md)、REQ-ARCH-011
> 实现位置：`backend/whale-apps/whale-crm`（**前端未创建**）

---

## 1. 门禁结论

| 项 | 结论 |
|---|---|
| 需求门禁 D2 | **未通过**：暂无经确认的客户 / 联系人 / 商机 / 销售漏斗真实需求、使用者与验收 |
| 本波范围 | 只落地**可构建、可启动的门禁骨架**：DDL、权限/角色登记、`GET /crm/meta` |
| 不做什么 | 不做客户/联系人/商机/跟进 CRUD；不注册菜单 57–62；不建前端 8113；不写业务种子 |
| 唯一开关 | `whale.crm.demand-gate-passed`（默认 `false`）；为 `true` 才允许进入 M4 |
| 下一步 | 门禁四项条件同时满足 → 完成 ADR-003 定稿 → CRM-01 设计评审 → 才可进入 CRM-02 编码 |

### 1.1 进入条件（必须同时满足）

1. 出现真实的客户 / 联系人 / 商机 / 销售漏斗需求，并明确使用者、流程与验收；
2. 明确客户主数据归属：CRM 拥有客户，ERP 只拥有供应商，合同域「相对方」只保存
   `crmCustomerId` 快照；
3. 完成 Domain Modeling 复核与 ADR-003 定稿；
4. 产品确认本期不做线索评分、营销自动化、客户服务工单。

---

## 2. 职责边界

| 拥有（M4/M5 启用后） | 不拥有 |
|---|---|
| 客户、联系人、商机、销售阶段、跟进记录 | 供应商主数据（ERP / 未来 `whale-srm`） |
| 客户 360 只读视图 | 合同正文、签署、印章（`whale-contract`） |
| 客户编码规则与数据权限范围 | 审批（`whale-workflow`）、消息投递（`whale-message`）、附件字节（`whale-file`） |
| —— | 线索评分、营销自动化、客服工单（明确不做） |

上下文映射：`whale-crm → auth` 为遵奉者；合同 / 消息 / 工作流 / 文件是下游内网 REST；
`whale-crm` 与 `whale-erp` **无关系**，客户 ≠ 供应商，禁止共享主数据表。

---

## 3. 本波已落地内容

| 项 | 内容 |
|---|---|
| 模块 | `backend/whale-apps/whale-crm`，端口 **9007**，前缀 `/crm/**`，库 `bluewhale_crm` |
| 启动类 | `WhaleCrmApplication` |
| 配置 | `CrmProperties`（门禁开关、客户编码前缀、跟进分页上限、合同引用开关） |
| 唯一接口 | `GET /crm/meta`（权限 `sys:crm:browse`）→ 门禁状态、说明、里程碑、边界约束 |
| DDL | `V1__baseline.sql`：`crm_customer` / `crm_contact` / `crm_opportunity` / `crm_follow_up` + `audit_log` / `api_log`；**无业务种子** |
| 权限 | `migration-5.77-crm.sql`：337–354（18 项）**仅登记**，并明确**不注册菜单** |
| 角色 | 25108 `crm_admin`、25109 `crm_member`（平台 + 业务租户复制） |
| 网关 | `lb://whale-crm`，不配 `StripPrefix`；`/crm/internal/**` 由 internal-deny 403 |
| 前端 | **不创建**；服务目录中的 8113 保持规划态 |
| 测试 | 8 项（Controller 3、Flyway 脚本 2、ArchUnit 3）；行覆盖 8/11 ≈ 72.7% |

> `/crm/meta` 不读写任何客户表，避免门禁前产生脏数据或给人“功能已上线”的错觉。

---

## 4. 数据模型（M4/M5 启用时执行）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `crm_customer` | `customer_code` / `name` / `status` / `owner_id` / `dept_id` / `level` / `source` / `industry` / `address` / `tags` | 客户聚合根；`customer_code` 租户内唯一 |
| `crm_contact` | `customer_id` / `name` / `phone` / `email` / `title` / `is_primary` / `status` | 每客户至多一个主联系人 |
| `crm_opportunity` | `customer_id` / `name` / `amount` / `stage` / `expected_close_date` / `owner_id` / `lost_reason` | 商机阶段状态机 |
| `crm_follow_up` | `customer_id` / `opportunity_id` / `channel` / `content` / `next_action_at` / `created_by` | 追加式（append-only） |

阶段状态机（草案）：

```text
LEAD → QUALIFIED → PROPOSAL → NEGOTIATION → WON
  └──────────────┴───────────┴──────────────► LOST（必须填写 lost_reason）
```

- 非法跳转返回业务错误（400）；
- `WON` 必须有金额与客户；
- 客户存在未关闭商机时禁止停用/删除。

---

## 5. API 规划（门禁通过后才实现）

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| `GET` | `/crm/meta` | 门禁状态（**已实现**） | `sys:crm:browse` |
| `GET` | `/crm/customer/page` | 客户分页 | `sys:crm:customer:list` |
| `GET` | `/crm/customer/{id}` | 客户详情 + 360 摘要 | `sys:crm:customer:list` |
| `POST` | `/crm/customer` | 新建客户 | `sys:crm:customer:add` |
| `PUT` | `/crm/customer/{id}` | 编辑客户 | `sys:crm:customer:edit` |
| `PUT` | `/crm/customer/{id}/status` | 停用/启用 | `sys:crm:customer:edit` |
| `POST` | `/crm/customer/{id}/contact` | 新增联系人 | `sys:crm:contact:manage` |
| `GET` | `/crm/opportunity/page` | 商机分页 | `sys:crm:opportunity:list` |
| `GET` | `/crm/opportunity/funnel` | 销售漏斗 | `sys:crm:opportunity:list` |
| `POST` | `/crm/opportunity` | 新建商机 | `sys:crm:opportunity:add` |
| `PUT` | `/crm/opportunity/{id}/stage` | 推进阶段 | `sys:crm:opportunity:stage` |
| `POST` | `/crm/follow-up` | 新增跟进 | `sys:crm:followup:add` |
| `GET` | `/crm/follow-up/page` | 跟进分页 | `sys:crm:followup:list` |

**权限编号 337–354**（18 项，已登记）；**菜单 57–62 预留**，M4 启用时再追加。
不提前注册菜单，避免用户看到点击即 403 的空入口。

---

## 6. 安全与数据权限（承诺，M4 验收）

1. 跨租户访问一律 404，不泄露存在性；
2. 客户可见范围按本人 / 本部门 / 授权范围收口（数据权限）；
3. 联系人手机号 / 邮箱在列表、导出与审计中脱敏；
4. 客户与商机写操作落 `@AuditLog`，不记录敏感明文；
5. 合同只引用 `crmCustomerId` 快照，不复制客户主数据；
6. 所有导出 / 列表接口受权限码控制。

---

## 7. 验收标准（门禁通过后生效）

1. `customer_code` 租户内唯一，重复新增 / 改码返回业务错误；
2. 只能看到本人 / 本部门 / 授权范围客户，跨租户 404；
3. 手机号 / 邮箱按规则脱敏；
4. 商机阶段只能按状态机流转，非法跳转返回 400；
5. 有未关闭商机的客户禁止停用 / 删除；合并客户保留引用可追溯；
6. 合同相对方通过 `crmCustomerId` 引用客户；
7. 客户 / 商机写操作落审计；
8. 后端单测、Checkstyle、ArchUnit、前端 lint/build、E2E 通过。

---

## 8. 风险与缓解

| # | 风险 | 缓解 |
|---|---|---|
| R5 | 需求未确认即编码 → 域模型漂移、返工 | 门禁四项条件 + ADR-003 + Domain Modeling 复核 |
| R6 | 客户主数据与 ERP/合同重复 | CRM 唯一客户主数据；合同只存引用快照；供应商归 ERP/SRM |
| R10 | 权限/菜单编号冲突 | 337–354 / 菜单 57–62 预留；迁移脚本幂等、只增不删 |

---

## 9. 变更触发清单（门禁通过时必须同步）

1. `whale.crm.demand-gate-passed=true`（环境变量 `WHALE_CRM_DEMAND_GATE_PASSED`）；
2. ADR-003 定稿与 Domain Modeling 复核记录；
3. M4 追加菜单 57–62 迁移脚本（幂等）；
4. 新建前端 `frontend/platform-apps/whale-crm`（8113、base `/crm`）并接入基座；
5. CI 镜像矩阵与部署契约计数同步（后端已有 `whale-crm`；前端新增时再 +1）；
6. 本设计稿状态改为“M4 已落地”，并补 `docs/reviews/` 检查报告与 devlog。
