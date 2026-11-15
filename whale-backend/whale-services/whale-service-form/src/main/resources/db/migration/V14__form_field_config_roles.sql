-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V14：字段级角色权限
-- =====================================================================
-- 背景   : 同一表单不同角色需要看到/可编辑的字段不同，当前只有全局 editable，
--          没有字段级的角色可见性与可编辑性。
-- 方案   : form_field_config 增加 visible_roles / editable_roles（JSON 字符串数组文本）；
--          编译表单视图快照时写入 schema 字段节点；服务端按请求头 X-User-Roles 裁剪与校验。
--          空值或空数组表示「不限制角色」。
-- 幂等   : 按 information_schema.COLUMNS 判定后再 ADD COLUMN，可重复执行。
-- =====================================================================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_field_config'
        AND COLUMN_NAME = 'visible_roles') = 0,
    'ALTER TABLE form_field_config ADD COLUMN visible_roles VARCHAR(512) DEFAULT NULL COMMENT ''可见角色编码JSON数组（空表示不限制）'' AFTER editable',
    'SELECT 1');
PREPARE form_v14_stmt FROM @ddl;
EXECUTE form_v14_stmt;
DEALLOCATE PREPARE form_v14_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_field_config'
        AND COLUMN_NAME = 'editable_roles') = 0,
    'ALTER TABLE form_field_config ADD COLUMN editable_roles VARCHAR(512) DEFAULT NULL COMMENT ''可编辑角色编码JSON数组（空表示不限制）'' AFTER visible_roles',
    'SELECT 1');
PREPARE form_v14_stmt FROM @ddl;
EXECUTE form_v14_stmt;
DEALLOCATE PREPARE form_v14_stmt;
