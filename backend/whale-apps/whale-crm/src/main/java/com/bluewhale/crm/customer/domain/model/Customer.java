package com.bluewhale.crm.customer.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户聚合根。
 *
 * <p>客户是 CRM 的关系主数据；合同、订单、工单只保存 {@code customerId} 引用。
 * 联系人与地址属于本聚合内的实体，画像为独立表但同属客户上下文。</p>
 *
 * @author BlueWhale
 */
@Data
public class Customer {

    /** 主键 */
    private Long id;

    /** 客户编码（租户内唯一） */
    private String customerCode;

    /** 客户名称 */
    private String name;

    /** 客户简称 */
    private String shortName;

    /** 状态：ACTIVE / INACTIVE / MERGED */
    private String status;

    /** 负责人（数据权限归属人） */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 等级：A / B / C / D */
    private String level;

    /** 客户来源 */
    private String source;

    /** 所属行业 */
    private String industry;

    /** 联系地址 */
    private String address;

    /** 标签（逗号分隔） */
    private String tags;

    /** 合并后指向的目标客户 ID */
    private Long mergedIntoId;

    /** 官网 */
    private String website;

    /** 企业规模 */
    private String scale;

    /** 纳税人识别号 */
    private String taxNo;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String region;

    /** 归一化名称 */
    private String normalizedName;

    /** 归一化域名 */
    private String normalizedDomain;

    /** 归一化税号 */
    private String normalizedTaxNo;

    /** 版本号 */
    private Integer version;

    /** 公海状态：OWNED / IN_POOL */
    private String poolStatus;

    /** 进入 / 离开公海时间 */
    private LocalDateTime poolAt;

    /** 最近跟进时间 */
    private LocalDateTime lastFollowUpAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 变更客户等级。
     *
     * @param level 等级码
     */
    public void changeLevel(String level) {
        assertWritable();
        this.level = CustomerLevel.canonical(level);
    }

    /**
     * 变更客户状态（MERGE 只能由合并流程写入）。
     *
     * @param status 状态码
     */
    public void changeStatus(String status) {
        CustomerStatus target = CustomerStatus.from(status);
        if (target == CustomerStatus.MERGED) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_STATE_CONFLICT, "合并状态只能由合并流程写入");
        }
        this.status = target.name();
    }

    /**
     * 领取：进入私有状态并绑定负责人。
     *
     * @param newOwnerId 领取人
     * @param at 领取时间
     */
    public void claim(Long newOwnerId, LocalDateTime at) {
        assertWritable();
        if (newOwnerId == null) {
            throw CrmErrorCode.exception(CrmErrorCode.ACTOR_REQUIRED);
        }
        this.ownerId = newOwnerId;
        this.poolStatus = PoolStatus.OWNED.name();
        this.poolAt = at;
    }

    /**
     * 释放到公海。
     *
     * @param at 释放时间
     */
    public void release(LocalDateTime at) {
        assertWritable();
        this.ownerId = null;
        this.poolStatus = PoolStatus.IN_POOL.name();
        this.poolAt = at;
    }

    /**
     * 分配负责人。
     *
     * @param newOwnerId 新负责人
     * @param newDeptId 新归属部门
     * @param at 分配时间
     */
    public void assign(Long newOwnerId, Long newDeptId, LocalDateTime at) {
        assertWritable();
        if (newOwnerId == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_OWNER_REQUIRED);
        }
        this.ownerId = newOwnerId;
        this.deptId = newDeptId;
        this.poolStatus = PoolStatus.OWNED.name();
        this.poolAt = at;
    }

    /**
     * 回写最近跟进时间。
     *
     * @param at 跟进时间
     */
    public void touchFollowUp(LocalDateTime at) {
        this.lastFollowUpAt = at;
    }

    /**
     * 是否已合并。
     *
     * @return 已合并返回 true
     */
    public boolean merged() {
        return CustomerStatus.MERGED.name().equals(status);
    }

    /**
     * 是否在公海。
     *
     * @return 在公海返回 true
     */
    public boolean inPool() {
        return PoolStatus.IN_POOL.name().equals(poolStatus);
    }

    /**
     * 校验客户可写。
     */
    private void assertWritable() {
        if (merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGED_READONLY);
        }
    }
}
