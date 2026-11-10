-- =====================================================================
-- Wave 5.79 M0：MES 车间工作日历（V3）
-- =====================================================================
-- 适用库   : bluewhale_mes
-- 执行方式 : Flyway 自动执行
-- 迁移说明 : 补齐设计稿中的 mes_md_shift_calendar（按日 / 班次维护工作日历），
--            用于 M1 有限产能排程与班组出勤校验。
-- 命名约定 : 继续沿用上下文二级前缀 mes_md_。
-- =====================================================================

CREATE TABLE IF NOT EXISTS mes_md_shift_calendar (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    calendar_date DATE NOT NULL COMMENT '日历日期',
    shift_id BIGINT NOT NULL COMMENT '班次 ID（mes_md_shift.id）',
    day_type VARCHAR(16) NOT NULL DEFAULT 'WORKDAY' COMMENT '日期类型：WORKDAY/REST/HOLIDAY',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_shift_calendar (tenant_id, calendar_date, shift_id),
    KEY idx_mes_md_shift_calendar_shift (tenant_id, shift_id, calendar_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 车间工作日历';

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'mes_md_shift_calendar';
-- =====================================================================
