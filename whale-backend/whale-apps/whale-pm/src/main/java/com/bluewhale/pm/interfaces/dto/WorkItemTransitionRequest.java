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
    @NotNull(message = "目标状态ID不能为空")
    private Long targetStatusId;

    /** 流转评论（required_comment 的流转必填） */
    @Size(max = 2000, message = "流转评论长度不能超过 2000")
    private String comment;
}
