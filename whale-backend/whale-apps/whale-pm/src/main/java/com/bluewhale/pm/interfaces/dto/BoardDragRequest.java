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
    @NotNull(message = "{validation.pm.board-drag.work-item-id-required}")
    private Long workItemId;

    /** 目标状态ID */
    @NotNull(message = "{validation.pm.board-drag.target-status-id-required}")
    private Long targetStatusId;

    /** 流转评论 */
    @Size(max = 512, message = "{validation.pm.board-drag.comment-size}")
    private String comment;
}
