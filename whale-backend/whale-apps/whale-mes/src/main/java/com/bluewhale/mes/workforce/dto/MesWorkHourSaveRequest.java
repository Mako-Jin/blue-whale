package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工时登记请求
 *
 * @author BlueWhale
 */
@Data
public class MesWorkHourSaveRequest {

    /** 员工 ID */
    @NotNull(message = "{validation.mes.mes-work-hour.employee-id-required}")
    private Long employeeId;

    /** 员工姓名 */
    @NotBlank(message = "{validation.mes.mes-work-hour.employee-name-required}")
    @Size(max = 64, message = "{validation.mes.mes-work-hour.employee-name-size}")
    private String employeeName;

    /** 工作日期 */
    @NotNull(message = "{validation.mes.mes-work-hour.work-date-required}")
    private LocalDate workDate;

    /** 任务 ID */
    @NotNull(message = "{validation.mes.mes-work-hour.task-id-required}")
    private Long taskId;

    /** 开始时间 */
    @NotNull(message = "{validation.mes.mes-work-hour.start-at-required}")
    private LocalDateTime startAt;

    /** 结束时间 */
    @NotNull(message = "{validation.mes.mes-work-hour.end-at-required}")
    private LocalDateTime endAt;

    /** 暂停秒数 */
    @Min(value = 0, message = "{validation.mes.mes-work-hour.pause-seconds-min}")
    private Long pauseSeconds;

    /** 来源：REPORT/MANUAL */
    @Pattern(regexp = "REPORT|MANUAL", message = "{validation.mes.mes-work-hour.source-pattern}")
    private String source;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-work-hour.remark-size}")
    private String remark;
}
