package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 加班申请新增 / 更新命令。
 *
 * @author BlueWhale
 */
@Data
public class OvertimeSaveCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请单号（租户内唯一） */
    @NotBlank(message = "{validation.oa.overtime.code-required}")
    @Size(max = 64, message = "{validation.oa.overtime.code-size}")
    private String code;

    /** 申请人账号 */
    @NotBlank(message = "{validation.oa.overtime.applicant-required}")
    @Size(max = 64, message = "{validation.oa.overtime.applicant-size}")
    private String applicant;

    /** 申请人用户ID（用于档案校验与调休入账） */
    private Long applicantId;

    /** 加班日期 */
    @NotNull(message = "{validation.oa.overtime.overtime-date-required}")
    private LocalDate overtimeDate;

    /** 加班开始时间 */
    @NotNull(message = "{validation.oa.overtime.start-time-required}")
    private LocalTime startTime;

    /** 加班结束时间 */
    @NotNull(message = "{validation.oa.overtime.end-time-required}")
    private LocalTime endTime;

    /** 补偿方式：COMP_TIME 调休 / PAY 加班费 */
    @Pattern(regexp = "COMP_TIME|PAY", message = "{validation.oa.overtime.compensation-pattern}")
    private String compensation;

    /** 关联项目名称 */
    @Size(max = 128, message = "{validation.oa.overtime.project-name-size}")
    private String projectName;

    /** 加班事由 */
    @Size(max = 512, message = "{validation.oa.overtime.reason-size}")
    private String reason;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.overtime.remark-size}")
    private String remark;
}
