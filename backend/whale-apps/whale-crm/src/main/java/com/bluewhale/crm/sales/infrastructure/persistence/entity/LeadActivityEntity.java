package com.bluewhale.crm.sales.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 线索活动实体，对应表 {@code crm_lead_activity}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead_activity")
public class LeadActivityEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 线索 ID */
    private Long leadId;

    /** 渠道 */
    private String channel;

    /** 内容 */
    private String content;

    /** 结果 */
    private String result;

    /** 下次行动时间 */
    private LocalDateTime nextActionAt;

    /** 记录人 */
    private Long createdBy;
}
