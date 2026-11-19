package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 回退到指定节点的请求。
 *
 * @author BlueWhale
 */
@Data
public class TaskRollbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回退目标节点键（必填，须为审批节点） */
    @NotBlank(message = "{validation.workflow.task.rollback-target-required}")
    @Size(max = 64, message = "{validation.workflow.task.node-key-size}")
    private String targetNodeKey;
}
