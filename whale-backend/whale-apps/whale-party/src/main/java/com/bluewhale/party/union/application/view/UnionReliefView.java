package com.bluewhale.party.union.application.view;

import com.bluewhale.party.union.domain.model.ReliefStatus;
import com.bluewhale.party.union.domain.model.UnionRelief;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 慰问帮扶申请视图。
 *
 * @author BlueWhale
 */
@Data
public class UnionReliefView {

    /** 申请 ID */
    private Long id;

    /** 申请编号 */
    private String reliefNo;

    /** 工会会员 ID */
    private Long unionMemberId;

    /** 申请人用户 ID */
    private Long applicantUserId;

    /** 慰问类型码 */
    private String reliefType;

    /** 申请年度 */
    private Integer applyYear;

    /** 申请金额 */
    private BigDecimal amount;

    /** 申请事由 */
    private String reason;

    /** 状态码 */
    private String status;

    /** 工作流实例 ID */
    private Long approvalInstanceId;

    /** 工作流状态快照 */
    private String approvalStatus;

    /** 审批降级 / 同步提示 */
    private String approvalHint;

    /** 提交审批时间 */
    private LocalDateTime submitTime;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 发放时间 */
    private LocalDateTime paidAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由领域模型构建视图。
     *
     * @param relief 慰问帮扶申请
     * @return 视图
     */
    public static UnionReliefView from(UnionRelief relief) {
        UnionReliefView view = new UnionReliefView();
        view.setId(relief.getId());
        view.setReliefNo(relief.getReliefNo());
        view.setUnionMemberId(relief.getUnionMemberId());
        view.setApplicantUserId(relief.getApplicantUserId());
        view.setReliefType(relief.getReliefType() == null ? null : relief.getReliefType().name());
        view.setApplyYear(relief.getApplyYear());
        view.setAmount(relief.getAmount());
        view.setReason(relief.getReason());
        ReliefStatus status = relief.getStatus() == null ? ReliefStatus.DRAFT : relief.getStatus();
        view.setStatus(status.name());
        view.setApprovalInstanceId(relief.getApprovalInstanceId());
        view.setApprovalStatus(relief.getApprovalStatus());
        view.setApprovalHint(relief.getApprovalHint());
        view.setSubmitTime(relief.getSubmitTime());
        view.setApprovedAt(relief.getApprovedAt());
        view.setPaidAt(relief.getPaidAt());
        view.setRemark(relief.getRemark());
        view.setCreateTime(relief.getCreateTime());
        view.setUpdateTime(relief.getUpdateTime());
        return view;
    }
}
