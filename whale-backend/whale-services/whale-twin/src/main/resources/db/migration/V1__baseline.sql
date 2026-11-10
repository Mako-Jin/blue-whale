-- =====================================================================
-- BlueWhale 微服务平台 - 数字孪生平台（whale-twin） Flyway 基线 V1
-- =====================================================================
-- 适用库   : bluewhale_twin（MySQL 8.0+，utf8mb4）
-- Wave     : 6.03（数字孪生平台 M0）
-- 说明     : 场景 / 孪生实体 / 数据源绑定 / 标注热点 / 发布快照 五张核心表。
--            建表规则对齐 whale-iot/whale-assistant V1：IF NOT EXISTS、
--            雪花主键、租户行级隔离、create_time/update_time/deleted 三件套。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 场景表（3D 孪生场景的定义载体）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS twin_scene (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    code             VARCHAR(64)   NOT NULL               COMMENT '场景编码（租户内唯一，如 plant-demo）',
    name             VARCHAR(128)  NOT NULL               COMMENT '场景名称',
    description      VARCHAR(512)  DEFAULT NULL           COMMENT '场景描述',
    thumbnail_file_id BIGINT       DEFAULT NULL           COMMENT '缩略图文件ID（whale-file）',
    scene_config     JSON          DEFAULT NULL           COMMENT '场景级配置（背景/光照/雾效等，前端约定）',
    version          INT           NOT NULL DEFAULT 1     COMMENT '当前编辑版本号（发布时冻结进快照）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'draft' COMMENT '状态：draft 草稿 / published 已发布 / disabled 已停用',
    created_by       BIGINT        DEFAULT NULL           COMMENT '创建人用户ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数字孪生场景表';

-- ---------------------------------------------------------------------
-- 孪生实体表（场景树节点：模型资产 + 变换 + 父节点）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS twin_entity (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    scene_id         BIGINT        NOT NULL               COMMENT '所属场景ID（twin_scene.id）',
    entity_code      VARCHAR(64)   NOT NULL               COMMENT '实体编码（场景内唯一，如 boiler-01）',
    name             VARCHAR(128)  NOT NULL               COMMENT '实体名称',
    parent_id        BIGINT        DEFAULT NULL           COMMENT '父节点ID（twin_entity.id，NULL 为根节点）',
    model_file_id    BIGINT        DEFAULT NULL           COMMENT '模型资产文件ID（whale-file，GLB/GLTF）',
    transform_json   JSON          DEFAULT NULL           COMMENT '变换矩阵（position/rotation/scale，前端约定 JSON）',
    extra_config     JSON          DEFAULT NULL           COMMENT '实体级扩展配置（如强调色/动画预设）',
    sort_order       INT           NOT NULL DEFAULT 0     COMMENT '同级排序号',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scene_code (scene_id, entity_code),
    KEY idx_scene_parent (scene_id, parent_id),
    KEY idx_model_file (model_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='孪生实体表';

-- ---------------------------------------------------------------------
-- 数据源绑定表（实体 ↔ 数据源：IoT 设备属性或 internal 接口）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS twin_binding (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    entity_id        BIGINT        NOT NULL               COMMENT '孪生实体ID（twin_entity.id）',
    source_type      VARCHAR(32)   NOT NULL               COMMENT '数据源类型：iot_device 设备属性 / internal_api 内网接口',
    bind_key         VARCHAR(256)  NOT NULL               COMMENT '绑定键：iot_device 为 deviceId；internal_api 为接口路径',
    property_map     JSON          DEFAULT NULL           COMMENT '属性映射 JSON（源字段 → 实体状态键，如 temperature→color）',
    poll_interval_ms INT           NOT NULL DEFAULT 5000  COMMENT '轮询间隔毫秒（internal_api 用；iot_device 走 SSE 推送忽略）',
    enabled          TINYINT       NOT NULL DEFAULT 1     COMMENT '是否启用：0 停用 / 1 启用',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_entity (entity_id),
    KEY idx_source (source_type, bind_key(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='孪生数据源绑定表';

-- ---------------------------------------------------------------------
-- 标注/热点表（场景内位置锚点：文本说明、跳转、告警定位载体）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS twin_marker (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    scene_id         BIGINT        NOT NULL               COMMENT '所属场景ID（twin_scene.id）',
    entity_id        BIGINT        DEFAULT NULL           COMMENT '挂靠实体ID（twin_entity.id，可空表示场景级锚点）',
    marker_type      VARCHAR(16)   NOT NULL DEFAULT 'label' COMMENT '类型：label 标签 / hotspot 热点 / alarm 告警定位',
    content          VARCHAR(1024) DEFAULT NULL           COMMENT '标注内容（文本或 JSON，前端约定）',
    position_json    JSON          DEFAULT NULL           COMMENT '三维坐标 JSON（x/y/z）',
    link_url         VARCHAR(512)  DEFAULT NULL           COMMENT '点击跳转地址（可空）',
    enabled          TINYINT       NOT NULL DEFAULT 1     COMMENT '是否启用：0 停用 / 1 启用',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_scene (scene_id),
    KEY idx_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='孪生标注热点表';

-- ---------------------------------------------------------------------
-- 发布快照表（场景发布时冻结版本，供回显与回滚）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS twin_snapshot (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    scene_id         BIGINT        NOT NULL               COMMENT '所属场景ID（twin_scene.id）',
    version          INT           NOT NULL               COMMENT '快照版本号（同场景内递增）',
    snapshot_json    JSON          NOT NULL               COMMENT '快照内容（场景配置 + 实体树 + 绑定 + 标注的整包 JSON）',
    remark           VARCHAR(256)  DEFAULT NULL           COMMENT '发布说明',
    published_by     BIGINT        DEFAULT NULL           COMMENT '发布人用户ID',
    publish_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scene_version (scene_id, version),
    KEY idx_scene (scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='孪生发布快照表';
