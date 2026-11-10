# Wave 5.35 工作流能力补全 + 网关治理

> 用户指令（2026-10-21）：「1、继续完成工作流；2、网关也继续完成；3、auth 要 mq 干什么？是审计日志吗？」
> 冻结口径（AskUserQuestion）：
> ① 工作流 = **全量 5 项**（条件表达式、并行/会签、转办/委派、回退任意节点、审批时修改表单）；
> ② 网关 = **GW-03 + GW-02 + GW-01 结案**。
> 第 3 问答复：AUTH-10 = 审计日志 MQ 汇聚（各服务 → audit 统一落库，根治跨库可见性），本机无 RabbitMQ，继续 backlog。

## 一、现状基线

- 工作流：`whale-backend/whale-services/whale-workflow`，状态机内核 `support/machine/`（`StateMachine` 仅 `AlwaysGuard`/`DefaultFlowGuard`，`pickTransition` 按单流/默认流/多分支处理）；`ProcessModelFlow.condition` 仅往返不参与选路；校验器发 `CONDITION_IGNORED` 警告；`process_task` 只有 `assignee/status/comment/node_key/form_id/form_code`，无会签/委派/转办字段；`ProcessInstanceStartRequest` 与 `ApprovalRequest` 无 `variables`/`formData`。
- 前端：`whale-frontend/platform-apps/whale-workflow-designer`（8003），`PropertyPanel` 的 condition 仅文本框；`TaskCenter` 只有同意/驳回；`api/workflow.ts` 无转办/委派/回退接口。
- 网关：`whale-backend/whale-services/whale-gateway`，自研 `CircuitBreakerGlobalFilter`（滑动窗口 + 半开 + 503 `Result` 体 + `gateway.circuit.*` 指标）；`NacosRouteDefinitionRepository` 仅校验 `id/uri` 非空与 predicate/filter 有 `name`；本地 Maven 仓库**已有** `spring-cloud-starter-circuitbreaker-reactor-resilience4j`，**无** `com.alibaba.csp`（Sentinel 离线不可得）。

## 二、任务清单

### A. 工作流（后端为主，前端配合）

- [x] **W1 条件表达式求值**
  - 扩展点：`ConditionGuard implements Guard`，用 Spring Expression（`spring-expression`，随 spring-context 已在依赖树）在 **`SimpleEvaluationContext.forReadOnlyDataBinding()`** 下求值（禁 bean 引用 / 类型引用 / 构造器），表达式长度 ≤ 512，根对象为只读 `Map<String,Object>`。
  - 变量来源：`ProcessInstanceStartRequest` 与 `ApprovalRequest` 增 `Map<String,Object> variables`，落 `process_instance.variables_json`（TEXT，null 安全）；审批时变量合并覆盖。
  - 选路：`StateMachine.transitions()` 对非空 `condition` 绑定 `ConditionGuard`；`pickTransition` 顺序：唯一命中 → 命中多个取定义顺序首个并记 WARN → 无命中回落 `isDefault` → 仍无则 `NO_MATCHING_FLOW`（新增错误码）。
  - 校验：`ProcessModelValidator` 用只解析不求值的方式校验语法，非法改报 **error** `CONDITION_SYNTAX_INVALID`；合法则不再发 `CONDITION_IGNORED`（该码保留兼容，仅 legacy 无 type 模型仍提示）。
- [x] **W2 并行网关 + 会签**
  - 模型：节点类型 `PARALLEL_GATEWAY`（fork/join）与 `COUNTER_SIGN`（会签，`counterSignPolicy` = `ALL`/`ANY`/`RATIO:n`，`assignees[]`）。
  - DB：`process_task` 增 `parent_node_key VARCHAR(64)`、`sign_type VARCHAR(16)`、`sign_policy VARCHAR(32)`；`process_instance` 增 `active_node_keys VARCHAR(512)`（逗号分隔，join 判定依据）。迁移脚本 `db/migration-5.35.sql`（幂等）。
  - 引擎：fork 为同一节点生成多条待办并写 `active_node_keys`；同节点会签按策略判定（ANY 首个同意即关节点、ALL 全同意、RATIO 达阈值）；join 在 `active_node_keys` 清空后推进下一节点。
- [x] **W3 转办 / 委派**
  - `process_task` 增 `owner_id VARCHAR(64)`、`delegate_from VARCHAR(64)`。
  - 端点：`PUT /workflow/task/{id}/transfer`（转办，assignee 改目标人，`owner_id` 记原人）、`PUT /workflow/task/{id}/delegate`（委派，`delegate_from` 记原人）、`PUT /workflow/task/{id}/resolve`（委派处理完回到原人，生成新待办）。
  - 权限种子：`sys:workflow:task:transfer`、`sys:workflow:task:delegate`（init.sql + `migration-5.35.sql`）。
- [x] **W4 回退任意节点**
  - `POST /workflow/task/{id}/rollback`，入参 `targetNodeKey`（必须是定义内 `USER_TASK` 节点）；取消当前活动待办 → 重置 `current_node_key`/`active_node_keys` → 在目标节点生成待办；实例状态回 `RUNNING`；权限复用 `sys:workflow:task:approve`。
