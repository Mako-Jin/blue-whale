package com.bluewhale.party.union.application.view;

import com.bluewhale.party.union.domain.model.ReliefStatus;
import com.bluewhale.party.union.domain.model.UnionRelief;
import lombok.Data;

/**
 * 慰问帮扶提交结果视图。
 *
 * <p>{@link #approvalStarted} 是给调用方的明确信号：审批没发起成功时申请仍是草稿，
 * 前端据此提示用户「还需重新提交」，而不是把草稿当成审批中。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionReliefSubmitView {

    /** 申请 ID */
    private Long id;

    /** 状态码 */
    private String status;

    /** 是否已成功发起审批 */
    private boolean approvalStarted;

    /** 审批降级提示 */
    private String approvalHint;

    /** 工作流实例 ID */
    private Long approvalInstanceId;

    /**
     * 由领域模型构建提交结果。
     *
     * @param relief 慰问帮扶申请
     * @param approvalStarted 是否已发起审批
     * @return 视图
     */
    public static UnionReliefSubmitView of(UnionRelief relief, boolean approvalStarted) {
        UnionReliefSubmitView view = new UnionReliefSubmitView();
        view.setId(relief.getId());
        ReliefStatus status = relief.getStatus() == null ? ReliefStatus.DRAFT : relief.getStatus();
        view.setStatus(status.name());
        view.setApprovalStarted(approvalStarted);
        view.setApprovalHint(relief.getApprovalHint());
        view.setApprovalInstanceId(relief.getApprovalInstanceId());
        return view;
    }
}
