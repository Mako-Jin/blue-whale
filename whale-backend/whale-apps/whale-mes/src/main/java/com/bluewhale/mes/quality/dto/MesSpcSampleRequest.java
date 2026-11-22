package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 样本录入请求
 *
 * @author BlueWhale
 */
@Data
public class MesSpcSampleRequest {

    /** SPC 规则 ID */
    @NotNull(message = "{validation.mes.mes-spc-sample.rule-id-required}")
    private Long ruleId;

    /** 检验计划项 ID（必须为有效检验项） */
    @NotNull(message = "{validation.mes.mes-spc-sample.plan-item-id-required}")
    private Long planItemId;

    /** 来源检验任务 ID */
    private Long inspectionTaskId;

    /** 工序编码 */
    @Size(max = 64, message = "{validation.mes.mes-spc-sample.operation-code-size}")
    private String operationCode;

    /** 子组号 */
    @NotNull(message = "{validation.mes.mes-spc-sample.subgroup-no-required}")
    @Min(value = 1, message = "{validation.mes.mes-spc-sample.subgroup-no-min}")
    private Integer subgroupNo;

    /** 子组内样本序号（空则追加到子组末尾） */
    @Min(value = 1, message = "{validation.mes.mes-spc-sample.sample-seq-min}")
    private Integer sampleSeq;

    /** 实测值 */
    @NotNull(message = "{validation.mes.mes-spc-sample.measured-value-required}")
    private BigDecimal measuredValue;

    /** 采样时间（空则取当前时间） */
    private LocalDateTime sampledAt;
}
