-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.47：知识助手菜单）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_menu 表的运行库
--            （保留存量菜单与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 2 行）。
-- 说明     : 本脚本幂等（INSERT IGNORE 按主键去重）。新增菜单：
--            · (9, 0)  知识助手（DIR，/agent）
--            · (10, 9) 知识问答（MENU，/agent/chat，permission_code=sys:agent:chat）
--            权限点 sys:agent:* 已在 Wave 5.47 轨 A 的 init.sql 中登记（id 100–103），
--            授权仍由角色分配界面完成（super_admin 默认拥有全部权限）。
-- =====================================================================

INSERT IGNORE INTO sys_menu
    (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (9, 0, '知识助手', '/agent', NULL, 'message', 2, 'DIR', NULL, 1, 1),
    (10, 9, '知识问答', '/agent/chat', 'agent/chat/index', 'comment', 1, 'MENU', 'sys:agent:chat', 1, 1);
