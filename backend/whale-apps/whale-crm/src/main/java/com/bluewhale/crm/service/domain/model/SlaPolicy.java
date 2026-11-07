package com.bluewhale.crm.service.domain.model;

import lombok.Data;

/**
 * 工单 SLA 策略（按优先级一档）。
 *
 * <p>表中无记录时由应用层用 D7 默认值补足，避免迁移写入种子数据。</p>
 *
 * @author BlueWhale
 */
@Data
public class SlaPolicy {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 优先级 */
    private TicketPriority priority;

    /** 响应时限（工作小时） */
    private Integer responseHours;

    /** 解决时限（工作小时） */
    private Integer resolveHours;

    /** 是否启用 */
    private Boolean enabled;
}
