-- Wave     : 6.43（论坛服务 M2：审核台 / 举报 / 处置 / 敏感词 / 通知）
-- 依据     : docs/requirements/2026-11-12-forum-2d-functional-spec-v1.3.md
--            §六 数据一致性硬规则（禁言以 fm_user_ban 为唯一事实源）
--            §十 集成与降级（通知走 outbox，whale-message 不可达不丢消息）
-- 说明     : 三张表都挂 tenant_id，走 MP 租户插件自动隔离；均为新增表，不动 V2 既有结构。

-- ---------------------------------------------------------------------
-- 敏感词库：动作三档（拦截 / 替换 / 转待审），词表可热重载
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_sensitive_word (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    word             VARCHAR(128)  NOT NULL               COMMENT '敏感词（入库前去空格、统一小写）',
    action           VARCHAR(16)   NOT NULL DEFAULT 'BLOCK' COMMENT '动作：BLOCK 拦截 / REPLACE 替换 / PENDING 转待审',
    category         VARCHAR(32)   DEFAULT NULL           COMMENT '分类（AD 广告 / ABUSE 辱骂 / POLITICS 等，仅统计用）',
    status           TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 启用 / 0 停用',
    creator_id       BIGINT        DEFAULT NULL           COMMENT '创建人',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_word (tenant_id, word),
    KEY idx_fm_word_action (tenant_id, action, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛敏感词库';

-- ---------------------------------------------------------------------
-- 禁言/封号：唯一事实源。判定只看「status=ACTIVE 且未到期」，
-- 不靠「最新一条处置记录」推断——那样解禁/改期会算错。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_user_ban (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    user_id          BIGINT        NOT NULL               COMMENT '被处置用户ID',
    ban_type         VARCHAR(16)   NOT NULL               COMMENT '类型：MUTE 禁言 / BAN 封号',
    reason           VARCHAR(512)  NOT NULL               COMMENT '处置原因（必填，通知与申诉依据）',
    expire_time      DATETIME      DEFAULT NULL           COMMENT '到期时间（NULL 表示永久）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 生效 / RELEASED 已解除',
    operator_id      BIGINT        DEFAULT NULL           COMMENT '操作人',
    source_report_id BIGINT        DEFAULT NULL           COMMENT '来源举报工单ID（可空）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_fm_ban_user (tenant_id, user_id, status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛禁言/封号记录';

-- ---------------------------------------------------------------------
-- 通知 outbox：先落库再投递。whale-message 不可达时消息留在 PENDING，
-- 由补偿任务重投（同 idempotency_key 重复投递在 message 侧幂等）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_notify_outbox (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    receiver_id      BIGINT        NOT NULL               COMMENT '接收人用户ID',
    biz_type         VARCHAR(32)   NOT NULL               COMMENT '业务类型：REPLY/MENTION/MODERATION',
    biz_id           BIGINT        DEFAULT NULL           COMMENT '业务主键（帖子/楼层/工单ID）',
    idempotency_key  VARCHAR(96)   NOT NULL               COMMENT '幂等键（同键只发一次）',
    title            VARCHAR(128)  NOT NULL               COMMENT '消息标题',
    content          VARCHAR(2000) NOT NULL               COMMENT '消息正文',
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待投 / SENT 已投 / FAILED 放弃',
    retry_count      INT           NOT NULL DEFAULT 0     COMMENT '已重试次数',
    last_error       VARCHAR(512)  DEFAULT NULL           COMMENT '最后一次失败原因',
    next_retry_time  DATETIME      DEFAULT NULL           COMMENT '下次重试时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_notify_key (tenant_id, idempotency_key),
    KEY idx_fm_notify_pending (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛通知 outbox';

-- ---------------------------------------------------------------------
-- 开发/演示用违规词种子（动作覆盖三档，便于验收）
-- 生产环境由管理端维护真实词库，此处仅保证「非空即有词」不会静默不生效。
-- ---------------------------------------------------------------------
INSERT INTO fm_sensitive_word (id, tenant_id, word, action, category, status)
VALUES
    (2430000000000000001, 1, '代开发票', 'BLOCK', 'AD', 1),
    (2430000000000000002, 1, '博彩', 'BLOCK', 'ILLEGAL', 1),
    (2430000000000000003, 1, '加微信', 'REPLACE', 'AD', 1),
    (2430000000000000004, 1, '刷单', 'PENDING', 'AD', 1);
