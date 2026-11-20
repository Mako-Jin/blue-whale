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
    @NotBlank(message = "{validation.form.approval.process-instance-id-required}")
    @Size(max = 64, message = "{validation.form.approval.process-instance-id-size}")
    private String processInstanceId;

    /** 审批结果：APPROVED（通过）/ REJECTED（驳回） */
    @NotBlank(message = "{validation.form.approval.result-required}")
    private String result;

    /** 审批意见（可选，仅用于留痕） */
    @Size(max = 500, message = "{validation.form.approval.comment-size}")
    private String comment;
}
