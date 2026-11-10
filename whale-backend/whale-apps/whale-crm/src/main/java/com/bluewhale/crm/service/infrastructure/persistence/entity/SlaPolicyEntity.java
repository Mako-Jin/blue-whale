package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SLA 策略实体，对应表 {@code crm_ticket_sla}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_ticket_sla")
public class SlaPolicyEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 优先级 */
    private String priority;

    /** 响应时限（工作小时） */
    private Integer responseHours;

    /** 解决时限（工作小时） */
    private Integer resolveHours;

    /** 是否启用 */
    private Boolean enabled;
}
