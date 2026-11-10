package com.bluewhale.erp.finance.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应收 / 应付台账分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArApQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字（单号或往来单位名称） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 往来单位 ID（客户或供应商） */
    private Long partyId;
}
