-- =====================================================================
-- 权限码格式修正：sys:mes:work-order:* → sys:mes:workorder:*（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.64-perm-code-format.sql
-- 背景     : PermCode 值对象要求 `^[a-z][a-z0-9]*(:[a-z0-9]+)+$`（不含连字符）；
--            Wave P3-9b 播种的 207–210 与菜单 26 使用 `work-order` 导致
--            `/auth/me` 组装权限时抛 PARAM_ERROR（code=400），登录后前端不可用。
-- 幂等说明 : 仅更新仍为旧值的行；sys_role_permission 按 permission_id 关联，不受影响。
-- =====================================================================

UPDATE sys_permission SET perm_code = 'sys:mes:workorder:list'
WHERE id = 207 AND perm_code = 'sys:mes:work-order:list';

UPDATE sys_permission SET perm_code = 'sys:mes:workorder:add'
WHERE id = 208 AND perm_code = 'sys:mes:work-order:add';

UPDATE sys_permission SET perm_code = 'sys:mes:workorder:edit'
WHERE id = 209 AND perm_code = 'sys:mes:work-order:edit';

UPDATE sys_permission SET perm_code = 'sys:mes:workorder:delete'
WHERE id = 210 AND perm_code = 'sys:mes:work-order:delete';

UPDATE sys_menu SET permission_code = 'sys:mes:workorder:list'
WHERE id = 26 AND permission_code = 'sys:mes:work-order:list';
