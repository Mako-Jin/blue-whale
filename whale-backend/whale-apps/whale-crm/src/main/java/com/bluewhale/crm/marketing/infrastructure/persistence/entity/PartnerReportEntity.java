package com.bluewhale.crm.marketing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 渠道报单实体，对应表 {@code crm_partner_report}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_partner_report")
public class PartnerReportEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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
    private String status;

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

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}
