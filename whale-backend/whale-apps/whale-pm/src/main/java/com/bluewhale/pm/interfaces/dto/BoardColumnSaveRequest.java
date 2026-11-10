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
    @NotNull(message = "状态ID不能为空")
    private Long statusId;

    /** 列名称 */
    @NotBlank(message = "列名称不能为空")
    @Size(max = 64, message = "列名称长度不能超过 64")
    private String name;

    /** 排序号（为空时追加到末尾） */
    private Integer sortNo;

    /** WIP 上限（0 表示不限） */
    @Min(value = 0, message = "WIP 上限不能为负数")
    private Integer wipLimit;
}
