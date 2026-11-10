package com.bluewhale.crm.marketing.application.view;

import com.bluewhale.crm.marketing.domain.model.PartnerReport;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 渠道报单视图。
 *
 * @author BlueWhale
 */
@Data
public class PartnerReportView {

    /** 报单 ID */
    private Long id;

    /** 报单号 */
    private String reportNo;

    /** 伙伴 ID */
    private Long partnerId;

    /** 伙伴名称 */
    private String partnerName;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 项目名称 */
    private String projectName;

    /** 预计金额 */
    private BigDecimal estimatedAmount;

    /** 状态 */
    private String status;

    /** 冲突原因 */
    private String conflictReason;

    /** 核准金额 */
    private BigDecimal approvedAmount;

    /** 裁决人 */
    private Long resolvedBy;

    /** 裁决时间 */
    private LocalDateTime resolvedAt;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由聚合构建视图。
     *
     * @param report 报单聚合
     * @return 视图
     */
    public static PartnerReportView from(PartnerReport report) {
        PartnerReportView view = new PartnerReportView();
        view.setId(report.getId());
        view.setReportNo(report.getReportNo());
        view.setPartnerId(report.getPartnerId());
        view.setPartnerName(report.getPartnerName());
        view.setCustomerId(report.getCustomerId());
        view.setCustomerName(report.getCustomerName());
        view.setProjectName(report.getProjectName());
        view.setEstimatedAmount(report.getEstimatedAmount());
        view.setStatus(report.getStatus() == null ? null : report.getStatus().name());
        view.setConflictReason(report.getConflictReason());
        view.setApprovedAmount(report.getApprovedAmount());
        view.setResolvedBy(report.getResolvedBy());
        view.setResolvedAt(report.getResolvedAt());
        view.setOwnerId(report.getOwnerId());
        view.setDeptId(report.getDeptId());
        view.setCreateTime(report.getCreateTime());
        view.setUpdateTime(report.getUpdateTime());
        return view;
    }
}
