package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发布审批状态视图（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocApprovalVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 页面状态 */
    private String pageStatus;

    /** 审批状态：PENDING / APPROVED / REJECTED / NONE */
    private String approvalStatus;

    /** 流程实例ID */
    private Long approvalInstanceId;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 审批功能是否启用 */
    private boolean approvalEnabled;
}
