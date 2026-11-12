-- =====================================================================
-- 员工论坛（whale-forum） Flyway V3：默认版块种子
-- =====================================================================
-- 适用范围 : 仅 seed 平台租户（tenant_id = 1）的初始版块，其他租户自行在管理台建版块。
-- 幂等口径 : 以 (tenant_id, code) 判重；重复执行不插入、不改既有配置。
-- 编号口径 : 主键用固定值（1001 起），便于本地环境重装后引用稳定；
--            正式环境新增版块由应用生成雪花 ID，不占用本段。
-- =====================================================================

INSERT INTO fm_board (id, tenant_id, parent_id, code, name, description, icon, visibility, visible_roles,
                      visible_depts, audit_mode, post_policy, sort_no, status)
SELECT 1001, 1, NULL, 'hall', '鲸聚贤堂', '综合讨论：公司动态、行业观察、随手分享', 'team', 'ALL', NULL,
       NULL, 0, 'OPEN', 10, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM fm_board WHERE tenant_id = 1 AND code = 'hall');

INSERT INTO fm_board (id, tenant_id, parent_id, code, name, description, icon, visibility, visible_roles,
                      visible_depts, audit_mode, post_policy, sort_no, status)
SELECT 1002, 1, NULL, 'workshop', '鲸百工坊', '技术交流：架构、代码、工具链与踩坑记录', 'tool', 'ALL', NULL,
       NULL, 0, 'OPEN', 20, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM fm_board WHERE tenant_id = 1 AND code = 'workshop');

INSERT INTO fm_board (id, tenant_id, parent_id, code, name, description, icon, visibility, visible_roles,
                      visible_depts, audit_mode, post_policy, sort_no, status)
SELECT 1003, 1, NULL, 'crossing', '鲸问津渡', '问答：提问前先搜一搜，采纳后请标记已解决', 'question', 'ALL', NULL,
       NULL, 0, 'OPEN', 30, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM fm_board WHERE tenant_id = 1 AND code = 'crossing');

INSERT INTO fm_board (id, tenant_id, parent_id, code, name, description, icon, visibility, visible_roles,
                      visible_depts, audit_mode, post_policy, sort_no, status)
SELECT 1004, 1, NULL, 'notice', '鲸榜文亭', '公告发布：仅管理员可发，先审后发', 'notification', 'ALL', NULL,
       NULL, 1, 'NOTICE_ONLY', 5, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM fm_board WHERE tenant_id = 1 AND code = 'notice');
