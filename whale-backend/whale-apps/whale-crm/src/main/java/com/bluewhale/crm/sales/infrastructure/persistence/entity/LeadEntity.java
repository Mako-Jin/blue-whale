package com.bluewhale.crm.sales.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 线索实体，对应表 {@code crm_lead}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead")
public class LeadEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
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
}
