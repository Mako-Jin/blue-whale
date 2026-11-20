package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 加签请求：为当前审批节点追加同级审批人（追加人全部同意后节点才完成）。
 *
 * @author BlueWhale
 */
@Data
public class TaskAddSignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 加签人账号列表（必填，单次最多 10 人，单个账号最长 64） */
    @NotEmpty(message = "{validation.workflow.task.add-signer-required}")
    @Size(max = 10, message = "{validation.workflow.task.add-signer-limit}")
    private List<@NotBlank(message = "{validation.workflow.task.add-signer-required}")
            @Size(max = 64, message = "{validation.workflow.task.add-signer-account-size}") String> assignees;
}
