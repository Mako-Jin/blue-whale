package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 线索分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadQuery extends PageQuery {

    /** 关键字（名称 / 编号 / 联系人） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 来源 */
    private String source;

    /** 负责人（数据范围收口） */
    private Long ownerId;
}
