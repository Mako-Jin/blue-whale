package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 检验计划项实体，对应表 {@code mes_qc_inspection_plan_item}。
 *
 * <p>同一计划内检验项序号唯一；测量型检验项通过上下限判定，文本型仅记录实测值。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_inspection_plan_item")
public class MesInspectionPlanItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 检验计划 ID */
    private Long planId;

    /** 检验项序号 */
    private Integer itemNo;

    /** 检验项名称 */
    private String itemName;

    /** 检验方式：VISUAL / MEASURE / FUNCTION / TEST */
    private String inspectionMethod;

    /** 标准描述 */
    private String standardText;

    /** 下限 */
    private BigDecimal minValue;

    /** 上限 */
    private BigDecimal maxValue;

    /** 计量单位 */
    private String unit;

    /** 是否关键项：0 否 / 1 是 */
    private Integer criticalFlag;

    /** 排序号 */
    private Integer sortNo;
}
