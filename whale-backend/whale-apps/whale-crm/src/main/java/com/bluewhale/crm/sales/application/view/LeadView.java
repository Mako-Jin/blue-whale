package com.bluewhale.crm.sales.application.view;

import com.bluewhale.crm.common.CrmMasking;
import com.bluewhale.crm.sales.domain.model.Lead;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线索视图。
 *
 * @author BlueWhale
 */
@Data
public class LeadView {

    /** 主键 */
    private Long id;

    /** 线索编号 */
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

    /** 联系电话（按权限脱敏） */
    private String contactPhone;

    /** 联系邮箱（按权限脱敏） */
    private String contactEmail;

    /** 状态 */
    private String status;

    /** 无效原因 */
    private String invalidReason;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 转化后客户 ID */
    private Long customerId;

    /** 转化后商机 ID */
    private Long opportunityId;

    /** 转化时间 */
    private LocalDateTime convertedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 领域模型转视图。
     *
     * @param lead 线索
     * @param allowPlain 是否允许敏感明文
     * @return 视图
     */
    public static LeadView from(Lead lead, boolean allowPlain) {
        LeadView view = new LeadView();
        view.setId(lead.getId());
        view.setLeadNo(lead.getLeadNo());
        view.setName(lead.getName());
        view.setSource(lead.getSource());
        view.setIndustry(lead.getIndustry());
        view.setRegion(lead.getRegion());
        view.setContactName(lead.getContactName());
        view.setContactPhone(CrmMasking.phone(lead.getContactPhone(), allowPlain));
        view.setContactEmail(CrmMasking.email(lead.getContactEmail(), allowPlain));
        view.setStatus(lead.getStatus() == null ? null : lead.getStatus().name());
        view.setInvalidReason(lead.getInvalidReason());
        view.setOwnerId(lead.getOwnerId());
        view.setDeptId(lead.getDeptId());
        view.setCustomerId(lead.getCustomerId());
        view.setOpportunityId(lead.getOpportunityId());
        view.setConvertedAt(lead.getConvertedAt());
        view.setRemark(lead.getRemark());
        view.setCreateTime(lead.getCreateTime());
        return view;
    }
}
