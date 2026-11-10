-- =====================================================================
-- BlueWhale 学习培训考试应用（whale-lms）M4-1 证书与换发（Wave 5.87）
-- =====================================================================
-- 范围       : 证书模板 / 证书（发放、撤销、换发）
-- 需求       : REQ-FUNC-090（证书与学分，M4）
-- 边界       : 证书正文只存模板占位文本与业务快照，不存员工主数据；
--              有效期以 valid_to 判定，status 只表达人工动作（撤销/换发），
--              避免「到期」需要定时任务刷状态而出现的空窗与漏刷。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 证书模板
--    issue_basis: 发证依据 MANUAL 手工 / COURSE 课程完成 / EXAM 考试通过 / TRAINING 培训完成，
--                 决定发证时校验哪个来源（手工发证不做来源校验）
--    valid_months: 有效期月数，0 表示长期有效（换发时沿用同一口径）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_cert_template (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    template_code       VARCHAR(64)   NOT NULL                COMMENT '模板编码（租户内唯一，大写归一）',
    name                VARCHAR(128)  NOT NULL                COMMENT '证书名称',
    category            VARCHAR(32)   NOT NULL DEFAULT 'GENERAL' COMMENT '证书类别（自由分类，如 SAFETY/SKILL/GENERAL）',
    issue_basis         VARCHAR(16)   NOT NULL DEFAULT 'MANUAL' COMMENT '发证依据：MANUAL/COURSE/EXAM/TRAINING',
    valid_months        INT           NOT NULL DEFAULT 0      COMMENT '有效期月数（0 为长期有效）',
    cert_prefix         VARCHAR(16)   NOT NULL DEFAULT 'CERT' COMMENT '证书编号前缀（大写字母数字）',
    content             TEXT          NULL                    COMMENT '证书正文模板（占位符按发证时替换）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_cert_template_code (tenant_id, template_code),
    KEY idx_lr_cert_template_status (tenant_id, status)
) COMMENT='证书模板';

-- ---------------------------------------------------------------------
-- 2. 证书
--    cert_no     : 前缀-年份-6 位流水，租户内唯一；流水按 (租户, 年份) 独立递增
--    seq_no      : 与 cert_no 分离冗余存储，供发证时取号（不解析字符串）
--    renew_from_id: 换发来源证书 ID，0 表示首次发证。参与唯一键，
--                   使「同一来源只发一次」与「同一旧证只换发一次」都由数据库保证
--    status      : ACTIVE / REVOKED（撤销）/ RENEWED（已换发）；EXPIRED 为展示期派生态，不落库
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_certificate (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    cert_no             VARCHAR(64)   NOT NULL                COMMENT '证书编号（租户内唯一）',
    template_id         BIGINT        NOT NULL                COMMENT '证书模板ID',
    template_name       VARCHAR(128)  NOT NULL                COMMENT '证书名称快照（模板改名不影响历史）',
    category            VARCHAR(32)   NOT NULL DEFAULT 'GENERAL' COMMENT '证书类别快照',
    user_id             BIGINT        NOT NULL                COMMENT '持证人用户ID',
    username            VARCHAR(64)   DEFAULT NULL            COMMENT '持证人用户名（冗余展示）',
    source_type         VARCHAR(16)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL/COURSE/EXAM/TRAINING',
    source_id           BIGINT        NOT NULL DEFAULT 0      COMMENT '来源业务ID（课程/考试/培训班 ID，手工为 0）',
    source_title        VARCHAR(200)  DEFAULT NULL            COMMENT '来源业务标题快照',
    issue_year          INT           NOT NULL                COMMENT '发证年份（流水按年重置的依据）',
    seq_no              INT           NOT NULL                COMMENT '当年流水号（从 1 起）',
    issued_at           DATETIME      NOT NULL                COMMENT '发证时间',
    valid_from          DATETIME      NOT NULL                COMMENT '有效期起',
    valid_to            DATETIME      DEFAULT NULL            COMMENT '有效期止（NULL 为长期有效）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/REVOKED/RENEWED',
    revoked_at          DATETIME      DEFAULT NULL            COMMENT '撤销时间',
    revoked_by          BIGINT        DEFAULT NULL            COMMENT '撤销人ID',
    revoke_reason       VARCHAR(500)  DEFAULT NULL            COMMENT '撤销原因（必填留痕）',
    renew_from_id       BIGINT        NOT NULL DEFAULT 0      COMMENT '换发来源证书ID（0 为首次发证）',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_certificate_no (tenant_id, cert_no),
    UNIQUE KEY uk_lr_certificate_seq (tenant_id, issue_year, seq_no),
    UNIQUE KEY uk_lr_certificate_source (tenant_id, template_id, user_id, source_type, source_id, renew_from_id),
    KEY idx_lr_certificate_user (tenant_id, user_id, issued_at),
    KEY idx_lr_certificate_status (tenant_id, status, valid_to)
) COMMENT='证书';
