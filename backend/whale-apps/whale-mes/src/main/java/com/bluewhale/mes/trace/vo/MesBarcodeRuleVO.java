package com.bluewhale.mes.trace.vo;

import lombok.Data;

/**
 * 条码规则视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesBarcodeRuleVO {

    /** 规则 ID */
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 条码前缀 */
    private String prefix;

    /** 码制 */
    private String codeType;

    /** 流水号长度 */
    private Integer serialLength;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
