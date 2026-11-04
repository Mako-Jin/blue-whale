-- =====================================================================
-- BlueWhale - whale-workflow Flyway 增量 V4：OA 流程定义种子
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 上级计划 : Wave 5.82 M0-6（模板/流程定义种子）
-- 说明     : 1) 种子默认租户的 OA_LEAVE 请假审批定义（部门经理 → HR）；
--            2) 同步写入版本快照（version 与定义一致，发布状态与定义服务 create 一致为已发布）；
--            3) 节点 assignee 仅为占位，OA 发起时通过 assigneeOverrides 覆盖为真实账号。
-- 幂等     : INSERT ... WHERE NOT EXISTS 守卫，不覆盖既有定义与快照，可重复执行。
-- 租户     : 只种默认租户，tenant_id = 1。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 流程定义：OA_LEAVE（规范化 stateMachine 模型，单行 JSON）
-- ---------------------------------------------------------------------
INSERT INTO process_definition
    (id, tenant_id, code, name, definition_json, version, status, remark, deleted)
SELECT 900000000000000101, 1, 'OA_LEAVE', 'OA 请假审批',
       '{"type":"stateMachine","nodes":[{"key":"start","type":"START_EVENT"},{"key":"dept_manager","type":"USER_TASK","name":"部门经理审批","assignee":"dept_manager"},{"key":"hr","type":"USER_TASK","name":"HR审批","assignee":"hr"},{"key":"end","type":"END_EVENT"}],"flows":[{"key":"f1","from":"start","to":"dept_manager"},{"key":"f2","from":"dept_manager","to":"hr"},{"key":"f3","from":"hr","to":"end"}]}',
       1, 1, 'M0-6 种子：OA 请假审批（部门经理 → HR）', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM process_definition
    WHERE tenant_id = 1 AND code = 'OA_LEAVE' AND deleted = 0
);

-- ---------------------------------------------------------------------
-- 2. 版本快照：与定义 version = 1 对应，publish_status = 1（已发布）
-- ---------------------------------------------------------------------
INSERT INTO process_definition_version
    (id, tenant_id, definition_id, version, definition_json, publish_status, deleted)
SELECT 900000000000000102, 1, 900000000000000101, 1,
       '{"type":"stateMachine","nodes":[{"key":"start","type":"START_EVENT"},{"key":"dept_manager","type":"USER_TASK","name":"部门经理审批","assignee":"dept_manager"},{"key":"hr","type":"USER_TASK","name":"HR审批","assignee":"hr"},{"key":"end","type":"END_EVENT"}],"flows":[{"key":"f1","from":"start","to":"dept_manager"},{"key":"f2","from":"dept_manager","to":"hr"},{"key":"f3","from":"hr","to":"end"}]}',
       1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM process_definition_version
    WHERE tenant_id = 1 AND definition_id = 900000000000000101 AND version = 1 AND deleted = 0
);
