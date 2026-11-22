package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工作项状态流转请求
 *
 * @author BlueWhale
 */
@Data
public class WorkItemTransitionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态ID */
    @NotNull(message = "{validation.pm.work-item-transition.target-status-id-required}")
    private Long targetStatusId;

    /** 流转评论（required_comment 的流转必填） */
    @Size(max = 2000, message = "{validation.pm.work-item-transition.comment-size}")
    private String comment;
}
