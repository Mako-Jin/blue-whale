-- 消息归档水位：按租户记录已归档消息的 (create_time, id)，归档任务据此增量导出
CREATE TABLE IF NOT EXISTS msg_archive_watermark (
    id               BIGINT        NOT NULL                COMMENT '主键，即租户ID',
    last_create_time DATETIME      DEFAULT NULL            COMMENT '已归档最后一条消息的 create_time',
    last_id          BIGINT        DEFAULT NULL            COMMENT '已归档最后一条消息的 id（同时间戳内去重）',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) COMMENT='消息归档水位表';
