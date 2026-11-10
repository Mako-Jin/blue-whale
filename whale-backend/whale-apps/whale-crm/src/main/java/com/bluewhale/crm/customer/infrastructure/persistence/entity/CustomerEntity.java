package com.bluewhale.crm.customer.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客户实体，对应表 {@code crm_customer}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer")
public class CustomerEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

    /** 税号 */
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

    /** 公海状态 */
    private String poolStatus;

    /** 公海时间 */
    private LocalDateTime poolAt;

    /** 最近跟进时间 */
    private LocalDateTime lastFollowUpAt;
}
