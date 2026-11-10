package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 加班申请视图。
 *
 * @author BlueWhale
 */
@Data
public class OvertimeView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 申请单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 加班日期 */
    private LocalDate overtimeDate;

    /** 加班开始时间 */
    private LocalTime startTime;

    /** 加班结束时间 */
    private LocalTime endTime;

    /** 加班时长（小时） */
    private BigDecimal hours;

    /** 补偿方式 */
    private String compensation;

    /** 关联项目名称 */
    private String projectName;

    /** 加班事由 */
    private String reason;

    /** 状态 */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
