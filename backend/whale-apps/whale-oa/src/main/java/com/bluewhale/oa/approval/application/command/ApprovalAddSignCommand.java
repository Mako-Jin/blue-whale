package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 审批加签命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalAddSignCommand {

    /** 加签账号列表 */
    @NotEmpty(message = "加签账号不能为空")
    @Size(max = 10, message = "加签账号不能超过 10 个")
    private List<@NotBlank(message = "加签账号不能为空") @Size(max = 64, message = "加签账号长度不能超过 64") String> assignees;
}
