-- =====================================================================
-- Wave 5.87：sys_permission.path 扩宽迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-permission-path-width.sql
-- 背景     : 5.77 视频（sys:video:browse）与 5.79 MES M3/M4 使用逗号分隔的多端点
--            权限路径，原 VARCHAR(255) 长度不足，导致 Data too long for column 'path'。
-- 变更     : path 扩宽至 VARCHAR(2048)，与 V1__baseline.sql / init.sql 定义对齐；
--            重复执行无副作用，不做数据删除。
-- =====================================================================

ALTER TABLE sys_permission
    MODIFY COLUMN path VARCHAR(2048) DEFAULT NULL COMMENT '接口请求路径（URI），支持逗号分隔多端点';
