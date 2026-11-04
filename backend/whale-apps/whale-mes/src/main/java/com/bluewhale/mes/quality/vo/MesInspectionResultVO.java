package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验结果视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionResultVO {

    /** 结果 ID */
    private Long id;

    /** 检验任务 ID */
    private Long taskId;

    /** 计划检验项 ID */
    private Long planItemId;

    /** 检验项序号 */
    private Integer itemNo;

    /** 检验项名称 */
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

    /** 录入时间 */
    private LocalDateTime createTime;
}
