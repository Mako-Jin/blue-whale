package com.bluewhale.erp.scm.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 价目表分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PriceListQuery extends ScmQuery {

    /** 价格类型 */
    private PriceType priceType;

    /** 往来方主键（客户或供应商） */
    private Long partnerId;
}
