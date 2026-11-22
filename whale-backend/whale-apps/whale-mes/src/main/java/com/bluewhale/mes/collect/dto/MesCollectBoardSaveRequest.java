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
    @Size(max = 64, message = "{validation.mes.mes-collect-board.board-code-size}")
    private String boardCode;

    /** 看板名称 */
    @NotBlank(message = "{validation.mes.mes-collect-board.board-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-collect-board.board-name-size}")
    private String boardName;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码 */
    @Size(max = 64, message = "{validation.mes.mes-collect-board.line-code-size}")
    private String lineCode;

    /** 刷新间隔（秒） */
    @Min(value = 5, message = "{validation.mes.mes-collect-board.refresh-seconds-min}")
    @Max(value = 60, message = "{validation.mes.mes-collect-board.refresh-seconds-max}")
    private Integer refreshSeconds;

    /** 指标编码列表（逗号分隔） */
    @Size(max = 500, message = "{validation.mes.mes-collect-board.metric-codes-size}")
    private String metricCodes;

    /** 布局配置 */
    @Size(max = 500, message = "{validation.mes.mes-collect-board.layout-size}")
    private String layout;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-collect-board.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-collect-board.remark-size}")
    private String remark;
}
