package com.bluewhale.workflow.dto;

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
    @NotBlank(message = "目标人不能为空")
    @Size(max = 64, message = "目标人账号长度不能超过 64")
    private String targetUser;
}
