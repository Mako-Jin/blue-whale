package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 回款计划分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionQuery extends PageQuery {

    /** 合同台账 ID */
    private Long contractId;

    /** 客户 ID */
    private Long customerId;

    /** 状态 */
    private String status;

    /** 是否仅看逾期 */
    private Boolean overdueOnly;

    /** 计划回款起始日 */
    private LocalDate plannedFrom;

    /** 计划回款截止日 */
    private LocalDate plannedTo;

    /** 负责人（数据范围收口） */
    private Long ownerId;
}
