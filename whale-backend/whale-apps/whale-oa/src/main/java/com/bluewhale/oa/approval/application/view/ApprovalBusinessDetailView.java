package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 审批业务详情视图（白名单字段）。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalBusinessDetailView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    private String businessType;

    /** 业务单据ID */
    private Long businessId;

    /** 摘要 */
    private String summary;

    /** 业务状态 */
    private String status;

    /** 业务详情路由 */
    private String url;

    /** 明细字段 */
    private List<ApprovalDetailFieldView> fields;
}
