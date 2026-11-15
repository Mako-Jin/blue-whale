-- =============================================================================
-- 文件        : V33__form_data_import_permission.sql
-- 说明        : 表单数据批量导入独立权限码 sys:form:data:import
--
-- 背景：
--   批次 2 新增 CSV 批量导入接口（模板下载 + 导入）。导入会批量写入主表与物理表，
--   不能复用单条提交权限 sys:form:data:submit，否则任何可提单角色都能一次性灌入大量数据。
--
-- 幂等：
--   - sys_permission 判重键 = perm_code（uk_perm_code），已存在则跳过；
--   - 不自动给任何角色补授权，由管理员按需分配。
--
-- 权限编号 : 2422 = 表单数据批量导入（sys:form:data:import）
-- =============================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2422, 0, 'sys:form:data:import', '表单数据批量导入', 'API',
       '/form/v1/data/import', 'POST', 2422, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:form:data:import');
