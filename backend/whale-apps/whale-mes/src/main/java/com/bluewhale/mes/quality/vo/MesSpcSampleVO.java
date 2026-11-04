package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 样本视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesSpcSampleVO {

    /** 样本 ID */
    private Long id;

    /** 样本号 */
    private String sampleNo;

    /** 规则 ID */
    private Long ruleId;

    /** 规则编码 */
    private String ruleCode;

    /** 检验计划项 ID */
    private Long planItemId;

    /** 来源检验任务 ID */
    private Long inspectionTaskId;

    /** 工序编码 */
    private String operationCode;

    /** 子组号 */
    private Integer subgroupNo;

    /** 子组内样本序号 */
    private Integer sampleSeq;

    /** 子组大小 */
    private Integer subgroupSize;

    /** 实测值 */
    private BigDecimal measuredValue;

    /** 子组均值 */
    private BigDecimal subgroupXbar;

    /** 子组极差 */
    private BigDecimal subgroupRange;

    /** 是否命中规则 */
    private Integer alertFlag;

    /** 命中规则说明 */
    private String alertRule;

    /** 采样时间 */
    private LocalDateTime sampledAt;
}
