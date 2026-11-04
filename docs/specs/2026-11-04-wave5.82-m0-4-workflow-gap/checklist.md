# Wave 5.82 · M0-4 Workflow 缺口补齐 验收检查表

> 对照 [spec.md](./spec.md) §5 验收标准与 §2 设计决策逐项核对。
> 验证依据：`.\mvnw.cmd -pl :whale-workflow test`（177 tests 全绿、0 Checkstyle 违规）、
> `.\mvnw.cmd -pl :whale-oa test`（42 tests 全绿）与代码走查。

- [x] AC-1 抄送不产生待办：`StateMachine#trace` 将 `CC_TASK` 收进 `ccNodes` 后继续推进，不进 `activeNodes`；`TaskActivationCommand` 仅对 `ccNodes` 写 `process_cc`（`StateMachineCcTest`、`TaskActivationCommandTest` 覆盖）
- [x] AC-1 发起级抄送与节点级抄送：`ccUserIds` 落 `node_key=START`/`node_name=发起抄送`，节点级按 `assignees` 落库；`uk_cc` + `DuplicateKeyException` 幂等
- [x] AC-2 审批人覆盖生效：覆盖节点按账号逐条建任务，快照写入 `process_instance.assignee_overrides_json`，后续 `ApproveCommand` 复用快照（`TaskActivationCommandTest`、`ProcessInstanceServiceImplTest` 覆盖）
- [x] AC-3 覆盖非法输入 400：未知节点、非 `USER_TASK/CC_TASK` 节点、空列表、单节点 >20、账号 >64 均抛 `PARAM_ERROR`；去空白去重（`AssigneeOverridesTest` 7 用例）
- [x] AC-4 代理单次改派：`TaskActivationCommand` 每任务仅调用一次 `resolveActive`，命中后 `assignee=toAccount`、`delegateFrom=fromAccount`，不判断目标人代理（`ProcessDelegateServiceImplTest`、`TaskActivationCommandTest` 覆盖）
- [x] AC-4 规则校验与优先级：`from != to`、`start < end` 均 400；重叠取 `start_time` 最大、再取 `id` 最大
- [x] AC-5 催办频控：同实例同催办人 60 分钟内二次催办 400「催办过于频繁，1 小时内仅允许一次」；实例不存在 404；`taskId` 不属该实例 400（`ProcessUrgeServiceImplTest` 4 用例）
- [x] AC-6 查询过滤：实例查询支持 `initiator` 等值；任务查询 `handled=true` → `status in (1,2)`，`actorAccount+actorId` → `assignee = ? or operator_id = ?`，单字段时退化为单条件
- [x] AC-7 定义 ensure 幂等：按 `code` 命中直接返回同一 id；未命中先 `ProcessModelValidator.validate` 再 `create`
- [x] AC-8 全量回归：`whale-workflow` 177 tests / 0 失败 / Checkstyle 0 违规；`whale-oa` 42 tests 全绿
- [x] D-1/D-2/D-3 落地：抄送独立表、`recipient` 统一字符串、覆盖快照持久化于实例
- [x] D-5/D-6 边界：催办仅留痕 + 频控（不调用 Message）；新增能力全部挂 `/workflow/internal/**` 且不新增权限码
- [x] 迁移安全：`V3__cc_delegate_urge.sql` 全为 `CREATE TABLE IF NOT EXISTS` + `information_schema` 守卫，无 `DROP/TRUNCATE/DELETE`（`WorkflowFlywayMigrationScriptTest#v3ShouldAddCcDelegateAndUrge`）
- [x] 任务创建单一入口：`TaskCommandSupport` 已删除 `createTasks/createTask`，start/approve/rollback 统一走 `TaskActivationCommand`
- [x] 文档同步：`docs/devlog/2026-11-04.md` 已追加 M0-4 章节；计划 M0 表下方已标注 M0-4 完成

## 环境受限（未执行，非缺陷）

- 真实库执行 `V3__cc_delegate_urge.sql` 与网关 `/workflow/internal/**` 403 验证需启动环境，本次未跑。
- 内网 `approve/reject` 未显式写 `operator_id`，已在 devlog「待做」中列为 M1A 承接项。
