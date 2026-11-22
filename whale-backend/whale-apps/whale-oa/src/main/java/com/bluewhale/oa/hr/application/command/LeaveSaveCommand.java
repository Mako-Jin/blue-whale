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
import java.util.List;

/**
 * 请假申请新增 / 更新命令。
 *
 * @author BlueWhale
 */
@Data
public class LeaveSaveCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请单号（租户内唯一） */
    @NotBlank(message = "{validation.oa.leave.code-required}")
    @Size(max = 64, message = "{validation.oa.leave.code-size}")
    private String code;

    /** 申请人账号 */
    @NotBlank(message = "{validation.oa.leave.applicant-required}")
    @Size(max = 64, message = "{validation.oa.leave.applicant-size}")
    private String applicant;

    /** 申请人用户ID（用于额度冻结与档案校验，历史调用方可不传） */
    private Long applicantId;

    /** 请假类型 */
    @NotBlank(message = "{validation.oa.leave.leave-type-required}")
    @Pattern(regexp = "ANNUAL|SICK|PERSONAL", message = "{validation.oa.leave.leave-type-pattern}")
    private String leaveType;

    /** 开始日期 */
    @NotNull(message = "{validation.oa.leave.start-date-required}")
    private LocalDate startDate;

    /** 开始时段：AM 上午 / PM 下午 */
    @Pattern(regexp = "AM|PM", message = "{validation.oa.leave.start-period-pattern}")
    private String startPeriod;

    /** 结束日期 */
    @NotNull(message = "{validation.oa.leave.end-date-required}")
    private LocalDate endDate;

    /** 结束时段：AM 上午 / PM 下午 */
    @Pattern(regexp = "AM|PM", message = "{validation.oa.leave.end-period-pattern}")
    private String endPeriod;

    /** 工作交接人用户ID */
    private Long handoverUserId;

    /** 请假天数 */
    @NotNull(message = "{validation.oa.leave.days-required}")
    @DecimalMin(value = "0.5", message = "{validation.oa.leave.days-min}")
    @DecimalMax(value = "365.0", message = "{validation.oa.leave.days-max}")
    private BigDecimal days;

    /** 请假事由 */
    @Size(max = 512, message = "{validation.oa.leave.reason-size}")
    private String reason;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.leave.remark-size}")
    private String remark;

    /** 新上传附件文件ID（最多 5 个） */
    @Size(max = 5, message = "{validation.oa.leave.file-ids-size}")
    private List<Long> fileIds;

    /** 编辑时保留的已有附件引用ID；为 {@code null} 表示全部保留 */
    @Size(max = 5, message = "{validation.oa.leave.keep-attachment-ids-size}")
    private List<Long> keepAttachmentIds;
}
