-- =====================================================================
-- 鲸议台 V12：申诉（M7-5 / F-09-08）
-- =====================================================================
-- 说明 : 1) target_type=BAN 时 target_id 存被处置用户ID；THREAD/POST 存内容ID。
--        2) status 三态与处置轨迹一致：PENDING → APPROVED / REJECTED。
--        3) 「同一对象仅一条待审申诉」无法用唯一键表达（历史记录会重复），
--           由服务层判重；这里只给查询索引。
-- =====================================================================

CREATE TABLE IF NOT EXISTS fm_appeal (
    id             BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    user_id        BIGINT       NOT NULL               COMMENT '申诉人用户ID',
    target_type    VARCHAR(8)   NOT NULL               COMMENT '申诉对象类型：THREAD / POST / BAN',
    target_id      BIGINT       NOT NULL               COMMENT '对象ID（BAN 时为被处置用户ID）',
    reason         VARCHAR(500) NOT NULL               COMMENT '申诉理由',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / APPROVED / REJECTED',
    handler_id     BIGINT       NULL                   COMMENT '复核人',
    handle_remark  VARCHAR(500) NULL                   COMMENT '复核意见',
    handle_time    DATETIME     NULL                   COMMENT '复核时间',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 正常 / 1 删除',
    PRIMARY KEY (id),
    KEY idx_fm_appeal_user (tenant_id, user_id, create_time),
    KEY idx_fm_appeal_status (tenant_id, status, create_time),
    CONSTRAINT ck_fm_appeal_target CHECK (target_type IN ('THREAD', 'POST', 'BAN')),
    CONSTRAINT ck_fm_appeal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛申诉工单';
