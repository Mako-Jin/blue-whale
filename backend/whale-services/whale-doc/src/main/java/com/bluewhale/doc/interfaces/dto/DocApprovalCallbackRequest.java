package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 发布审批结果回调请求（M3，内网幂等）。
 *
 * @author BlueWhale
 */
@Data
public class DocApprovalCallbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程实例ID */
    @NotNull(message = "流程实例ID不能为空")
    private Long instanceId;

    /** 流程状态：1 通过 / 2 驳回 / 3 撤销 */
    @NotNull(message = "流程状态不能为空")
    private Integer status;

    /** 审批意见（可选，仅记录日志） */
    private String comment;
}
