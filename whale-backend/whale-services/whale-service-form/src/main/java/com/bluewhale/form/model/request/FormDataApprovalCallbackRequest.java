package com.bluewhale.form.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 审批结果回调请求（内网接口，由工作流在实例结束时调用）。
 *
 * @author BlueWhale
 */
@Data
public class FormDataApprovalCallbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程实例ID（必填，用于定位表单数据） */
    @NotBlank(message = "流程实例ID不能为空")
    @Size(max = 64, message = "流程实例ID长度不能超过 64")
    private String processInstanceId;

    /** 审批结果：APPROVED（通过）/ REJECTED（驳回） */
    @NotBlank(message = "审批结果不能为空")
    private String result;

    /** 审批意见（可选，仅用于留痕） */
    @Size(max = 500, message = "审批意见长度不能超过 500")
    private String comment;
}
