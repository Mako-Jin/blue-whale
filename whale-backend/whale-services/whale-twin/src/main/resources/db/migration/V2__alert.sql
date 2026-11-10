-- ---------------------------------------------------------------------
-- Wave 6.06 M1：阈值告警表（运行时级别变化落库，供历史查询与未恢复列表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS twin_alert (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    scene_id         BIGINT        NOT NULL               COMMENT '所属场景ID（twin_scene.id）',
    entity_id        BIGINT        DEFAULT NULL           COMMENT '孪生实体ID（twin_entity.id，可空）',
    entity_code      VARCHAR(64)   DEFAULT NULL           COMMENT '实体编码（冗余，实体删除后仍可读）',
    binding_id       BIGINT        NOT NULL               COMMENT '绑定ID（twin_binding.id）',
    state_key        VARCHAR(64)   NOT NULL               COMMENT '实体状态键',
    alert_value      VARCHAR(256)  DEFAULT NULL           COMMENT '触发取值（字符串化）',
    previous_level   VARCHAR(16)   DEFAULT NULL           COMMENT '上一级别（首评为空）',
    level            VARCHAR(16)   NOT NULL               COMMENT '当前级别：normal / warn / critical',
    thresholds_json  VARCHAR(256)  DEFAULT NULL           COMMENT '参与判定的阈值（JSON 数组字符串）',
    resolved         TINYINT       NOT NULL DEFAULT 0     COMMENT '是否已恢复：0 未恢复 / 1 已恢复',
    resolve_time     DATETIME      DEFAULT NULL           COMMENT '恢复时间（恢复到 normal 时回填）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_scene_time (scene_id, create_time),
    KEY idx_scene_resolved (scene_id, resolved),
    KEY idx_binding_key (binding_id, state_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='孪生阈值告警表';
