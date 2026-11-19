package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 转办 / 委派请求（两者共用 {@code targetUser}）。
 *
 * @author BlueWhale
 */
@Data
public class TaskTransferRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标审批人账号（必填，最长 64） */
    @NotBlank(message = "{validation.workflow.task.target-required}")
    @Size(max = 64, message = "{validation.workflow.task.target-account-size}")
    private String targetUser;
}
