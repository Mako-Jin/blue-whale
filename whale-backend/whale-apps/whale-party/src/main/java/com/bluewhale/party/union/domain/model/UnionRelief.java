package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 慰问帮扶申请（领域模型）。
 *
 * <p>{@link #applyYear} 独立落库而不是从提交时间现算：额度按<b>业务年度</b>计，
 * 跨年补录若按提交时点算，会把去年的额度记到今年。</p>
 *
 * <p>{@link #approvalHint} 保存审批降级与同步提示。审批服务不可达时申请保留为草稿并写入提示，
 * 让列表能直接展示「没提交成功」，而不是让用户以为已经进了审批。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionRelief {

    /** 申请金额上限（DECIMAL(12,2) 口径内的业务上限） */
    public static final BigDecimal AMOUNT_MAX = new BigDecimal("999999999.99");

    /** 事由最大长度 */
    public static final int REASON_MAX_LENGTH = 500;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 申请年度下限 */
    public static final int YEAR_MIN = 2000;

    /** 申请年度上限 */
    public static final int YEAR_MAX = 2100;

    /** 金额小数位 */
    public static final int AMOUNT_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 申请编号（租户内唯一，取号生成） */
    private String reliefNo;

    /** 工会会员 ID */
    private Long unionMemberId;

    /** 申请人用户 ID */
    private Long applicantUserId;

    /** 慰问类型 */
    private ReliefType reliefType;

    /** 申请年度（计上限） */
    private Integer applyYear;

    /** 申请金额 */
    private BigDecimal amount;

    /** 申请事由 */
    private String reason;

    /** 状态 */
    private ReliefStatus status;

    /** 工作流实例 ID */
    private Long approvalInstanceId;

    /** 工作流状态快照：RUNNING / APPROVED / REJECTED / CANCELLED */
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
     * 校验并归一字段。
     */
    public void validate() {
        if (unionMemberId == null || unionMemberId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID, "工会会员不能为空");
        }
        if (reliefType == null) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID, "慰问类型不能为空");
        }
        if (amount == null) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID, "申请金额不能为空");
        }
        if (amount.signum() <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID, "申请金额必须大于 0");
        }
        if (amount.compareTo(AMOUNT_MAX) > 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID,
                    "申请金额不得超过 " + AMOUNT_MAX.toPlainString());
        }
        amount = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        reason = requireReason(reason);
        remark = limitRemark(remark);
        if (applyYear == null || applyYear < YEAR_MIN || applyYear > YEAR_MAX) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID,
                    "申请年度应在 " + YEAR_MIN + "–" + YEAR_MAX + " 之间");
        }
        if (status == null) {
            status = ReliefStatus.DRAFT;
        }
    }

    /**
     * 提交审批（仅草稿 → 审批中）。
     */
    public void submit() {
        if (status != ReliefStatus.DRAFT) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT,
                    "只有草稿状态的申请可以提交审批");
        }
        status = ReliefStatus.SUBMITTED;
    }

    /**
     * 审批通过（仅审批中 → 已通过）。
     */
    public void markApproved() {
        if (status != ReliefStatus.SUBMITTED) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT,
                    "只有审批中的申请可以标记为通过");
        }
        status = ReliefStatus.APPROVED;
    }

    /**
     * 审批退回（仅审批中 → 已退回，终态）。
     */
    public void markRejected() {
        if (status != ReliefStatus.SUBMITTED) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT,
                    "只有审批中的申请可以标记为退回");
        }
        status = ReliefStatus.REJECTED;
    }

    /**
     * 登记发放（仅已通过 → 已发放，终态）。
     */
    public void pay() {
        if (status != ReliefStatus.APPROVED) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT,
                    "只有审批通过的申请可以登记发放");
        }
        status = ReliefStatus.PAID;
    }

    /**
     * 是否为草稿。
     *
     * @return 草稿返回 true
     */
    public boolean isDraft() {
        return status == ReliefStatus.DRAFT;
    }

    /**
     * 必填事由校验。
     *
     * @param value 原值
     * @return 去空白后的事由
     */
    private String requireReason(String value) {
        if (value == null || value.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID, "申请事由不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > REASON_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID,
                    "申请事由不得超过 " + REASON_MAX_LENGTH + " 字符");
        }
        return trimmed;
    }

    /**
     * 选填备注校验。
     *
     * @param value 原值
     * @return 去空白后的备注（空值为 null）
     */
    private String limitRemark(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > REMARK_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
        return trimmed;
    }
}
