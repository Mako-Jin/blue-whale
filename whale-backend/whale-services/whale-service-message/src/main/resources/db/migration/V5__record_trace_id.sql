-- 链路追踪：记录发送请求的 traceId，回执回调报文原样透传，便于业务方串联调用链
ALTER TABLE msg_record
    ADD COLUMN trace_id VARCHAR(64) DEFAULT NULL COMMENT '发送请求链路追踪ID（回执回调原样透传）' AFTER expire_at;
