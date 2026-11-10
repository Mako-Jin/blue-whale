package com.bluewhale.crm.commerce.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * CRM 领域事件 Outbox 记录（对应表 {@code crm_common_outbox}）。
 *
 * @author BlueWhale
 */
@Data
public class OrderOutboxEvent {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 聚合 ID（商务订单 ID） */
    private Long aggregateId;

    /** 状态：PENDING / SENT / FAILED / DEAD */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 最近失败原因 */
    private String lastError;
}
