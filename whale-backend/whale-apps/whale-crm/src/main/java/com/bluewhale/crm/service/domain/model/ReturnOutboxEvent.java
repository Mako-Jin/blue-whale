package com.bluewhale.crm.service.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退换货下发 Outbox 事件（对应表 {@code crm_common_outbox}）。
 *
 * @author BlueWhale
 */
@Data
public class ReturnOutboxEvent {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 聚合 ID（退换货申请 ID） */
    private Long aggregateId;

    /** 状态：PENDING / SENT / FAILED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 最近失败原因 */
    private String lastError;
}
