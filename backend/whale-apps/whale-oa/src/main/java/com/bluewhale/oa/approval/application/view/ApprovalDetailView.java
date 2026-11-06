package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 审批详情视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalDetailView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程实例 */
    private ApprovalInstanceRowView instance;

    /** 任务时间线 */
    private List<ApprovalTimelineTaskView> tasks;

    /** 抄送记录 */
    private List<ApprovalCcView> ccs;

    /** 业务详情 */
    private ApprovalBusinessDetailView businessDetail;

    /** 当前用户是否可撤销 */
    private Boolean canCancel;

    /** 当前用户是否可催办 */
    private Boolean canUrge;
}
