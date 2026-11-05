package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请假申请新增 / 更新命令。
 *
 * @author BlueWhale
 */
@Data
public class LeaveSaveCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请单号（租户内唯一） */
    @NotBlank(message = "申请单号不能为空")
    @Size(max = 64, message = "申请单号长度不能超过 64")
    private String code;

    /** 申请人账号 */
    @NotBlank(message = "申请人不能为空")
    @Size(max = 64, message = "申请人长度不能超过 64")
    private String applicant;

    /** 申请人用户ID（用于额度冻结与档案校验，历史调用方可不传） */
    private Long applicantId;

    /** 请假类型 */
    @NotBlank(message = "请假类型不能为空")
    @Pattern(regexp = "ANNUAL|SICK|PERSONAL", message = "请假类型仅支持 ANNUAL / SICK / PERSONAL")
    private String leaveType;

    /** 开始日期 */
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    /** 开始时段：AM 上午 / PM 下午 */
    @Pattern(regexp = "AM|PM", message = "开始时段仅支持 AM / PM")
    private String startPeriod;

    /** 结束日期 */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 结束时段：AM 上午 / PM 下午 */
    @Pattern(regexp = "AM|PM", message = "结束时段仅支持 AM / PM")
    private String endPeriod;

    /** 工作交接人用户ID */
    private Long handoverUserId;

    /** 请假天数 */
    @NotNull(message = "请假天数不能为空")
    @DecimalMin(value = "0.5", message = "请假天数不能少于 0.5 天")
    @DecimalMax(value = "365.0", message = "请假天数不能超过 365 天")
    private BigDecimal days;

    /** 请假事由 */
    @Size(max = 512, message = "请假事由长度不能超过 512")
    private String reason;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
