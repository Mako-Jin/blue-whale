-- V13: 分析导出接入 whale-file——导出内容不再落 MEDIUMTEXT，改为文件引用
-- 新增列对历史数据兼容（NULL 即仍回读 content 列的旧导出）。

ALTER TABLE `crm_analytics_export`
    ADD COLUMN `file_id`   BIGINT       DEFAULT NULL COMMENT '导出文件ID（whale-file 文件服务）' AFTER `content`,
    ADD COLUMN `file_name` VARCHAR(255) DEFAULT NULL COMMENT '导出文件名（含扩展名）' AFTER `file_id`,
    ADD COLUMN `file_size` BIGINT       DEFAULT NULL COMMENT '导出文件大小（字节）' AFTER `file_name`;

ALTER TABLE `crm_analytics_export`
    MODIFY COLUMN `content` MEDIUMTEXT DEFAULT NULL COMMENT 'CSV 内容（V13 前的历史导出仍保留于此，新导出不再写入）';
