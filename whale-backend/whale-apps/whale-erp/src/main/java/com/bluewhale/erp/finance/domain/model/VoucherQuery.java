package com.bluewhale.erp.finance.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 凭证分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VoucherQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字（凭证号） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 期间编码 */
    private String periodCode;
}
