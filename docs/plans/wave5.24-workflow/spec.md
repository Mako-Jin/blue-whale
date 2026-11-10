# Wave 5.24 规格（whale-workflow 骨架填充 · 流程定义 CRUD 最小可用 · 冻结契约）

> 用户指令（2026-10-19）：「开始搭建表单工作流服务」。
> 经 AskUserQuestion 确认本轮口径：目标服务 = **填充既有 `whale-workflow` 骨架**（不新建服务、不与 whale-form 耦合）；MVP = **流程定义 CRUD 最小可用**。
> 执行原则：**先落文档（REQ-DOC-002）→ 冻结契约（本文 §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归（REQ-QA-002）**。
> 关联台账：[roadmap.md](../roadmap.md)、[task-breakdown.md](../task-breakdown.md)、[service-catalog.md](../../service-catalog.md)。

---

## 一、范围与非目标

| 轨 | 范围 | 落点模块 |
|---|---|---|
| **T1 whale-workflow 骨架接线** | 依赖与配置补齐：`pom.xml` 增 `whale-spring-boot-starter`；`application.yml` 修端口自引用占位符 + 补 datasource(`bluewhale_workflow`) + mybatis-plus + 权限头开关 | `whale-backend/whale-services/whale-workflow` |
| **T2 流程定义 CRUD 最小可用** | `bluewhale_workflow` 建库 + `process_definition` 表（含流程 JSON 定义）+ 5 个 REST 接口 + `sys:workflow:*` 权限点种子 + 单测 + 经网关冒烟 | `whale-workflow`、`whale-auth` db 种子 |

**非目标（明确不做）**
- 流程**实例**（process_instance）、**待办任务**（process_task）、**审批动作**（同意/驳回）与状态流转 —— 本轮仅「定义」侧。
- 流程定义**发布/版本快照**、定时触发、条件网关/表单绑定等引擎能力。
- whale-pay / whale-monitor / whale-alert 等服务；前端工作流设计器 UI。

---

## 二、现状事实（研究结论，勿重复调研）

- **whale-workflow 为裸骨架**：仅 `WhaleWorkflowApplication` + `pom.xml`（已含 web/validation/actuator/nacos/mybatis-plus/mysql/whale-tools）+ `application.yml`（**无 datasource、无 mybatis 配置、无权限头开关**）；`/workflow/**` 网关路由（`lb://whale-workflow`）与 **8083** 端口**已就绪**（[service-catalog.md](../../service-catalog.md) §二）；`bluewhale_workflow` 库**未建**。
- **端口自引用缺陷同 whale-form**：现 `application.yml` 写 `server.port: ${server.port:8083}`（自引用 → Spring Boot 4 抛 `Circular placeholder reference 'server.port'`），须改 `${SERVER_PORT:8083}`。
- **权限种子落库位置**：`sys_permission` 表在 `bluewhale_auth`（whale-auth `db/init.sql`），当前种子 id 至 **39**（file 32–35、form 36–39）。
- **参考实现模板**：whale-form 的 `FormDefinition`(继承 `BaseEntity`) / `FormDefinitionMapper`(仅继承 `BaseMapper`，靠 `com.bluewhale.**.mapper` 扫描) / `FormDefinitionService(+Impl)` / `FormController`（`@RequirePermission`） / `db/init.sql`；pom 依赖 `whale-spring-boot-starter`（传递 common 契约 + 权限码头 + whale-auth-sdk）。

---

## 三、冻结契约（实现前不得更改）

### T1 · 骨架接线

- `application.yml` 端口：`${SERVER_PORT:8083}`（对齐 whale-form/whale-file 约定，消除自引用）。
- datasource：`jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/bluewhale_workflow?...&useSSL=false&allowPublicKeyRetrieval=true`，账号 `${MYSQL_USER:root}` / `${MYSQL_PASSWORD:root}`。
- mybatis-plus：`map-underscore-to-camel-case: true` + 逻辑删除 `deleted`（1 删 / 0 未删）。
- 权限头：`whale.permission.header.enabled: true`（启用 starter 的 `RequirePermissionInterceptor`，按网关注入的 `X-Permission-Codes` 判定）。
- `pom.xml`：新增 `com.bluewhale:whale-spring-boot-starter:${project.version}`。

### T2 · 接口与数据

- 服务内路径前缀 `/workflow`（服务自持一级前缀，网关 `Path=/workflow/**` 只转发不剥离）。
- 5 个接口与权限码一一对应：

  | 方法 | 路径 | 权限码 | 说明 |
  |---|---|---|---|
  | POST | `/workflow/definition` | `sys:workflow:add` | 新增流程定义 |
  | PUT | `/workflow/definition/{id}` | `sys:workflow:edit` | 更新流程定义 |
  | DELETE | `/workflow/definition/{id}` | `sys:workflow:delete` | 逻辑删除 |
  | GET | `/workflow/definition/{id}` | `sys:workflow:list` | 详情 |
  | GET | `/workflow/definition/page` | `sys:workflow:list` | 分页查询 |

- 统一响应体 `Result<T>` / `PageResult<T>`；列表支持 `name` 模糊、`code` 等值、`status` 等值过滤，按 `create_time desc`，单页上限 200。
- 表 `process_definition`（库 `bluewhale_workflow`，`utf8mb4_general_ci`，InnoDB）列：
  `id / code / name / definition_json / version / status / remark / create_time / update_time / deleted`；
  `code` 建普通索引 `idx_code`（**不用唯一索引**，避免与逻辑删除冲突），唯一性由服务层「先查重」保证（冲突抛 `PARAM_ERROR`「流程编码已存在」）。
- `definition_json` 为流程定义 JSON 串（节点与流转），类型 `MEDIUMTEXT`，长度上限 64KB（超限抛 `PARAM_ERROR`）。
- 新增权限种子 id **40–43**：`sys:workflow:add(40) / sys:workflow:edit(41) / sys:workflow:delete(42) / sys:workflow:list(43)`（写入 whale-auth `db/init.sql` 并落 `bluewhale_auth.sys_permission`）。

---

## 四、完成定义（DoD）

- `whale-workflow` 模块 `test` + `checkstyle:check` 全绿（0 违规）。
- 全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时段证据：建 `bluewhale_workflow` + DDL + 种子落库；起 8083 经网关 `/workflow/**` 完成 新增→分页→详情→更新→删除 冒烟，`code=0`；重复 code → `code=400`；删后详情 → `code=404`。
- `docs/reviews/2026-10-19-wave5.24-workflow.md` + devlog 追加 + 台账（roadmap / task-breakdown）勾选 + commit & push origin（Gitee）。
