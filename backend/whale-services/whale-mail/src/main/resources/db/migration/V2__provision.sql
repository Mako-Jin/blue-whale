-- ============================================================
-- V2：平台代开邮箱（whale-auth 建用户时自动创建 Mailcow 邮箱）
--
-- 背景：V1 只支持「手工绑定已有邮箱」，凭据靠 secret_ref 指向部署侧配置。
-- 平台代开时口令由服务端随机生成，必须能被服务端读回（IMAP 登录），
-- 因此新增 secret_cipher 保存密文——密文不是明文，密钥只在进程环境，
-- 不入库、不进镜像、不打日志。
--
-- 解析优先级（见 MailCredentialResolver）：
--   secret_ref 有值 → 读配置（外部托管邮箱）
--   否则 secret_cipher 有值 → 解密（平台代开邮箱）
--   两者都空 → 明确报错，不做空口令降级
-- ============================================================

ALTER TABLE mail_account
    ADD COLUMN secret_cipher    VARCHAR(1024) DEFAULT NULL
        COMMENT '平台代开邮箱的口令密文（ENC(...)，whale-config-crypto 加解密；与 secret_ref 二选一）' AFTER secret_ref,
    ADD COLUMN provision_source VARCHAR(16)   NOT NULL DEFAULT 'MANUAL'
        COMMENT '开通方式：MANUAL 手工绑定 / AUTO 平台代开' AFTER account_type;
