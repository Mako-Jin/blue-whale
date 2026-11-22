package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 看板列保存请求
 *
 * @author BlueWhale
 */
@Data
public class BoardColumnSaveRequest {

    /** 映射状态ID */
    @NotNull(message = "{validation.pm.board-column.status-id-required}")
    private Long statusId;

    /** 列名称 */
    @NotBlank(message = "{validation.pm.board-column.name-required}")
    @Size(max = 64, message = "{validation.pm.board-column.name-size}")
    private String name;

    /** 排序号（为空时追加到末尾） */
    private Integer sortNo;

    /** WIP 上限（0 表示不限） */
    @Min(value = 0, message = "{validation.pm.board-column.wip-limit-min}")
    private Integer wipLimit;
}
