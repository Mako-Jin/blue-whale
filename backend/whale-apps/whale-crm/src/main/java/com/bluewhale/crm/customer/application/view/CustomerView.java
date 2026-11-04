package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.common.CrmMasking;
import com.bluewhale.crm.customer.domain.model.Customer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户摘要视图。
 *
 * @author BlueWhale
 */
@Data
public class CustomerView {

    /** 主键 */
    private Long id;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String name;

    /** 客户简称 */
    private String shortName;

    /** 状态 */
    private String status;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 等级 */
    private String level;

    /** 来源 */
    private String source;

    /** 行业 */
    private String industry;

    /** 地址 */
    private String address;

    /** 标签 */
    private String tags;

    /** 合并目标 */
    private Long mergedIntoId;

    /** 官网 */
    private String website;

    /** 规模 */
    private String scale;

    /** 税号（按权限脱敏） */
    private String taxNo;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String region;

    /** 公海状态 */
    private String poolStatus;

    /** 公海时间 */
    private LocalDateTime poolAt;

    /** 最近跟进时间 */
    private LocalDateTime lastFollowUpAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由领域模型构建视图。
     *
     * @param customer 客户
     * @param allowPlain 是否允许查看敏感明文
     * @return 客户视图
     */
    public static CustomerView from(Customer customer, boolean allowPlain) {
        CustomerView view = new CustomerView();
        view.setId(customer.getId());
        view.setCustomerCode(customer.getCustomerCode());
        view.setName(customer.getName());
        view.setShortName(customer.getShortName());
        view.setStatus(customer.getStatus());
        view.setOwnerId(customer.getOwnerId());
        view.setDeptId(customer.getDeptId());
        view.setLevel(customer.getLevel());
        view.setSource(customer.getSource());
        view.setIndustry(customer.getIndustry());
        view.setAddress(customer.getAddress());
        view.setTags(customer.getTags());
        view.setMergedIntoId(customer.getMergedIntoId());
        view.setWebsite(customer.getWebsite());
        view.setScale(customer.getScale());
        view.setTaxNo(CrmMasking.taxNo(customer.getTaxNo(), allowPlain));
        view.setProvince(customer.getProvince());
        view.setCity(customer.getCity());
        view.setRegion(customer.getRegion());
        view.setPoolStatus(customer.getPoolStatus());
        view.setPoolAt(customer.getPoolAt());
        view.setLastFollowUpAt(customer.getLastFollowUpAt());
        view.setCreateTime(customer.getCreateTime());
        view.setUpdateTime(customer.getUpdateTime());
        return view;
    }
}
