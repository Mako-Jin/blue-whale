-- =====================================================================
-- Wave 5.61 M2：印章与本地盖章（whale-contract）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_contract
-- 执行方式 : mysql -u<user> -p bluewhale_contract < migration-5.62-contract-esign.sql
-- 说明     : 仅新增 4 张签署相关表 + 操作审计日志表，不修改/删除 M1 既有数据；
--            CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS ct_seal (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    name          VARCHAR(128) NOT NULL            COMMENT '印章名称',
    seal_type     VARCHAR(16)  NOT NULL DEFAULT 'CONTRACT'
                                                   COMMENT '印章类型：COMPANY 公章 / CONTRACT 合同章 / LEGAL 法人章 / FINANCE 财务章',
    image_file_id BIGINT       DEFAULT NULL        COMMENT '印章图片文件ID（whale-file，空则签章时使用内置兜底印章）',
    keeper_user_id BIGINT      DEFAULT NULL        COMMENT '印章保管人用户ID',
    valid_from    DATE         DEFAULT NULL        COMMENT '有效期起',
    valid_to      DATE         DEFAULT NULL        COMMENT '有效期止',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED'
                                                   COMMENT '状态：ENABLED 启用 / DISABLED 停用',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='印章表';

CREATE TABLE IF NOT EXISTS ct_seal_grant (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    seal_id           BIGINT        NOT NULL            COMMENT '印章ID',
    grantee_user_id   BIGINT        NOT NULL            COMMENT '被授权用户ID',
    contract_category VARCHAR(32)   DEFAULT NULL        COMMENT '适用合同类别（空表示全类别）',
    max_amount        DECIMAL(18,2) DEFAULT NULL        COMMENT '单份合同金额上限（空表示不限）',
    valid_from        DATE          DEFAULT NULL        COMMENT '授权生效日期',
    valid_to          DATE          DEFAULT NULL        COMMENT '授权失效日期',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ENABLED'
                                                        COMMENT '状态：ENABLED 有效 / DISABLED 停用',
    granted_by        BIGINT        DEFAULT NULL        COMMENT '授权操作人用户ID',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_seal_grantee (seal_id, grantee_user_id),
    KEY idx_grantee (tenant_id, grantee_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用章授权表';

CREATE TABLE IF NOT EXISTS ct_sign_task (
    id               BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    contract_id      BIGINT       NOT NULL            COMMENT '合同ID',
    version_id       BIGINT       DEFAULT NULL        COMMENT '合同版本ID',
    seq_no           INT          NOT NULL DEFAULT 1  COMMENT '签署顺序号（从 1 递增）',
    signer_user_id   BIGINT       DEFAULT NULL        COMMENT '签署人用户ID（外部相对方为空）',
    signer_party     VARCHAR(8)   DEFAULT NULL        COMMENT '签署方：A 甲方 / B 乙方 / OTHER 其他',
    signer_name      VARCHAR(64)  DEFAULT NULL        COMMENT '签署人姓名',
    provider         VARCHAR(16)  NOT NULL DEFAULT 'LOCAL'
                                                      COMMENT '签署渠道：LOCAL 本地 / ESIGNBAO / FADADA / SSQ',
    provider_task_id VARCHAR(128) DEFAULT NULL        COMMENT '三方平台任务ID（M3 启用）',
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                      COMMENT '状态：PENDING 待签 / SIGNING 签署中 / SIGNED 已签 / REFUSED 已拒签 / EXPIRED 已失效',
    signed_at        DATETIME     DEFAULT NULL        COMMENT '签署完成时间',
    signed_file_id   BIGINT       DEFAULT NULL        COMMENT '签章件文件ID',
    refuse_reason    VARCHAR(255) DEFAULT NULL        COMMENT '拒签原因',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_contract_seq (tenant_id, contract_id, seq_no),
    KEY idx_contract (contract_id, seq_no),
    KEY idx_provider_task (provider, provider_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='签署任务表';

CREATE TABLE IF NOT EXISTS ct_sign_record (
    id              BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    contract_id     BIGINT       NOT NULL            COMMENT '合同ID',
    sign_task_id    BIGINT       NOT NULL            COMMENT '签署任务ID',
    signer_user_id  BIGINT       DEFAULT NULL        COMMENT '签署人用户ID',
    seal_id         BIGINT       DEFAULT NULL        COMMENT '使用印章ID',
    cert_serial     VARCHAR(128) DEFAULT NULL        COMMENT '签名证书序列号（未配置 P12 时为空）',
    signed_at       DATETIME     NOT NULL            COMMENT '签署时间',
    original_sha256 VARCHAR(64)  NOT NULL            COMMENT '盖章前文件 SHA-256',
    signed_sha256   VARCHAR(64)  NOT NULL            COMMENT '签章件 SHA-256',
    algorithm       VARCHAR(32)  NOT NULL DEFAULT 'NONE'
                                                     COMMENT '签名算法：SHA256withRSA / NONE（仅视觉章 + 哈希）',
    evidence_no     VARCHAR(64)  NOT NULL            COMMENT '证据编号（租户内唯一）',
    chain_hash      VARCHAR(64)  NOT NULL            COMMENT '链式哈希',
    client_ip       VARCHAR(64)  DEFAULT NULL        COMMENT '签署客户端IP',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_task (tenant_id, sign_task_id),
    UNIQUE KEY uk_tenant_evidence (tenant_id, evidence_no),
    KEY idx_contract_signed (contract_id, signed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='签署记录表';

CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-contract',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(16)   DEFAULT NULL           COMMENT '操作类型',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型',
    target_id      VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象ID',
    request_params TEXT          NULL                   COMMENT '请求参数（JSON，已脱敏）',
    result_body    TEXT          NULL                   COMMENT '返回体（JSON，已脱敏并截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码',
    biz_status     INT           NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '操作IP',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（操作发生时间）',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_user_id (user_id),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_biz_status (biz_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作审计日志表';

INSERT IGNORE INTO ct_seal (id, tenant_id, name, seal_type, image_file_id, keeper_user_id, valid_from, valid_to, status)
VALUES (1, 1, '公司合同专用章', 'CONTRACT', NULL, 1, '2026-01-01', '2030-12-31', 'ENABLED');

INSERT IGNORE INTO ct_seal_grant (id, tenant_id, seal_id, grantee_user_id, contract_category, max_amount,
                                  valid_from, valid_to, status, granted_by)
VALUES (1, 1, 1, 1, NULL, 1000000.00, '2026-01-01', '2030-12-31', 'ENABLED', 1);
