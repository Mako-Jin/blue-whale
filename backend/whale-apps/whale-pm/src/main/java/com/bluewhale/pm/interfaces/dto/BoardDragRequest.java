package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 看板拖拽请求（列间拖拽等价于状态流转）
 *
 * @author BlueWhale
 */
@Data
public class BoardDragRequest {

    /** 工作项ID */
    @NotNull(message = "工作项ID不能为空")
    private Long workItemId;

    /** 目标状态ID */
    @NotNull(message = "目标状态ID不能为空")
    private Long targetStatusId;

    /** 流转评论 */
    @Size(max = 512, message = "流转评论长度不能超过 512")
    private String comment;
}
