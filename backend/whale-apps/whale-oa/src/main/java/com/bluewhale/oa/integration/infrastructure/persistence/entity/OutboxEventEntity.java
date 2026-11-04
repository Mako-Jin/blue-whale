package com.bluewhale.oa.integration.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 集成事件 Outbox 实体，对应表 {@code oa_integration_outbox}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_integration_outbox")
public class OutboxEventEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 事件幂等键 */
    private String eventKey;

    /** 事件类型 */
    private String eventType;

    /** 聚合类型 */
    private String aggregateType;

    /** 聚合ID */
    private String aggregateId;

    /** 事件体（JSON） */
    private String payload;

    /** 状态 */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 发送成功时间 */
    private LocalDateTime sentAt;

    /** 失败原因 */
    private String errorMsg;
}