- [x] **W5 审批时修改表单**
  - 方案（避免 workflow → form 跨服务耦合）：whale-form 增 `PUT /form/data/{id}`（权限 `sys:form:data:edit`，校验定义存在 + JSON ≤64KB + 主从表双写）；审批抽屉允许编辑并保存，再调用同意。workflow 侧仅需在 `ApprovalRequest` 回传 `formDataId` 便于轨迹展示（可选）。

### B. 网关

- [x] **G3 动态路由配置治理**
  - `route/RouteDefinitionValidator`：必填（`id/uri/predicates/filters`）、predicate/filter `name` 白名单、`uri` scheme 白名单（`lb`/`http`/`https`）、`Path` 谓词必须带一级前缀。
  - 版本治理：内存双快照（current/previous），非法配置 → **整体拒绝** + 保留上一版 + `gateway.routes.rejected` Counter + WARN 日志；新增 `@Endpoint(id="routeGovernance")` 暴露 `GET /actuator/routeGovernance`（当前/上一版摘要、拒绝计数）与 `POST /actuator/routeGovernance`（回滚到上一版，body `{}`）。
  - 单测：合法/非法/回滚/空配置/解析异常。
- [x] **G2 resilience4j 替换自研熔断**
  - 依赖：`spring-cloud-starter-circuitbreaker-reactor-resilience4j`（本地仓库已存在）。
  - `CircuitBreakerGlobalFilter` 改为委托 `ReactiveCircuitBreakerFactory`，保留：`apply-to-routes` 语义、503 + `Result` 体、`gateway.circuit.*` 指标（calls/short_circuits/state 改为从 resilience4j 事件或包装转换）。
  - 配置映射：`CircuitBreakerProperties` → `resilience4j.circuitbreaker.instances.<route>` 动态注册（`ReactiveResilience4JCircuitBreakerFactory#configure`）。
  - 保留旧实现为 `LegacyCircuitBreaker`？**不保留**（用户口径为「替换」），删除状态机并更新测试。
- [x] **G1 Sentinel 评估结案（不改代码）**
  - 结论：不引入。理由：本地仓库无 `com.alibaba.csp` 制品（离线不可得）、Boot 4 兼容未验证、现有 Resilience4j + Redis 限流已满足契约；写入 `docs/reviews` 与 backlog 版本记录。

### C. 验证与收尾

- [x] **V1** 后端模块自检：`mvn -o -pl whale-services/whale-workflow,whale-services/whale-form,whale-services/whale-gateway -am test`（Checkstyle + 单测）
- [x] **V2** 全量回归：`mvn -o -f whale-backend/pom.xml clean install -T 1C`（27/27 **BUILD SUCCESS**）
- [x] **V3** DDL：`migration-5.35.sql` 在 `bluewhale_workflow` 落库并核对列
- [x] **V4** 运行时冒烟（经网关 8080）：条件选路、会签 ANY/ALL、转办/委派/回退、审批改表单、网关非法路由拒绝 + 回滚
- [x] **V5** 前端构建 + 浏览器 E2E：设计器条件输入 → 发起带变量 → 待办转办/回退 → 审批改表单（`pnpm build` 通过；浏览器实测登录/待办列表/审批抽屉表单编辑保存 PASS；**转办弹窗确认在自动化环境点击被拦截，未发出请求 → 需人工复核**；设计器本轮未复验，Wave 5.32 已通过）
- [x] **V6** 文档：devlog、review、roadmap、task-breakdown、两份 backlog 版本记录

## 三、关键技术决策

1. **条件表达式用 SpEL 受限上下文**，不引入 Aviator/MVEL（本地仓库无、依赖膨胀）；`SimpleEvaluationContext` 禁用类型/Bean 引用，只允许比较、逻辑、算术与 `#var` 读取。
2. **变量下沉到实例**（`variables_json`），而非跨服务查表单，保证选路可重放、可审计。
3. **会签与并行共用 `active_node_keys`**：单节点单待办时该列为空，保持 legacy 行为零回归。
4. **审批改表单走 form 服务**，workflow 不新增 HTTP 客户端，避免循环依赖与超时级联。
5. **网关熔断整体替换为 resilience4j**，契约（503 体、路由开关、指标名）保持不变。
6. **G1 不改代码**，仅出具评估结论（离线无制品 + 兼容未验证）。

## 四、风险与回退

| 风险 | 说明 | 回退 |
|---|---|---|
| SpEL 表达式注入 | 用 `SimpleEvaluationContext` + 长度/字符白名单 + 异常降级为 400 | 关闭 `workflow.condition.enabled` 即回到「条件不参与选路」旧行为 |
| 并行/会签回归 | 单待办路径不变，`active_node_keys` 为空即走旧逻辑 | 保留 legacy 分支，DB 列可空 |
| resilience4j 运行时装配 | Boot 4.0.8 兼容性曾在 Wave 5.21 未验证 | 保留自研实现类于分支 tag；配置开关 `gateway.circuit.impl=resilience4j|legacy` |
| 网关非法路由 | 校验过严导致正常路由被拒 | 校验失败只拒绝该版并保留上一版，可 rollback |

