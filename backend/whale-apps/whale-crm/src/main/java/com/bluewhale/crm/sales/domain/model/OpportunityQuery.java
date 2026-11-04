package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 商机分页 / 漏斗查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpportunityQuery extends PageQuery {

    /** 关键字（商机名称） */
    private String keyword;

    /** 客户 ID */
    private Long customerId;

    /** 阶段 */
    private String stage;

    /** 负责人（数据范围收口） */
    private Long ownerId;

    /** 预计关闭日期起 */
    private LocalDate expectedCloseFrom;

    /** 预计关闭日期止 */
    private LocalDate expectedCloseTo;
}
