package com.bluewhale.audit.mq.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计 MQ 消息去重实体，对应 {@code audit_mq_message} 表（AUTH-10）。
 *
 * <p>RabbitMQ 为「至少一次」投递语义，网络抖动、消费者重启或手动重放死信都可能重复投递
 * 同一条消息。生产者投递时设置 {@code messageId}（32 位无横线 UUID），消费端以该值作为本表
 * 主键，与审计落库同事务写入：去重行插入成功才落审计；主键冲突即判定为重复消息直接跳过，
 * 保证 {@code audit_log} 不被重复追加。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("audit_mq_message")
public class AuditMqMessagePo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** MQ 消息ID（生产者生成的 32 位 UUID，幂等去重键） */
    @TableId(type = IdType.INPUT)
    private String messageId;

    /** 首次消费时间 */
    private LocalDateTime createTime;
}
