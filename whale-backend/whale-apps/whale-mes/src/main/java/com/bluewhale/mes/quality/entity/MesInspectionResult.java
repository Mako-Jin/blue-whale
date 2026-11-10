package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 检验结果实体，对应表 {@code mes_qc_inspection_result}。
 *
 * <p>按检验项与样本逐条追加；{@code result} 为 {@code OK} 或 {@code NG}，测量值同时保留文本与数值两种形态。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_inspection_result")
public class MesInspectionResult extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 检验任务 ID */
    private Long taskId;

    /** 计划检验项 ID */
    private Long planItemId;

    /** 检验项序号快照 */
    private Integer itemNo;

    /** 检验项名称快照 */
    private String itemName;

    /** 样本序号 */
    private Integer sampleNo;

    /** 实测值文本 */
    private String measuredValue;

    /** 实测数值 */
    private BigDecimal numericValue;

    /** 判定：OK / NG */
    private String result;

    /** 检验员 */
    private String inspector;

    /** 备注 */
    private String remark;
}
