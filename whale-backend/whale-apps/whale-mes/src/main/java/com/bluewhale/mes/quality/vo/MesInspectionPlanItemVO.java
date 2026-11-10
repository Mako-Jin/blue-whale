package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 检验计划项视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionPlanItemVO {

    /** 检验项 ID */
    private Long id;

    /** 检验计划 ID */
    private Long planId;

    /** 检验项序号 */
    private Integer itemNo;

    /** 检验项名称 */
    private String itemName;

    /** 检验方式 */
    private String inspectionMethod;

    /** 标准描述 */
    private String standardText;

    /** 下限 */
    private BigDecimal minValue;

    /** 上限 */
    private BigDecimal maxValue;

    /** 计量单位 */
    private String unit;

    /** 是否关键项 */
    private Integer criticalFlag;

    /** 排序号 */
    private Integer sortNo;
}
