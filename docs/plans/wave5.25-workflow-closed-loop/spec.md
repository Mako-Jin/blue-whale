# Wave 5.25 规格（whale-workflow 全闭环 · 实例 + 待办 + 审批 · 冻结契约）

> 用户指令（2026-10-19）：「开始搭建表单工作流服务」→ 经 AskUserQuestion 确认本轮口径：**工作流全闭环（实例 + 待办 + 审批）** —— 在 Wave 5.24 的「流程定义」之上，加 `process_instance` 实例、`process_task` 待办与审批动作，形成「定义 → 发起 → 待办 → 审批（同意/驳回）→ 状态流转」最小闭环。
> 执行原则：**先落文档（REQ-DOC-002）→ 冻结契约（本文 §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归（REQ-QA-002）**。
> 前置：Wave 5.24 已交付 `whale-workflow`（8083 / `/workflow/**` / `bluewhale_workflow` / `process_definition` 表 / `sys:workflow:add/edit/delete/list` 种子 id 40–43 / `WorkflowController`）。
> 关联台账：[roadmap.md](../roadmap.md)、[task-breakdown.md](../task-breakdown.md)、[service-catalog.md](../../service-catalog.md)。

---

## 一、范围与非目标

| 轨 | 范围 | 落点 |
|---|---|---|
| **A 流程实例** | `process_instance` 表 + 发起实例（解析定义节点生成首个待办）/ 实例详情（含任务）/ 实例分页 / 撤销实例 | `whale-workflow` |
| **B 待办与审批** | `process_task` 表 + 待办分页 / 同意（推进节点，末节点通过则实例通过）/ 驳回（实例驳回并取消其余待办） | `whale-workflow` |
| **C 接线与门禁** | 权限种子 `sys:workflow:instance:*`、`sys:workflow:task:*`（id 44–48）+ 单测 + 检查报告 | `whale-workflow`、`whale-auth` db |

**非目标（明确不做）**
- 会签/或签/加签、转办/委派、回退到任意节点、并行网关、条件分支、定时超时自动审批。
- 消息通知（站内信/邮件/IM）、流程发起表单绑定（`form_data`）、前端工作流设计器与待办页。
- 流程定义的发布/版本快照、灰度实例迁移。

---

## 二、现状事实（研究结论，勿重复调研）

- **Wave 5.24 已就绪**：`process_definition` 表（`definition_json` MEDIUMTEXT ≤64KB、`idx_code`）、`ProcessDefinition`/`ProcessDefinitionMapper`、`ProcessDefinitionService(+Impl)`、`WorkflowController`（`/workflow/definition` 5 接口）、`ProcessDefinitionServiceImplTest`（8 用例）；库 `bluewhale_workflow` 已建；网关 `Path=/workflow/**` → `lb://whale-workflow` 已配；权限种子至 **43**。
- **模板**：whale-form / whale-form-workflow 的 `Xxx`(继承 `BaseEntity`) / `XxxMapper`(仅 `BaseMapper`) / `XxxService(+Impl)` / `XxxController`(`@RequirePermission`)；`MyBatis-Plus` 逻辑删除 `deleted`；`PageQuery`/`PageResult`；`BizException(ErrorCode.PARAM_ERROR/NOT_FOUND)`。

---

## 三、冻结契约（实现前不得更改）

### 3.1 表结构（库 `bluewhale_workflow`，`utf8mb4_general_ci`，InnoDB）

**`process_instance`**（流程实例）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| definition_id | BIGINT NOT NULL | 流程定义 ID |
| definition_code | VARCHAR(64) NOT NULL | 发起时的定义编码（冗余） |
| definition_version | INT NOT NULL | 发起时的定义版本（冗余） |
| title | VARCHAR(128) NOT NULL | 实例标题 |
| business_key | VARCHAR(64) NULL | 业务键（可选） |
| status | TINYINT NOT NULL DEFAULT 0 | 0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 |
| current_index | INT NOT NULL DEFAULT 0 | 当前审批节点下标（0 起） |
| initiator | VARCHAR(64) NULL | 发起人账号 |
| create_time / update_time / deleted | | 公共字段 |
| KEY idx_definition_id (definition_id)、KEY idx_status (status) | | |

**`process_task`**（待办任务）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| instance_id | BIGINT NOT NULL | 所属实例 ID |
| node_index | INT NOT NULL | 节点下标 |
| node_name | VARCHAR(128) NULL | 节点名称 |
| assignee | VARCHAR(64) NOT NULL | 审批人账号 |
| status | TINYINT NOT NULL DEFAULT 0 | 0 待办 / 1 已同意 / 2 已驳回 / 3 已取消 |
| comment | VARCHAR(255) NULL | 审批意见 |
| create_time / update_time / deleted | | 公共字段 |
| KEY idx_instance_id (instance_id)、KEY idx_assignee (assignee)、KEY idx_status (status) | | |

### 3.2 `definition_json` 节点契约（发起时解析）

```json
{ "nodes": [ { "key": "n1", "name": "一级审批", "assignee": "admin" },
             { "key": "n2", "name": "二级审批", "assignee": "user" } ] }
```

