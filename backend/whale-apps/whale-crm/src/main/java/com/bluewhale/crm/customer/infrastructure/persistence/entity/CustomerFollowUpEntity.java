package com.bluewhale.crm.customer.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 跟进记录实体，对应表 {@code crm_follow_up}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_follow_up")
public class CustomerFollowUpEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户 ID */
    private Long customerId;

    /** 商机 ID */
    private Long opportunityId;

    /** 渠道 */
    private String channel;

    /** 内容 */
    private String content;

    /** 结果 */
    private String result;

    /** 下次动作 */
    private String nextAction;

    /** 下次动作时间 */
    private LocalDateTime nextActionAt;

    /** 记录人 */
    private Long createdBy;
}
