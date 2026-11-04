package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户跟进记录（append-only）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerFollowUp {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 关联商机 ID */
    private Long opportunityId;

    /** 跟进渠道 */
    private String channel;

    /** 跟进内容 */
    private String content;

    /** 跟进结果 */
    private String result;

    /** 下次动作 */
    private String nextAction;

    /** 下次动作时间 */
    private LocalDateTime nextActionAt;

    /** 记录人 */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
