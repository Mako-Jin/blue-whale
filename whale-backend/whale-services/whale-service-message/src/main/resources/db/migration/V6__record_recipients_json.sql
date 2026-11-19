-- 异步投递：持久化接收人列表，后台线程据此重建投递对象
ALTER TABLE msg_record
    ADD COLUMN recipients_json TEXT DEFAULT NULL COMMENT '接收人列表 JSON（异步投递时重建投递对象）' AFTER payload_json;
