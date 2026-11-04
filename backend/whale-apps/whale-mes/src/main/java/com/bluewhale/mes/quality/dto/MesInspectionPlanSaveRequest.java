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
    @Size(max = 64, message = "计划编号长度不能超过 64")
    private String planNo;

    /** 计划名称 */
    @NotBlank(message = "计划名称不能为空")
    @Size(max = 128, message = "计划名称长度不能超过 128")
    private String planName;

    /** 检验类型：FIRST_ARTICLE / IPQC */
    @NotBlank(message = "检验类型不能为空")
    @Pattern(regexp = "FIRST_ARTICLE|IPQC", message = "检验类型仅支持 FIRST_ARTICLE / IPQC")
    private String inspectionType;

    /** 适用物料编码 */
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String itemCode;

    /** 适用工序编码 */
    @Size(max = 64, message = "工序编码长度不能超过 64")
    private String operationCode;

    /** 适用产线 ID */
    private Long lineId;

    /** 适用产线编码 */
    @Size(max = 64, message = "产线编码长度不能超过 64")
    private String lineCode;

    /** 抽样数量 */
    @Min(value = 1, message = "抽样数量必须大于 0")
    private Integer sampleSize;

    /** 巡检频次（分钟） */
    @Min(value = 0, message = "巡检频次不能为负")
    private Integer frequencyMinutes;

    /** 检验 FAILED 是否自动生成 NCR：0 否 / 1 是 */
    private Integer autoNcr;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 检验项列表 */
    @Valid
    @NotEmpty(message = "检验计划至少包含一个检验项")
    private List<MesInspectionPlanItemRequest> items;
}
