-- =====================================================================
-- 执行器注册与寻址（对齐 xxl-job 的 executor 层）
-- =====================================================================
-- 背景：调度中心原先把目标地址写在 job_info.handler_param 里，容器重建、
--       扩缩容、灰度多版本都会导致任务打到错误实例；本迁移引入执行器
--       注册表，把「谁在线、谁支持哪些任务」变成可查询的数据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 执行器注册表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS job_executor (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID',
    app_name    VARCHAR(64)   NOT NULL               COMMENT '执行器应用名（Nacos 服务名），如 whale-crm',
    address     VARCHAR(128)  NOT NULL               COMMENT '实例地址 host:port',
    job_codes   VARCHAR(2048) DEFAULT NULL           COMMENT '该实例支持的任务编码，逗号分隔（来自 @ScheduledJob 扫描）',
    last_beat   DATETIME      NOT NULL               COMMENT '最近心跳时间（超时未心跳视为离线）',
    status      TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 在线 / 0 已注销',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_app_address (tenant_id, app_name, address),
    KEY idx_tenant_app (tenant_id, app_name),
    KEY idx_last_beat (last_beat)
) COMMENT='执行器注册表';

-- ---------------------------------------------------------------------
-- 2. 任务信息表补充执行器定位字段
-- ---------------------------------------------------------------------
ALTER TABLE job_info
    ADD COLUMN executor_app VARCHAR(64)  DEFAULT NULL COMMENT '执行器应用名（Nacos 服务名），HTTP 型任务据此寻址' AFTER job_group,
    ADD COLUMN job_code     VARCHAR(128) DEFAULT NULL COMMENT '任务编码（对应 @ScheduledJob.code），为空时按执行器参数直连' AFTER executor_app;
