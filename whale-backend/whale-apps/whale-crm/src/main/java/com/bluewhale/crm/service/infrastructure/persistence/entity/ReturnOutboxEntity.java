package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 退换货下发 Outbox 实体，对应表 {@code crm_common_outbox}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_common_outbox")
public class ReturnOutboxEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID（后台跨租户扫描时显式携带） */
    private Long tenantId;

    /** 聚合类型 */
    private String aggregateType;

    /** 聚合 ID（退换货申请 ID） */
    private Long aggregateId;

    /** 事件类型 */
    private String eventType;

    /** 事件负载 */
    private String payload;

    /** 状态 */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 最近失败原因 */
    private String lastError;
}
