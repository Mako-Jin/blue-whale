-- 消息过期时间：超过后不再发起投递（含失败重试），对应投递记 EXPIRED
ALTER TABLE msg_record
    ADD COLUMN expire_at DATETIME DEFAULT NULL COMMENT '消息过期时间（超过后不再投递，投递记 EXPIRED）' AFTER callback_url;
