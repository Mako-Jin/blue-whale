-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.53：档案服务权限点）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_permission 表的运行库
--            （保留存量权限与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这些权限点）。
-- 说明     : 幂等（INSERT IGNORE 按唯一键去重）。段位约定：档案预留 140–159。
--            · 140–143 门类 / 规则维护（M1 已落地接口）
--            · 144–150 条目 / 下载 / 销毁 / 同步（M3–M4 接口落地后生效）
--            super_admin 默认拥有全部权限，其余角色由授权界面分配。
--            菜单项待 admin 档案管理前端页面落地后再登记，避免指向 404。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (140, 0, 'sys:archive:category:list', '查询档案门类', 'API', '/archive/category/tree', 'GET', 140, 1),
    (141, 0, 'sys:archive:category:manage', '维护档案门类', 'API', '/archive/category/**', 'POST,PUT,DELETE', 141, 1),
    (142, 0, 'sys:archive:rule:list', '查询归档规则', 'API', '/archive/rule/page', 'GET', 142, 1),
    (143, 0, 'sys:archive:rule:manage', '维护归档规则', 'API', '/archive/rule/**', 'POST,PUT,DELETE', 143, 1),
    (144, 0, 'sys:archive:item:list', '查询档案条目', 'API', '/archive/item/**', 'GET', 144, 1),
    (145, 0, 'sys:archive:item:read', '查看档案详情', 'API', '/archive/item/{id}', 'GET', 145, 1),
    (146, 0, 'sys:archive:item:edit', '整理档案条目', 'API', '/archive/item/{id}', 'PUT', 146, 1),
    (147, 0, 'sys:archive:item:archive', '确认归档', 'API', '/archive/item/{id}/confirm', 'POST', 147, 1),
    (148, 0, 'sys:archive:item:download', '下载档案文件', 'API', '/archive/item/{id}/files/{fileId}/download', 'GET', 148, 1),
    (149, 0, 'sys:archive:item:destroy', '销毁档案', 'API', '/archive/item/{id}/destroy', 'POST', 149, 1),
    (150, 0, 'sys:archive:sync:run', '触发档案同步', 'API', '/archive/sync/run', 'POST', 150, 1);
