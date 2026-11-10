package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班分配保存请求（新建/编辑）
 *
 * @author BlueWhale
 */
@Data
public class MesShiftAssignmentSaveRequest {

    /** 排班 ID（编辑时必填） */
    private Long id;

    /** 排班日期 */
    @NotNull(message = "排班日期不能为空")
    private LocalDate shiftDate;

    /** 班次编码 */
    @NotBlank(message = "班次编码不能为空")
    @Size(max = 64, message = "班次编码长度不能超过 64")
    private String shiftCode;

    /** 班组 ID */
    @NotNull(message = "班组不能为空")
    private Long teamId;

    /** 工位 ID */
    @NotNull(message = "工位不能为空")
    private Long stationId;

    /** 工作中心 ID（为空时取工位所属工作中心） */
    private Long workCenterId;

    /** 员工 ID */
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    /** 员工姓名 */
    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过 64")
    private String employeeName;

    /** 是否主操作：0 否 / 1 是 */
    private Integer primaryOperator;

    /** 班次开始时间 */
    private LocalDateTime startAt;

    /** 班次结束时间 */
    private LocalDateTime endAt;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
