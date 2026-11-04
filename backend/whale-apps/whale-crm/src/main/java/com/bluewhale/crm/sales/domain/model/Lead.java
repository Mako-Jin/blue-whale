package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线索聚合根。
 *
 * <p>状态机：{@code NEW → CONTACTED → QUALIFIED → CONVERTED}，未转化前可标记 {@code INVALID}；
 * 转化只允许一次，转化后线索只读。</p>
 *
 * @author BlueWhale
 */
@Data
public class Lead {

    /** 主键 */
    private Long id;

    /** 线索编号（租户内唯一） */
    private String leadNo;

    /** 线索名称 */
    private String name;

    /** 来源 */
    private String source;

    /** 行业 */
    private String industry;

    /** 地区 */
    private String region;

    /** 联系人姓名 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 状态 */
    private LeadStatus status;

    /** 无效原因 */
    private String invalidReason;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 转化后的客户 ID */
    private Long customerId;

    /** 转化后的商机 ID */
    private Long opportunityId;

    /** 转化时间 */
    private LocalDateTime convertedAt;

    /** 归一化名称 */
    private String normalizedName;

    /** 归一化电话 */
    private String normalizedPhone;

    /** 归一化邮箱 */
    private String normalizedEmail;

    /** 备注 */
    private String remark;

    /** 版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否已转化。
     *
     * @return 已转化返回 true
     */
    public boolean converted() {
        return status == LeadStatus.CONVERTED;
    }

    /**
     * 是否已标记无效。
     *
     * @return 无效返回 true
     */
    public boolean invalid() {
        return status == LeadStatus.INVALID;
    }

    /**
     * 是否允许转化。
     *
     * @return 允许返回 true
     */
    public boolean canConvert() {
        return status != null && !status.terminal();
    }

    /**
     * 标记为已联系。
     */
    public void markContacted() {
        moveTo(LeadStatus.CONTACTED);
    }

    /**
     * 标记为已确认。
     */
    public void markQualified() {
        moveTo(LeadStatus.QUALIFIED);
    }

    /**
     * 标记无效。
     *
     * @param reason 无效原因
     */
    public void invalidate(String reason) {
        if (reason == null || reason.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_INVALID_REASON_REQUIRED);
        }
        moveTo(LeadStatus.INVALID);
        this.invalidReason = reason.trim();
    }

    /**
     * 转化线索。
     *
     * @param targetCustomerId 目标客户 ID
     * @param targetOpportunityId 目标商机 ID
     * @param at 转化时间
     */
    public void convertTo(Long targetCustomerId, Long targetOpportunityId, LocalDateTime at) {
        if (converted()) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_ALREADY_CONVERTED);
        }
        if (!canConvert()) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_STATE_CONFLICT);
        }
        this.status = LeadStatus.CONVERTED;
        this.customerId = targetCustomerId;
        this.opportunityId = targetOpportunityId;
        this.convertedAt = at;
    }

    /**
     * 分配负责人。
     *
     * @param targetOwnerId 负责人
     * @param targetDeptId 归属部门
     */
    public void assign(Long targetOwnerId, Long targetDeptId) {
        if (targetOwnerId == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "分配线索时必须指定负责人");
        }
        if (status != null && status.terminal()) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_STATE_CONFLICT);
        }
        this.ownerId = targetOwnerId;
        this.deptId = targetDeptId;
    }

    /**
     * 按状态码推进。
     *
     * @param target 目标状态
     */
    public void moveTo(LeadStatus target) {
        if (!status.canMoveTo(target)) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_STATE_CONFLICT);
        }
        this.status = target;
    }
}
