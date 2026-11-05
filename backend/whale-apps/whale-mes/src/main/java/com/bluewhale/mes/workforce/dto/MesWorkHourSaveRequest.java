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
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    /** 员工姓名 */
    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过 64")
    private String employeeName;

    /** 工作日期 */
    @NotNull(message = "工作日期不能为空")
    private LocalDate workDate;

    /** 任务 ID */
    @NotNull(message = "任务不能为空")
    private Long taskId;

    /** 开始时间 */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startAt;

    /** 结束时间 */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endAt;

    /** 暂停秒数 */
    @Min(value = 0, message = "暂停秒数不能为负")
    private Long pauseSeconds;

    /** 来源：REPORT/MANUAL */
    @Pattern(regexp = "REPORT|MANUAL", message = "工时来源不合法")
    private String source;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
