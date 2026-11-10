package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商务订单分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderQuery extends PageQuery {

    /** 关键字（订单编号 / 标题） */
    private String keyword;

    /** 客户 ID */
    private Long customerId;

    /** 订单状态 */
    private String status;

    /** 负责人（数据范围收口） */
    private Long ownerId;
}
