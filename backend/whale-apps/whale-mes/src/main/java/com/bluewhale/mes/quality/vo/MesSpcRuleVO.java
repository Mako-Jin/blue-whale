package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 规则视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesSpcRuleVO {

    /** 规则 ID */
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 检验计划项 ID */
    private Long planItemId;

    /** 工序编码 */
    private String operationCode;

    /** 控制图类型 */
    private String chartType;

    /** 子组大小 */
    private Integer subgroupSize;

    /** 中心线 */
    private BigDecimal centerLine;

    /** 均值上控制限 */
    private BigDecimal ucl;

    /** 均值下控制限 */
    private BigDecimal lcl;

    /** 极差上控制限 */
    private BigDecimal rUcl;

    /** 极差下控制限 */
    private BigDecimal rLcl;

    /** 命中处理 */
    private String alertAction;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
