-- ============================================================
-- V4：收信规则 + 邮件回源锚点
--
-- 背景一：规则要「把邮件归到某个文件夹」，归类就会改 folder_id。而正文与附件
-- 是按 (文件夹, UID) 回源的，一旦 folder_id 被改写成目标文件夹，UID 还是原文件夹的，
-- 回源就会取到「目标文件夹里同一 UID 的另一封邮件」——不是取不到，是取错。
-- 因此把「展示位置」和「实际所在文件夹」拆开：
--   folder_id       展示/归类位置（规则、移入回收站会改它）
--   imap_folder_id  实际所在的 IMAP 文件夹（同步写入后永不变，回源只用它）
--
-- 背景二：唯一键原本是 (tenant, account, folder_id, uid)。folder_id 一旦可被改写，
-- 两个不同文件夹的 UID 数字完全可能相同（各自从 1 开始），归类后的行会与目标文件夹
-- 里真实的同 UID 邮件撞唯一键，同步直接报错。所以唯一键必须跟着 imap_folder_id 走。
--
-- 背景三：mail_rule 存平台侧归类规则。规则只改本平台的归类，不动服务器上的文件夹
-- （原因见 docs/ops/mail-service-ops.md §十四）。
--
-- 执行注意：DROP INDEX + ADD UNIQUE 会重建索引并短暂阻塞写入（不是重建表，但要拿元数据锁）。
-- 十万级以内的表可直接执行；更大规模请在维护窗口跑，或把「加列 + 回填」与「换唯一键」拆成两步，
-- 中间留出观察期（回填是分批 UPDATE 的，不会长时间持锁）。
-- ============================================================

ALTER TABLE mail_message
    ADD COLUMN imap_folder_id BIGINT DEFAULT NULL
        COMMENT '实际所在的 IMAP 文件夹ID（回源锚点，规则归类不改它）' AFTER folder_id;

-- 存量数据：此前 folder_id 就是真实位置，直接回填
UPDATE mail_message SET imap_folder_id = folder_id WHERE imap_folder_id IS NULL;

ALTER TABLE mail_message
    MODIFY COLUMN imap_folder_id BIGINT NOT NULL
        COMMENT '实际所在的 IMAP 文件夹ID（回源锚点，规则归类不改它）';

-- 唯一键换锚点：(account, folder, uid) → (account, imap_folder, uid)
-- 注意 idx_tenant_folder_seen 仍以 folder_id 打头，列表按展示位置过滤不受影响
ALTER TABLE mail_message
    DROP INDEX uk_tenant_account_folder_uid,
    ADD UNIQUE KEY uk_tenant_account_imap_uid (tenant_id, account_id, imap_folder_id, uid);

-- ---------------------------------------------------------------------
-- 收信规则
--
-- 条件语义：同一规则内，填写了的条件之间是「与」；同一条件里用英文逗号分隔的多个值
-- 之间是「或」（包含匹配，大小写不敏感）。三个条件全空且不要求附件时，规则会命中
-- 所有邮件——这种规则没有意义，服务层直接拒绝。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_rule (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    account_id            BIGINT        NOT NULL                COMMENT '所属邮箱账号ID',
    name                  VARCHAR(64)   NOT NULL                COMMENT '规则名称',
    sort_no               INT           NOT NULL DEFAULT 100    COMMENT '优先级（小的先匹配，命中即停）',
    enabled               TINYINT       NOT NULL DEFAULT 1      COMMENT '启用：1 是 / 0 否',
    match_from            VARCHAR(190)  DEFAULT NULL            COMMENT '发件人包含（逗号分隔多值，任一命中）',
    match_subject         VARCHAR(255)  DEFAULT NULL            COMMENT '主题包含（逗号分隔多值，任一命中）',
    match_to              VARCHAR(190)  DEFAULT NULL            COMMENT '收件人包含（逗号分隔多值，任一命中）',
    only_with_attachment  TINYINT       NOT NULL DEFAULT 0      COMMENT '仅含附件邮件：1 是 / 0 不限制',
    action_folder_id      BIGINT        DEFAULT NULL            COMMENT '归入文件夹ID（空表示不移动）',
    action_mark_seen      TINYINT       NOT NULL DEFAULT 0      COMMENT '标记已读：1 是 / 0 否',
    action_flagged        TINYINT       NOT NULL DEFAULT 0      COMMENT '加星标：1 是 / 0 否',
    hit_count             BIGINT        NOT NULL DEFAULT 0      COMMENT '累计命中封数',
    last_hit_time         DATETIME      DEFAULT NULL            COMMENT '最近命中时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_account_sort (tenant_id, account_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收信规则表';
