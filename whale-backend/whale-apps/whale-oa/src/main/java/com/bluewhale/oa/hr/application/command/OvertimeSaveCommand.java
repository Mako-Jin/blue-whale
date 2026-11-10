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
    @NotBlank(message = "申请单号不能为空")
    @Size(max = 64, message = "申请单号长度不能超过 64")
    private String code;

    /** 申请人账号 */
    @NotBlank(message = "申请人不能为空")
    @Size(max = 64, message = "申请人长度不能超过 64")
    private String applicant;

    /** 申请人用户ID（用于档案校验与调休入账） */
    private Long applicantId;

    /** 加班日期 */
    @NotNull(message = "加班日期不能为空")
    private LocalDate overtimeDate;

    /** 加班开始时间 */
    @NotNull(message = "加班开始时间不能为空")
    private LocalTime startTime;

    /** 加班结束时间 */
    @NotNull(message = "加班结束时间不能为空")
    private LocalTime endTime;

    /** 补偿方式：COMP_TIME 调休 / PAY 加班费 */
    @Pattern(regexp = "COMP_TIME|PAY", message = "补偿方式仅支持 COMP_TIME / PAY")
    private String compensation;

    /** 关联项目名称 */
    @Size(max = 128, message = "项目名称长度不能超过 128")
    private String projectName;

    /** 加班事由 */
    @Size(max = 512, message = "加班事由长度不能超过 512")
    private String reason;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
