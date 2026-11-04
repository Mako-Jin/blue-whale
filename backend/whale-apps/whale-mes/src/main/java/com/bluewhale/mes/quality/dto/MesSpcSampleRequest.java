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
    @NotNull(message = "SPC 规则 ID 不能为空")
    private Long ruleId;

    /** 检验计划项 ID（必须为有效检验项） */
    @NotNull(message = "检验计划项 ID 不能为空")
    private Long planItemId;

    /** 来源检验任务 ID */
    private Long inspectionTaskId;

    /** 工序编码 */
    @Size(max = 64, message = "工序编码长度不能超过 64")
    private String operationCode;

    /** 子组号 */
    @NotNull(message = "子组号不能为空")
    @Min(value = 1, message = "子组号必须大于 0")
    private Integer subgroupNo;

    /** 子组内样本序号（空则追加到子组末尾） */
    @Min(value = 1, message = "样本序号必须大于 0")
    private Integer sampleSeq;

    /** 实测值 */
    @NotNull(message = "实测值不能为空")
    private BigDecimal measuredValue;

    /** 采样时间（空则取当前时间） */
    private LocalDateTime sampledAt;
}
