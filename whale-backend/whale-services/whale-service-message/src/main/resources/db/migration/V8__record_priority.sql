-- 消息优先级：数值越大越优先，异步投递队列与补偿任务按此排序；存量消息按普通优先级补齐
ALTER TABLE msg_record
    ADD COLUMN priority TINYINT NOT NULL DEFAULT 20 COMMENT '优先级：30 HIGH / 20 NORMAL / 10 LOW' AFTER status;
