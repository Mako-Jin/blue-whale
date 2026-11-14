package com.bluewhale.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 提交表单数据请求。
 *
 * @author BlueWhale
 */
@Data
public class FormDataSubmitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表单定义 ID（必填） */
    @NotNull(message = "表单定义ID不能为空")
    private Long definitionId;

    /** 表单数据 JSON（必填，最长 64KB） */
    @NotBlank(message = "表单数据不能为空")
    private String dataJson;

    /** 业务键（可选，最长 64） */
    @Size(max = 64, message = "业务键长度不能超过 64")
    private String bizKey;

    /** 客户端幂等键（可选，最长 64；相同键重复提交返回首次提交结果） */
    @Size(max = 64, message = "客户端请求ID长度不能超过 64")
    private String clientRequestId;
}
