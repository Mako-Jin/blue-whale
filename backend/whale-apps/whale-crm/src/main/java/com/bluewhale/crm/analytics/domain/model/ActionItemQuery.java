package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

/**
 * 行动项查询条件。
 *
 * @author BlueWhale
 */
@Data
public class ActionItemQuery {

    /** 规则编码，可空 */
    private String ruleCode;

    /** 状态，可空 */
    private String status;

    /** 优先级，可空 */
    private String priority;

    /** 关联对象类型，可空 */
    private String refType;

    /** 客户 ID，可空 */
    private Long customerId;

    /** 负责人，可空 */
    private Long ownerId;

    /** 数据范围负责人（非全量操作人自动填充） */
    private Long scopeOwnerId;

    /** 当前页 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 10L;
}
