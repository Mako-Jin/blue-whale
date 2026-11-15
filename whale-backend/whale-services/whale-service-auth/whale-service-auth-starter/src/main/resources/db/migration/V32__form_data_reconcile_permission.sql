-- =============================================================================
-- 文件        : V32__form_data_reconcile_permission.sql
-- 说明        : 表单主从表对账独立权限码 sys:form:data:reconcile
--
-- 背景：
--   批次 1 新增主表 form_data 与各版本物理表的数据对账、差异查询与人工修复接口。
--   对账结果与修复动作直接触达物理表，不能复用查询权限 sys:form:data:list，
--   否则只读角色即可改写物理表数据。
--
-- 幂等：
--   - sys_permission 判重键 = perm_code（uk_perm_code），已存在则跳过；
--   - 不自动给任何角色补授权：对账属于运维动作，由管理员按需分配。
--
-- 权限编号 : 2421 = 表单数据对账（sys:form:data:reconcile）
-- =============================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2421, 0, 'sys:form:data:reconcile', '表单数据对账与修复', 'API',
       '/form/v1/data/reconcile', 'POST', 2421, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:form:data:reconcile');
