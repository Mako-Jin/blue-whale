-- =====================================================================
-- 鲸议台 V13：申诉材料（M7-7 / F-09-08 补充）
-- =====================================================================
-- 说明 : 1) 文件字节归 whale-file，本表只存 fileId 与展示用元数据快照。
--        2) append-only：材料随工单一次提交，提交后不可追加/删除，
--           故不设 update_time / deleted（规范 §2.2 例外）。
--        3) 唯一键挡住同一工单重复引用同一文件（前端重试、连点）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS fm_appeal_material (
    id           BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    appeal_id    BIGINT       NOT NULL               COMMENT '申诉工单ID',
    file_id      BIGINT       NOT NULL               COMMENT 'whale-file 文件ID',
    file_name    VARCHAR(255) NULL                   COMMENT '文件名快照（方便列表直显）',
    file_size    BIGINT       NULL                   COMMENT '文件字节数快照',
    content_type VARCHAR(128) NULL                   COMMENT 'MIME 类型快照',
    sort_no      INT          NOT NULL DEFAULT 0     COMMENT '展示顺序（提交顺序）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_appeal_material (tenant_id, appeal_id, file_id),
    KEY idx_fm_appeal_material_appeal (tenant_id, appeal_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛申诉材料（仅存文件引用）';
