package com.bluewhale.mall.pricing.domain.model;

import lombok.Data;

/**
 * 运费模板查询条件。
 *
 * @author BlueWhale
 */
@Data
public class FreightTemplateQuery {

    /** 关键字（模板名称） */
    private String keyword;

    /** 计费方式 */
    private String chargeType;

    /** 状态 */
    private String status;
}
