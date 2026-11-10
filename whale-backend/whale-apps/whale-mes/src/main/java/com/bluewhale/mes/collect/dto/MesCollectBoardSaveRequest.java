package com.bluewhale.mes.collect.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 看板配置保存请求
 *
 * @author BlueWhale
 */
@Data
public class MesCollectBoardSaveRequest {

    /** 看板 ID（编辑时必填） */
    private Long id;

    /** 看板编码（空则编辑时保持不变） */
    @Size(max = 64, message = "看板编码长度不能超过 64")
    private String boardCode;

    /** 看板名称 */
    @NotBlank(message = "看板名称不能为空")
    @Size(max = 128, message = "看板名称长度不能超过 128")
    private String boardName;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码 */
    @Size(max = 64, message = "产线编码长度不能超过 64")
    private String lineCode;

    /** 刷新间隔（秒） */
    @Min(value = 5, message = "刷新间隔不能小于 5 秒")
    @Max(value = 60, message = "刷新间隔不能大于 60 秒")
    private Integer refreshSeconds;

    /** 指标编码列表（逗号分隔） */
    @Size(max = 500, message = "指标编码列表长度不能超过 500")
    private String metricCodes;

    /** 布局配置 */
    @Size(max = 500, message = "布局配置长度不能超过 500")
    private String layout;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
