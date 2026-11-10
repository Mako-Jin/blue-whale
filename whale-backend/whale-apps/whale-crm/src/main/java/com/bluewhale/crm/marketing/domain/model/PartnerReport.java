package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 渠道报单聚合。
 *
 * <p>同一客户同一项目已有 {@code PENDING} / {@code APPROVED} 报单时，新报单进入
 * {@code CONFLICT}；仅 {@code CONFLICT} 允许裁决为 {@code APPROVED} / {@code REJECTED}，
 * 核准时必须填写金额。</p>
 *
 * @author BlueWhale
 */
@Data
public class PartnerReport {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 报单号 */
    private String reportNo;

    /** 渠道伙伴 ID */
    private Long partnerId;

    /** 渠道伙伴名称快照 */
    private String partnerName;

    /** CRM 客户 ID */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 项目名称 */
    private String projectName;

    /** 预计金额 */
    private BigDecimal estimatedAmount;

    /** 报单状态 */
    private PartnerReportStatus status;

    /** 冲突原因 */
    private String conflictReason;

    /** 核准金额 */
    private BigDecimal approvedAmount;

    /** 裁决人 ID */
    private Long resolvedBy;

    /** 裁决时间 */
    private LocalDateTime resolvedAt;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 报单初始状态：冲突优先，其次按自动通过开关决定。
     *
     * @param conflict 是否存在同客户同项目的有效报单
     * @param autoApprove 是否自动通过
     * @return 初始状态
     */
    public static PartnerReportStatus initialStatus(boolean conflict, boolean autoApprove) {
        if (conflict) {
            return PartnerReportStatus.CONFLICT;
        }
        return autoApprove ? PartnerReportStatus.APPROVED : PartnerReportStatus.PENDING;
    }

    /**
     * 标记报单冲突。
     *
     * @param reason 冲突原因
     */
    public void markConflict(String reason) {
        this.status = PartnerReportStatus.CONFLICT;
        this.conflictReason = reason;
    }

    /**
     * 裁决冲突报单。
     *
     * @param target 目标状态（APPROVED / REJECTED）
     * @param amount 核准金额（APPROVED 必填）
     * @param resolverId 裁决人 ID
     * @param at 裁决时间
     */
    public void resolve(PartnerReportStatus target, BigDecimal amount, Long resolverId, LocalDateTime at) {
        if (status != PartnerReportStatus.CONFLICT) {
            throw CrmErrorCode.exception(CrmErrorCode.PARTNER_STATE_CONFLICT, "仅冲突报单允许裁决");
        }
        if (target != PartnerReportStatus.APPROVED && target != PartnerReportStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.PARTNER_STATE_CONFLICT, "裁决结果只能为核准或驳回");
        }
        if (target == PartnerReportStatus.APPROVED) {
            if (amount == null || amount.signum() < 0) {
                throw CrmErrorCode.exception(CrmErrorCode.PARTNER_STATE_CONFLICT, "核准报单必须填写非负金额");
            }
            this.approvedAmount = amount;
        } else {
            this.approvedAmount = null;
        }
        this.status = target;
        this.resolvedBy = resolverId;
        this.resolvedAt = at;
    }
}
