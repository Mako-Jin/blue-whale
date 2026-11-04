package com.bluewhale.mes.trace.vo;

import lombok.Data;

/**
 * 扫码防错规则视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesScanRuleVO {

    /** 规则 ID */
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 适用工位编码 */
    private String stationCode;

    /** 适用工序编码 */
    private String operationCode;

    /** 期望物料编码 */
    private String expectedItemCode;

    /** 命中处理：BLOCK / WARN */
    private String action;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
