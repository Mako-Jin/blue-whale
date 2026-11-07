package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应收快照分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArSnapshotQuery extends PageQuery {

    /** 关键字（客户编码 / 客户名称） */
    private String keyword;

    /** 客户 ID */
    private Long customerId;

    /** 快照日期 */
    private java.time.LocalDate snapshotDate;

    /** 负责人（数据范围收口） */
    private Long ownerId;
}
