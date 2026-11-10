package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 出差申请视图。
 *
 * @author BlueWhale
 */
@Data
public class TripView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

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

    /** 行程列表 */
    private List<TripItineraryView> itineraries;

    /** 预支记录ID */
    private Long advanceId;

    /** 预支记录状态 */
    private String advanceStatus;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
