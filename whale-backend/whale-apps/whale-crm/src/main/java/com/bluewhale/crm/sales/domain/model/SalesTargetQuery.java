package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 销售目标分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesTargetQuery extends PageQuery {

    /** 周期 */
    private String period;

    /** 周期类型 */
    private String periodType;

    /** 范围类型 */
    private String scopeType;

    /** 负责人（数据范围收口） */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
