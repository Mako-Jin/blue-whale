-- ---------------------------------------------------------------------
-- 用户场景视图偏好（2D / 3D 与默认场景）
-- 每个用户在每个租户下至多一条记录；无记录时前端回退 2D + 首个可见场景。
-- view_mode: 2D / 3D；default_scene_key 为空表示不指定（前端取默认场景）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sc_user_preference (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id             BIGINT        NOT NULL                COMMENT '用户ID',
    view_mode           VARCHAR(8)    NOT NULL DEFAULT '2D'   COMMENT '视图模式：2D / 3D',
    default_scene_key   VARCHAR(64)   DEFAULT NULL            COMMENT '默认场景标识（空表示未指定）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sc_user_preference (tenant_id, user_id)
) COMMENT='用户场景视图偏好';
