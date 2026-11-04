package com.bluewhale.mes.quality.dto;

import lombok.Data;

/**
 * SPC 样本分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesSpcSampleQueryRequest {

    /** SPC 规则 ID */
    private Long ruleId;

    /** 检验计划项 ID */
    private Long planItemId;

    /** 是否命中规则：0 否 / 1 是 */
    private Integer alertFlag;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
