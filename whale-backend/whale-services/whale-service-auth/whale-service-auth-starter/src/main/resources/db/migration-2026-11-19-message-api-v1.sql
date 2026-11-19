-- =====================================================================
-- whale-message 接口版本化：/message/** → /message/v1/**（bluewhale_auth）
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-2026-11-19-message-api-v1.sql
-- 背景     : whale-message 全部 REST 接口统一加版本段（/message/v1/{资源}），
--            与 auth /user/v1/**、file /file/v1/** 保持一致。
--            权限点路径是精确匹配的，路径不改会导致新接口 403。
-- 变更范围 : sys_permission.path（仅消息服务的 API 权限点）：
--            234  查询消息记录          /message/v1/record/**
--            239  查询消息模板          /message/v1/template/**
--            240  新增消息模板          /message/v1/template
--            241  编辑消息模板          /message/v1/template/{id}
--            242  删除消息模板          /message/v1/template/{id}
--            243  重试消息投递          /message/v1/record/{id}/retry
--            305  查询系统公告          /message/v1/announce/page
--            306  发布系统公告          /message/v1/announce
--            307  撤回系统公告          /message/v1/announce/{id}/revoke
--            308  删除系统公告          /message/v1/announce/{id}
-- 不变更   : sys_menu.path（/message/template、/message/announce 是前端路由，非接口路径）；
--            /message/v1/inapp/** 为登录用户自助能力，仍不分配权限码；
--            /message/v1/internal/** 仅内网调用，由网关 internal-deny 拦截，不分配权限码。
-- 幂等说明 : UPDATE 值固定，重复执行无副作用；不新增/删除权限点与角色授权。
-- =====================================================================

UPDATE sys_permission
SET path = '/message/v1/record/page,/message/v1/record/{id}'
WHERE perm_code = 'sys:message:record:list' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/template/page,/message/v1/template/{id}'
WHERE perm_code = 'sys:message:template:list' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/template'
WHERE perm_code = 'sys:message:template:add' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/template/{id}'
WHERE perm_code = 'sys:message:template:edit' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/template/{id}'
WHERE perm_code = 'sys:message:template:delete' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/record/{id}/retry'
WHERE perm_code = 'sys:message:record:retry' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/announce/page'
WHERE perm_code = 'sys:message:announce:list' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/announce'
WHERE perm_code = 'sys:message:announce:publish' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/announce/{id}/revoke'
WHERE perm_code = 'sys:message:announce:revoke' AND deleted = 0;

UPDATE sys_permission
SET path = '/message/v1/announce/{id}'
WHERE perm_code = 'sys:message:announce:delete' AND deleted = 0;

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission
--    WHERE perm_code LIKE 'sys:message:%' AND deleted = 0
--    ORDER BY id;
--     期望：path 全部以 /message/v1/ 开头，且无残留 /message/record、/message/template、/message/announce
-- =====================================================================
