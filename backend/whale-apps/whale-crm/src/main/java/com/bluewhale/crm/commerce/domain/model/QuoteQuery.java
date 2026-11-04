package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报价分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuoteQuery extends PageQuery {

    /** 关键字（编号 / 标题） */
    private String keyword;

    /** 商机 ID */
    private Long opportunityId;

    /** 客户 ID */
    private Long customerId;

    /** 状态 */
    private String status;

    /** 负责人（数据范围收口） */
    private Long ownerId;
}
