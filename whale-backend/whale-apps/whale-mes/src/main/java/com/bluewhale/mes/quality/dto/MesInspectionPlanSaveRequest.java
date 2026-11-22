package com.bluewhale.mes.quality.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 检验计划保存请求（含检验项，整体覆盖）
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionPlanSaveRequest {

    /** 计划 ID（编辑时必填） */
    private Long id;

    /** 计划编号（空则自动生成） */
    @Size(max = 64, message = "{validation.mes.mes-inspection-plan.plan-no-size}")
    private String planNo;

    /** 计划名称 */
    @NotBlank(message = "{validation.mes.mes-inspection-plan.plan-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-inspection-plan.plan-name-size}")
    private String planName;

    /** 检验类型：FIRST_ARTICLE / IPQC */
    @NotBlank(message = "{validation.mes.mes-inspection-plan.inspection-type-required}")
    @Pattern(regexp = "FIRST_ARTICLE|IPQC", message = "{validation.mes.mes-inspection-plan.inspection-type-pattern}")
    private String inspectionType;

    /** 适用物料编码 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-plan.item-code-size}")
    private String itemCode;

    /** 适用工序编码 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-plan.operation-code-size}")
    private String operationCode;

    /** 适用产线 ID */
    private Long lineId;

    /** 适用产线编码 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-plan.line-code-size}")
    private String lineCode;

    /** 抽样数量 */
    @Min(value = 1, message = "{validation.mes.mes-inspection-plan.sample-size-min}")
    private Integer sampleSize;

    /** 巡检频次（分钟） */
    @Min(value = 0, message = "{validation.mes.mes-inspection-plan.frequency-minutes-min}")
    private Integer frequencyMinutes;

    /** 检验 FAILED 是否自动生成 NCR：0 否 / 1 是 */
    private Integer autoNcr;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-inspection-plan.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-inspection-plan.remark-size}")
    private String remark;

    /** 检验项列表 */
    @Valid
    @NotEmpty(message = "{validation.mes.mes-inspection-plan.items-required}")
    private List<MesInspectionPlanItemRequest> items;
}
