package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 出差申请实体，对应表 {@code oa_trip}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_trip")
public class TripEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 申请单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 出差目的地 */
    private String destination;

    /** 出差开始日期 */
    private LocalDate startDate;

    /** 出差结束日期 */
    private LocalDate endDate;

    /** 出差天数 */
    private Integer days;

    /** 交通方式 */
    private String transportType;

    /** 预算金额 */
    private BigDecimal budgetAmount;

    /** 预支金额 */
    private BigDecimal advanceAmount;

    /** 报告状态 */
    private String reportStatus;

    /** 出差报告内容 */
    private String reportContent;

    /** 报告提交时间 */
    private LocalDateTime reportTime;

    /** 状态 */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 备注 */
    private String remark;
}