- `nodes` 为**有序**数组，下标即审批推进顺序；每节点取 `name`（节点名，可空）与 `assignee`（审批人账号，必填）。
- 解析失败 / `nodes` 缺失 / 为空 / 任一 `assignee` 为空 → 发起时抛 `PARAM_ERROR`「流程定义缺少有效审批节点」。

### 3.3 接口（服务前缀 `/workflow`；网关 `Path=/workflow/**` 只转发不剥离）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| POST | `/workflow/instance` | `sys:workflow:instance:start` | 发起实例（body：`definitionId` 必填、`title` 必填、`businessKey` 可选）→ 建实例(status=0) + 首节点待办 |
| GET | `/workflow/instance/page` | `sys:workflow:instance:list` | 实例分页（`title` 模糊、`status` 等值、`definitionCode` 等值），按 `create_time desc`，单页上限 200 |
| GET | `/workflow/instance/{id}` | `sys:workflow:instance:list` | 实例详情（含该实例 `tasks` 列表） |
| POST | `/workflow/instance/{id}/cancel` | `sys:workflow:instance:cancel` | 撤销实例（仅运行中可撤销；其待办全部置「已取消」） |
| GET | `/workflow/task/page` | `sys:workflow:task:list` | 待办分页（`assignee` 等值、`status` 等值、`instanceId` 等值），按 `create_time desc` |
| POST | `/workflow/task/{id}/approve` | `sys:workflow:task:approve` | 同意（body：`comment` 可选）→ 推进下一节点；末节点同意 → 实例「已通过」 |
| POST | `/workflow/task/{id}/reject` | `sys:workflow:task:approve` | 驳回（body：`comment` 可选）→ 实例「已驳回」+ 同实例其余待办置「已取消」 |

- 统一响应体 `Result<T>` / `PageResult<T>`。
- 业务校验：任务已处理（status≠0）→ `PARAM_ERROR`「任务已处理」；实例非运行中 → `PARAM_ERROR`「实例非运行中」；定义/实例/任务不存在 → `NOT_FOUND`。

### 3.4 状态流转（唯一权威定义）

```
发起  : instance.status=0(RUNNING), current_index=0, 生成 nodes[0] 待办(status=0)
同意  : task.status=1 ; 若 current_index+1 >= nodes.size → instance.status=1(APPROVED)
                        否则 instance.current_index++ 并生成该节点待办(status=0)
驳回  : task.status=2 ; instance.status=2(REJECTED) ; 同实例其余 PENDING 任务 → status=3(CANCELED)
撤销  : instance.status=3(CANCELED) ; 同实例 PENDING 任务 → status=3(CANCELED)
```

### 3.5 权限种子（whale-auth `db/init.sql` 追加，落 `bluewhale_auth.sys_permission`）

| id | perm_code | perm_name | method | path |
|---|---|---|---|---|
| 44 | `sys:workflow:instance:start` | 发起流程实例 | POST | `/workflow/instance` |
| 45 | `sys:workflow:instance:list` | 查询流程实例 | GET | `/workflow/instance/**` |
| 46 | `sys:workflow:instance:cancel` | 撤销流程实例 | POST | `/workflow/instance/{id}/cancel` |
| 47 | `sys:workflow:task:list` | 查询待办任务 | GET | `/workflow/task/**` |
| 48 | `sys:workflow:task:approve` | 审批待办任务 | POST | `/workflow/task/{id}/{approve\|reject}` |

---

## 四、新增/修改文件清单（预期）

- 新增 `entity/{ProcessInstance,ProcessTask}.java`
- 新增 `mapper/{ProcessInstanceMapper,ProcessTaskMapper}.java`
- 新增 `dto/{ProcessInstanceStartRequest,ProcessInstanceQueryRequest,ProcessTaskQueryRequest,ApprovalRequest}.java`
- 新增 `vo/{ProcessInstanceVO,ProcessTaskVO}.java`
- 新增 `service/{ProcessInstanceService,ProcessTaskService}(+impl/...)` + `support/ProcessNodeParser.java`（解析 `definition_json` → 节点列表）
- 新增 `controller/{ProcessInstanceController,ProcessTaskController}.java`
- 新增 `src/main/resources/db/init.sql` 追加 2 表（或按现有文件追加）
- 修改 `whale-auth/src/main/resources/db/init.sql`（种子 44–48）
- 新增测试 `src/test/java/com/bluewhale/workflow/service/impl/{ProcessInstanceServiceImplTest,ProcessTaskServiceImplTest}.java`

---

## 五、完成定义（DoD）

- `whale-workflow` 模块 `test` + `checkstyle:check` 全绿（0 违规）；全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时段证据（经网关 8080）：
  - 发起 → 待办列表出现首节点任务 → 同意推进下一节点 → 末节点同意 → 实例「已通过」。
  - 另起实例 → 某一节点驳回 → 实例「已驳回」+ 其余待办「已取消」。
  - 撤销运行中实例 → 实例「已撤销」+ 待办「已取消」。
  - 负向：任务重复处理 `code=400`、实例不存在 `code=404`。
- `docs/reviews/2026-10-19-wave5.25-workflow-closed-loop.md` + devlog 追加 + roadmap/task-breakdown 勾选 + commit & push origin（Gitee）。
