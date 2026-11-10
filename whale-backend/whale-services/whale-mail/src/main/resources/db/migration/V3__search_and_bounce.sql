-- ============================================================
-- V3：全文检索（ngram 全文索引）+ 退信回执
--
-- 背景一：M1 之前的「搜索」是三列 LIKE，`%kw%` 用不上任何索引，
-- 十万封邮件时列表接口会被单次搜索拖到秒级以上，而且只能搜到主题、
-- 发件人和摘要，搜不到正文。这里补 ngram 全文索引。
--
-- 背景二：发信被退回时，退信通知会进 postmaster@ 或被退信的账号本身，
-- 原先既不入库也不提示，用户只能看到「发出去了」却不知道对方没收到。
-- 新增 mail_delivery_report 承接 DSN（RFC 3464）解析结果。
--
-- 执行注意：
-- 1. ngram 是 MySQL 内置解析器，无需安装插件，但 ngram_token_size 默认 2，
--    即单字（1 个汉字）无法命中；调用方对单字查询自动走 LIKE 兜底。
-- 2. 在已有表上 ADD FULLTEXT 会重建表且不允许并发写（LOCK=SHARED），
--    十万级以上表请在维护窗口执行，或临时关掉 Flyway 手工跑本文件。
-- 3. 索引覆盖 body_text，而正文是懒加载的（多数行为 NULL）。
--    NULL 不占索引项，回填正文后需要由应用侧补齐索引项。
-- ============================================================

ALTER TABLE mail_message
    ADD FULLTEXT KEY ft_mail_message (subject, from_addr, from_name, preview, body_text) WITH PARSER ngram;

-- ---------------------------------------------------------------------
-- 退信回执（DSN：multipart/report; report-type=delivery-status）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_delivery_report (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    account_id          BIGINT        NOT NULL                COMMENT '所属邮箱账号ID',
    notify_message_id   BIGINT        NOT NULL                COMMENT '退信通知邮件ID（mail_message.id，即 DSN 本身）',
    original_message_id BIGINT        DEFAULT NULL            COMMENT '被退回的原邮件ID（按 Message-ID 关联，未命中为空）',
    original_rfc_id     VARCHAR(255)  DEFAULT NULL            COMMENT '被退回原邮件的 RFC822 Message-ID',
    recipient           VARCHAR(190)  NOT NULL                COMMENT '被退回的收件人地址',
    action              VARCHAR(16)   DEFAULT NULL            COMMENT 'DSN Action：failed / delayed / delivered',
    status_code         VARCHAR(16)   DEFAULT NULL            COMMENT 'RFC 3463 增强状态码，如 5.1.1 / 4.2.2',
    diagnostic          VARCHAR(1000) DEFAULT NULL            COMMENT '远端返回的诊断信息（SMTP 应答原文）',
    bounce_type         VARCHAR(8)    DEFAULT NULL            COMMENT '退信类型：HARD 永久 / SOFT 临时',
    reporting_mta       VARCHAR(190)  DEFAULT NULL            COMMENT 'Reporting-MTA（哪一跳退回的）',
    remote_mta          VARCHAR(190)  DEFAULT NULL            COMMENT 'Remote-MTA（对端服务器）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_notify_rcpt (tenant_id, account_id, notify_message_id, recipient),
    KEY idx_tenant_original (tenant_id, original_message_id),
    KEY idx_tenant_account_time (tenant_id, account_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='邮件退信回执表';
