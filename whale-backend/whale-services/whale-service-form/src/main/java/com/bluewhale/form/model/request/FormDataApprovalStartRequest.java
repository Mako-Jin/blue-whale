package com.bluewhale.form.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 发起表单数据审批请求。
 *
 * <p>审批实例由外部工作流（whale-workflow）创建，本模块只登记流程实例ID 并把业务状态置为「审批中」。</p>
 *
 * @author BlueWhale
 */
@Data
public class FormDataApprovalStartRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 外部工作流实例ID（必填，最长 64） */
    @NotBlank(message = "{validation.form.approval.process-instance-id-required}")
    @Size(max = 64, message = "{validation.form.approval.process-instance-id-size}")
    private String processInstanceId;
}
