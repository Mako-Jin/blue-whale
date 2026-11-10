package com.bluewhale.mes.quality.dto;

import lombok.Data;

/**
 * SPC 规则分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesSpcRuleQueryRequest {

    /** 规则编码（模糊） */
    private String ruleCode;

    /** 检验计划项 ID */
    private Long planItemId;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
