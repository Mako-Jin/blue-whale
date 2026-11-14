package com.bluewhale.workflow.dto;

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
    @NotEmpty(message = "加签人不能为空")
    @Size(max = 10, message = "单次最多加签 10 人")
    private List<@NotBlank(message = "加签人不能为空") @Size(max = 64, message = "加签人账号长度不能超过 64") String> assignees;
}
